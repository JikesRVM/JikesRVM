/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepAllocator, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepAllocator.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class SimpleRCCollector implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methos (i.e. methods whose scope is limited to a
  // particular space that is collected under a mark-sweep policy).
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  SimpleRCCollector(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  /**
   * Return the VMResource associated with this collector
   *
   * @return the VMResource associated with this collector
   */
  public final FreeListVMResource getVMResource() 
    throws VM_PragmaInline {
    return vmResource;
  }

  /**
   * Return the memory resource associated with this collector
   *
   * @return The memory resource associated with this collector
   */
  public final MemoryResource getMemoryResource() 
    throws VM_PragmaInline {
    return memoryResource;
  }

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void prepare(VMResource vm, MemoryResource mr) { 
    phase = PROCESS;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void release(SimpleRCAllocator allocator) { 
  }


  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   * @param isScalar True if the cell will be occupied by a scalar
   * @param bytes The size of the cell in bytes
   * @param small True if the cell is for a small object
   * @param large True if the cell is for a large object
   * @param copy True if this allocation is for a copy rather than a
   * fresh allocation.
   * @param allocator The mark sweep allocator instance through which
   * this instance was allocated.
   */
  public final void postAlloc(VM_Address cell, boolean isScalar,
			      EXTENT bytes, boolean small, boolean large,
			      boolean copy, SimpleRCAllocator allocator)
    throws VM_PragmaInline {
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param obj The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
   public boolean isLive(VM_Address obj)
    throws VM_PragmaInline {
     return SimpleRCHeader.isLiveRC(obj);
   }

  /**
   * Trace a reference to an object.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  public final VM_Address traceObject(VM_Address object, boolean root)
    throws VM_PragmaInline {
    switch (phase) {
    case PROCESS:  
      incRC(object);
      if (root)
	VM_Interface.getPlan().addToRootSet(object); 
      break;
    case DECREMENT: 
      VM_Interface.getPlan().addToDecBuf(object); 
      break;
    case MARK_GREY: 
      if (VM.VerifyAssertions) VM._assert(SimpleRCHeader.isLiveRC(object));
      SimpleRCHeader.decRC(object);
      markGrey(object); 
      break;
    case SCAN: 
      scan(object); 
      break;
    case SCAN_BLACK: 
      SimpleRCHeader.incRC(object);
      if (!SimpleRCHeader.isBlack(object))
	scanBlack(object);
      break;
    case COLLECT:  
      collectWhite(object, VM_Interface.getPlan()); 
      break;
    default:
      if (VM.VerifyAssertions) VM._assert(false);
    }
    return object;
  }
 
  public final void markGrey(VM_Address object)
    throws VM_PragmaInline {
    if (!SimpleRCHeader.isGrey(object)) {
      SimpleRCHeader.makeGrey(object);
      ScanObject.scan(object);
    }
  }
  public final void scan(VM_Address object)
    throws VM_PragmaInline {
    if (SimpleRCHeader.isGrey(object)) {
      if (SimpleRCHeader.isLiveRC(object)) {
	phase = SCAN_BLACK;
	scanBlack(object);
	phase = SCAN;
      } else {
	SimpleRCHeader.makeWhite(object);
	ScanObject.scan(object);
      }
    }
  }

  public final void scanBlack(VM_Address object) 
    throws VM_PragmaInline {
    SimpleRCHeader.makeBlack(object);
    ScanObject.scan(object);
  }

  public final void collectWhite(VM_Address object, Plan plan)
    throws VM_PragmaInline {
    if (SimpleRCHeader.isWhite(object) && 
	!SimpleRCHeader.isBuffered(object)) {
      SimpleRCHeader.makeBlack(object);
      ScanObject.scan(object);
      plan.addToFreeBuf(object);
    }
  }


  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final int getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
    if (size <= SimpleRCAllocator.MAX_SMALL_SIZE)
      return SimpleRCHeader.SMALL_OBJECT_MASK;
    else
      return 0;
  }

  public final void incRC(VM_Address object) 
    throws VM_PragmaInline {
    SimpleRCHeader.incRC(object);
    if (Plan.refCountCycleDetection && phase == PROCESS)
      SimpleRCHeader.makeBlack(object);
  }

  public final void decRC(VM_Address object) 
    throws VM_PragmaInline {
    decRC(object, null, null);
  }
  public final void decRC(VM_Address object, SimpleRCAllocator allocator,
			  Plan plan) 
    throws VM_PragmaInline {
    if (!Plan.refCountCycleDetection) {
      if (SimpleRCHeader.decRC(object)) {
	// this object is now dead, scan it for recursive decrement
	//       VM.sysWrite(object); VM.sysWrite(" k\n");
	ScanObject.scan(object);
	if (VM.VerifyAssertions) VM._assert(allocator != null);
	free(object, allocator);
      } 
    } else {
      if (VM.VerifyAssertions) VM._assert(phase == DECREMENT);
      if (SimpleRCHeader.decRC(object)) {
	ScanObject.scan(object);
	if (VM.VerifyAssertions) VM._assert(allocator != null);
	if (!SimpleRCHeader.isBuffered(object)) 
	  free(object, allocator);
      } else {
	if (VM.VerifyAssertions) VM._assert(plan != null);
	if (SimpleRCHeader.makePurple(object))
	  plan.addToCycleBuf(VM_Magic.objectAsAddress(object));
      }
    }
  }

  public final void free(VM_Address object, SimpleRCAllocator allocator)
    throws VM_PragmaNoInline {
    VM_Address ref = VM_JavaHeader.getPointerInMemoryRegion(object);
    boolean isSmall = SimpleRCHeader.isSmallObject(VM_Magic.addressAsObject(object));
    VM_Address cell = VM_JavaHeader.objectStartRef(object);
//      VM.sysWrite(object); VM.sysWrite(" "); VM.sysWrite(cell); VM.sysWrite(" k\n");
    VM_Address sp = SimpleRCAllocator.getSuperPage(cell, isSmall);
    int sizeClass = SimpleRCAllocator.getSizeClass(sp);
    if (allocator == null)
      allocator = VM_Interface.getPlan().getAllocator();
    allocator.free(cell, sp, sizeClass);
  }

  public final void decrementPhase() 
    throws VM_PragmaInline {
    phase = DECREMENT;
  }
  public final void markGreyPhase() 
    throws VM_PragmaInline {
    phase = MARK_GREY;
  }
  public final void scanPhase() 
    throws VM_PragmaInline {
    phase = SCAN;
  }
  public final void scanBlackPhase() 
    throws VM_PragmaInline {
    phase = SCAN_BLACK;
  }
  public final void collectPhase() 
    throws VM_PragmaInline {
    phase = COLLECT;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Protected and private methods
  //

  
  ////////////////////////////////////////////////////////////////////////////
  //
  // The following methods, declared as abstract in the superclass, do
  // nothing in this implementation, so they have empty bodies.
  //
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private int phase;

  private static final int    PROCESS = 0;
  private static final int  DECREMENT = 1;
  private static final int  MARK_GREY = 2;
  private static final int       SCAN = 3;
  private static final int SCAN_BLACK = 4;
  private static final int    COLLECT = 5;
}
