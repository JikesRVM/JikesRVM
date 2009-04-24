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

package org.jikesrvm.tuningfork;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Time;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;

import com.ibm.tuningfork.tracegen.chunk.EventChunk;
import com.ibm.tuningfork.tracegen.types.EventType;

/**
 * <p>A Feedlet is a entity that is a unit of trace generation
 * for TuningFork.  In Jikes RVM, a Feedlet is typically
 * associated with a VM_Thread.</p>
 *
 * <p>Note an important assumption that a Feedlet will only
 * be used by a single thread at a time.  All operations are
 * unsynchronized.  This invariant is usually met because only
 * the VM_Thread to which it is attached is allowed to perform
 * addEvent operations on the Feedlet.  If a Feedlet is attached
 * to something other than a VM_Thread, then this invariant must be
 * established via external synchronization.</p>
 */
@Uninterruptible
public final class Feedlet {
  private static final boolean CHECK_TYPES = VM.VerifyAssertions;

  private final TraceEngine engine;
  private final int feedletIndex;
  private int sequenceNumber;
  @Untraced /* NB: Assumes EventChunk is NonMoving and externally kept alive for GC */
  private EventChunk events;

  /**
   * Enabled is true when TF engine is enabled, false otherwise.
   * This field is intentionally not made final to
   *   (1) allow us to disable the boot thread's feedlet during VM booting
   *   (2) allow us to dynamically enable/disable event generation (for eventual socket hookup)
   *   (3) allow us to turn off event generation during VM shutdown
   */
  boolean enabled;

  /**
   * Create a new Feedlet.
   * This method is only meant to be called from TraceEngine.
   * @param engine the TraceEngine instance to which this feedlet is attached.
   * @param feedletIndex the index to use for the Feedlet
   */
  Feedlet(TraceEngine engine, int feedletIndex) {
    this.engine = engine;
    this.feedletIndex = feedletIndex;
    this.sequenceNumber = 0;
    this.events = null;  /* defer actually acquiring an EventChunk until the feedlet emits its first event */
    this.enabled = true; /* If tracing is not enabled, then TraceEngine will set this field to false. */
  }

  /**
   * @return the feedlet's index
   */
  int getFeedletIndex() {
    return feedletIndex;
  }

