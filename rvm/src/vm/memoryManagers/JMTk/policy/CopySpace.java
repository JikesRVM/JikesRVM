/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Address;
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
final class CopySpace extends BasePolicy 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  public static void prepare(VMResource vm, MemoryResource mr) { }
  public static void release(VMResource vm, MemoryResource mr) { }

  public static VM_Address forwardObject(VM_Address object) 
    throws VM_PragmaInline {
    int forwardingPtr = CopyingHeader.attemptToForward(object);
    // prevent instructions moving infront of attemptToForward
    VM_Magic.isync();   

    // Somebody else got to it first.
    //
    if (CopyingHeader.stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      while (CopyingHeader.stateIsBeingForwarded(forwardingPtr)) 
	forwardingPtr = CopyingHeader.getForwardingWord(object);
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();  
      VM_Address newObject = VM_Address.fromInt(forwardingPtr & ~CopyingHeader.GC_FORWARDING_MASK);
      return newObject;
    }

    // We are the designated copier
    //
    VM_Address newObject = VM_Interface.copy(object, forwardingPtr);
    CopyingHeader.setForwardingPointer(object, newObject);

    return newObject;
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param object The object to be copied.
   */
  public static VM_Address traceObject(VM_Address object) 
    throws VM_PragmaInline {
    VM_Address newObject = forwardObject(object);
    if (newObject != object)
      Plan.enqueue(newObject);       // Scan it later
    return newObject;
  }

  public static boolean isLive(VM_Address obj) {
    return CopyingHeader.isForwarded(obj);
  }

}
