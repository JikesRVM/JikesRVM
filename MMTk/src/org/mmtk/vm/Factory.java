/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
   * Create a new ActivePlan instance using the appropriate VM-specific
   * concrete ActivePlan sub-class.
   * 
   * @see ActivePlan
   * @return A concrete VM-specific ActivePlan instance.
   */
  public abstract ActivePlan newActivePlan();

  /**
   * Create a new Assert instance using the appropriate VM-specific
   * concrete Assert sub-class.
   * 
   * @see Assert
   * @return A concrete VM-specific Assert instance.
   */
  public abstract Assert newAssert();

  /**
   * Create a new Barriers instance using the appropriate VM-specific
   * concrete Barriers sub-class.
   * 
   * @see Barriers
   * @return A concrete VM-specific Barriers instance.
   */
  public abstract Barriers newBarriers();

  /**
   * Create a new Collection instance using the appropriate VM-specific
   * concrete Collection sub-class.
   * 
   * @see Collection
   * @return A concrete VM-specific Collection instance.
   */
  public abstract Collection newCollection();

  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   * 
   * @see Lock
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public abstract Lock newLock(String name);
  
  /**
   * Create a new Memory instance using the appropriate VM-specific
   * concrete Memory sub-class.
   * 
   * @see Memory
   * @return A concrete VM-specific Memory instance.
   */
  public abstract Memory newMemory();

  /**
   * Create a new ObjectModel instance using the appropriate VM-specific
   * concrete ObjectModel sub-class.
   * 
   * @see ObjectModel
   * @return A concrete VM-specific ObjectModel instance.
   */
  public abstract ObjectModel newObjectModel();

  /**
   * Create a new Options instance using the appropriate VM-specific
   * concrete Options sub-class.
   * 
   * @see Options
   * @return A concrete VM-specific Options instance.
   */
  public abstract Options newOptions();

  /**
   * Create a new ReferenceGlue instance using the appropriate VM-specific
   * concrete ReferenceGlue sub-class.
   * 
   * @see ReferenceGlue
   * @return A concrete VM-specific ReferenceGlue instance.
   */
  public abstract ReferenceGlue newReferenceGlue();

  /**
   * Create a new Scanning instance using the appropriate VM-specific
   * concrete Scanning sub-class.
   * 
   * @see Scanning
   * @return A concrete VM-specific Scanning instance.
   */
  public abstract Scanning newScanning();

  /**
   * Create a new Statistics instance using the appropriate VM-specific
   * concrete Statistics sub-class.
   * 
   * @see Statistics
   * @return A concrete VM-specific Statistics instance.
   */
  public abstract Statistics newStatistics();

  /**
   * Create a new Strings instance using the appropriate VM-specific
   * concrete Strings sub-class.
   * 
   * @see Strings
   * @return A concrete VM-specific Strings instance.
   */
  public abstract Strings newStrings();
  
  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   * 
   * @see SynchronizedCounter
   * 
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public abstract SynchronizedCounter newSynchronizedCounter();
  
  /**
   * Create a new TraceInterface instance using the appropriate VM-specific
   * concrete TraceInterface sub-class.
   * 
   * @see TraceInterface
   * @return A concrete VM-specific TraceInterface instance.
   */
  public abstract TraceInterface newTraceInterface();
    
}
