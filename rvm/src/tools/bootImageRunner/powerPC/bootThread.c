/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 *$Id$ 
 * Begin execution of a VM_Thread "startoff" method.
 * @author Derek Lieber
 * @date 21 Oct 1998
 *
 * Signature:
 *    void bootThread(int jtoc, int pr, int ti, int fp);
 *
 * Taken:
 *    arg0 == value to put into vm table-of-contents register
 *    arg1 == value to put into vm processor register
 *    arg2 == ignored (is ip) TODO: use it instead of getting from stackframe
 *    arg3 == value to put into vm frame-pointer register
 *
 * Returned:
 *    does not return
 *
 */
#define NEED_ASSEMBLER_DECLARATIONS
#include <InterfaceDeclarations.h>
 
       .file    "bootThread.s"
#if (defined __linux__)
       .text    0   // function name
       .globl   bootThread   /* external visibility */
       bootThread:
#elif (defined __MACH__)
       .text
       .globl   _bootThread   /* external visibility */
       _bootThread:
#else
#ifdef __GNUC__
	.globl	.bootThread
       .bootThread:
#else
       .csect   .bootThread[ro]   /* function name */
       .globl   .bootThread[ro]   /* external visibility */
       bootThread:
#endif
#endif
        mr      JTOC,T0
        mr      PROCESSOR_REGISTER,T1
        mr      FP,T3
        
        /*
         * At this point we've abandoned the C stack and are running on a VM_Thread's stack.
         */
        
#ifdef RVM_FOR_32_ADDR 
        lwz     S0,STACKFRAME_NEXT_INSTRUCTION_OFFSET(FP)   /* fetch method entrypoint address*/
#else
        ld      S0,STACKFRAME_NEXT_INSTRUCTION_OFFSET(FP)   /* fetch method entrypoint address*/
#endif
        mtlr    S0
        blr                       /* branch to it */
