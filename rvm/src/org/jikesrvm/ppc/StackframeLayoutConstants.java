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

import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 *--------------------------------------------------------------------------
 *                     Stackframe layout conventions
 *---------------------------------------------------------------------------
 *
 * A stack is an array of "slots", declared formally as 4 bytes on 32bit architectures
 * and 8 bytes on 64bit architectures, each slot
 * containing either a primitive (byte, int, float, etc), an object pointer,
 * a machine code pointer (a return address pointer), or a pointer to another
 * slot in the same stack (a frame pointer). The interpretation of a slot's
 * contents depends on the current value of IP, the machine instruction
 * address register.
 * Each machine code generator provides maps, for use by the garbage collector,
 * that tell how to interpret the stack slots at "safe points" in the
 * program's execution.<p>
 *
 * Here's a picture of what a stack might look like in memory.<p>
 *
 * Note: this (array) object is drawn upside down compared to other objects
 * because the hardware stack grows from high memory to low memory, but
 * array objects are laid out from low memory to high (header first).
 * <pre>
 *  hi-memory
 *                 +===============+
 *                 |  LR save area |
 *                 +---------------+
 *                 |     MI=-1     |   <-- "invisible method" id
 *                 +---------------+
 *             +-> |     FP=0      |   <-- "end of vm stack" sentinel
 *             |   +===============+ . . . . . . . . . . . . . . . . . . . . . . . . . . .
 *             |   |   saved FPRs  |  \                                                  .
 *             |   +---------------+   \_nonvolatile register save area                  .
 *             |   |   saved GPRS  |   /                                                 .
 *             |   +---------------+  /                                                  .
 *             |   |   (padding)   |  <--optional padding so frame size is multiple of 8 .
 *             |   +---------------+                                                     .
 *             |   |   local0      |  \                                                  .
 *             |   +---------------+   \_local variables (++)                            .
 *             |   |   local1      |   /                                                 .
 *             |   +---------------+  /                                                  .
 *             |   |   operand0    |  \                                                  .
 *             |   +---------------+   \_operand stack (++)                              .
 *             |   |   operand1    |   /                                                 .
 *             |   +---------------+  /                                                  ..frame
 *             |   |     ...       |                                                     .
 *             |   +---------------+                                                     .
 *             |   |    spill1     |  \                                                  .
 *             |   +---------------+   \_parameter spill area                            .
 *             |   |    spill0     |   /                                                 .
 *             |   +===============+  /                                                  .
 *             |   |               |   <-- spot for this frame's callee's return address .
 *             |   +---------------+                                                     .
 *             |   |     MI        |   <-- this frame's method id                        .
 *             \   +---------------+                                                     .
 *      FP ->      |   saved FP    |   <-- this frame's caller's frame                   .
 *                 +===============+ . . . . . . . . . . . . . . . . . . . . . . . . . . .
 * th.stackLimit-> |     ...       | \
 *                 +---------------+  \_guard region for detecting & processing stack overflow
 *                 |     ...       |  /
 *                 +---------------+ /
 *                 |(object header)|
 *  low-memory     +---------------+
 *
 * note: (++) means "baseline compiler frame layout and register
 * usage conventions"
 *
 * </pre>
 *
 * On the 64 bit powerPC some design decisions were made as follows:<p>
 *
 * An Address, Float and Integer-Like types take 1 slot of 8 bytes.
 * The 4 byte quantities resides in the least significant half of the slot,
 * which is at side of the higher addresses.
 * A Long and a Double take 2 slots of 8 bytes, one of which is not used:
 * the actual value is also stored at the side of the highest addresses.<p>
 *
 * Here are some pictures of what a stackslot might look like in memory.
 *
 * <pre>
 *
 *                               64 bit                                          32 bit
 *
 *   High Memory
 *
 *   |  Stack grows down
 *   |
 *   |              +---------------+---------------+                        +---------------+
 *   |              |  Integer      | '''garbage''' |                        |    Integer    |
 *   |              +---------------+---------------+                        +---------------+
 *   |              |            Address            |                        |    Address    |
 *   |              +---------------+---------------+                        +---------------+
 *   |              |              Long             |                        |               |
 *   |              +---------------+---------------+                        +--    Long  ---+
 *   |              |         '''garbage'''         |                        |               |
 *   |              +---------------+---------------+                        +---------------+
 *   |              |Byte| ''| 'g| a| r b a g e ''' |                        |Byte| 'garbage'|
 *   |              +---------------+---------------+                        +---------------+
 *   |              |            Double             |                        |               |
 *   |              +---------------+---------------+                        +---  Double ---+
 *   |              |         '''garbage'''         |                        |               |
 *   |              +---------------+---------------+                        +---------------+
 *   |              |            empty              |                        |     empty     |
 *   |              +---------------+---------------+                        +---------------+
 *  \ /
 *
 *   Low Memory
 *
 * </pre>
 */
