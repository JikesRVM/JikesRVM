/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_JavaHeader;

/**
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class RefCountLocal extends SegregatedFreeList
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static SharedQueue rootPool;
  private static SharedQueue tracingPool;

  private static final boolean sanityTracing = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private RefCountSpace rcSpace;
  private RefCountLOSLocal los;
  private Plan plan;

  private AddressQueue incBuffer;
  private AddressQueue decBuffer;
  private AddressQueue rootSet;
  private AddressQueue tracingBuffer;

  private boolean decrementPhase = false;

  private CycleDetector cycleDetector;

  // counters
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;

  private boolean cycleBufferAisOpen = true;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param space The ref count space with which this local thread is
   * associated.
   * @param plan The plan with which this local thread is associated.
   */
  RefCountLocal(RefCountSpace space, Plan plan_, RefCountLOSLocal los_, 
		AddressQueue inc, AddressQueue dec, AddressQueue root) {
    super(space.getVMResource(), space.getMemoryResource(), plan_);
    rcSpace = space;
    plan = plan_;
    los = los_;

    incBuffer = inc;
    decBuffer = dec;
    rootSet = root;
    if (sanityTracing) {
      tracingBuffer = new AddressQueue("tracing buffer", tracingPool);
    }
    if (Plan.refCountCycleDetection)
      cycleDetector = new TrialDeletion(this, plan_);
  }

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    rootPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    rootPool.newClient();
    if (sanityTracing) {
      tracingPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
      tracingPool.newClient();
    }

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
	if (((avail < PAGE_SIZE) && (cells*2 > MAX_CELLS)) ||
	    ((avail > (PAGE_SIZE>>1)) && (cells > MIN_CELLS)))
	  break;
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //
  public final void postAlloc(VM_Address cell, VM_Address block, int sizeClass,
			      int bytes) throws VM_PragmaInline {}
  protected final void postExpandSizeClass(VM_Address block, int sizeClass){}
  protected final void advanceToBlock(VM_Address block, int sizeClass){}

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a collection.
   */
  public final void prepare() { 
    flushFreeLists();
    if (Plan.verbose > 2) processRootBufsAndCount(); else processRootBufs();
  }

  /**
   * Finish up after a collection.
   */
  public final void release() {
    if (sanityTracing) VM.sysWrite("--------- Increment --------\n");
    if (Plan.verbose > 2) processIncBufsAndCount(); else processIncBufs();
    if (sanityTracing) VM.sysWrite("--------- Decrement --------\n");
    VM_CollectorThread.gcBarrier.rendezvous();
    decrementPhase = true;
    if (Plan.verbose > 2) processDecBufsAndCount(); else processDecBufs();
    decrementPhase = false;
    if (Plan.refCountCycleDetection)
      cycleDetector.collectCycles();
    restoreFreeLists();
    
    if (sanityTracing) rcSanityCheck();
  }

  private final void processIncBufs() {
    VM_Address tgt;
    while (!(tgt = incBuffer.pop()).isZero()) {
      rcSpace.increment(tgt);
    }
  }
  private final void processIncBufsAndCount() {
    VM_Address tgt;
    incCounter = 0;
    while (!(tgt = incBuffer.pop()).isZero()) {
      rcSpace.increment(tgt);
      incCounter++;
    }
  }
  private final void processDecBufs() {
    VM_Address tgt;
    while (!(tgt = decBuffer.pop()).isZero()) {
      decrement(tgt);
    }
  }
  private final void processDecBufsAndCount() {
    VM_Address tgt;
    decCounter = 0;
    while (!(tgt = decBuffer.pop()).isZero()) {
      decrement(tgt);
      decCounter++;
    }
  }
  private final void processRootBufs() {
    VM_Address tgt;
    while (!(tgt = rootSet.pop()).isZero())
      decBuffer.push(tgt);
  }
  private final void processRootBufsAndCount() {
    VM_Address tgt;
    rootCounter = 0;
    while (!(tgt = rootSet.pop()).isZero()) {
      decBuffer.push(tgt);
      rootCounter++;
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * A pointer location has been enumerated by ScanObject.  This is
   * the callback method, allowing the plan to perform an action with
   * respect to that location.
   *
   * @param object
   */
  public final void enumeratePointer(VM_Address object)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(!object.isZero());

    if (!Plan.refCountCycleDetection || decrementPhase)
      decBuffer.push(object);
    else if (Plan.refCountCycleDetection)
      cycleDetector.enumeratePointer(object);
  }
  
  public final void decrement(VM_Address object) 
    throws VM_PragmaInline {
    if (RCBaseHeader.decRC(object))
      release(object);
    else if (Plan.refCountCycleDetection)
      cycleDetector.possibleCycleRoot(object);
  }

  private final void release(VM_Address object) 
    throws VM_PragmaInline {
    // this object is now dead, scan it for recursive decrement
    ScanObject.enumeratePointers(object, plan);
    if (!Plan.refCountCycleDetection ||	!RCBaseHeader.isBuffered(object)) 
      free(object);
  }

  public final void free(VM_Address object) 
    throws VM_PragmaInline {
    VM_Address ref = VM_JavaHeader.getPointerInMemoryRegion(object);
    byte space = VMResource.getSpace(ref);
    if (space == Plan.LOS_SPACE)
      los.free(ref);
    else {
      byte tag = VMResource.getTag(ref);
      
      VM_Address block = BlockAllocator.getBlockStart(ref, tag);
      int sizeClass = getSizeClass(block);
      int index = (ref.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
      VM_Address cell = block.add(blockHeaderSize[sizeClass]).add(index*cellSize[sizeClass]);
      free(cell, block, sizeClass);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Methods relating to sanity tracing (tracing used to check
  // reference counts)
  //
  private final void rcSanityCheck() {
    if (VM.VerifyAssertions) VM._assert(sanityTracing);
    VM_Address obj;
    int checked = 0;
    while (!(obj = tracingBuffer.pop()).isZero()) {
      checked++;
      int rc = RCBaseHeader.getRC(obj);
      int sanityRC = RCBaseHeader.getTracingRC(obj);
      RCBaseHeader.clearTracingRC(obj);
      if (rc != sanityRC) {
	VM.sysWrite("---> ");
	VM.sysWrite(checked);
	VM.sysWrite(" roots checked, RC mismatch: ");
	VM.sysWrite(obj); VM.sysWrite(" -> ");
	VM.sysWrite(rc); VM.sysWrite(" (rc) != ");
	VM.sysWrite(sanityRC); VM.sysWrite(" (sanity)\n");
	if (VM.VerifyAssertions) VM._assert(false);
      }
    }
  }
  public final void postAllocImmortal(VM_Address object)
    throws VM_PragmaInline {
    if (sanityTracing) {
      if (rcSpace.bootImageMark)
	RCBaseHeader.setBufferedBit(object);
      else
	RCBaseHeader.clearBufferedBit(object);
    }
  }

  public void rootScan(VM_Address obj) {
    if (VM.VerifyAssertions) VM._assert(sanityTracing);
    // this object has been explicitly scanned as part of the root scanning
    // process.  Mark it now so that it does not get re-scanned.
    if (obj.LE(Plan.RC_START) && obj.GE(Plan.BOOT_START)) {
      if (rcSpace.bootImageMark)
	RCBaseHeader.setBufferedBit(obj);
      else
	RCBaseHeader.clearBufferedBit(obj);
    }
  }

  public final void addToTraceBuffer(VM_Address root) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(sanityTracing);
    tracingBuffer.push(VM_Magic.objectAsAddress(root));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Misc
  //
  public final void setPurpleCounter(int purple) {
    purpleCounter = purple;
  }
  public final void printStats() {
    VM.sysWrite("<GC ", Statistics.gcCount); VM.sysWrite(" "); 
    VM.sysWriteInt(incCounter); VM.sysWrite(" incs, ");
    VM.sysWriteInt(decCounter); VM.sysWrite(" decs, ");
    VM.sysWriteInt(rootCounter); VM.sysWrite(" roots");
    if (Plan.refCountCycleDetection) {
      VM.sysWrite(", "); 
      VM.sysWriteInt(purpleCounter); VM.sysWrite(" purple");
    }
    VM.sysWrite(">\n");
  }

//   public final FreeListVMResource getVMResource() { return rcSpace.getVMResource();}
//   public final MemoryResource getMemoryResource() { return rcSpace.getMemoryResource();}
}
