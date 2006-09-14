/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.vm;

/**
 * This class defines factory methods for VM-specific types which must
 * be instantiated within MMTk.  Since the concrete type is defined at
 * build time, we leave it to a concrete vm-specific instance of this class
 * to perform the object instantiation.
 * 
 * $Id: Factory.java,v 1.7 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * 
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Factory {

  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   * 
   * @see Lock
   * 
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public abstract Lock newLock(String name);
  
  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   * 
   * @see SynchronizedCounter
   * 
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public abstract SynchronizedCounter newSynchronizedCounter();
}
