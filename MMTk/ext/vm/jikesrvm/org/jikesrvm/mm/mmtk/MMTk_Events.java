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

package org.jikesrvm.mm.mmtk;

import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.tuningfork.TraceEngine;
import org.mmtk.policy.Space;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

import com.ibm.tuningfork.tracegen.types.EventAttribute;
import com.ibm.tuningfork.tracegen.types.EventType;
import com.ibm.tuningfork.tracegen.types.ScalarType;

/**
 * Implementation of simple MMTK event generation hooks
 * to allow MMTk to generate TuningFork events.
 */
@Uninterruptible
public class MMTk_Events extends org.mmtk.vm.MMTk_Events {
  public static MMTk_Events events;

  public final EventType gcStart;
  public final EventType gcStop;
  public final EventType pageAction;
  public final EventType heapSizeChanged;

  private final TraceEngine engine;

  public MMTk_Events(TraceEngine engine) {
    this.engine = engine;

    /* Define events used by the MMTk subsystem */
    gcStart = engine.defineEvent("GC Start", "Start of a GC cycle", new EventAttribute("Reason","Encoded reason for GC",ScalarType.INT));
    gcStop = engine.defineEvent("GC Stop", "End of a GC Cycle");
    pageAction = engine.defineEvent("Page Action", "A space has acquired or released one or more pages",
                              new EventAttribute[] {
                                  new EventAttribute("Space", "Space ID", ScalarType.INT),
                                  new EventAttribute("Start Address", "Start address of range of released pages", ScalarType.INT),
                                  new EventAttribute("Num Pages", "Number of pages released", ScalarType.INT),
                                  new EventAttribute("Acquire/Release", "0 for acquire, 1 for release", ScalarType.INT)});
    heapSizeChanged = engine.defineEvent("Heapsize", "Current heapsize ceiling", new EventAttribute("Heapsize", "Heapsize in bytes", ScalarType.INT));
    events = this;
  }

  public void tracePageAcquired(Space space, Address startAddress, int numPages) {
    RVMThread.getCurrentFeedlet().addEvent(pageAction, space.getIndex(), startAddress.toInt(), numPages, 0);
  }

  public void tracePageReleased(Space space, Address startAddress, int numPages) {
    RVMThread.getCurrentFeedlet().addEvent(pageAction, space.getIndex(), startAddress.toInt(), numPages, 1);
  }

  public void heapSizeChanged(Extent heapSize) {
    RVMThread.getCurrentFeedlet().addEvent(heapSizeChanged, heapSize.toInt());
  }
}
