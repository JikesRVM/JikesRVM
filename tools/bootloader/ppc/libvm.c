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

/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment
 * and branching to its startoff code.  It also deals with interrupt and
 * exception handling.   The file "sys.cpp" contains the o/s support services
 * required by the java class libraries.
 *
 * PowerPC version for Linux
 *
 *
 *             everything except the command line parsing in main().
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE             /* so that the Linux (GNU Libc) string.h will
                                // include strsignal(), */
#endif

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>             // among other things, provides strsignal()
#include <assert.h>
#include <sys/mman.h>
#define SIGNAL_STACKSIZE (16 * 1024)    // in bytes

#include <ucontext.h>
#include <signal.h>
#include <errno.h>
#include <unistd.h>
#define NGPRS 32
// Linux on PPC does not save FPRs - is this true still?
#define NFPRS  32
// Third argument to signal handler is of type ucontext_t
#define SIGNAL_ARG3_IS_UCONTEXT

// The following comes from /usr/src/linux-2.4/arch/ppc/kernel/signal.c
#define ELF_NGREG       48      /* includes nip, msr, lr, etc. */
#define ELF_NFPREG      33      /* includes fpscr */
typedef unsigned long elf_greg_t;
typedef elf_greg_t elf_gregset_t[ELF_NGREG];
struct linux_sigregs {
  elf_gregset_t  gp_regs;
  double   fp_regs[ELF_NFPREG];
  unsigned long  tramp[2];
  /* Programs using the rs6000/xcoff abi can save up to 19 gp regs
     and 18 fp regs below sp before decrementing it. */
  int    abigap[56];
};

// #define PTRS_X_WITHOUT_PUNCTUATION
#define PTRS_VIA_PERCENT_P
//#define PTRS_X_WITH_PUNCTUATION
#define  __STDC_FORMAT_MACROS
/* A uintptr_t is an integral type guaranteed to be able to represent any
   pointer to void (void *)
*/
#include <inttypes.h>           // PRIxPTR, uintptr_t

/* FMTrvmPTR is a printf()-style format string for printing a Jikes RVM
   pointer or a numeric quantity that represents a pointer.  The same string
   works for both, thanks to the rvmPTR_ARG() macro.

   FMTrvmPTR32 is FMTrvmPTR when we know we're just dealing with a 32-bit
   quantity.  This is for dealing with records that haven't yet been upgraded
   to 64-bit size.

   The rvmPTR_ARG(p) macro takes a pointer or unsigned integral value p and
   does magic on it (casts it to uintptr_t, and possibly breaks it apart into
   multiple arguments) so that it can be part of the argument list of a
   printf()-style function.

   rvmPTR32_ARG(p) is to rvmPTR_ARG(p) as FMTrvmPTR32 is to FMTrvmPTR
*/

#ifdef PTRS_X_WITHOUT_PUNCTUATION
#define FMTrvmPTR32 "%08" PRIxPTR

#if defined RVM_FOR_32_ADDR
#define FMTrvmPTR FMTrvmPTR32
#else
#define FMTrvmPTR "%016" PRIxPTR
#endif

#define rvmPTR_ARG(p) ((uintptr_t) (p))
#define rvmPTR32_ARG(p) rvmPTR_ARG((p))

#elif defined PTRS_VIA_PERCENT_P                \

#define FMTrvmPTR32 "%08p"

#ifdef RVM_FOR_32_ADDR
#define FMTrvmPTR FMTrvmPTR32
#else
#define FMTrvmPTR "%016p"
#endif

#define rvmPTR_ARG(p) ((void *) (p))

#elif defined PTRS_X_WITH_PUNCTUATION

#define FMTrvmPTR32                                 \
        "%04" PRIxPTR ","                               \
        "%04" PRIxPTR

// Expands to two arguments.
#define rvmPTR32_ARG(p)                             \
        ((uintptr_t) (p) >> 16) & (uintptr_t) 0xFFFF,   \
        ((uintptr_t) (p) & (uintptr_t) 0xFFFF)

#ifdef RVM_FOR_32_ADDR

#define FMTrvmPTR FMTrvmPTR32
#define rvmPTR_ARG(p) rvmPTR32_ARG(p)

#else

// Expands to a nice fat string.
#  define FMTrvmPTR                             \
             "%04" PRIxPTR                              \
            ",%04" PRIxPTR                              \
            ",%04" PRIxPTR                              \
            ",%04" PRIxPTR

// This expands to four arguments:
#define rvmPTR_ARG(p)                                   \
            (((uintptr_t) (p) >> 48) & (uintptr_t) 0xFFFF),             \
            (((uintptr_t) (p) >> 32) & (uintptr_t) 0xFFFF),         \
            (((uintptr_t) (p) >> 16) & (uintptr_t) 0xFFFF),             \
            (((uintptr_t) (p) & (uintptr_t) 0xFFFF))
#endif

