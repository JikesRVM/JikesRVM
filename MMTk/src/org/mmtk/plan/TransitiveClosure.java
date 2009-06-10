/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * Some mechanisms only operate on nodes or edges, but due to limitations
 * of inheritance we have combined these two here.
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public abstract class TransitiveClosure {

  /** Database of specialized scan classes. */
  private static final Class<?>[] specializedScans = new Class[VM.activePlan.constraints().numSpecializedScans()];

  /**
   * A transitive closure has been created that is designed to work with a specialized scan method. We must
   * register it here so the specializer can return the class when queried.
   *
   * @param id The method id to register.
   * @param specializedScanClass The class to register.
   */
  @Interruptible
  public static synchronized void registerSpecializedScan(int id, Class<?> specializedScanClass) {
    specializedScans[id] = specializedScanClass;
  }

  /**
   * Get the specialized scan with the given id.
   */
  public static Class<?> getSpecializedScanClass(int id) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(specializedScans[id] != null);
    return specializedScans[id];
  }

  /** The specialized scan identifier */
  protected final int specializedScan;

  /**
   * Constructor
   */
  protected TransitiveClosure() {
    this(-1);
  }

  /**
   * Constructor
   *
   * @param specializedScan The specialized scan for this trace.
   */
  protected TransitiveClosure(int specializedScan) {
    this.specializedScan = specializedScan;
    if (specializedScan >= 0) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getClass() == getSpecializedScanClass(specializedScan));
    }
  }

  /**
   * Trace an edge during GC.
   *
   * @param source The source of the reference.
   * @param slot The location containing the object reference.
   */
  public void processEdge(ObjectReference source, Address slot) {
    VM.assertions.fail("processEdge not implemented.");
  }

  /**
   * Trace a node during GC.
   *
   * @param object The object to be processed.
   */
  public void processNode(ObjectReference object) {
    VM.assertions.fail("processNode not implemented.");
  }
}
