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
#ifndef JRVM_SOLARIS_IA32_UCONTEXT
#define JRVM_SOLARIS_IA32_UCONTEXT

#define __MC(context) ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context) (__MC(context).gregs)

#define IA32_ESP(context) (__GREGS(context)[ESP])

#define IA32_EAX(context) (__GREGS(context)[EAX])
#define IA32_EBX(context) (__GREGS(context)[EBX])
#define IA32_ECX(context) (__GREGS(context)[ECX])
#define IA32_EDX(context) (__GREGS(context)[EDX])
#define IA32_EDI(context)  (__GREGS(context)[EDI])
#define IA32_ESI(context)  (__GREGS(context)[ESI])
#define IA32_EBP(context)  (__GREGS(context)[EBP])
#define IA32_ESP(context) (__GREGS(context)[ESP])
#define IA32_SS(context)  (__GREGS(context)[SS])
#define IA32_EFLAGS(context)  (__GREGS(context)[EFL])
#define IA32_EIP(context)  (__GREGS(context)[EIP])
#define IA32_CS(context)  (__GREGS(context)[CS])
#define IA32_DS(context)  (__GREGS(context)[DS])
#define IA32_ES(context)  (__GREGS(context)[ES])
#define IA32_FS(context)  (__GREGS(context)[FS])
#define IA32_GS(context)  (__GREGS(context)[GS])
#define IA32_TRAPNO(context) (__GREGS(context)[TRAPNO])
#define IA32_ERR(context) (__GREGS(context)[ERR])
#define IA32_FALUTVADDR(context) (__GREGS(context)[CS])

#define IA32_FPREGS(context) (__MC(context).fpregs)

// FIXME: These don't seem to have a Solaris equivalent
#define IA32_FPFAULTDATA(context)     (0xFFFFFFFF)
#define IA32_FPSTATE(context) (0xFFFFFFF) 
#define IA32_OLDMASK(context) (0xFFFFFFFF)

// reg = 0..7, n = 0 .. 3
#define IA32_STMM(context, reg, n) (IA32_FPREGS(context)->_st[reg].significand[n])
#define IA32_STMMEXP(context, reg) (IA32_FPREGS(context)->_st[reg].exponent)

/* Currently unused
#define IA32_XMM(context, reg, n) \
*/

#endif
