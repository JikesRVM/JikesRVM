/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;

/**----------------------------------------------------------------------
 *                   Stackframe layout conventions - Intel version.  
 *-----------------------------------------------------------------------
 *
 * A stack is an array of "slots", declared formally as integers, each slot
 * containing either a primitive (byte, int, float, etc), an object pointer,
 * a machine code pointer (a return address pointer), or a pointer to another
 * slot in the same stack (a frame pointer). The interpretation of a slot's 
 * contents depends on the current value of IP, the machine instruction 
 * address register.
 * Each machine code generator provides maps, for use by the garbage collector,
 * that tell how to interpret the stack slots at "safe points" in the
 * program's execution.
 *
 * Here's a picture of what a stack might look like in memory.
 *
 * Note: this (array) object is drawn upside down compared to other objects
 * because the hardware stack grows from high memory to low memory, but
 * array objects are layed out from low memory to high (header first).
 * <pre>
 *  hi-memory
 *              +---------------+                                            ...
 *              |     IP=0      |                                             .
 *              +---------------+                                             .
 *          +-> |     FP=0      |   <-- "end of vm stack" sentinel            .
 *          |   +---------------+                                             . caller's frame
 *          |   |    cmid=0      |   <-- "invisible method" id                .
 *          |   +---------------+                                          ---.
 *          |   |   parameter0  |  \                                        | .
 *          |   +---------------+   \ parameter area                        | .
 *          |   |   parameter1  |   /  (== caller's operand stack area)     | .
 *   ---    |   +---------------+  /                                        |...
 *    |     |   |   saved IP    |  <-- return address (in caller)           |
 *    |      \  +---------------+                                           |
 *  header FP-> |   saved FP    |  <-- this frame's caller's frame          |
 *    |         +---------------+                                           |
 *    |         |    cmid       |  <-- this frame's compiledmethod id       |
 *    |         +---------------+                                           |
 *    |         |   saved GPRs  |  \                                        |
 *    |         +---------------+   \ nonvolatile register save area        |
 *    |         |   saved FPRS  |   /                                       | frame
 *    |         +---------------+                                           |
 *    |         |   local0      |  \                                        |
 *   body       +---------------+   \_local variables area                  |
 *    |         |   local1      |   /                                       |
 *    |         +---------------+  /                                        |
 *    |         |   operand0    |  \                                        |
 *    |         +---------------+   \_operand stack area                    |
 *    |    SP-> |   operand1    |   /                                       |
 *    |         +---------------+  /                                        |
 *    |         |     ...       |                                           |
 *   ---        +===============+                                          ---
 *              |     ...       |
 *              +---------------+
 * stackLimit-> |     ...       | \
 *              +---------------+  \_guard region for detecting & processing stack overflow
 *              |     ...       |  /
 *              +---------------+ /
 *              |(object header)|
 *  low-memory  +---------------+
 *
 *
 *
 *  The opt compiler uses a different stackframe layout
 *
 *  hi-memory
 *              +---------------+                                            ...
 *              |     IP=0      |                                             .
 *              +---------------+                                             .
 *          +-> |     FP=0      |   <-- "end of vm stack" sentinel           .
 *          |   +---------------+                                             . caller's frame
 *          |   |    cmid=-1    |   <-- "invisible method" id                .
 *          |   +---------------+                                          ---.
 *          |   |   parameter0  |  \                                        | .
 *          |   +---------------+   \ parameter area                        | .
 *          |   |   parameter1  |   /  (== caller's operand stack area)     | .
 *   ---    |   +---------------+  /                                        |...
 *    |     |   |   saved IP    |  <-- return address (in caller)           |
 *    |      \  +---------------+                                           |
 *  header FP-> |   saved FP    |  <-- this frame's caller's frame          |
 *    |         +---------------+                                           |
 *    |         |    cmid       |  <-- this frame's compiledmethod id       |
 *   ---        +---------------+                                           |
 *    |         |               |                                           |
 *    |         |  Spill Area   |  <-- spills and other method-specific     |
 *    |         |     ...       |      compiler-managed storage             |
 *    |         +---------------+                                           |
 *    |         |   Saved FP    |     only SaveVolatile Frames              |   
 *    |         |    State      |                                           |
 *    |         +---------------+                                           |
 *    |         |  VolGPR[0]    |                                           |
 *    |         |     ...       |     only SaveVolatile Frames              |   
 *    |         |  VolGPR[n]    |                                           |
 *    |         +---------------+                                           | 
 *   body       |  NVolGPR[k]   |  <-- info.getUnsignedNonVolatileOffset()  | frame
 *    |         |     ...       |   k == info.getFirstNonVolatileGPR()      |
 *    |         |  NVolGPR[n]   |                                           |
 *    |         +---------------+                                           |
 *    |         |  NVolFPR[k]   |                                           |
 *    |         |     ...       |   k == info.getFirstNonVolatileFPR()      |
 *    |         |  NVolFPR[n]   |                                           |
 *    |         +---------------+                                           |
 *    |         |   parameter0  |  \                                        |
 *    |         +---------------+   \_parameters to callee frame            |
 *    |    SP-> |   parameter1  |   /                                       |
 *    |         +---------------+  /                                        |
 *    |         |     ...       |                                           |
 *   ---        +===============+                                          ---
 *              |     ...       |
 *              +---------------+
 * stackLimit-> |     ...       | \
 *              +---------------+  \_guard region for detecting & processing stack overflow
 *              |     ...       |  /
 *              +---------------+ /
 *              |(object header)|
 *  low-memory  +---------------+
 *
 * </pre>
 *
 * @author David Grove
 * @author Bowen Alpern
 */
