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
#ifndef JRVM_OSX_IA32_UCONTEXT
#define JRVM_OSX_IA32_UCONTEXT

#ifdef __DARWIN_UNIX03 
#define DARWIN_PREFIX(x) __##x
#else
#define DARWIN_PREFIX(x) ##x
#endif

#define __MCSS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(ss)
#define __MCES(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(es)
#define __MCFS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(fs)

#define IA32_EAX(context) (__MCSS(context).DARWIN_PREFIX(eax))
#define IA32_EBX(context) (__MCSS(context).DARWIN_PREFIX(ebx))
#define IA32_ECX(context) (__MCSS(context).DARWIN_PREFIX(ecx))
#define IA32_EDX(context) (__MCSS(context).DARWIN_PREFIX(edx))
#define IA32_EDI(context)  (__MCSS(context).DARWIN_PREFIX(edi))
#define IA32_ESI(context)  (__MCSS(context).DARWIN_PREFIX(esi))
#define IA32_EBP(context)  (__MCSS(context).DARWIN_PREFIX(ebp))
#define IA32_ESP(context) (__MCSS(context).DARWIN_PREFIX(esp))
#define IA32_SS(context)  (__MCSS(context).DARWIN_PREFIX(ss))
#define IA32_EFLAGS(context)  (__MCSS(context).DARWIN_PREFIX(eflags))
#define IA32_EIP(context)  (__MCSS(context).DARWIN_PREFIX(eip))
#define IA32_CS(context)  (__MCSS(context).DARWIN_PREFIX(cs))
#define IA32_DS(context)  (__MCSS(context).DARWIN_PREFIX(ds))
#define IA32_ES(context)  (__MCSS(context).DARWIN_PREFIX(es))
#define IA32_FS(context)  (__MCSS(context).DARWIN_PREFIX(fs))
#define IA32_GS(context)  (__MCSS(context).DARWIN_PREFIX(gs))

#define IA32_TRAPNO(context) (__MCES(context).DARWIN_PREFIX(trapno))
#define IA32_ERR(context) (__MCES(context).DARWIN_PREFIX(err))
#define IA32_FALUTVADDR(context) (__MCES(context).DARWIN_PREFIX(faultvaddr))

// FIXME: These don't seem to have an OSX equivalent
#define IA32_FPSTATE(context) (0xFFFFFFFF)
#define IA32_OLDMASK(context) (0xFFFFFFFF)

// FIXME: Not sure which structure member corresponds...
#define IA32_FPFAULTDATA(context)     (__MCFS(context).DARWIN_PREFIX(fpu_dp))

// Always defined in OSX
#define IA32_FPREGS(context)     (1)

// reg = 0..7, n = 0 .. 4
#define IA32_STMM(context, reg, n) \
  (((unsigned short *)(__MCFS(context).DARWIN_PREFIX(fpu_stmm##reg).DARWIN_PREFIX(mmst_reg)))[n])

#define IA32_STMMEXP(context, reg) \
  (((unsigned short *)(__MCFS(context).DARWIN_PREFIX(fpu_stmm##reg).DARWIN_PREFIX(mmst_reg)))[4])

/* Currently unused
// reg = 0..7, n = 0 .. 7
#define IA32_XMM(context, reg, n) \
        (((unsigned short *)(__MCFS(context).DARWIN_PREFIX(fpu_xmm##reg).DARWIN_PREFIX(mmst_reg)))[n])
*/

#endif
