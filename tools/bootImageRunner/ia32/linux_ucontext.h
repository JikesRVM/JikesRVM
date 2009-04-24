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
#ifndef JRVM_LINUX_IA32_UCONTEXT
#define JRVM_LINUX_IA32_UCONTEXT

#define __MC(context) ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context) (__MC(context).gregs)

#ifndef __x86_64__
#  define IA32_EAX(context) (__GREGS(context)[REG_EAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_EBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_ECX])
#  define IA32_EDX(context) (__GREGS(context)[REG_EDX])
#  define IA32_EDI(context)  (__GREGS(context)[REG_EDI])
#  define IA32_ESI(context)  (__GREGS(context)[REG_ESI])
#  define IA32_EBP(context)  (__GREGS(context)[REG_EBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_ESP])
#  define IA32_EIP(context)  (__GREGS(context)[REG_EIP])

#  define IA32_CS(context)  (__GREGS(context)[REG_CS])
#  define IA32_DS(context)  (__GREGS(context)[REG_DS])
#  define IA32_ES(context)  (__GREGS(context)[REG_ES])
#  define IA32_FS(context)  (__GREGS(context)[REG_FS])
#  define IA32_GS(context)  (__GREGS(context)[REG_GS])
#  define IA32_SS(context)  (__GREGS(context)[REG_SS])

#  define IA32_OLDMASK(context) (__MC(context).oldmask)
#  define IA32_FPFAULTDATA(context)     (__MC(context).cr2)
#else
#  define IA32_EAX(context) (__GREGS(context)[REG_RAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_RBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_RCX])
#  define IA32_EDX(context) (__GREGS(context)[REG_RDX])
#  define IA32_EDI(context)  (__GREGS(context)[REG_RDI])
#  define IA32_ESI(context)  (__GREGS(context)[REG_RSI])
#  define IA32_EBP(context)  (__GREGS(context)[REG_RBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_RSP])
#  define IA32_EIP(context)  (__GREGS(context)[REG_RIP])
#endif

#define IA32_EFLAGS(context)  (__GREGS(context)[REG_EFL])
#define IA32_TRAPNO(context) (__GREGS(context)[REG_TRAPNO])
#define IA32_ERR(context) (__GREGS(context)[REG_ERR])
#define IA32_FALUTVADDR(context) (__GREGS(context)[REG_CS])

#define IA32_FPREGS(context) (__MC(context).fpregs)

// reg = 0..7, n = 0 .. 3
#define IA32_STMM(context, reg, n) (IA32_FPREGS(context)->_st[reg].significand[n])
#define IA32_STMMEXP(context, reg) (IA32_FPREGS(context)->_st[reg].exponent)

/* Currently unused
#define IA32_XMM(context, reg, n) \
*/

#endif
