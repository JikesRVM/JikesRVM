/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 * Begin execution of a VM_Thread "startoff" method.
 * 21 Oct 1998 Derek Lieber
 *
 * Signature:
 *    void bootThread(int jtoc, int pr, int ti, int fp);
 *
 * Taken:
 *    arg0 == value to put into vm table-of-contents register
 *    arg1 == value to put into vm processor register
 *    arg2 == value to put into vm thread-id register
 *    arg3 == value to put into vm frame-pointer register
 *
 * Returned:
 *    does not return
 *
 */
#define NEED_ASSEMBLER_DECLARATIONS
#include <InterfaceDeclarations.h>
 
       .file    "bootThread.s"
#ifdef __linux__
       .text    0   # function name
       .globl   bootThread   # external visibility
       bootThread:
#else
#ifdef __GNUC__
	.globl	.bootThread
       .bootThread:
#else
       .csect   .bootThread[ro]   # function name
       .globl   .bootThread[ro]   # external visibility
       bootThread:
#endif
#endif
        mr      JTOC,T0
        mr      PROCESSOR_REGISTER,T1
        mr      TI,T2
        mr      FP,T3
        
        /*
         * At this point we've abandoned the C stack and are running on a VM_Thread's stack.
         */
        
        lwz     S0,STACKFRAME_NEXT_INSTRUCTION_OFFSET(FP)   # fetch method entrypoint address
        mtlr    S0
        blr                       # branch to it
