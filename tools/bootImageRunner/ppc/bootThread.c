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
/*
 * Begin execution of a RVMThread "startoff" method.
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
 */
#define NEED_ASSEMBLER_DECLARATIONS
#include <InterfaceDeclarations.h>

       .file    "bootThread.s"
#if (defined __linux__)
#ifdef RVM_FOR_32_ADDR
       .text    0   // function name
       .globl   bootThread   /* external visibility */
       bootThread:
#else
       .text
       .globl  bootThread
       bootThread:
#endif
#elif (defined __MACH__)
       .text
       .globl   _bootThread   /* external visibility */
       _bootThread:
#else
#ifdef __GNUC__
        .globl  .bootThread
       .bootThread:
#else
       .csect   .bootThread[ro]   /* function name */
       .globl   .bootThread[ro]   /* external visibility */
       bootThread:
#endif
#endif
        mr      JTOC,T0
        mr      THREAD_REGISTER,T1
        mr      FP,T3

        /*
         * At this point we've abandoned the C stack and are running on a RVMThread's stack.
         */

#ifdef RVM_FOR_32_ADDR
        lwz     S0,STACKFRAME_RETURN_ADDRESS_OFFSET(FP)   /* fetch method entrypoint address*/
#else
        ld      S0,STACKFRAME_RETURN_ADDRESS_OFFSET(FP)   /* fetch method entrypoint address*/
#endif
        mtlr    S0
        blr                       /* branch to it */
