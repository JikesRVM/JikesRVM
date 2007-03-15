/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */

package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM_SizeConstants;

/**
 * This class merely exposes the MMTk constants into the Jikes RVM
 * package space so that they can be accessed by the VM in an
 * MM-neutral way.  It is separate from MM_Interface to break
 * cyclic class-loading dependencies.
 * 
 * @author Steve Blackburn
 */
public class MM_Constants implements VM_SizeConstants {
  /** True if the selected plan needs support for linearly scanning the heap */
  public static final boolean NEEDS_LINEAR_SCAN = Selected.Constraints.get().needsLinearScan();
  /** Number of bits in the GC header required by the selected plan */
  public static final int GC_HEADER_BITS = Selected.Constraints.get().gcHeaderBits();
  /** Number of additional bytes required in the header by the selected plan */
  public static final int GC_HEADER_BYTES = Selected.Constraints.get().gcHeaderWords() << LOG_BYTES_IN_WORD;
  /** True if the selected plan requires write barriers on putfield, arraystore or modifycheck */
  public static final boolean NEEDS_WRITE_BARRIER = Selected.Constraints.get().needsWriteBarrier();
  /** True if the selected plan requires write barriers on putstatic */
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER = Selected.Constraints.get().needsStaticWriteBarrier();
  /** True if the selected plan needs support for generating a GC trace */
  public static final boolean GENERATE_GC_TRACE = Selected.Constraints.get().generateGCTrace();
  /** True if the selected plan may move objects */
  public static final boolean MOVES_OBJECTS = Selected.Constraints.get().movesObjects();
  /** True if the selected plan moves tib objects */
  public static final boolean MOVES_TIBS = false;
}

