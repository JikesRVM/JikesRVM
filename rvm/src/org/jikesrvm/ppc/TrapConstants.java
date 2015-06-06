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
package org.jikesrvm.ppc;

/**
 * Trap Conventions
 */
public final class TrapConstants {
  //--------------------------------------------------------------------------------------------//
  //                              Trap Conventions.                                             //
  //--------------------------------------------------------------------------------------------//

  // Compilers should generate trap instructions that conform to the following
  // values in order for traps to be correctly recognized by the trap handler
  // in libvm.C
  //
  public static final int DIVIDE_BY_ZERO_MASK = 0xFBE0FFFF; // opcode, condition mask, & immediate
  public static final int DIVIDE_BY_ZERO_TRAP = 0x08800000; // teqi, divisor, 0
  public static final int ARRAY_INDEX_MASK = 0xFFE007FE; // extended opcode and condition mask
  public static final int ARRAY_INDEX_TRAP = 0x7CC00008; // tlle arraySize, arrayIndex
  public static final int ARRAY_INDEX_REG_MASK = 0x0000f800;
  public static final int ARRAY_INDEX_REG_SHIFT = 11;
  public static final int CONSTANT_ARRAY_INDEX_MASK = 0xFFE00000; // opcode and condition mask
  public static final int CONSTANT_ARRAY_INDEX_TRAP = 0x0CC00000; // tllei arraySize, arrayIndexConstant
  public static final int CONSTANT_ARRAY_INDEX_INFO = 0x0000ffff;
  public static final int STACK_OVERFLOW_MASK = 0xFFE0077E; // opcode and condition mask
  public static final int STACK_OVERFLOW_TRAP = 0x7E000008; // tlt stackPointer, stackLimit
  public static final int STACK_OVERFLOW_HAVE_FRAME_TRAP = 0x7D000008; // tgt stackLimit, stackPointer
  public static final int WRITE_BUFFER_OVERFLOW_MASK = 0xFFE0077E; // opcode and condition mask
  public static final int WRITE_BUFFER_OVERFLOW_TRAP = 0x7E800008; // tle modifiedOldObjectMax, modifiedOldObjectAddr

  public static final int STACK_OVERFLOW_TRAP_INFO_SET_HAVE_FRAME = 0x1;

  /* JNI stack size checking */
  public static final int JNI_STACK_TRAP_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0001
  public static final int JNI_STACK_TRAP = 0x0BEC0001;

  /* USED BY THE OPT COMPILER */
  public static final int CHECKCAST_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0000
  public static final int CHECKCAST_TRAP = 0x0BEC0000;
  public static final int MUST_IMPLEMENT_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0002
  public static final int MUST_IMPLEMENT_TRAP = 0x0BEC0002;
  public static final int STORE_CHECK_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0003
  public static final int STORE_CHECK_TRAP = 0x0BEC0003;
  public static final int REGENERATE_MASK = 0xFFE0077E;
  public static final int REGENERATE_TRAP = 0x7C600008; // tlne
  public static final int NULLCHECK_MASK = 0xFBE0FFFF;
  public static final int NULLCHECK_TRAP = 0x08400001; // tllt 1

  private TrapConstants() {
    // prevent instantiation
  }
}
