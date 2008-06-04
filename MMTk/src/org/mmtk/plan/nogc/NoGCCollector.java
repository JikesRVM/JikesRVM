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
package org.mmtk.plan.nogc;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class implements <i>per-collector thread</i> behavior and state
 * for the <i>NoGC</i> plan, which simply allocates (without ever collecting
 * until the available space is exhausted.<p>
 *
 * Specifically, this class <i>would</i> define <i>NoGC</i> collection time semantics,
 * however, since this plan never collects, this class consists only of stubs which
 * may be useful as a template for implementing a basic collector.
 *
 * @see NoGC
 * @see NoGCMutator
 * @see CollectorContext
 */
@Uninterruptible
public class NoGCCollector extends CollectorContext {

  /************************************************************************
   * Instance fields
   */
  private final NoGCTraceLocal trace;

  /************************************************************************
   * Initialization
   */

  /**
   * Constructor. One instance is created per physical processor.
   */
  public NoGCCollector() {
    trace = new NoGCTraceLocal(global().trace);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a garbage collection
   */
  public final void collect() {
    VM.assertions.fail("GC Triggered in NoGC Plan. Is -X:gc:ignoreSystemGC=true ?");
  }

  /** Perform some concurrent garbage collection */
  public final void concurrentCollect() {
    VM.assertions.fail("Concurrent GC Triggered in NoGC Plan.");
  }

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  public final void collectionPhase(short phaseId, boolean primary) {
    VM.assertions.fail("GC Triggered in NoGC Plan.");
  }

  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  public void concurrentCollectionPhase(short phaseId) {
    VM.assertions.fail("GC Triggered in NoGC Plan.");
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>NoGC</code> instance. */
  @Inline
  private static NoGC global() {
    return (NoGC) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() { return trace; }
}
