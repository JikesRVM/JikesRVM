/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package org.mmtk.policy;

import org.mmtk.plan.CopyingHeader;
import org.mmtk.plan.Plan;
import org.mmtk.utility.VMResource;
import org.mmtk.utility.MemoryResource;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;


/**
 * This class implements tracing functionality for a simple copying
 * space.  Since no state needs to be held globally or locally, all
 * methods are static.
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class CopySpace extends BasePolicy 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 
  static final VM_Word GC_MARK_BIT_MASK = VM_Word.one();

  public static void prepare(VMResource vm, MemoryResource mr) { }
  public static void release(VMResource vm, MemoryResource mr) { }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param object The object to be traced.
   * @return The forwarded object.
   */
  public static VM_Address traceObject(VM_Address object) 
    throws VM_PragmaInline {
    return forwardObject(object, true);
  }

  /**
   * Mark an object as having been traversed.
   *
   * @param object The object to be marked
   * @param markState The sense of the mark bit (flips from 0 to 1)
   */
  public static void markObject(VM_Address object, VM_Word markState) 
    throws VM_PragmaInline {
    if (testAndMark(object, markState)) 
      VM_Interface.getPlan().enqueue(object);
  }
  
  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   * @param value The value to store in the mark bit
   */
  private static boolean testAndMark(VM_Address object, VM_Word value) 
    throws VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      VM_Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM_Interface.attemptAvailableBits(object,oldValue,oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

  /**
   * Forward an object.
   *
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  public static VM_Address forwardObject(VM_Address object) 
    throws VM_PragmaInline {
    return forwardObject(object, false);
  }

  /**
   * Forward an object.  If the object has not already been forwarded,
   * then conditionally enqueue it for scanning.
   *
   * @param object The object to be forwarded.
   * @param scan If <code>true</code>, then enqueue the object for
   * scanning if the object was previously unforwarded.
   * @return The forwarded object.
   */
  private static VM_Address forwardObject(VM_Address object, boolean scan) 
    throws VM_PragmaInline {
    VM_Word forwardingPtr = CopyingHeader.attemptToForward(object);
    // prevent instructions moving infront of attemptToForward
    VM_Magic.isync();   

    // Somebody else got to it first.
    //
    if (CopyingHeader.stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      while (CopyingHeader.stateIsBeingForwarded(forwardingPtr)) 
        forwardingPtr = CopyingHeader.getForwardingWord(object);
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();  
      VM_Address newObject = forwardingPtr.and(CopyingHeader.GC_FORWARDING_MASK.not()).toAddress();
      return newObject;
    }

    // We are the designated copier
    //
    VM_Address newObject = VM_Interface.copy(object);
    CopyingHeader.setForwardingPointer(object, newObject);
    if (scan) {
      Plan.enqueue(newObject);       // Scan it later
    } else {
      Plan.enqueueForwardedUnscannedObject(newObject);
    }

    return newObject;
  }


  public static boolean isLive(VM_Address obj) {
    return CopyingHeader.isForwarded(obj);
  }
}
