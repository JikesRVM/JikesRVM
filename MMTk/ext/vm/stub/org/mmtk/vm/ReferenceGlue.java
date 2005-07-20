/* -*-coding: iso-8859-1 -*-
 * 
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 */
package org.mmtk.vm;


import org.vmmagic.unboxed.*;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;


/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences. When a java/lang/ref/Reference object is created,
 * its address is added to a list of pending reference objects of the
 * appropriate type. An address is used so the reference will not stay
 * alive during gc if it isn't in use elsewhere the mutator. During
 * gc, the various lists are processed in the proper order to
 * determine if any Reference objects are ready to be enqueued or
 * whether referents that have died should be kept alive until the
 * Reference is explicitly cleared. The ReferenceProcessor class drives
 * this processing and uses this class, via VM_Interface, to scan
 * the lists of pending reference objects.
 *
 * Elsewhere, there is a distinguished Finalizer thread which enqueues
 * itself on the VM_Scheduler finalizerQueue.  At the end of gc, if
 * needed and if any Reference queue is not empty, the finalizer
 * thread is scheduled to be run when gc is completed. This thread
 * calls Reference.enqueue() to make the actual notifcation to the
 * user program that the object state has changed.
 *
 * Based on previous ReferenceProcessor.java, which was loosely based
 * on Finalizer.java
 * 
 * @author Chris Hoffmann
 * @modified Andrew Gray
 */
public class ReferenceGlue {
  /**
   * <code>true</code> if the references are implemented as heap
   * objects (rather than in a table, for example).  In this context
   * references are soft, weak or phantom references.
   */
  public static final boolean REFERENCES_ARE_OBJECTS = true;

  /**
   * Scan through the list of references with the specified semantics.
   * @param semantics the number representing the semantics
   * @param True if it is safe to only scan new references.
   */
  public static void scanReferences(int semantics, boolean nursery) {
  }

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
  public static final boolean enqueueReference(Address addr,
                                               boolean onlyOnce) {
    return false;
  }

  /**
   * Add a reference to the list of soft references.
   * @param ref the SoftReference to add
   */
  public static void addSoftCandidate(SoftReference ref)
    {
  }

  /**
   * Add a reference to the list of weak references.
   * @param ref the WeakReference to add
   */
  public static void addWeakCandidate(WeakReference ref)
    {
  }
  
  /**
   * Add a reference to the list of phantom references.
   * @param ref the PhantomReference to add
   */
  public static void addPhantomCandidate(PhantomReference ref)
    {
  }
  
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
  public static ObjectReference getReferent(Address addr) {
    return null;
  }
  
  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param addr the address of the reference
   * @param referent the referent address
   */
  public static void setReferent(Address addr, ObjectReference referent) {
  }
  
  public static int countWaitingSoftReferences() {
    return 0;
  }

  public static int countWaitingWeakReferences() {
    return 0;
  }
}
