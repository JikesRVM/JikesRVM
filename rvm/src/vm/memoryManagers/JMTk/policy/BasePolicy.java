/*
 * (C) Copyright IBM Corp. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package org.mmtk.policy;

import org.mmtk.utility.MemoryResource;
import org.mmtk.utility.VMResource;

import com.ibm.JikesRVM.VM_Address;

/**
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
import org.mmtk.vm.VM_Interface;
abstract public class BasePolicy { // implements HeaderConstants {
  
  public final static String Id = "$Id$"; 

  /*
   * If these where instance methods they would be declared abstract.
   * However, they are class methods and cannot be abstract.  Given
   * that, mabye instead of methods there should just be comments that
   * can be used as templates for the classes that extend BasePolicy.
   * Maybe the whole class is unnecessary.
   */
  public static void prepare(VMResource vm, MemoryResource mr) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }
  public static void release(VMResource vm, MemoryResource mr) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
  }
  public static VM_Address traceObject(VM_Address object) { 
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
    return VM_Address.zero(); 
  }
  public static    boolean isLive(VM_Address obj) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
    return false; 
  }
}