public interface VM_StackframeLayoutConstants  {

  /** offset of caller's return address from FP */
  static final int STACKFRAME_RETURN_ADDRESS_OFFSET   =  4;
  /** base of this frame */
  static final int STACKFRAME_FRAME_POINTER_OFFSET    =  0;
  /** offset of method id from FP */
  static final int STACKFRAME_METHOD_ID_OFFSET        = -4;
  /** offset of work area from FP */
  static final int STACKFRAME_BODY_OFFSET             = -8;
  /** size of frame header, in bytes */
  static final int STACKFRAME_HEADER_SIZE             = 12;
   
   // space to save entire FPU state.  The FPU state is saved only for 'bridge' frames
   static final int FPU_STATE_SIZE                     = 108;

  /** fp value indicating end of stack walkback */
  static final Address STACKFRAME_SENTINEL_FP = Address.fromIntSignExtend(-2);
  /** marker for "assembler" frames that have no associated VM_Method */
  static final int INVISIBLE_METHOD_ID    = -1;

   // Stackframe alignment.
   // Align to 8 byte boundary for good floating point save/restore performance (on powerPC, anyway).
   //
   static final int STACKFRAME_ALIGNMENT = 8;
   
   // Sizes for stacks and subregions thereof.
   // Values are in bytes and must be a multiple of 4 (size of a stack slot).
   //
  /** how much to grow stack when overflow detected */
  static final int STACK_SIZE_GROW      = 8*1024;
  /** max space needed for stack overflow trap processing */
  static final int STACK_SIZE_GUARD     = 64*1024;
  /** max space needed for any native code called by vm */
  static final int STACK_SIZE_SYSCALL   = 4*1024;
  /** max space needed for dlopen sys call */
  static final int STACK_SIZE_DLOPEN    = 30*1024;
  /** max space needed while running with gc disabled */
  static final int STACK_SIZE_GCDISABLED= 4*1024;
   
   // Complications:
   // - STACK_SIZE_GUARD must be greater than STACK_SIZE_NATIVE or STACK_SIZE_GCDISABLED
   //   to ensure that frames allocated by stack growing code will fit within guard region.
   // - STACK_SIZE_GROW must be greater than STACK_SIZE_NATIVE or STACK_SIZE_GCDISABLED
   //   to ensure that, if stack is grown prior to disabling gc or calling native code,
   //   the new stack will accomodate that code without generating a stack overflow trap.
   // - Values chosen for STACK_SIZE_NATIVE and STACK_SIZE_GCDISABLED are pure guesswork
   //   selected by trial and error.
   
   // Stacks for "normal" threads grow as needed by trapping on guard region.
   // Stacks for "boot" and "collector" threads are fixed in size and cannot grow.
   //
  /** initial stack space to allocate for normal    thread (includes guard region) */
   static final int STACK_SIZE_NORMAL    = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +  200*1024; // initial stack space to allocate for normal    thread (includes guard region)
   static final int STACK_SIZE_BOOT      = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +  20*1024; // total   stack space to allocate for boot      thread (includes guard region)
   static final int STACK_SIZE_COLLECTOR = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +  20*1024; // total   stack space to allocate for collector thread (includes guard region)
   static final int STACK_SIZE_MAX       = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED + 200*1024; // upper limit on stack size (includes guard region)
   
   static final int STACK_SIZE_JNINATIVE_GROW = 0; // TODO!!;
}