#else
#error "One of PTRS_X_WITH_PUNCTUATION, PTRS_X_WITHOUT_PUNCTUATION, or PTRS_VIA_PERCENT_P must be defined."
#endif
/*  We could also fix the pointer-printing problem by adding numeric
    arguments, via the printf() "%*" feature.   This would let us
    group the digits nicely. */


/* Interface to virtual machine data structures. */
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_EXIT_STATUS_CODES
#define NEED_MEMORY_MANAGER_DECLARATIONS
#include "sys.h"
extern "C" void setLinkage(BootRecord *);

#define GET_GPR(info, r)             ((info)->gpr[(r)])
#define SET_GPR(info, r, value)     (((info)->gpr[(r)]) = (value))
//  int* getRegAddress(mstsave *save, int r) {
//    return &save->gpr[r];
//  }

BootRecord *theBootRecord;
#define NULL 0

#include "../bootImageRunner.h" // In tools/bootImageRunner

/**** These are definitions of items declared in bootImageRunner.h ****/

/* jump buffer for primordial thread */
jmp_buf primordial_jb;

/* Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile = stderr;

/* Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile = stderr;
int SysTraceFd = 2;

/* Command line arguments to be passed to the VM. */
const char **JavaArgs;
int JavaArgc;

/* Emit trace information? */
int lib_verbose = 0;

/* Terminate the VM if 3 or more fatal errors occur.  This is to prevent
   infinitely (recursive) fatal errors. */
int remainingFatalErrors = 3;

/* used to pass jtoc, pr, tid, fp to bootThread.s */
static uintptr_t startupRegs[4];
static uintptr_t VmToc;                 // Location of VM's JTOC

/* Method id for inserting stackframes at sites of hardware traps. */
int HardwareTrapMethodId;

/* TOC offset of Runtime.deliverHardwareException */
Offset DeliverHardwareExceptionOffset;
Offset DumpStackAndDieOffset;      // TOC offset of Scheduler.dumpStackAndDie
Offset DebugRequestedOffset;       // TOC offset of Scheduler.debugRequested

typedef void (*SIGNAL_HANDLER)(int); // Standard unix signal handler.

#include <pthread.h>

pthread_t vm_pthreadid;         // pthread id of the main RVM pthread


int
inRVMAddressSpace(Address addr)
{
  /* get the boot record */
  Address *heapRanges = theBootRecord->heapRanges;
  for (int which = 0; which < MAXHEAPS; which++) {
    Address start = heapRanges[2 * which];
    Address end = heapRanges[2 * which + 1];
    // Test against sentinel.
    if (start == ~(Address) 0 && end == ~ (Address) 0) break;
    if (start <= addr  && addr < end) {
      return true;
    }
  }
  return false;
}

/*! Was a Unix signal raised while VM code was running?

   Taken:    contents of "iar" and "jtoc" registers at time of signal
   Returned: 1 --> vm code was running
             0 --> non-vm (eg. C library) code was running
*/
static int isVmSignal(Address iar, Address jtoc)
{
  return inRVMAddressSpace(iar) && inRVMAddressSpace(jtoc);
}

/* The following code is factored out while getcontext() remains
   unimplemented for PPC Linux.
*/


pt_regs*
getLinuxSavedRegisters(int signum, void* arg3)
{
#if defined(GETCONTEXT_IMPLEMENTED)
  ucontext_t uc;
  getcontext(&uc);
  return uc.uc_mcontext.regs;
#elif defined(SIGNAL_ARG3_IS_UCONTEXT)
  return ((ucontext_t*)arg3)->uc_mcontext.regs;
#else
  /* Unfortunately, getcontext() (/usr/include/ucontext.h) is not implemented
     in Linux at the time of this writing (Linux/PPC Kernel <= 2.4.3), but
     some analysis of the signal stack frames reveals that the info we want
     is on the signal stack.  We do a simple check (get the signum value from
     the structure) to try to make sure we've got it right.
  */
#warning Linux does not support getcontext()
#define SIGCONTEXT_MAGIC 5
  mcontext_t* context = (mcontext_t *) (((void **) arg3) + SIGCONTEXT_MAGIC);
  if (context->signal != signum) {
    // We're in trouble.  Try to produce some useful debugging info
    ERROR_PRINTF("%s:%d Failed to grab context from signal stack frame!\n", __FILE__, __LINE__);
    for (int i = -16; i < 32; i++) {
      ERROR_PRINTF("%12p %p arg3[%d]\n", (void *) ((void**) arg3)[i], ((void**) arg3) + i, i);
    }
    ERROR_PRINTF("trap: %d link: %p nip: %p\n", context->regs->trap, context->regs->link, context->regs->nip);
    exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }

  return context->regs;
#endif
}

