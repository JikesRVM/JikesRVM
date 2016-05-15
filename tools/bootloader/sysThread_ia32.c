/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Architecture specific thread code for IA32
 */

#include "sys.h"

/**
 * Transfer execution from C to Java for thread startup.
 *
 * Note: jtoc is currently unused on IA-32 32-bit.
 */
void bootThread (void *ip, void *tr, void *sp, void *jtoc UNUSED)
{
  void *saved_ebp;
  asm volatile (
#ifndef __x86_64__
       "mov   %%ebp, %0     \n"
       "mov   %%esp, %%ebp  \n"
       "mov   %3, %%esp     \n"
       "push  %%ebp         \n"
       "call  *%%eax        \n"
       "pop   %%esp         \n"
       "mov   %0, %%ebp     \n"
#else
       "mov   %4, %%r15     \n"
       "mov   %%rbp, %0     \n"
       "mov   %%rsp, %%rbp  \n"
       "mov   %3, %%rsp     \n"
       "push  %%rbp         \n"
       "call  *%%rax        \n"
       "pop   %%rsp         \n"
       "mov   %0, %%rbp     \n"
#endif
       : "=m"(saved_ebp)
       : "a"(ip), // EAX = Instruction Pointer
         "S"(tr), // ESI = Thread Register
         "r"(sp)
#ifdef __x86_64__
        ,"r"(jtoc)
       : "r15"
#endif
       );
}
