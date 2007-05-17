/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences. 
 */
@Uninterruptible public abstract class ReferenceGlue {
  /**
   * Scan through the list of references with the specified semantics.
   * @param semantics the number representing the semantics
   * @param nursery true if it is safe to only scan new references.
   */
  public abstract void scanReferences(int semantics, boolean nursery);

  /**
   * Scan through all references and forward. Only called when references
   * are objects.
   */
  public abstract void forwardReferences();

  /**
   * Put this Reference object on its ReferenceQueue (if it has one)
   * when its referent is no longer sufficiently reachable. The
   * definition of "reachable" is defined by the semantics of the
   * particular subclass of Reference. The implementation of this
   * routine is determined by the the implementation of
   * java.lang.ref.ReferenceQueue in GNU classpath. It is in this
   * class rather than the public Reference class to ensure that Jikes
   * has a safe way of enqueueing the object, one that cannot be
   * overridden by the application program.
   * 
   * @see java.lang.ref.ReferenceQueue
   * @param addr the address of the Reference object
   * @param onlyOnce <code>true</code> if the reference has ever
   * been enqueued previously it will not be enqueued
   * @return <code>true</code> if the reference was enqueued
   */
  public abstract boolean enqueueReference(Address addr,
                                               boolean onlyOnce);

  /***********************************************************************
   * 
   * Reference object field accesors
   */

  /**
   * Get the referent from a reference.  For Java the reference
   * is a Reference object.
   * @param addr the address of the reference
   * @return the referent address
   */
  public abstract ObjectReference getReferent(Address addr);

  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param addr the address of the reference
   * @param referent the referent address
   */
  public abstract void setReferent(Address addr, ObjectReference referent);
 
  /**
   * @return <code>true</code> if the references are implemented as heap
   * objects (rather than in a table, for example).  In this context
   * references are soft, weak or phantom references.
   * 
   * This must be implemented by subclasses, but is never called by MMTk users.
   */
  protected abstract boolean getReferencesAreObjects();
  
  /**
   * NOTE: This method should not be called by anything other than the
   * reflective mechanisms in org.mmtk.vm.VM, and is not implemented by
   * subclasses.
   * 
   * This hack exists only to allow us to declare getVerifyAssertions() as 
   * a protected method.
   */
  static boolean referencesAreObjectsTrapdoor(ReferenceGlue a) {
    return a.getReferencesAreObjects();
  }

}
