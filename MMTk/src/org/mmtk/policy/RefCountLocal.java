/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.RCBaseHeader;
import org.mmtk.utility.AddressDeque;
import org.mmtk.utility.AddressPairDeque;
import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.SegregatedFreeList;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;
import org.mmtk.utility.Scan;
import org.mmtk.utility.SharedDeque;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.RCSanityEnumerator;
import org.mmtk.utility.TrialDeletion;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements thread-local behavior for a reference counted
 * space.  Each instance of this class captures state associated with
 * one thread/CPU acting over a particular reference counted space.
 * Since all state is thread local, instance methods of this class are
 * not required to be synchronized.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class RefCountLocal extends SegregatedFreeList
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  private static SharedDeque oldRootPool;

  // sanity tracing
  private static SharedDeque incSanityRootsPool;
  private static SharedDeque sanityWorkQueuePool;
  private static SharedDeque checkSanityRootsPool;
  private static SharedDeque sanityImmortalPoolA;
  private static SharedDeque sanityImmortalPoolB;
  private static SharedDeque sanityLastGCPool;
  public static int rcLiveObjects = 0;
  public static int sanityLiveObjects = 0;

  private static final int DEC_COUNT_QUANTA = 2000; // do 2000 decs at a time
  private static final double DEC_TIME_FRACTION = 0.66; // 2/3 remaining time

  // Statistics
  private static Timer decTime;
  private static Timer incTime;
  private static Timer cdTime;


  /****************************************************************************
   *
   * Instance variables
   */
  private RefCountSpace rcSpace;
  private RefCountLOSLocal los;
  private Plan plan;

  private AddressDeque incBuffer;
  private AddressDeque decBuffer;
  private AddressDeque newRootSet;
  private AddressDeque oldRootSet;

  private boolean decrementPhase = false;

  private TrialDeletion cycleDetector;

  // counters
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;

  private boolean cycleBufferAisOpen = true;

  // sanity tracing
  private AddressDeque incSanityRoots;
  private AddressPairDeque sanityWorkQueue;
  private AddressDeque checkSanityRoots;
  private AddressDeque sanityImmortalSetA;
  private AddressDeque sanityImmortalSetB;
  private AddressDeque sanityLastGCSet;

  protected final boolean preserveFreeList() { return true; }
  protected final boolean maintainInUse() { return true; }

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    oldRootPool = new SharedDeque(Plan.getMetaDataRPA(), 1);
    oldRootPool.newClient();

    if (RefCountSpace.RC_SANITY_CHECK) {
      incSanityRootsPool = new SharedDeque(Plan.getMetaDataRPA(), 1);
      incSanityRootsPool.newClient();
      sanityWorkQueuePool = new SharedDeque(Plan.getMetaDataRPA(), 2);
      sanityWorkQueuePool.newClient();
      checkSanityRootsPool = new SharedDeque(Plan.getMetaDataRPA(), 1);
      checkSanityRootsPool.newClient();
      sanityImmortalPoolA = new SharedDeque(Plan.getMetaDataRPA(), 1);
      sanityImmortalPoolA.newClient();
      sanityImmortalPoolB = new SharedDeque(Plan.getMetaDataRPA(), 1);
      sanityImmortalPoolB.newClient();
      sanityLastGCPool = new SharedDeque(Plan.getMetaDataRPA(), 1);
      sanityLastGCPool.newClient();
    }

    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];
    
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int usableBytes = BlockAllocator.blockSize(blk);
        int cells = usableBytes/cellSize[sc];
        blockSizeClass[sc] = blk;
        cellsInBlock[sc] = cells;
        /*cells must start at multiple of BYTES_IN_PARTICLE
           because cellSize is also supposed to be multiple, this should do the trick: */
        blockHeaderSize[sc] = BlockAllocator.blockSize(blk) - cells * cellSize[sc];
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
    decTime = new Timer("dec", false, true);
    incTime = new Timer("inc", false, true);
    cdTime = new Timer("cd", false, true);
  }

 /**
   * Constructor
   *
   * @param space The ref count space with which this local thread is
   * associated.
   * @param plan The plan with which this local thread is associated.
   */
  public RefCountLocal(RefCountSpace space, Plan plan_, RefCountLOSLocal los_, 
                       AddressDeque dec, AddressDeque root) {
    super(space.getVMResource(), space.getMemoryResource());
    rcSpace = space;
    plan = plan_;
    los = los_;

    decBuffer = dec;
    newRootSet = root;
    oldRootSet = new AddressDeque("old root set", oldRootPool);
    if (RefCountSpace.RC_SANITY_CHECK) {
      incSanityRoots = new AddressDeque("sanity increment root set", incSanityRootsPool);
      sanityWorkQueue = new AddressPairDeque(sanityWorkQueuePool);
      checkSanityRoots = new AddressDeque("sanity check root set", checkSanityRootsPool);
      sanityImmortalSetA = new AddressDeque("immortal set A", sanityImmortalPoolA);
      sanityImmortalSetB = new AddressDeque("immortal set B", sanityImmortalPoolB);
      sanityLastGCSet = new AddressDeque("last GC set", sanityLastGCPool);
    }
    if (Plan.REF_COUNT_CYCLE_DETECTION)
      cycleDetector = new TrialDeletion(this, plan_);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Prepare the next block in the free block list for use by the free
   * list allocator.  In the case of lazy sweeping this involves
   * sweeping the available cells.  <b>The sweeping operation must
   * ensure that cells are pre-zeroed</b>, as this method must return
   * pre-zeroed cells.
   *
   * @param block The block to be prepared for use
   * @param sizeClass The size class of the block
   * @return The address of the first pre-zeroed cell in the free list
   * for this block, or zero if there are no available cells.
   */
  protected final VM_Address advanceToBlock(VM_Address block, int sizeClass) {
    return makeFreeListFromLiveBits(block, sizeClass);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection.
   */
  public final void prepare(boolean time) { 
    if (RefCountSpace.RC_SANITY_CHECK && !Options.noFinalizer) 
      VM_Interface.sysFail("Ref count sanity checks must be run with finalization disabled (-X:gc:noFinalizer=true)");

    flushFreeLists();
    if (RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose > 2)
        processRootBufsAndCount(); 
      else
        processRootBufs();
    }
  }

  /**
   * Finish up after a collection.
   */
  public final void release(int count, boolean timekeeper) {
    flushFreeLists();
    VM_Interface.rendezvous(4400);
    if (!RefCountSpace.INC_DEC_ROOT) {
      processOldRootBufs();
    }
    if (timekeeper) decTime.start();
    if (RefCountSpace.RC_SANITY_CHECK) incSanityTrace();
    processDecBufs();
    if (timekeeper) decTime.stop();
    VM_Interface.rendezvous(4410);
    sweepBlocks();
    if (Plan.REF_COUNT_CYCLE_DETECTION) {
      if (timekeeper) cdTime.start();
      if (cycleDetector.collectCycles(count, timekeeper)) 
        processDecBufs();
      if (timekeeper) cdTime.stop();
    }
    VM_Interface.rendezvous(4420);
    if (RefCountSpace.RC_SANITY_CHECK) checkSanityTrace();
    if (!RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose > 2) 
        processRootBufsAndCount(); 
      else 
        processRootBufs();
    }
    restoreFreeLists();
  }

  /**
   * Sweep all blocks for free objects. 
   */
  private final void sweepBlocks() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      VM_Extent blockSize = VM_Extent.fromInt(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
      while (!block.isZero()) {
        /* check to see if block is completely free and if possible
         * free the entire block */
        VM_Address next = BlockAllocator.getNextBlock(block);
        if (isEmpty(block, blockSize))
          freeBlock(block, sizeClass);
        block = next;
      }
    }
  }

  /**
   * Process the decrement buffers
   */
  private final void processDecBufs() {
    VM_Address tgt = VM_Address.zero();
    long tc = Plan.getTimeCap();
    long remaining =  tc - VM_Interface.cycles();
    long limit = tc - (long)(remaining * (1 - DEC_TIME_FRACTION));
    decrementPhase = true;
    decCounter = 0;
    do {
      int count = 0;
      while (count < DEC_COUNT_QUANTA && !(tgt = decBuffer.pop()).isZero()) {
        decrement(tgt);
        count++;
      } 
      decCounter += count;
    } while (!tgt.isZero() && (RefCountSpace.RC_SANITY_CHECK || VM_Interface.cycles() < limit));
    decrementPhase = false;
  }

  /**
   * Process the root buffers from the previous GC, if the object is
   * no longer live release it.
   */
  private final void processOldRootBufs() {
    VM_Address object;
    while (!(object = oldRootSet.pop()).isZero()) {
      if (!RCBaseHeader.isLiveRC(object))
        release(object);
    }
  }

  /**
   * Process the root buffers, moving entries over to the decrement
   * buffers for the next GC. 
   */
  private final void processRootBufs() {
    VM_Address object;
    while (!(object = newRootSet.pop()).isZero()) {
      if (RefCountSpace.INC_DEC_ROOT)
        decBuffer.push(object);
      else {
        RCBaseHeader.unsetRoot(object);
        oldRootSet.push(object);
      }
    }
  }

  /**
   * Process the root buffers and maintain statistics.
   */
  private final void processRootBufsAndCount() {
    VM_Address object;
    rootCounter = 0;
    while (!(object = newRootSet.pop()).isZero()) {
      if (RefCountSpace.INC_DEC_ROOT)
        decBuffer.push(object);
      else {
        RCBaseHeader.unsetRoot(object);
        oldRootSet.push(object);
      }
      rootCounter++;
    }
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Decrement the reference count of an object.  If the count drops
   * to zero, the release the object, performing recursive decremetns
   * and freeing the object.  If not, then if cycle detection is being
   * used, record this object as the possible source of a cycle of
   * garbage (all non-zero decrements are potential sources of new
   * cycles of garbage.
   *
   * @param object The object whose count is to be decremented
   */
  public final void decrement(VM_Address object) 
    throws VM_PragmaInline {
    int state = RCBaseHeader.decRC(object);
    if (state == RCBaseHeader.DEC_KILL)
      release(object);
    else if (Plan.REF_COUNT_CYCLE_DETECTION && state ==RCBaseHeader.DEC_BUFFER)
      cycleDetector.possibleCycleRoot(object);
  }

  /**
   * An object is dead, so before freeing it, scan the object for
   * recursive decrement (each outgoing pointer from this dead object
   * is now dead, so the targets must have their counts decremented).<p>
   *
   * If the object is being held in a buffer by the cycle detector,
   * then the object must not be freed.  It will be freed later when
   * the cycle detector processes its buffers.
   *
   * @param object The object to be released
   */
  private final void release(VM_Address object) 
    throws VM_PragmaInline {
    // this object is now dead, scan it for recursive decrement
    if (RefCountSpace.RC_SANITY_CHECK) rcLiveObjects--;
    Scan.enumeratePointers(object, plan.decEnum);
    if (!Plan.REF_COUNT_CYCLE_DETECTION || !RCBaseHeader.isBuffered(object)) 
      free(object);
  }

  /**
   * Free an object.  First determine whether it is managed by the LOS
   * or the regular free list.  If managed by LOS, delegate freeing to
   * the LOS.  Otherwise, establish the cell, block and sizeclass for
   * this object and call the free method of our subclass.
   *
   * @param object The object to be freed.
   */
  public final void free(VM_Address object) 
    throws VM_PragmaInline {
    VM_Address ref = VM_Interface.refToAddress(object);
    byte space = VMResource.getSpace(ref);
    if (space == Plan.LOS_SPACE)
      los.free(ref);
    else
      deadObject(object);
  }

  /****************************************************************************
   *
   * Sanity check support
   *
   * Sanity check code allows reference counts to be cross-checked
   * with counts established via a transitive closure.  The code has
   * two significant limitations:
   *
   * . Finalization is not supported---it must be turned off to avoid
   *   anomalies relating to finalization's odd reachability semantics.
   *
   * . Currently immortal (and boot image) objects are uncollected.
   *   If any such object were to become unreachable, decrements would
   *   not be issued for any referent RC objects---leading to a
   *   discrepancy between RC and sanity RC counts.
   *
   * To maximize the utility of this mechanism in the face of the
   * above problems, it is best to trigger frequent GCs by setting the
   * metadata limit to its minimum.
   */
 
  /**
   * Add an entry to the root buffer for the increment sanity
   * traversal. (used only for sanity checks).
   *
   * @param object The object to be added to the root buffer
   */
  public final void incSanityTraceRoot(VM_Address object) {
    incSanityRoots.push(object);
  }

  /**
   * Add an entry to the sanity traversal work queue (the work queue
   * is used instead of a stack in establishing the transitive
   * closure). (used only for sanity checks).
   *
   * @param object The object to be added to the work queue buffer
   * @param location The location from which the object is reached
   */
  public final void sanityTraceEnqueue(VM_Address object, 
                                       VM_Address location) {
    sanityWorkQueue.push(object, location);
  }


  /**
   * Perform a sanity increment traversal.  This involves starting
   * from roots and performing a transitive closure, incrementing the
   * sanity reference count of each object each time it is visited.
   */
  final void incSanityTrace() {
    sanityLiveObjects = 0;
    VM_Address object;
    while (!(object = sanityImmortalSetA.pop()).isZero()) {
      plan.checkSanityTrace(object, VM_Address.zero());
      sanityImmortalSetB.push(object);
    }
    while (!(object = incSanityRoots.pop()).isZero()) {
      plan.incSanityTrace(object, VM_Address.zero(), true);
      checkSanityRoots.push(object);
    }
    while (!(object = sanityWorkQueue.pop1()).isZero()) {
      plan.incSanityTrace(object, sanityWorkQueue.pop2(), false);
    }
  }

  /**
   * Perform a sanity check traversal.  This involves starting from
   * roots and performing a transitive closure, checking the sanity
   * reference count of each object against its actual reference
   * count, failing with an error if there is a mismatch.  A check is
   * also made of the number of live objects (comparing RC and
   * tracing).
   */
  final void checkSanityTrace() {
    VM_Address object;
    while (!(object = sanityLastGCSet.pop()).isZero()) {
      RCBaseHeader.checkOldObject(object);
    }
    while (!(object = sanityImmortalSetB.pop()).isZero()) {
      plan.checkSanityTrace(object, VM_Address.zero());
      sanityImmortalSetA.push(object);
    }
    while (!(object = checkSanityRoots.pop()).isZero()) {
      if (VM_Interface.getCollectionCount() == 1) checkForImmortal(object);
      plan.checkSanityTrace(object, VM_Address.zero());
    }
    while (!(object = sanityWorkQueue.pop1()).isZero()) {
      if (VM_Interface.getCollectionCount() == 1) checkForImmortal(object);
      plan.checkSanityTrace(object, sanityWorkQueue.pop2());
    }
    if (rcLiveObjects != sanityLiveObjects) {
      Log.write("live mismatch: "); Log.write(rcLiveObjects); 
      Log.write(" (rc) != "); Log.write(sanityLiveObjects);
      Log.writeln(" (sanityRC)");
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    }
  }

  static int lastGCsize = 0;
  public final void addLiveSanityObject(VM_Address object) {
    lastGCsize++;
    sanityLastGCSet.push(object);
  }

  public final void addImmortalObject(VM_Address object) {
    sanityImmortalSetA.push(object);
  }

  final void checkForImmortal(VM_Address object) {
    byte space = VMResource.getSpace(VM_Interface.refToAddress(object));
    if (space == Plan.IMMORTAL_SPACE || space == Plan.BOOT_SPACE) {
      addImmortalObject(object);
    }
  }

  /**
   * An allocation has occured, so increment the count of live objects.
   */
  public final static void sanityAllocCount(VM_Address object) {
    rcLiveObjects++;
  }

 
  /****************************************************************************
   *
   * Misc
   */
  
  /**
   * Setter method for the purple counter.
   *
   * @param purple The new value for the purple counter.
   */
  public final void setPurpleCounter(int purple) {
    purpleCounter = purple;
  }
  
  /**
   * Print out statistics on increments, decrements, roots and
   * potential garbage cycles (purple objects).
   */
  public final void printStats() {
    Log.write("<GC "); Log.write(Stats.gcCount()); Log.write(" "); 
    Log.write(incCounter); Log.write(" incs, ");
    Log.write(decCounter); Log.write(" decs, ");
    Log.write(rootCounter); Log.write(" roots");
    if (Plan.REF_COUNT_CYCLE_DETECTION) {
      Log.write(", "); 
      Log.write(purpleCounter);Log.write(" purple");
    }
    Log.writeln(">");
  }
}