public final class StackframeLayoutConstants {

  public static final int LOG_BYTES_IN_STACKSLOT = LOG_BYTES_IN_ADDRESS;
  public static final int BYTES_IN_STACKSLOT = 1 << LOG_BYTES_IN_STACKSLOT;

  /** size of frame header, in bytes */
  public static final int STACKFRAME_HEADER_SIZE = 3 * BYTES_IN_STACKSLOT;

  // SVR4 ABI has no space between FP and LR, swap the positions for LR and CMID depending on ABI.
  public static final Offset STACKFRAME_RETURN_ADDRESS_OFFSET = Offset.fromIntSignExtend(VM.BuildForPower64ELF_ABI ? 2 * BYTES_IN_STACKSLOT : BYTES_IN_STACKSLOT);
  public static final Offset STACKFRAME_METHOD_ID_OFFSET = Offset.fromIntSignExtend(VM.BuildForPower64ELF_ABI ? BYTES_IN_STACKSLOT : 2 * BYTES_IN_STACKSLOT);

  /** base of this frame **/
  public static final Offset STACKFRAME_FRAME_POINTER_OFFSET = Offset.zero();

  /** fp value indicating end of stack walkback */
  public static final Address STACKFRAME_SENTINEL_FP = Address.fromIntSignExtend(-2);
  /** marker for "assembler" frames that have no associated RVMMethod **/
  public static final int INVISIBLE_METHOD_ID = -1;

  /**
   * Stackframe alignment. Align to 8 byte boundary for good floating point
   * save/restore performance (on powerPC, anyway).
   */
  public static final int STACKFRAME_ALIGNMENT = BYTES_IN_DOUBLE;

  // Sizes for stacks and sub-regions thereof.
  // Values are in bytes and must be a multiple of 8 (size of a stack slot on 64-architecture).

  /** how much to grow normal stack when overflow detected */
  public static final int STACK_SIZE_GROW = 8 * 1024;
  /** max space needed for stack overflow trap processing */
  public static final int STACK_SIZE_GUARD = 8 * 1024;
  /** max space needed for entry to sysCall# via Magic */
  public static final int STACK_SIZE_NATIVE = 4 * 1024;
  /** max space needed for first entry to native code via JNI */
  public static final int STACK_SIZE_JNINATIVE = 180 * 1024;
  /** max space needed for dlopen sys call */
  public static final int STACK_SIZE_DLOPEN = 30 * 1024;
  /** size to grow once for native on first entry via JNI */
  public static final int STACK_SIZE_JNINATIVE_GROW = 184 * 1024;
  /** max space needed while running with gc disabled */
  public static final int STACK_SIZE_GCDISABLED = 4 * 1024; //
  /** upper limit on stack size (includes guard region) */
  public static final int STACK_SIZE_MAX = 512 * 1024;

  // Complications:
  // - STACK_SIZE_GUARD must be greater than STACK_SIZE_NATIVE or STACK_SIZE_GCDISABLED
  //   to ensure that frames allocated by stack growing code will fit within guard region.
  // - STACK_SIZE_GROW must be greater than STACK_SIZE_NATIVE or STACK_SIZE_GCDISABLED
  //   to ensure that, if stack is grown prior to disabling gc or calling native code,
  //   the new stack will accommodate that code without generating a stack overflow trap.
  // - Values chosen for STACK_SIZE_NATIVE and STACK_SIZE_GCDISABLED are pure guess work
  //   selected by trial and error.
  //

  // Initial stack sizes. Note that stacks for collector threads cannot grow.

  /** Initial stack size for normal thread. Stacks for "normal" threads grow as needed by trapping on guard region. */
  public static final int STACK_SIZE_NORMAL = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED + 16 * 1024 + (VM.BuildFor64Addr ? 256 * 1024 : 0);
  /** Initial stack size for boot thread. Stacks for "boot" thread grow as needed - boot thread calls JNI during initialization */
  public static final int STACK_SIZE_BOOT = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED + STACK_SIZE_JNINATIVE + 128 * 1024;
  /** Initial stack size for collector thread. Stacks for "collector" threads are fixed in size and cannot grow. */
  public static final int STACK_SIZE_COLLECTOR = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED + 32 * 1024;

  private StackframeLayoutConstants() {
    // prevent instantiation
  }

}
