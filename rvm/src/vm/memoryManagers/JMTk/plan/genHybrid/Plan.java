/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
import com.ibm.JikesRVM.memoryManagers.vmInterface.CallSite;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements the functionality of a two-generation copying
 * collector where <b>the higher generation is a mark-sweep space</b>
 * (free list allocation, mark-sweep collection).  Nursery collections
 * occur when either the heap is full or the nursery is full.  The
 * nursery size is determined by an optional command line argument.
 * If undefined, the nursery size is "infinite", so nursery
 * collections only occur when the heap is full (this is known as a
 * flexible-sized nursery collector).  Thus both fixed and flexible
 * nursery sizes are supported.  Full heap collections occur when the
 * nursery size has dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends Generational implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  protected static final boolean usesLOS = false;
  protected static final boolean copyMature = false;
  
  // virtual memory resources
  private static FreeListVMResource matureVM;

  // mature space collector
  private static MarkSweepCollector matureCollector;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  private MarkSweepAllocator mature;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    matureVM = new FreeListVMResource("Mature", MATURE_START, MATURE_SIZE, VMResource.MOVABLE);
    matureCollector = new MarkSweepCollector(matureVM, matureMR);
  }

  /**
   * Constructor
   */
  public Plan() {
    super();
    mature = new MarkSweepAllocator(matureCollector);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //
  protected final VM_Address matureAlloc(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return mature.alloc(isScalar, bytes);
  }
  protected final VM_Address matureCopy(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return mature.allocCopy(isScalar, bytes);
  }

  public final static int getInitialHeaderValue(int size) {
    return matureCollector.getInitialHeaderValue(size);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  protected final void globalMaturePrepare() {
    matureCollector.prepare(matureVM, matureMR);
  }
  protected final void threadLocalMaturePrepare(int count) {
    mature.prepare();
  }

  /**
   * Clean up after a collection.
   */
  protected final void threadLocalMatureRelease(int count) {
    mature.release();
  }
  protected final void globalMatureRelease() {
    matureCollector.release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  protected static final VM_Address traceMatureObject(VM_Address obj,
						      VM_Address addr) {
    return matureCollector.traceObject(obj);
  }

  protected final boolean willNotMoveMature(VM_Address addr) 
    throws VM_PragmaInline {
    return true;
  }

  public final static boolean isNurseryObject(Object base) {
    VM_Address addr =VM_Interface.refToAddress(VM_Magic.objectAsAddress(base));
    return (addr.GE(NURSERY_START) && addr.LE(HEAP_END));
  }

  public final static boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return Copy.isLive(obj);
      else if (addr.GE(MATURE_START))
	return matureCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  public final static int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingPtr, int bytes) {
    return (forwardingPtr & ~HybridHeader.GC_BITS_MASK) | matureCollector.getInitialHeaderValue(bytes);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  protected final void showMature() {
    mature.show();
  }
}
