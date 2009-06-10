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
public interface TrapConstants {
  //--------------------------------------------------------------------------------------------//
  //                              Trap Conventions.                                             //
  //--------------------------------------------------------------------------------------------//

  // Compilers should generate trap instructions that conform to the following
  // values in order for traps to be correctly recognized by the trap handler
  // in libvm.C
  //
  int DIVIDE_BY_ZERO_MASK = 0xFBE0FFFF; // opcode, condition mask, & immediate
  int DIVIDE_BY_ZERO_TRAP = 0x08800000; // teqi, divisor, 0
  int ARRAY_INDEX_MASK = 0xFFE007FE; // extended opcode and condition mask
  int ARRAY_INDEX_TRAP = 0x7CC00008; // tlle arraySize, arrayIndex
  int ARRAY_INDEX_REG_MASK = 0x0000f800;
  int ARRAY_INDEX_REG_SHIFT = 11;
  int CONSTANT_ARRAY_INDEX_MASK = 0xFFE00000; // opcode and condition mask
  int CONSTANT_ARRAY_INDEX_TRAP = 0x0CC00000; // tllei arraySize, arrayIndexConstant
  int CONSTANT_ARRAY_INDEX_INFO = 0x0000ffff;
  int STACK_OVERFLOW_MASK = 0xFFE0077E; // opcode and condition mask
  int STACK_OVERFLOW_TRAP = 0x7E000008; // tlt stackPointer, stackLimit
  int STACK_OVERFLOW_HAVE_FRAME_TRAP = 0x7D000008; // tgt stackLimit, stackPointer
  int WRITE_BUFFER_OVERFLOW_MASK = 0xFFE0077E; // opcode and condition mask
  int WRITE_BUFFER_OVERFLOW_TRAP = 0x7E800008; // tle modifiedOldObjectMax, modifiedOldObjectAddr

  /* JNI stack size checking */ int JNI_STACK_TRAP_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0001
  int JNI_STACK_TRAP = 0x0BEC0001;

  /* USED BY THE OPT COMPILER */ int CHECKCAST_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0000
  int CHECKCAST_TRAP = 0x0BEC0000;
  int MUST_IMPLEMENT_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0002
  int MUST_IMPLEMENT_TRAP = 0x0BEC0002;
  int STORE_CHECK_MASK = 0x0BECFFFF; // tALWAYSi, 12, 0x0003
  int STORE_CHECK_TRAP = 0x0BEC0003;
  int REGENERATE_MASK = 0xFFE0077E;
  int REGENERATE_TRAP = 0x7C600008; // tlne
  int NULLCHECK_MASK = 0xFBE0FFFF;
  int NULLCHECK_TRAP = 0x08400001; // tllt 1
}
