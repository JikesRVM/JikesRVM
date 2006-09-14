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
   * Create a new ActivePlan instance using the appropriate VM-specific
   * concrete ActivePlan sub-class.
   * 
   * @see ActivePlan
   * @return A concrete VM-specific ActivePlan instance.
   */
  public org.mmtk.vm.ActivePlan newActivePlan() {
    try {
      return new ActivePlan();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ActivePlan!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Assert instance using the appropriate VM-specific
   * concrete Assert sub-class.
   * 
   * @see Assert
   * @return A concrete VM-specific Assert instance.
   */
  public org.mmtk.vm.Assert newAssert() {
    try {
      return new Assert();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Assert!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Barriers instance using the appropriate VM-specific
   * concrete Barriers sub-class.
   * 
   * @see Barriers
   * @return A concrete VM-specific Barriers instance.
   */
  public org.mmtk.vm.Barriers newBarriers() {
    try {
      return new Barriers();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Barriers!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Collection instance using the appropriate VM-specific
   * concrete Collection sub-class.
   * 
   * @see Collection
   * @return A concrete VM-specific Collection instance.
   */
  public org.mmtk.vm.Collection newCollection() {
    try {
      return new Collection();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Collection!");
      return null; // never get here
    }
  }
  
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
      VM.sysFail("Failed to allocate new Lock!");
      return null; // never get here
    }
  }

  /**
   * Create a new Memory instance using the appropriate VM-specific
   * concrete Memory sub-class.
   * 
   * @see Memory
   * @return A concrete VM-specific Memory instance.
   */
  public org.mmtk.vm.Memory newMemory() {
    try {
      return new Memory();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Memory!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ObjectModel instance using the appropriate VM-specific
   * concrete ObjectModel sub-class.
   * 
   * @see ObjectModel
   * @return A concrete VM-specific ObjectModel instance.
   */
  public org.mmtk.vm.ObjectModel newObjectModel() {
    try {
      return new ObjectModel();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ObjectModel!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Options instance using the appropriate VM-specific
   * concrete Options sub-class.
   * 
   * @see Options
   * @return A concrete VM-specific Options instance.
   */
  public org.mmtk.vm.Options newOptions() {
    try {
      return new Options();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Options!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ReferenceGlue instance using the appropriate VM-specific
   * concrete ReferenceGlue sub-class.
   * 
   * @see ReferenceGlue
   * @return A concrete VM-specific ReferenceGlue instance.
   */
  public org.mmtk.vm.ReferenceGlue newReferenceGlue() {
    try {
      return new ReferenceGlue();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ReferenceGlue!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Scanning instance using the appropriate VM-specific
   * concrete Scanning sub-class.
   * 
   * @see Scanning
   * @return A concrete VM-specific Scanning instance.
   */
  public org.mmtk.vm.Scanning newScanning() {
    try {
      return new Scanning();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Scanning!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Statistics instance using the appropriate VM-specific
   * concrete Statistics sub-class.
   * 
   * @see Statistics
   * @return A concrete VM-specific Statistics instance.
   */
  public org.mmtk.vm.Statistics newStatistics() {
    try {
      return new Statistics();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Statistics!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Strings instance using the appropriate VM-specific
   * concrete Strings sub-class.
   * 
   * @see Strings
   * @return A concrete VM-specific Strings instance.
   */
  public org.mmtk.vm.Strings newStrings() {
    try {
      return new Strings();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Strings!");
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
     VM.sysFail("Failed to allocate new SynchronizedCounter!");
      return null; // never get here
    }
  }

  /**
   * Create a new TraceInterface instance using the appropriate VM-specific
   * concrete TraceInterface sub-class.
   * 
   * @see TraceInterface
   * @return A concrete VM-specific TraceInterface instance.
   */
  public org.mmtk.vm.TraceInterface newTraceInterface() {
    try {
      return new TraceInterface();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new TraceInterface!");
      return null; // never get here
    }
  }
}