/* Handle software signals.

   Note: this code runs in a small "signal stack" allocated by "sigstack()"
   (see later).  Therefore care should be taken to keep the stack depth
   small.  If mysterious crashes seem to occur, consider the possibility that
   the signal stack might need to be made larger. [--DL].
*/
void
cSignalHandler(int signum, siginfo_t* siginfo, void* arg3)
{
  pt_regs *save = getLinuxSavedRegisters(signum, arg3);
  Word iar  =  save->nip;
  // UNUSED: // Address jtoc =  GET_GPR(save, Constants_JTOC_POINTER);

  if (signum == SIGHUP) {
    /* asynchronous signal used to awaken external
                               debugger  */
    if (lib_verbose)
      CONSOLE_PRINTF("%s: signal SIGHUP at ip=" FMTrvmPTR " ignored\n",
              Me, rvmPTR_ARG(iar));
    return;
  }

  if (signum == SIGQUIT) {
    /* asynchronous signal used to awaken internal
                                debugger  */
    /* Turn on the debug-request flag.

       Note that "jtoc" is not necessarily valid, because we might have
       interrupted C-library code, so we use the boot image's
       jtoc address (== VmToc) instead.
    */
    unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
    if (*flag) {
      CONSOLE_PRINTF("%s: debug request already in progress,"
              " please wait\n", Me);
    } else {
      CONSOLE_PRINTF("%s: debug requested, waiting for"
              " a thread switch\n", Me);
      *flag = 1;
    }
    return;
  }

  if (signum == SIGTERM) {

    // Presumably we received this signal because someone wants us
    // to shut down.  Exit directly (unless the lib_verbose flag is set).
    // TODO: Run the shutdown hooks instead.
    if (!lib_verbose) {
      /* Now reraise the signal.  We reactivate the signal's
         default handling, which is to terminate the process.
         We could just call `exit' or `abort',
         but reraising the signal sets the return status
         from the process correctly.
         TODO: Go run shutdown hooks before we re-raise the signal. */
      signal(signum, SIG_DFL);
      raise(signum);
      return;
    }

    CONSOLE_PRINTF("%s: kill requested: invoking dumpStackAndDie\n", Me);
    /* Dump the stack by returning to Scheduler.dumpStackAndDie(),
       passing it the FP of the current thread.

       Note that "jtoc" is not necessarily valid, because we might have
       interrupted C-library code, so we use the boot image's jtoc address
       (== VmToc) instead.
    */
    Address dumpStack
      = *(Address *)((char *)VmToc + DumpStackAndDieOffset);
    save->link = save->nip + 4; // +4 so it looks like a return address
    save->nip = dumpStack;
    SET_GPR(save, Constants_FIRST_VOLATILE_GPR,
            GET_GPR(save, Constants_FRAME_POINTER));
    return;
  }
}


/* Handle hardware traps.

   Note: this code runs in a small "signal stack" allocated by "sigstack()"
   (see later).  Therefore care should be taken to keep the stack depth
   small.  If mysterious crashes seem to occur, consider the possibility that
   the signal stack might  need to be made larger. [--DL].
*/

static const int noise = 0;

/** Now we define the start of the hardware trap handler.  It needs
 * a different prologue for each operating system. */

