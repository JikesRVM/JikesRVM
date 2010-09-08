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
package org.mmtk.harness.lang;

import java.util.EnumSet;

import org.vmmagic.unboxed.harness.Clock;

/**
 * Tracing of events in the harness, both for debugging MMTk
 * and the harness itself.
 *
 * Tracing can be enabled using the command-line trace=<i>ITEM</i> parameter,
 * or by setting Trace.enable(Item.xx) in the code.
 */
public final class Trace {
  /**
   * Items that can be traced.
   */
  public enum Item {
    /** Object allocation */                                    ALLOC,
    /** Available byte operations */                            AVBYTE,
    /** Procedure calls in the harness language */              CALL,
    /** Harness language semantic checker */                    CHECKER,
    /** Garbage collection */                                   COLLECT,
    /** P-code compiler */                                      COMPILER,
    /** Expected exceptions */                                  EXCEPTION,
    /** Environment (stack frame) loads/stores */               ENV,
    /** P-code evaluation */                                    EVAL,
    /** Hashcode operations */                                  HASH,
    /** Calls to intrinsic methods in the harness language */   INTRINSIC,
    /** Load operations in the harness language */              LOAD,
    /** Memory operations (mmap, zero etc) */                   MEMORY,
    /** Object reads and writes */                              OBJECT,
    /** Harness language parser */                              PARSER,
    /** Queueing operations in the MMTk collectors */           QUEUE,
    /** Reference type processing */                            REFERENCES,
    /** Remset */                                               REMSET,
    /** Tracing of roots */                                     ROOTS,
    /** Sanity checker - verbose output */                      SANITY,
    /** Scanning of objects */                                  SCAN,
    /** Harness language thread scheduler */                    SCHEDULER,
    /** Harness language thread scheduler - detailed */         SCHED_DETAIL,
    /** Harness language simplifier */                          SIMPLIFIER,
    /** Store operations in the harness language */             STORE,
    /** calls to traceObject during GC */                       TRACEOBJECT,
    /** Yieldpoints - used to debug scheduler policy */         YIELD,
    }

  private static EnumSet<Item> enabled = EnumSet.noneOf(Item.class);

  static {
    //enable(Item.ENV);
  }

  /**
   * @return the names of the items in the Item enumeration
   */
  public static String[] itemNames() {
    String[] result = new String[Item.values().length+1];
    result[0] = "NONE";
    for (int i=0; i < Item.values().length; i++) {
      result[i+1] = Item.values()[i].toString();
    }
    return result;
  }

  /**
   * Enable tracing of the given item
   * @param item Item to trace
   */
  public static void enable(String item) {
    enable(Item.valueOf(item));
  }

  /**
   * Enable tracing of the given item
   * @param item Item to trace
   */
  public static void enable(Item item) {
    enabled.add(item);
  }

  /**
   * Is the given item enabled for tracing ?
   * @param item The trace item
   * @return Is the given item enabled for tracing ?
   */
  public static boolean isEnabled(Item item) {
    return enabled.contains(item);
  }

  /**
   * Print a message iff tracing for the given item is enabled.
   * @param item The trace item
   * @param pattern String pattern (as per java.lang.String#format(...))
   * @param args Format arguments
   */
  public static synchronized void trace(Item item, String pattern, Object...args) {
    if (isEnabled(item)) {
      printf(item, pattern + "%n", args);
    }
  }

  /**
   * Message prefix for messages enabled by a given trace item
   * @param item The trace item
   * @return The string prefix
   */
  public static String prefix(Item item) {
    return (Clock.ENABLE_CLOCK ? Clock.read()+":" : "") +"["+item+"] ";
  }

  /**
   * Print a message as trace output.
   * @param item The trace item
   * @param pattern String pattern (as per java.lang.String#format(...))
   * @param args Format arguments
   */
  public static void printf(Item item, String pattern, Object... args) {
    printf(prefix(item) + pattern ,args);
  }

  /**
   * Print a raw message as trace output.
   * @param pattern String pattern (as per java.lang.String#format(...))
   * @param args Format arguments
   */
  public static void printf(String pattern, Object...args) {
    System.err.printf(pattern,args);
    System.err.flush();
  }
}
