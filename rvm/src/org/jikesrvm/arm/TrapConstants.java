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
package org.jikesrvm.arm;

/**
 * Trap Conventions
 */
public final class TrapConstants {

  // The trap handler in sysSignal_arm.c will read instructions to interpret what type of exception to pass
  // See compilers.common.arm.Assembler.java
  public static final int ARRAY_INDEX_TRAP    = 0x9300C000; // emitMOVimm(LS, R12, 0);
  public static final int DIVIDE_ZERO_TRAP    = 0x0300C000; // emitMOVimm(EQ, R12, 0);
  public static final int STACK_OVERFLOW_TRAP = 0xA300C000; // emitMOVimm(GE, R12, 0);
  public static final int JNI_STACK_TRAP      = 0xD300C000; // emitMOVimm(LE, R12, 0);
  public static final int NULLCHECK_TRAP      = 0x0300C001; // emitMOVimm(EQ, R12, 1);
  public static final int INTERFACE_TRAP      = 0x0300C002; // emitMOVimm(cond, R12, 2);
  public static final int CHECKCAST_TRAP      = 0x0300C003; // emitMOVimm(cond, R12, 3);
  public static final int IGNORE_COND         = 0x0FFFFFFF; // mask out the "cond" value

  public static final int ARRAY_INDEX_REG_MASK = 0x0000000f; // emitCMP(ALWAYS, Rlength, Rindex); <- want to find the value of Rindex
  public static final int ARRAY_INDEX_REG_SHIFT = 0;         // Register is already in the lower 4 bits of the instruction

  private TrapConstants() {
    // prevent instantiation
  }
}