  void shutdown() {
    enabled = false;
    flushEventChunk();
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   */
  @Inline
  public void addEvent(EventType et) {
    if (!enabled) return;
    addEventInternal(et);
  }
  @NoInline
  private void addEventInternal(EventType et) {
    if (CHECK_TYPES && !checkTypes(et, 0, 0, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   */
  @Inline
  public void addEvent(EventType et, int ival1) {
    if (!enabled) return;
    addEventInternal(et, ival1);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1) {
    if (CHECK_TYPES && !checkTypes(et, 1, 0, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   * @param ival2 The second int data value
   */
  @Inline
  public void addEvent(EventType et, int ival1, int ival2) {
    if (!enabled) return;
    addEventInternal(et, ival1, ival2);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1, int ival2) {
    if (CHECK_TYPES && !checkTypes(et, 2, 0, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1, ival2)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   * @param ival2 The second int data value
   * @param ival3 The third int data value
   */
  @Inline
  public void addEvent(EventType et, int ival1, int ival2, int ival3) {
    if (!enabled) return;
    addEventInternal(et, ival1, ival2, ival3);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1, int ival2, int ival3) {
    if (CHECK_TYPES && !checkTypes(et, 3, 0, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1, ival2, ival3)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   * @param ival2 The second int data value
   * @param ival3 The third int data value
   * @param ival4 The fourth int data value
   */
  @Inline
  public void addEvent(EventType et, int ival1, int ival2, int ival3, int ival4) {
    if (!enabled) return;
    addEventInternal(et, ival1, ival2, ival3, ival4);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1, int ival2, int ival3, int ival4) {
    if (CHECK_TYPES && !checkTypes(et, 4, 0, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1, ival2, ival3, ival4)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param lval1 The first double data value
   */
  @Inline
  public void addEvent(EventType et, long lval1) {
    if (!enabled) return;
    addEventInternal(et, lval1);
  }
  @NoInline
  private void addEventInternal(EventType et, long lval1) {
    if (CHECK_TYPES && !checkTypes(et, 0, 1, 0, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, lval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param dval1 The first double data value
   */
  @Inline
  public void addEvent(EventType et, double dval1) {
    if (!enabled) return;
    addEventInternal(et, dval1);
  }
  @NoInline
  private void addEventInternal(EventType et, double dval1) {
    if (CHECK_TYPES && !checkTypes(et, 0, 0, 1, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, dval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param sval1 The first String data value
   */
  @Inline
  public void addEvent(EventType et, String sval1) {
    if (!enabled) return;
    addEventInternal(et, sval1);
  }
  @NoInline
  private void addEventInternal(EventType et, String sval1) {
    if (CHECK_TYPES && !checkTypes(et, 0, 0, 0, 1)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, sval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   * @param dval1 The first double data value
   */
  @Inline
  public void addEvent(EventType et, int ival1, double dval1) {
    if (!enabled) return;
    addEventInternal(et, ival1, dval1);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1, double dval1) {
    if (CHECK_TYPES && !checkTypes(et, 1, 0, 1, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1, dval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param ival1 The first int data value
   * @param ival2 The second int data value
   * @param dval1 The first double data value
   */
  @Inline
  public void addEvent(EventType et, int ival1, int ival2, double dval1) {
    if (!enabled) return;
    addEventInternal(et, ival1, ival2, dval1);
  }
  @NoInline
  private void addEventInternal(EventType et, int ival1, int ival2, double dval1) {
    if (CHECK_TYPES && !checkTypes(et, 2, 0, 1, 0)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, ival1, ival2, dval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et The type of event to add
   * @param dval1 The first double data value
   * @param sval1 The first String data value
   */
  @Inline
  public void addEvent(EventType et, double dval1, String sval1) {
    if (!enabled) return;
    addEventInternal(et, dval1, sval1);
  }
  @NoInline
  private void addEventInternal(EventType et, double dval1, String sval1) {
    if (CHECK_TYPES && !checkTypes(et, 0, 0, 1, 1)) return;

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, dval1, sval1)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  /**
   * Add an event to the feedlet's generated event stream
   * @param et
   * @param idata an array of int data values (may be null if no such values for this event)
   * @param ldata an array of long data values (may be null if no such values for this event)
   * @param ddata an array of double data values (may be null if no such values for this event)
   * @param sdata an array of String data values (may be null if no such values for this event)
   */
  @Inline
  public void addEvent(EventType et, int[] idata, long[] ldata, double[] ddata, String[] sdata) {
    if (!enabled) return;
    addEventInternal(et, idata, ldata, ddata, sdata);
  }
  @NoInline
  private void addEventInternal(EventType et, int[] idata, long[] ldata, double[] ddata, String[] sdata) {
    if (CHECK_TYPES) {
      int ilen = idata == null ? 0 : idata.length;
      int llen = ldata == null ? 0 : ldata.length;
      int dlen = ddata == null ? 0 : ddata.length;
      int slen = sdata == null ? 0 : sdata.length;
      if (!checkTypes(et, ilen, llen, dlen, slen)) return;
    }

    long timeStamp = getTimeStamp();
    while (true) {
      if (events == null && !acquireEventChunk()) {
        return; /* failure */
      }
      if (events.addEvent(timeStamp, et, idata, ldata, ddata, sdata)) {
        return; /* success */
      }
      flushEventChunk(); /* events is full or stale; flush and try again */
    }
  }

  private boolean checkTypes(EventType et, int numInts, int numLongs, int numDoubles, int numStrings) {
    if (et.getNumberOfInts() != numInts ||
        et.getNumberOfLongs() != numLongs ||
        et.getNumberOfDoubles() != numDoubles ||
        et.getNumberOfStrings() != numStrings) {
      VM.sysWriteln("Feedlet: Dropping addEvent of ill-typed event ", et.getName());
      return false;
    } else {
      return true;
    }
  }

  private long getTimeStamp() {
    return Time.nanoTime();
  }

  @NoInline
  private boolean acquireEventChunk() {
    if (VM.VerifyAssertions) VM._assert(events == null);
    events = engine.getEventChunk();
    if (events == null) {
      // TODO: here is where we would need to record in the Feedlet's
      //       state that we had to drop an event.  We would then later
      //       (when we got an event chunk back again) emit an event
      //       to indicate the number of events that were dropped due to
      //       an inability to obtain an event chunk.
      return false;
    }
    events.reset(feedletIndex, sequenceNumber++);
    return true;
  }

  @NoInline
  private void flushEventChunk() {
    if (events != null) {
      events.close();
      engine.returnFullEventChunk(events);
      events = null;
    }
  }
}
