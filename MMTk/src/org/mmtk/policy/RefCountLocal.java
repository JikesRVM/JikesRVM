/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Statistics;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
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
final class RefCountLocal extends SegregatedFreeList
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  private static SharedDeque oldRootPool;
  private static SharedDeque tracingPool;

  private static final int DEC_COUNT_QUANTA = 2000; // do 2000 decs at a time
  private static final double DEC_TIME_FRACTION = 0.66; // 2/3 remaining time

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
  private AddressDeque tracingBuffer;

  private boolean decrementPhase = false;

  private TrialDeletion cycleDetector;

  // counters
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;

  private boolean cycleBufferAisOpen = true;

  protected final boolean preserveFreeList() { return true; }
  protected final boolean maintainInUse() { return true; }

  /****************************************************************************
   *
   * Initialization
   */

 /**
   * Constructor
   *
   * @param space The ref count space with which this local thread is
   * associated.
   * @param plan The plan with which this local thread is associated.
   */
  RefCountLocal(RefCountSpace space, Plan plan_, RefCountLOSLocal los_, 
		AddressDeque dec, AddressDeque root) {
    super(space.getVMResource(), space.getMemoryResource(), plan_);
    rcSpace = space;
    plan = plan_;
    los = los_;

    decBuffer = dec;
    newRootSet = root;
    oldRootSet = new AddressDeque("old root set", oldRootPool);
    if (Plan.REF_COUNT_CYCLE_DETECTION)
      cycleDetector = new TrialDeletion(this, plan_);
  }

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    oldRootPool = new SharedDeque(Plan.getMetaDataRPA(), 1);
    oldRootPool.newClient();

    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];
    
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
	int avail = BlockAllocator.blockSize(blk) - FREE_LIST_HEADER_BYTES;
	int cells = avail/cellSize[sc];
	blockSizeClass[sc] = blk;
	cellsInBlock[sc] = cells;
	blockHeaderSize[sc] = FREE_LIST_HEADER_BYTES;
	if (((avail < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
	    ((avail > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
	  break;
      }
    }
  }

  /****************************************************************************
   *
   * Allocation
   */
  public final void postAlloc(VM_Address cell, VM_Address block, int sizeClass,
			      int bytes, boolean inGC) throws VM_PragmaInline{}
  protected final void postExpandSizeClass(VM_Address block, int sizeClass){}
  protected final VM_Address advanceToBlock(VM_Address block, int sizeClass){
    return getFreeList(block);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection.
   */
  public final void prepare(boolean time) { 
    flushFreeLists();
    if (RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose > 2) processRootBufsAndCount(); else processRootBufs();
    }
  }

  /**
   * Finish up after a collection.
   */
  public final void release(boolean time) {
    flushFreeLists();
    VM_Interface.rendezvous(4400);
    if (!RefCountSpace.INC_DEC_ROOT) {
      processOldRootBufs();
    }
    if (time) Statistics.rcDecTime.start();
    processDecBufs();
    if (time) Statistics.rcDecTime.stop();
    if (Plan.REF_COUNT_CYCLE_DETECTION) {
      if (time) Statistics.cdTime.start();
      if (cycleDetector.collectCycles(time)) 
	processDecBufs();
      if (time) Statistics.cdTime.stop();
    }
    if (!RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose > 2) processRootBufsAndCount(); else processRootBufs();
    }
    restoreFreeLists();
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
    } while (!tgt.isZero() && VM_Interface.cycles() < limit);
    decrementPhase = false;
  }

  /**
   * Process the root buffers from the previous GC, if the object is
   * no longer live release it.
   */
  private final void processOldRootBufs() {
    VM_Address object;
    while (!(object = oldRootSet.pop()).isZero()) {
      if (!RCBaseHeader.isLiveRC(object)) {
	release(object);
      }
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
      else 
	RCBaseHeader.unsetRoot(object);
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
    ScanObject.enumeratePointers(object, plan.decEnum);
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
    if (space == Plan.LOS_SPACE) {
      los.free(ref);
    } else {
      byte tag = VMResource.getTag(ref);
      
      VM_Address block = BlockAllocator.getBlockStart(ref, tag);
      int sizeClass = getBlockSizeClass(block);
      int index = (ref.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
      VM_Address cell = block.add(blockHeaderSize[sizeClass]).add(index*cellSize[sizeClass]);
      free(cell, block, sizeClass);
    }
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
    Log.write("<GC "); Log.write(Statistics.gcCount); Log.write(" "); 
    Log.write(incCounter); Log.write(" incs, ");
    Log.write(decCounter); Log.write(" decs, ");
    Log.write(rootCounter); Log.write(" roots");
    if (Plan.REF_COUNT_CYCLE_DETECTION) {
      Log.write(", "); 
      Log.write(purpleCounter);Log.write(" purple");
    }
    Log.writeln(">");
  }


  /**
   * Print out timing info for last GC
   */
  public final void printTimes(boolean totals) {
    double time;
    time = (totals) ? Statistics.rcIncTime.sum() : Statistics.rcIncTime.lastMs();
    Log.write(" inc: "); Log.write(time);
    time = (totals) ? Statistics.rcDecTime.sum() : Statistics.rcDecTime.lastMs();
    Log.write(" dec: "); Log.write(time);
    if (Plan.REF_COUNT_CYCLE_DETECTION) {
      time = (totals) ? Statistics.cdTime.sum() : Statistics.cdTime.lastMs();
      Log.write(" cd: "); Log.write(time);
      cycleDetector.printTimes(totals);
    }
  }
}