void
cTrapHandler(int signum, siginfo_t *siginfo, void* arg3)
{
  uintptr_t faultingAddress = (uintptr_t) siginfo->si_addr;
  pt_regs *save = getLinuxSavedRegisters(signum, arg3);
  uintptr_t ip = save->nip;
  uintptr_t lr = save->link;
  Address jtoc =  save->gpr[Constants_JTOC_POINTER];

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler, my jtoc = %p, while the real jtoc = %p\n",jtoc,getJTOC());

    jtoc=(Address)getJTOC();

    // fetch address of java exception handler
    //
    Address javaExceptionHandler
      = *(Address *)((char *)jtoc + DeliverHardwareExceptionOffset);

    const int FP  = Constants_FRAME_POINTER;
    const int P0  = Constants_FIRST_VOLATILE_GPR;
    const int P1  = Constants_FIRST_VOLATILE_GPR+1;

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (1)\n");

    // We are prepared to handle these kinds of "recoverable" traps.
    // (Anything else indicates some sort of unrecoverable vm error)
    //
    //  1. SIGSEGV - a null object dereference of the form "obj[+-fieldOffset]"
    //
    //  2. SIGTRAP - an array bounds trap
    //               or integer divide by zero trap
    //               or stack overflow trap
    //               or explicit nullCheck
    //
    int isNullPtrExn = (signum == SIGSEGV) && (isVmSignal(ip, jtoc));
    if (isNullPtrExn) {
      // Address range filtering.  Must be in very top or very bottom of address range for
      // us to treat this as a null pointer exception.
      // NOTE: assumes that first access off a null pointer occurs at offset of +/- 64k.
      //       Could be false for very large scalars; should generate an explicit null check for those.
#ifdef RVM_FOR_32_ADDR
      uintptr_t faultMask = 0xffff0000;
#else
      uintptr_t faultMask = 0xffffffffffff0000;
#endif
      if (!(((faultingAddress & faultMask) == faultMask) || ((faultingAddress & faultMask) == 0))) {
        if (lib_verbose) {
          CONSOLE_PRINTF("assuming that this is not a null pointer exception because the faulting address does not lie in the first or last page.\n");
        }
        isNullPtrExn = 0;
      }
    }

    int isTrap = signum == SIGTRAP;
    int isRecoverable = isNullPtrExn | isTrap;

    unsigned instruction   = *((unsigned *)ip);

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (2)\n");

    if (lib_verbose || !isRecoverable) {
      CONSOLE_PRINTF("            mem=" FMTrvmPTR "\n",
              rvmPTR_ARG(faultingAddress));
      CONSOLE_PRINTF("             fp=" FMTrvmPTR "\n",
              rvmPTR_ARG(GET_GPR(save,FP)));
      CONSOLE_PRINTF("             tr=" FMTrvmPTR "\n",
              rvmPTR_ARG(GET_GPR(save,Constants_THREAD_REGISTER)));
      CONSOLE_PRINTF("trap/exception: type=%s\n", strsignal(signum));
      CONSOLE_PRINTF("             ip=" FMTrvmPTR "\n", rvmPTR_ARG(ip));
      CONSOLE_PRINTF("          instr=0x%08x\n", instruction);

      CONSOLE_PRINTF("    exn_handler=" FMTrvmPTR "\n", rvmPTR_ARG(javaExceptionHandler));
      CONSOLE_PRINTF("             lr=" FMTrvmPTR "\n",  rvmPTR_ARG(lr));

      CONSOLE_PRINTF("   pthread_self=" FMTrvmPTR "\n", rvmPTR_ARG(pthread_self()));

      if (isRecoverable) {
        TRACE_PRINTF("%s: normal trap\n", Me);
      } else {
        ERROR_PRINTF("%s: internal error trap\n", Me);
        if (--remainingFatalErrors <= 0)
          exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
      }
    }

    /* Copy the trapped register set into the current thread's "hardware
       exception registers" save area. */
    Address thread = GET_GPR(save,Constants_THREAD_REGISTER);
    Address *registers = *(Address **)
                         ((char *)thread + RVMThread_exceptionRegisters_offset);

    Word *gprs
      = *(Word **)((char *)registers + Registers_gprs_offset);
    double   *fprs
      = *(double   **)((char *)registers + Registers_fprs_offset);

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (3)\n");

    Word *ipLoc
      =  (Word  *)((char *)registers + Registers_ip_offset);
    Word *lrLoc
      =  (Word  *)((char *)registers + Registers_lr_offset);
    unsigned char *inuse
      =  (unsigned  char*)((char *)registers + Registers_inuse_offset);

    if (*inuse) {
      CONSOLE_PRINTF("%s: internal error: recursive use of hardware exception registers in thread %p (exiting)\n", Me, thread);
      /* Things went badly wrong, so attempt to generate a useful error
         dump before exiting by returning to Scheduler.dumpStackAndDie,
         passing it the fp of the offending thread.

         We could try to continue, but sometimes doing so results in
         cascading failures and it's hard to tell what the real problem
         was.
      */
      Address dumpStack
        = *(Address *)((char *)jtoc + DumpStackAndDieOffset);
      SET_GPR(save, P0, GET_GPR(save, FP));
      save->link = save->nip + 4; // +4 so it looks like a return address
      save->nip = dumpStack;
      return;
    }

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (4)\n");

    for (int i = 0; i < NGPRS; ++i)
      gprs[i] = save->gpr[i];
    for (int i = 0; i < NFPRS; ++i)
      fprs[i] = ((struct linux_sigregs*)save)->fp_regs[i];
    *ipLoc = save->nip + 4; // +4 so it looks like return address
    *lrLoc = save->link;

    *inuse = 1;

    // Insert artificial stackframe at site of trap.
    // This frame marks the place where "hardware exception registers" were saved.
    //
    Address   oldFp = GET_GPR(save, FP);
    Address   newFp = oldFp - Constants_STACKFRAME_HEADER_SIZE;
    *(Address *)(oldFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = save->nip + 4; // +4 so it looks like return address

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (6)\n");
    *(int *)(newFp + Constants_STACKFRAME_METHOD_ID_OFFSET)
      = HardwareTrapMethodId;
    *(Address *)(newFp + Constants_STACKFRAME_FRAME_POINTER_OFFSET)
      = oldFp;
    SET_GPR(save, FP, newFp);

    /* Set execution to resume in java exception handler rather than
       re-executing the instruction that caused the trap.
    */

    int      trapCode    = Runtime_TRAP_UNKNOWN;
    int      trapInfo    = 0;
    int      haveFrame   = 1;

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (7)\n");
    switch (signum) {
    case SIGSEGV:
      if (isNullPtrExn) {  // touched top segment of memory, presumably by wrapping negatively off 0
        if (lib_verbose) CONSOLE_PRINTF("%s: null pointer trap\n", Me);
        trapCode = Runtime_TRAP_NULL_POINTER;
        break;
      }
      if (lib_verbose) CONSOLE_PRINTF("%s: unknown seg fault\n", Me);
      trapCode = Runtime_TRAP_UNKNOWN;
      break;

    case SIGTRAP:
      if ((instruction & Constants_ARRAY_INDEX_MASK) == Constants_ARRAY_INDEX_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: array bounds trap\n", Me);
        trapCode = Runtime_TRAP_ARRAY_BOUNDS;
        trapInfo = gprs[(instruction & Constants_ARRAY_INDEX_REG_MASK)
                        >> Constants_ARRAY_INDEX_REG_SHIFT];
        break;
      } else if ((instruction & Constants_CONSTANT_ARRAY_INDEX_MASK) == Constants_CONSTANT_ARRAY_INDEX_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: array bounds trap\n", Me);
        trapCode = Runtime_TRAP_ARRAY_BOUNDS;
        trapInfo = ((int)((instruction & Constants_CONSTANT_ARRAY_INDEX_INFO)<<16))>>16;
        break;
      } else if ((instruction & Constants_DIVIDE_BY_ZERO_MASK) == Constants_DIVIDE_BY_ZERO_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: divide by zero trap\n", Me);
        trapCode = Runtime_TRAP_DIVIDE_BY_ZERO;
        break;
      } else if ((instruction & Constants_MUST_IMPLEMENT_MASK) == Constants_MUST_IMPLEMENT_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: must implement trap\n", Me);
        trapCode = Runtime_TRAP_MUST_IMPLEMENT;
        break;
      } else if ((instruction & Constants_STORE_CHECK_MASK) == Constants_STORE_CHECK_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: objarray store check trap\n", Me);
        trapCode = Runtime_TRAP_STORE_CHECK;
        break;
      } else if ((instruction & Constants_CHECKCAST_MASK ) == Constants_CHECKCAST_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: checkcast trap\n", Me);
        trapCode = Runtime_TRAP_CHECKCAST;
        break;
      } else if ((instruction & Constants_REGENERATE_MASK) == Constants_REGENERATE_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: regenerate trap\n", Me);
        trapCode = Runtime_TRAP_REGENERATE;
        break;
      } else if ((instruction & Constants_NULLCHECK_MASK) == Constants_NULLCHECK_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: null pointer trap\n", Me);
        trapCode = Runtime_TRAP_NULL_POINTER;
        break;
      } else if ((instruction & Constants_JNI_STACK_TRAP_MASK) == Constants_JNI_STACK_TRAP) {
        if (lib_verbose) CONSOLE_PRINTF("%s: resize stack for JNI call\n", Me);
        trapCode = Runtime_TRAP_JNI_STACK;
        /* We haven't actually bought the stackframe yet, so pretend that
           we are actually trapping directly from the call instruction
           that invoked the native method that caused the stackoverflow
           trap. */
        haveFrame = 0;
        break;
      } else if ((instruction & Constants_WRITE_BUFFER_OVERFLOW_MASK) == Constants_WRITE_BUFFER_OVERFLOW_TRAP) {
        //!!TODO: someday use logic similar to stack guard page to force a gc
        if (lib_verbose) CONSOLE_PRINTF("%s: write buffer overflow trap\n", Me);
        ERROR_PRINTF("%s: write buffer overflow trap\n", Me);
        exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
      } else if (((instruction & Constants_STACK_OVERFLOW_MASK)
                  == Constants_STACK_OVERFLOW_TRAP)
                 || ((instruction & Constants_STACK_OVERFLOW_MASK)
                     == Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP))
      {
        if (lib_verbose) CONSOLE_PRINTF("%s: stack overflow trap\n", Me);
        trapCode = Runtime_TRAP_STACK_OVERFLOW;
        haveFrame = ((instruction & Constants_STACK_OVERFLOW_MASK)
                     == Constants_STACK_OVERFLOW_TRAP);

        /* Adjust the stack limit downward to give the exception handler
            some space in which to run.
        */
        Address stackLimit = *(Address *)((char *)thread + RVMThread_stackLimit_offset);
        Address stackStart = *(Address *)((char *)thread + RVMThread_stack_offset);
        stackLimit -= Constants_STACK_SIZE_GUARD;
        if (stackLimit < stackStart) {
          /* double fault - stack overflow exception handler used too
             much stack space */
          ERROR_PRINTF(
                  "%s: stack overflow exception (double fault)\n", Me);

          /* Go ahead and get all the stack space we need to generate
           * the error dump (since we're crashing anyways)
           */
          *(Address *)((char *)thread + RVMThread_stackLimit_offset) = 0;

          /* Things are very badly wrong anyways, so attempt to generate
             a useful error dump before exiting by returning to
             Scheduler.dumpStackAndDie, passing it the fp of the
             offending thread. */
          Address dumpStack
            = *(Address *)((char *)jtoc + DumpStackAndDieOffset);
          SET_GPR(save, P0, GET_GPR(save, FP));
          save->link = save->nip + 4; // +4 so it looks like a return address
          save->nip = dumpStack;
          return;
        }
        *(Address *)((char *)thread + RVMThread_stackLimit_offset)
          = stackLimit;

        break;
      }
      if (lib_verbose) CONSOLE_PRINTF("%s: unknown trap\n", Me);
      trapCode = Runtime_TRAP_UNKNOWN;
      break;

    default:
      if (lib_verbose) CONSOLE_PRINTF("%s: unknown trap\n", Me);
      trapCode = Runtime_TRAP_UNKNOWN;
      break;
    }

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (8)\n");
    /* Pass arguments to the Java exception handler.
     */
    SET_GPR(save, P0, trapCode);
    SET_GPR(save, P1, trapInfo);

    if (haveFrame) {
      /* Set the link register to make it look as if the Java exception
         handler had been called directly from the exception site.
      */
      if (save->nip == 0)
        do {
          /* A bad branch -- use the contents of the link register as
                 our best guess of the call site. */
        } while(0);
      else
        save->link = save->nip + 4; // +4 so it looks like a return address
    } else {
      /* We haven't actually bought the stackframe yet, so pretend that
         we are actually trapping directly from the call instruction that
         invoked the method whose prologue caused the stackoverflow.
      */
      *(Address *)(oldFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = save->link;
    }

    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (9)\n");
    /* Resume execution at the Java exception handler.
     */
    save->nip = javaExceptionHandler;
    if (noise) CONSOLE_PRINTF("just got into cTrapHandler (10)\n");

  }


  static void *bootThreadCaller(void *);

// A startup configuration option with default values.
// Declared in bootImageRunner.h
  const char *bootDataFilename     = 0;
  const char *bootCodeFilename     = 0;
  const char *bootRMapFilename     = 0;

// The name of the program that will load and run the RVM.
// Declared in bootImageRunner.h
  char *Me;

  static size_t
  pageRoundUp(size_t size)
  {
    size_t pageSize = 4096;
    return (size + pageSize - 1) / pageSize * pageSize;
  }

  static void*
  mapImageFile(const char *fileName, const void *targetAddress, bool isCode,
               size_t *roundedImageSize) {
    // open image file
    //
    FILE *fin = fopen(fileName, "r");
    if (!fin) {
      ERROR_PRINTF("%s: can't find boot image \"%s\"\n", Me, fileName);
      return (void*)1;
    }

    // measure image size
    //
    if (lib_verbose)
      CONSOLE_PRINTF("%s: loading from \"%s\"\n", Me, fileName);
    fseek(fin, 0L, SEEK_END);
    size_t actualImageSize = ftell(fin);
    *roundedImageSize = pageRoundUp(actualImageSize);
    fseek(fin, 0L, SEEK_SET);


    // allocate memory regions in units of system page size
    //
    void    *bootRegion = 0;
    bootRegion = mmap((void *) targetAddress, *roundedImageSize,
                      PROT_READ | PROT_WRITE | PROT_EXEC,
                      MAP_FIXED | MAP_PRIVATE,
                      fileno(fin), 0);
    if (bootRegion == (void *) MAP_FAILED) {
      ERROR_PRINTF("%s: mmap failed (errno=%d): %e\n",
              Me, errno, errno);
      return (void*)1;
    }
    if (bootRegion != (void *) targetAddress) {
      ERROR_PRINTF("%s: Attempted to mmap in the address %p; "
              " got %p instead.  This should never happen.",
              bootRegion, targetAddress);
      /* Don't check the return value.  This is insane already.
       * If we weren't part of a larger runtime system, I'd abort at this
       * point.  */
      (void) munmap(bootRegion, *roundedImageSize);
      return (void*)1;
    }

    return bootRegion;
  }



  int
  createVM(void)
  {
    // don't buffer trace or error message output
    //
    setbuf(SysErrorFile, NULL);
    setbuf(SysTraceFile, NULL);

    size_t roundedDataRegionSize;

    void *bootDataRegion = mapImageFile(bootDataFilename,
                                        bootImageDataAddress,
                                        false,
                                        &roundedDataRegionSize);
    if (bootDataRegion != bootImageDataAddress)
      return 1;

    size_t roundedCodeRegionSize;
    void *bootCodeRegion = mapImageFile(bootCodeFilename,
                                        bootImageCodeAddress,
                                        true,
                                        &roundedCodeRegionSize);
    if (bootCodeRegion != bootImageCodeAddress)
      return 1;

    size_t roundedRMapRegionSize;
    void *bootRMapRegion = mapImageFile(bootRMapFilename,
                                        bootImageRMapAddress,
                                        true,
                                        &roundedRMapRegionSize);
    if (bootRMapRegion != bootImageRMapAddress)
      return 1;


    // fetch contents of boot record which is at beginning of data portion of boot image
    //
    theBootRecord               = (BootRecord *) bootDataRegion;
    BootRecord& bootRecord   = *theBootRecord;


    if ((bootRecord.spRegister % 4) != 0) {
      /* In the RISC6000 asm manual we read that sp had to be quad word
         aligned, but we don't align our stacks...yet. */
      ERROR_PRINTF("%s: image format error: sp (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.spRegister));
      return 1;
    }

    if ((bootRecord.ipRegister % 4) != 0) {
      ERROR_PRINTF("%s: image format error: ip (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.ipRegister));
      return 1;
    }

    if ((bootRecord.tocRegister % 4) != 0) {
      ERROR_PRINTF("%s: image format error: toc (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.tocRegister));
      return 1;
    }

    if (((int *)bootRecord.spRegister)[-1] != (int) 0xdeadbabe) {
      ERROR_PRINTF("%s: image format error: missing stack sanity check marker (0x%08x)\n", Me, (unsigned) ((int *)bootRecord.spRegister)[-1]);
      return 1;
    }

    if (bootRecord.bootImageDataStart != (Address)bootDataRegion) {
      ERROR_PRINTF("%s: image load error: built for " FMTrvmPTR " but loaded at " FMTrvmPTR "\n",
              Me, bootRecord.bootImageDataStart, bootDataRegion);
      return 1;
    }

    if (bootRecord.bootImageCodeStart != (Address)bootCodeRegion) {
      ERROR_PRINTF("%s: image load error: built for "  FMTrvmPTR " but loaded at "  FMTrvmPTR "\n",
              Me, bootRecord.bootImageCodeStart, bootCodeRegion);
      return 1;
    }

    if (bootRecord.bootImageRMapStart != (Address)bootRMapRegion) {
      ERROR_PRINTF("%s: image load error: built for "  FMTrvmPTR " but loaded at "  FMTrvmPTR "\n",
              Me, bootRecord.bootImageRMapStart, bootRMapRegion);
      return 1;
    }

    // remember jtoc location for later use by trap handler - but jtoc might change
    //
    VmToc = bootRecord.tocRegister;

    // set freespace information into boot record
    //
    bootRecord.initialHeapSize  = initialHeapSize;
    bootRecord.maximumHeapSize  = maximumHeapSize;
    bootRecord.bytesInPage = pageSize;
    bootRecord.bootImageDataStart   = (Address) bootDataRegion;
    bootRecord.bootImageDataEnd     = (Address) bootDataRegion + roundedDataRegionSize;
    bootRecord.bootImageCodeStart   = (Address) bootCodeRegion;
    bootRecord.bootImageCodeEnd     = (Address) bootCodeRegion + roundedCodeRegionSize;
    bootRecord.bootImageRMapStart   = (Address) bootRMapRegion;
    bootRecord.bootImageRMapEnd     = (Address) bootRMapRegion + roundedRMapRegionSize;
    bootRecord.verboseBoot      = verboseBoot;

    // set host o/s linkage information into boot record
    //
    if (lib_verbose) CONSOLE_PRINTF("%s: setting linkage\n", Me);
    setLinkage(&bootRecord);

    // remember location of java exception handler
    //
    DeliverHardwareExceptionOffset = bootRecord.deliverHardwareExceptionOffset;
    HardwareTrapMethodId           = bootRecord.hardwareTrapMethodId;

    // remember JTOC offset of Scheduler.dumpStackAndDie, Scheduler.processors[],
    //    Scheduler.threads[], Scheduler.DebugRequested
    //
    DumpStackAndDieOffset = bootRecord.dumpStackAndDieOffset;
    DebugRequestedOffset = bootRecord.debugRequestedOffset;

    if (lib_verbose) {
      CONSOLE_PRINTF("%s: boot record contents:\n", Me);
      CONSOLE_PRINTF("   bootImageDataStart:   " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageDataStart));
      CONSOLE_PRINTF("   bootImageDataEnd:     " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageDataEnd));
      CONSOLE_PRINTF("   bootImageCodeStart:   " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageCodeStart));
      CONSOLE_PRINTF("   bootImageCodeEnd:     " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageCodeEnd));
      CONSOLE_PRINTF("   bootImageRMapStart:   " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageRMapStart));
      CONSOLE_PRINTF("   bootImageRMapEnd:     " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageRMapEnd));
      CONSOLE_PRINTF("   initialHeapSize:      " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.initialHeapSize));
      CONSOLE_PRINTF("   maximumHeapSize:      " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.maximumHeapSize));
      CONSOLE_PRINTF("   tiRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.tiRegister));
      CONSOLE_PRINTF("   spRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.spRegister));
      CONSOLE_PRINTF("   ipRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.ipRegister));
      CONSOLE_PRINTF("   tocRegister:          " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.tocRegister));
      CONSOLE_PRINTF("   sysConsoleWriteCharIP:" FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.sysConsoleWriteCharIP));
    }

    // install a stack for cSignalHandler() and cTrapHandler() to run on
    //
    char *bottomOfSignalStack = new char[SIGNAL_STACKSIZE];
    char *topOfSignalStack = bottomOfSignalStack + SIGNAL_STACKSIZE;

    // Install hardware trap handler and signal stack
    //   mask all signals during signal handling
    //   exclude the signal used to poke pthreads
    //
    stack_t altstack;
    altstack.ss_sp = bottomOfSignalStack;
    altstack.ss_flags = 0;
    altstack.ss_size = SIGNAL_STACKSIZE;
    if (sigaltstack(&altstack, 0) < 0) {
      ERROR_PRINTF("%s: sigstack failed (errno=%d)\n", Me, errno);
      return 1;
    }
    struct sigaction action;
    action.sa_sigaction = cTrapHandler;
    action.sa_flags     = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
    if (sigfillset(&(action.sa_mask)) ||
        sigdelset(&(action.sa_mask), SIGCONT)) {
      ERROR_PRINTF("%s: sigfillset or sigdelset failed (errno=%d)\n", Me, errno);
      return 1;
    }
    if (sigaction(SIGSEGV, &action, 0) // catch null pointer references
        || sigaction(SIGTRAP, &action, 0) // catch array bounds violations
        || sigaction(SIGILL, &action, 0)) /* catch vm errors (so we can try to
                                          give a traceback) */
    {
      ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
      return 1;
    }

    // install software signal handler
    //
    action.sa_sigaction = cSignalHandler;
    if (sigaction(SIGALRM, &action, 0) /* catch timer ticks (so we can
                                          timeslice user level threads) */
        || sigaction(SIGHUP, &action, 0) /* catch signal to awaken external
                                            debugger */
        || sigaction(SIGQUIT, &action, 0) /* catch signal to awaken internal
                                             debugger */
        || sigaction(SIGTERM, &action, 0)) /* catch signal to dump stack and
                                              die */
    {
      ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
      return 1;
    }

    // Ignore "write (on a socket) with nobody to read it" signals so
    // that sysWriteBytes() will get an EPIPE return code instead of trapping.
    //
    action.sa_handler = (SIGNAL_HANDLER) SIG_IGN;
    if (sigaction(SIGPIPE, &action, 0)) {
      ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
      return 1;
    }

    // set up initial stack frame
    //
    Address  jtoc = bootRecord.tocRegister;
    Address  tr = *(Address *) (bootRecord.tocRegister + bootRecord.bootThreadOffset);
    Address tid = bootRecord.tiRegister;
    Address  ip = bootRecord.ipRegister;
    Address  sp = bootRecord.spRegister;

    // Set up thread stack
    Address  fp = sp - Constants_STACKFRAME_HEADER_SIZE;  // size in bytes
    fp = fp & ~(Constants_STACKFRAME_ALIGNMENT -1);     // align fp

    *(Address *)(fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = ip;
    *(int *)(fp + Constants_STACKFRAME_METHOD_ID_OFFSET) = Constants_INVISIBLE_METHOD_ID;
    *(Address *)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET) = Constants_STACKFRAME_SENTINEL_FP;

    // force any machine code within image that's still in dcache to be
    // written out to main memory so that it will be seen by icache when
    // instructions are fetched back
    //
    sysSyncCache(bootCodeRegion, roundedCodeRegionSize);

    if (setjmp(primordial_jb)) {
      *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;
      // cannot return or else the process will exit.  this is how pthreads
      // work on the platforms I've tried (OS X and Linux).  So, when the
      // primordial thread is done, we just have it idle.  When the process
      // is supposed to exit, it'll call exit().
      for (;;) pause();
    } else {
      if (lib_verbose) {
        CONSOLE_PRINTF("%s: calling boot thread: jtoc = " FMTrvmPTR
                "   tr = " FMTrvmPTR "   tid = %d   fp = " FMTrvmPTR "\n",
                Me, rvmPTR_ARG(jtoc), rvmPTR_ARG(tr), tid, rvmPTR_ARG(fp));
      }
      bootThread(jtoc, tr, tid, fp);
      ERROR_PRINTF("Unexpected return from bootThread\n");
      return 1;
    }

  }


  /*
   * Wrapper for bootThread for a new pthread to start up the VM
   */
  static void *
  bootThreadCaller(void UNUSED *dummy)
  {
    uintptr_t jtoc = startupRegs[0];
    uintptr_t pr   = startupRegs[1];
    uintptr_t tid  = startupRegs[2];
    uintptr_t fp   = startupRegs[3];

    TRACE_PRINTF("about to boot vm:\n");

    bootThread(jtoc, pr, tid, fp);

    ERROR_PRINTF("%s: Unexpected return from vm startup thread\n",
            Me);
    return NULL;

  }


// Get address of JTOC.
  extern "C" void *
  getJTOC()
  {
    return (void*) VmToc;
  }


// We do not have this yet on the PowerPC
  extern Address
  createJavaVM()
  {
    ERROR_PRINTF("Cannot CreateJavaVM on PowerPC yet");
    return 1;
  }
