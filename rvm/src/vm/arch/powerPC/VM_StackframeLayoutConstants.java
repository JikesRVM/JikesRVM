/*
 * (C) Copyright IBM Corp. 2001
 */

//$Id$

/**
 *--------------------------------------------------------------------------
 *                     Stackframe layout conventions           
 *---------------------------------------------------------------------------
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
 *                 +===============+
 *                 |  LR save area |
 *                 +---------------+
 *                 |     MI=-1     |   <-- "invisible method" id
 *                 +---------------+
 *             +-> |     FP=0      |   <-- "end of vm stack" sentinal
 *             |   +===============+ . . . . . . . . . . . . . . . . . . . . . . . . . . .
 *             |   |   saved FPRs  |  \                                                  .
 *             |   +---------------+   \_nonvolatile register save area                  .
 *             |   |   saved GPRS  |   /                                                 .
 *             |   +---------------+  /                                                  .
 *             |   |   (padding)   |  <--optional padding so frame size is multiple of 8 .
 *             |   +---------------+                                                     .
 *             |   |     SP        |  <-- caller save/restore area (++)                  .
 *             |   +---------------+                                                     .
 *             |   |   local0      |  \                                                  .
 *             |   +---------------+   \_local variables (++)                            .
 *             |   |   local1      |   /                                                 .
 *             |   +---------------+  /                                                  .
 *             |   |   operand0    |  \                                                  .
 *             |   +---------------+   \_operand stack (++)                              .
 *  (++)SP ->  |   |   operand1    |   /                                                 .
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
 * @author Bowen Alpern
 * @author Derek Lieber
 */
interface VM_StackframeLayoutConstants  {

   static final int STACKFRAME_HEADER_SIZE             = 12; // size of frame header, in bytes
   static final int STACKFRAME_NEXT_INSTRUCTION_OFFSET =  8; // spot for this frame's callee to put return address
   static final int STACKFRAME_METHOD_ID_OFFSET        =  4; // spot for this frame's method id
   static final int STACKFRAME_FRAME_POINTER_OFFSET    =  0; // base of this frame

   static final int STACKFRAME_SENTINAL_FP = -2; // fp value indicating end of stack walkback
   static final int INVISIBLE_METHOD_ID    = -1; // marker for "assembler" frames that have no associated VM_Method

   // Stackframe alignment.
   // Align to 8 byte boundary for good floating point save/restore performance (on powerPC, anyway).
   //
   static final int STACKFRAME_ALIGNMENT = 8;
   static final int STACKFRAME_ALIGNMENT_MASK = STACKFRAME_ALIGNMENT - 1; // roundedUpSize = (size + STACKFRAME_ALIGNMENT_MASK) & ~STACKFRAME_ALIGNMENT_MASK
   
   // Sizes for stacks and subregions thereof.
   // Values are in bytes and must be a multiple of 4 (size of a stack slot).
   //
   static final int STACK_SIZE_GROW      = 8*1024; // how much to grow normal stack when overflow detected
   static final int STACK_SIZE_GUARD     = 8*1024; // max space needed for stack overflow trap processing
   static final int STACK_SIZE_NATIVE    = 4*1024; // max space needed for entry to sysCall# via VM_Magic
   static final int STACK_SIZE_JNINATIVE      = 180*1024; // max space needed for first entry to native code via JNI
   static final int STACK_LOG_JNINATIVE      = 6 + 10; // large constants are a pain.  This is log2 of STACK_SIZE_JNINATIVE
   static final int STACK_SIZE_DLOPEN    = 30*1024; // max space needed for dlopen sys call 
   static final int STACK_SIZE_JNINATIVE_GROW = 184*1024; // size to grow once for native on first entry via JNI
   static final int STACK_SIZE_GCDISABLED= 4*1024; // max space needed while running with gc disabled
   
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
   static final int STACK_SIZE_NORMAL    = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +   4*1024; // initial stack space to allocate for normal    thread (includes guard region)
   static final int STACK_SIZE_BOOT      = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +  20*1024; // total   stack space to allocate for boot      thread (includes guard region)
   static final int STACK_SIZE_COLLECTOR = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED +  20*1024; // total   stack space to allocate for collector thread (includes guard region)
   static final int STACK_SIZE_MAX       = STACK_SIZE_GUARD + STACK_SIZE_GCDISABLED + 244*1024; // upper limit on stack size (includes guard region)
   }



