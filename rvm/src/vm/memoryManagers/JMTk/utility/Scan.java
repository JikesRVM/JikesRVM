/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * Class that supports scanning of objects (scalar and array)
 *
 * @author Robin Garner
 * @author Andrew Gray
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */  
public final class Scan implements VM_Uninterruptible {
  /**
   * Scan a object, processing each pointer field encountered. 
   *
   * @param object The object to be scanned.
   */
  static void scanObject(VM_Address object) throws VM_PragmaInline {
    MMType type = VM_Interface.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        VM_Address slot = type.getSlot(object, i);
        Plan.traceObjectLocation(slot);
      }
    } else
      VM_Interface.scanObject(object);
  }

  /**
   * Enumerate the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param enum the Enumerate object through which the callback
   * is made
   */
  public static void enumeratePointers(VM_Address object, Enumerate enum) 
    throws VM_PragmaInline {
    MMType type = VM_Interface.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        VM_Address slot = type.getSlot(object, i);
        enum.enumeratePointerLocation(slot);
      }
    } else
      VM_Interface.enumeratePointers(object, enum);
  }
}
