/*
 * (C) Copyright IBM Corp. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.heap.MemoryResource;
import org.mmtk.utility.heap.VMResource;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
public class BasePolicy implements Uninterruptible {
  protected boolean immortal;
  protected boolean movable;

  /*
   * If these where instance methods they would be declared abstract.
   * However, they are class methods and cannot be abstract.  Given
   * that, mabye instead of methods there should just be comments that
   * can be used as templates for the classes that extend BasePolicy.
   * Maybe the whole class is unnecessary.
   */
  public void prepare(VMResource vm, MemoryResource mr) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
  }
  public void release(VMResource vm, MemoryResource mr) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
  }
  public Address traceObject(Address object) { 
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
    return Address.zero(); 
  }
  public  boolean isLive(Address object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
    return false; 
  }
  public  boolean isImmortal() {
    return immortal; 
  }
  public  boolean isMovable() {
    return movable;
  }
}
