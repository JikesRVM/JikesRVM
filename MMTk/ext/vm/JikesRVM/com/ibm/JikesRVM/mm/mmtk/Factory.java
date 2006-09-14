/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package com.ibm.JikesRVM.mm.mmtk;

import com.ibm.JikesRVM.VM;

/**
 * This is a VM-specific class which defines factory methods for
 * VM-specific types which must be instantiated within MMTk.
 * 
 * @see org.mmtk.vm.Factory
 * 
 * $Id: Factory.java,v 1.7 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public final class Factory extends org.mmtk.vm.Factory {

  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   * 
   * @see Lock
   * 
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public org.mmtk.vm.Lock newLock(String name) {
    try {
      return new Lock(name);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate lock!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   * 
   * @see SynchronizedCounter
   * 
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public org.mmtk.vm.SynchronizedCounter newSynchronizedCounter() {
    try {
      return new SynchronizedCounter();
    } catch (Exception e) {
     VM.sysFail("Failed to allocate synchronized counter!");
      return null; // never get here
    }
  }
}
