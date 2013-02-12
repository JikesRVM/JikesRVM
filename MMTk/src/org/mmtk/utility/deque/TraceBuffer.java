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
package org.mmtk.utility.deque;

import org.mmtk.utility.Log;
import org.mmtk.utility.TracingConstants;
import org.mmtk.vm.VM;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of tracing data
 * and bulk processing of the buffer.
 */
@Uninterruptible public class TraceBuffer extends LocalQueue
  implements Constants, TracingConstants {

  /***********************************************************************
   *
   * Class based constants
   */

  /**
   *
   */
  private static final Word TRACE_NEW_RECORD = Word.fromIntSignExtend(3);
  private static final Word TRACE_ALLOC_SIZE = Word.fromIntSignExtend(5);
//  private static final Word TRACE_ALLOC_NAME = Word.fromIntSignExtend(6);
  private static final Word TRACE_ALLOC_FP = Word.fromIntSignExtend(7);
  private static final Word TRACE_ALLOC_THREAD = Word.fromIntSignExtend(9);
  private static final Word TRACE_TIB_VALUE = Word.fromIntSignExtend(10);
  private static final Word TRACE_DEATH_TIME = Word.fromIntSignExtend(11);
  private static final Word TRACE_FIELD_TARGET = Word.fromIntSignExtend(12);
  private static final Word TRACE_ARRAY_TARGET = Word.fromIntSignExtend(13);
  private static final Word TRACE_FIELD_SLOT = Word.fromIntSignExtend(14);
  private static final Word TRACE_ARRAY_ELEMENT = Word.fromIntSignExtend(15);
  private static final Word TRACE_STATIC_TARGET = Word.fromIntSignExtend(17);
  private static final Word TRACE_BOOT_ALLOC_SIZE = Word.fromIntSignExtend(18);

  /*
   * Debugging and trace reducing constants
   */
  public static final boolean OMIT_ALLOCS=false;
  public static final boolean OMIT_UPDATES=false;
  public static final boolean OMIT_BOOTALLOCS=false;
  public static final boolean OMIT_UNREACHABLES=false;
  public static final boolean OMIT_OTHERS=false;
  public static final boolean OMIT_OUTPUT=OMIT_ALLOCS && OMIT_UPDATES &&
                                          OMIT_OTHERS;


  /***********************************************************************
   *
   * Public methods
   */

  /**
   * Constructor
   *
   * @param pool The shared queue to which this queue will append its
   * buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public TraceBuffer(SharedDeque pool) {
    super(pool);
  }

  /**
   * Push word onto the tracing queue.
   *
   * @param i The data to be pushed onto the tracing queue
   */
  @Inline
  public final void push(Word i) {
    checkTailInsert(1);
    uncheckedTailInsert(i.toAddress());
  }

  /**
   * Process the data in the tracing buffer, output information as needed.
   */
  public final void process() {
    Word traceState = TRACE_NEW_RECORD;
    int entriesNotFlushed = 0;
    boolean loggedRecord = false;
    /* First we must flush any remaining data */
    if (!OMIT_OUTPUT) Log.writeln();

    /* Process through the entire buffer. */
    while (checkDequeue(1)) {
      /* For speed and efficiency, we will actually process the data buffer by
         buffer and not by dequeue-ing each entry. */
      while (!bufferOffset(head).isZero()) {
        head = head.minus(BYTES_IN_ADDRESS);
        Word val = head.loadWord();
        if (traceState.EQ(TRACE_NEW_RECORD)) {
          loggedRecord = false;
          if (val.EQ(TRACE_GCSTART)) {
            if (!OMIT_OTHERS) {
              Log.write('G');
              Log.write('C');
              Log.writeln('B', true);
            }
          } else if (val.EQ(TRACE_GCEND)) {
            if (!OMIT_OTHERS) {
              Log.write('G');
              Log.write('C');
              Log.writeln('E', true);
            }
          } else {
            traceState = val;
          }
        } else {
          if (traceState.EQ(TRACE_EXACT_ALLOC) ||
              traceState.EQ(TRACE_ALLOC)) {
            if (!OMIT_ALLOCS) {
              Log.write((traceState.EQ(TRACE_EXACT_ALLOC)) ? 'A' : 'a');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_ALLOC_SIZE;
          } else if (traceState.EQ(TRACE_EXACT_IMMORTAL_ALLOC) ||
                     traceState.EQ(TRACE_IMMORTAL_ALLOC)) {
            if (!OMIT_ALLOCS) {
              Log.write((traceState.EQ(TRACE_EXACT_IMMORTAL_ALLOC)) ? 'I' : 'i');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_ALLOC_SIZE;
          } else if (traceState.EQ(TRACE_BOOT_ALLOC)) {
            if (!OMIT_BOOTALLOCS) {
              Log.write('B');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_BOOT_ALLOC_SIZE;
          } else if (traceState.EQ(TRACE_DEATH)) {
            if (!OMIT_UNREACHABLES) {
              Log.write('D');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_DEATH_TIME;
          } else if (traceState.EQ(TRACE_BOOT_ALLOC_SIZE)) {
            if (!OMIT_BOOTALLOCS)
              Log.write(val);
            traceState = TRACE_NEW_RECORD;
          } else if (traceState.EQ(TRACE_ALLOC_SIZE)) {
            if (!OMIT_ALLOCS)
              Log.write(val);
            traceState = TRACE_ALLOC_FP;
          } else if (traceState.EQ(TRACE_ALLOC_FP)) {
            if (!OMIT_ALLOCS)
              Log.write(val);
            traceState = TRACE_ALLOC_THREAD;
          } else if (traceState.EQ(TRACE_ALLOC_THREAD)) {
            if (!OMIT_ALLOCS)
              Log.write(val);
            traceState = TRACE_NEW_RECORD;
          } else if (traceState.EQ(TRACE_TIB_SET)) {
            if (!OMIT_UPDATES) {
              Log.write('T');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_TIB_VALUE;
          } else if (traceState.EQ(TRACE_STATIC_SET)) {
            if (!OMIT_UPDATES) {
              Log.write('S');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_STATIC_TARGET;
          } else if (traceState.EQ(TRACE_TIB_VALUE) ||
                     traceState.EQ(TRACE_STATIC_TARGET)) {
            if (!OMIT_UPDATES)
              Log.write(val);
            traceState = TRACE_NEW_RECORD;
          } else if (traceState.EQ(TRACE_DEATH_TIME)) {
            if (!OMIT_UNREACHABLES)
              Log.write(val);
            traceState = TRACE_NEW_RECORD;
          } else if (traceState.EQ(TRACE_FIELD_SET) ||
                     traceState.EQ(TRACE_ARRAY_SET)) {
            if (!OMIT_UPDATES) {
              Log.write('U');
              Log.write(' ');
              Log.write(val);
              loggedRecord = true;
            }
            traceState = TRACE_FIELD_SLOT;
          } else if (traceState.EQ(TRACE_FIELD_TARGET) ||
                     traceState.EQ(TRACE_ARRAY_TARGET)) {
            if (!OMIT_UPDATES)
              Log.write(val);
            traceState = TRACE_NEW_RECORD;
          } else if (traceState.EQ(TRACE_FIELD_SLOT) ||
                     traceState.EQ(TRACE_ARRAY_ELEMENT)) {
            if (!OMIT_UPDATES)
              Log.write(val);
            traceState = TRACE_FIELD_TARGET;
          } else {
            VM.assertions.fail("Cannot understand directive!\n");
          }
          if (traceState.EQ(TRACE_NEW_RECORD) && loggedRecord) {
            entriesNotFlushed++;
            Log.writeln();
          } else if (loggedRecord) {
              Log.write(' ');
          }
        }
        if (entriesNotFlushed == 10) {
          if (!OMIT_OUTPUT)
            Log.flush();
          entriesNotFlushed = 0;
        }
      }
    }
    resetLocal();
  }
}
