/*
 * (C) Copyright IBM Corp. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package org.mmtk.policy;
import org.mmtk.vm.Assert;

import org.mmtk.utility.heap.MemoryResource;
import org.mmtk.utility.heap.VMResource;

import org.vmmagic.unboxed.*;

/**
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
  }
  public static void release(VMResource vm, MemoryResource mr) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
  }
  public static Address traceObject(Address object) { 
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
    return Address.zero(); 
  }
  public static    boolean isLive(Address obj) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
    return false; 
  }
}
