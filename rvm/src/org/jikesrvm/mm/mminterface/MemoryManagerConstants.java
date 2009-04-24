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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.SizeConstants;

/**
 * This class merely exposes the MMTk constants into the Jikes RVM
 * package space so that they can be accessed by the VM in an
 * MM-neutral way.  It is separate from MemoryManager to break
 * cyclic class-loading dependencies.
 */
public class MemoryManagerConstants implements SizeConstants {
  /** True if the selected plan needs support for linearly scanning the heap */
  public static final boolean NEEDS_LINEAR_SCAN = Selected.Constraints.get().needsLinearScan();
  /** Number of bits in the GC header required by the selected plan */
  public static final int GC_HEADER_BITS = Selected.Constraints.get().gcHeaderBits();
  /** Number of additional bytes required in the header by the selected plan */
  public static final int GC_HEADER_BYTES = Selected.Constraints.get().gcHeaderWords() << LOG_BYTES_IN_WORD;
  /** True if the selected plan requires a read barrier on reference types */
  public static final boolean NEEDS_REFTYPE_READ_BARRIER = Selected.Constraints.get().needsReferenceTypeReadBarrier();
  /** True if the selected plan requires write barriers on putfield, arraystore or modifycheck */
  public static final boolean NEEDS_WRITE_BARRIER = Selected.Constraints.get().needsWriteBarrier();
  /** True if the selected plan requires read barriers on getfield or arrayload */
  public static final boolean NEEDS_READ_BARRIER = Selected.Constraints.get().needsReadBarrier();
  /** True if the selected plan requires write barriers on putstatic */
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER = Selected.Constraints.get().needsStaticWriteBarrier();
  /** True if the selected plan requires read barriers on getstatic */
  public static final boolean NEEDS_GETSTATIC_READ_BARRIER = Selected.Constraints.get().needsStaticReadBarrier();
  /** True if the selected plan requires concurrent worker threads */
  public static final boolean NEEDS_CONCURRENT_WORKERS = Selected.Constraints.get().needsConcurrentWorkers();
  /** True if the selected plan needs support for generating a GC trace */
  public static final boolean GENERATE_GC_TRACE = Selected.Constraints.get().generateGCTrace();
  /** True if the selected plan may move objects */
  public static final boolean MOVES_OBJECTS = Selected.Constraints.get().movesObjects();
  /** True if the selected plan moves tib objects */
  public static final boolean MOVES_TIBS = false;
  /** True if the selected plan moves code */
  public static final boolean MOVES_CODE = false;

}

