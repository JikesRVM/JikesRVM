/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;

/**
 * Each instance of this class corresponds to one treadmill *space*.
 *
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).
 *
 * This stands in contrast to TreadmillThread, which is
 * instantiated and called on a per-thread basis.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class TreadmillSpace implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static final int   TREADMILL_PREV_OFFSET = -2 * WORD_SIZE;
  private static final int   TREADMILL_NEXT_OFFSET = -3 * WORD_SIZE;
  private static final int  TREADMILL_OWNER_OFFSET = -4 * WORD_SIZE;
  public static final int    TREADMILL_HEADER_SIZE = 3 * WORD_SIZE;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private boolean inTreadmillCollection = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  TreadmillSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   * @param thread The treadmill thread instance through which
   * this instance was allocated.
   */
  public final void postAlloc(VM_Address cell, TreadmillThread thread)
    throws VM_PragmaInline {
    addToTreadmill(cell, thread);
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
      return markState;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void prepare(VMResource vm, MemoryResource mr) { 
    markState = MarkSweepHeader.MARK_BIT_MASK - markState;
    inTreadmillCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void release() {
    inTreadmillCollection = false;
  }

  /**
   * Return true if this mark-sweep space is currently being collected.
   *
   * @return True if this mark-sweep space is currently being collected.
   */
  public boolean inTreadmillCollection() 
    throws VM_PragmaInline {
    return inTreadmillCollection;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference to an object under a mark sweep collection
   * policy.  If the object header is not already marked, mark the
   * object in either the bitmap or by moving it off the treadmill,
   * and enqueue the object for subsequent processing. The object is
   * marked as (an atomic) side-effect of checking whether already
   * marked.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  public final VM_Address traceObject(VM_Address object)
    throws VM_PragmaInline {
    if (MarkSweepHeader.testAndMark(object, markState)) {
      internalMarkObject(object);
      VM_Interface.getPlan().enqueue(object);
    }
    return object;
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
     return MarkSweepHeader.testMarkBit(obj, markState);
   }

  /**
   * An object has been marked (identified as live).  Large objects
   * are added to the to-space treadmill, while all other objects will
   * have a mark bit set in the superpage header.
   *
   * @param object The object which has been marked.
   */
  private final void internalMarkObject(VM_Address object) 
    throws VM_PragmaInline {
    VM_Address ref = VM_JavaHeader.getPointerInMemoryRegion(object);
    
    if (VM.VerifyAssertions) VM._assert(!MarkSweepHeader.isSmallObject(VM_Magic.addressAsObject(object)));
	
    VM_Address cell = VM_JavaHeader.objectStartRef(object);
    moveToTreadmill(cell, true, false);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Treadmill
  //

  /**
   * Return true if a cell is on a given treadmill
   *
   * @param cell The cell being searched for
   * @param head The head of the treadmill
   * @return True if the cell is found on the treadmill
   */
  public final boolean isOnTreadmill(VM_Address cell, VM_Address head) {
    VM_Address next = head;
    while (!next.isZero()) {
      if (next.EQ(cell)) 
	return true;
      next = getNextTreadmill(next);
    }
    return false;
  }

  public final void showTreadmill(TreadmillThread thread) {
    VM_Address next = thread.getTreadmillFromHead();
    VM.sysWrite("FROM: ");
    VM.sysWrite(next);
    while (!next.isZero()) {
      next = getNextTreadmill(next);
      VM.sysWrite(" -> ", next);
    }
    VM.sysWriteln();
    next = thread.getTreadmillToHead();
    VM.sysWrite("TO: ");
    VM.sysWrite(next);
    while (!next.isZero()) {
      next = getNextTreadmill(next);
      VM.sysWrite(" -> ", next);
    }
    VM.sysWriteln();
  }
  
  /**
   * Add a cell to the from-space treadmill
   *
   * @param cell The cell to be added to the treadmill
   * @param thread The thread through which this cell was
   * allocated.
   */
  public void addToTreadmill(VM_Address cell, TreadmillThread thread) 
    throws VM_PragmaInline {
    setTreadmillOwner(cell, VM_Magic.objectAsAddress((Object) thread));
    moveToTreadmill(cell, inTreadmillCollection, true);
  }

  /**
   * Move a cell to either the to or from space treadmills
   * 
   * @param cell The cell to be placed on the treadmill
   * @param to If true the cell should be placed on the to-space
   * treadmill.  Otherwise it should go on the from-space treadmill.
   * @param fresh If true then this is a new allocation, and therefore
   * is not already on the from-space treadmill.
   */
  private void moveToTreadmill(VM_Address cell, boolean to, boolean fresh) 
    throws VM_PragmaInline {
    TreadmillThread owner = (TreadmillThread) VM_Magic.addressAsObject(getTreadmillOwner(cell));
    owner.lockTreadmill();
    // If it is already on some other treadmill, remove it from there.
    if (to && !fresh) { 
      if (VM.VerifyAssertions)
	VM._assert(isOnTreadmill(cell, owner.getTreadmillFromHead()));
      // remove from "from" treadmill
      VM_Address prev = getPrevTreadmill(cell);
      VM_Address next = getNextTreadmill(cell);
      if (!prev.EQ(VM_Address.zero()))
	setNextTreadmill(prev, next);
      else
	owner.setTreadmillFromHead(next);
      if (!next.EQ(VM_Address.zero()))
	setPrevTreadmill(next, prev);
    } else {
      if (VM.VerifyAssertions)
	VM._assert(!isOnTreadmill(cell, owner.getTreadmillFromHead()));
    }

    // add to treadmill
    VM_Address head = (to ? owner.getTreadmillToHead() : owner.getTreadmillFromHead());
    setNextTreadmill(cell, head);
    setPrevTreadmill(cell, VM_Address.zero());
    if (!head.EQ(VM_Address.zero()))
      setPrevTreadmill(head, cell);
    if (to)
      owner.setTreadmillToHead(cell);
    else
      owner.setTreadmillFromHead(cell);

    if (VM.VerifyAssertions) {
      if (to)
	VM._assert(isOnTreadmill(cell, owner.getTreadmillToHead()));
      else
	VM._assert(isOnTreadmill(cell, owner.getTreadmillFromHead()));
    }
    owner.unlockTreadmill();
  }

  private static void setTreadmillOwner(VM_Address cell, VM_Address owner)
    throws VM_PragmaInline {
    setTreadmillLink(cell, owner, TREADMILL_OWNER_OFFSET);
  }
  private static void setNextTreadmill(VM_Address cell, VM_Address value)
    throws VM_PragmaInline {
    setTreadmillLink(cell, value, TREADMILL_NEXT_OFFSET);
  }
  private static void setPrevTreadmill(VM_Address cell, VM_Address value)
    throws VM_PragmaInline {
    setTreadmillLink(cell, value, TREADMILL_PREV_OFFSET);
  }
  private static void setTreadmillLink(VM_Address cell, VM_Address value,
				       int offset)
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(cell.add(offset), value);
  }
  private static VM_Address getTreadmillOwner(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_OWNER_OFFSET);
  }
  public static VM_Address getNextTreadmill(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_NEXT_OFFSET);
  }
  private static VM_Address getPrevTreadmill(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_PREV_OFFSET);
  }
  private static VM_Address getTreadmillLink(VM_Address cell, int offset)
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(cell.add(offset));
  }
  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  
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
}
