/*
 * (C) Copyright © IBM Corp 2001,2002,2003,2004
 *
 * $Id$
 */

/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment
 * and branching to its startoff code.  It also deals with interrupt and
 * exception handling.   The file "sys.C" contains the o/s support services
 * required by the java class libraries. 
 *
 * PowerPC version for AIX, Linux, and Mac OS X
 *
 * @author  Derek Lieber
 * @modified Perry Cheng
 * @date    03 Feb 1998
 *
 * @date    17 Oct 2000     Split from the original RunBootImage.  Contains
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
#include <sys/shm.h>
#define SIGNAL_STACKSIZE (16 * 1024)    // in bytes

/* There are several ways to allocate large areas of virtual memory:

   1. malloc() is simplest, but doesn't allow a choice of address, thus
      requiring address relocation of the boot image.  Unsupported. 

   2. mmap() is simple to use but, on AIX, uses 2x amount of physical memory
      requested (per Al Chang) 

   3. shmat() doesn't have the 2x problem, but is limited to 256M per
      allocation on AIX 4.1 (unlimited on 4.3) 

   [--DL] 
*/

#ifdef RVM_FOR_LINUX
#include <asm/cache.h>
#define HAVE_SA_SIGACTION       /* Set this if a "struct sigaction" contains
                                 * a member named "sa_sigaction". */
#endif

#ifdef RVM_FOR_OSX
#include <sys/stat.h>
#include <mach/ppc/thread_status.h>
extern "C"     int sigaltstack(const struct sigaltstack *ss, struct sigaltstack *oss);
#define MAP_ANONYMOUS MAP_ANON
#define NEED_STRSIGNAL
#endif

#if (defined RVM_FOR_LINUX || defined RVM_FOR_OSX)
#include <ucontext.h>
#include <signal.h>
#include <errno.h>
#include <unistd.h>
#define USE_MMAP 1 // choose mmap() for Linux --SB
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
	elf_gregset_t	gp_regs;
	double		fp_regs[ELF_NFPREG];
	unsigned long	tramp[2];
	/* Programs using the rs6000/xcoff abi can save up to 19 gp regs
	   and 18 fp regs below sp before decrementing it. */
	int		abigap[56];
};

#endif

#ifdef RVM_FOR_AIX
#define USE_MMAP 0 // choose shmat() otherwise --DL
#include <sys/cache.h>
#include <sys/context.h>
extern "C" char *sys_siglist[];
#define NEED_STRSIGNAL
#endif  // RVM_FOR_AIX

#ifdef NEED_STRSIGNAL
#define strsignal(signum) ((signum) < NSIG ? sys_siglist[(signum)] : "?")
#endif //  NEED_STRSIGNAL



// #define PTRS_X_WITHOUT_PUNCTUATION
// #define PTRS_VIA_PERCENT_P
#define PTRS_X_WITH_PUNCTUATION
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
    #elif defined RVM_FOR_64_ADDR
        #define FMTrvmPTR "%016" PRIxPTR
    #else
        #error "RVM_FOR_64_ADDR or RVM_FOR_32_ADDR must be defined"
    #endif // RVM_FOR_XX_ADDR

    #define rvmPTR_ARG(p) ((uintptr_t) (p))
    #define rvmPTR32_ARG(p) rvmPTR_ARG((p))

#elif defined PTRS_VIA_PERCENT_P                \

    #define FMTrvmPTR32 "%08p"

    #ifdef RVM_FOR_32_ADDR
        #define FMTrvmPTR FMTrvmPTR32
    #elif defined RVM_FOR_64_ADDR
        #define FMTrvmPTR "%016p"
    #else
        #error "RVM_FOR_64_ADDR or RVM_FOR_32_ADDR must be defined"
    #endif // RVM_FOR_XX_ADDR

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

    #elif defined RVM_FOR_64_ADDR

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
    #else
        #error "RVM_FOR_64_ADDR or RVM_FOR_32_ADDR must be defined"
    #endif // RVM_FOR_XX_ADDR

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
#define NEED_MM_INTERFACE_DECLARATIONS
#include <InterfaceDeclarations.h>
extern "C" void setLinkage(VM_BootRecord *);

#if (defined RVM_FOR_OSX)
#define GET_GPR(info, r) (*getRegAddress((info), (r)))
#define SET_GPR(info, r, value) (*getRegAddress((info), (r))=(value))

VM_Word * 
getRegAddress(ppc_thread_state_t *state, int r) 
{
    VM_Word *result = 0;
    switch (r) {
    case  0:
        result = &state->r0;
        break;
    case 1  :
        result = &state->r1;
        break;
    case 2  :
        result = &state->r2;
        break;
    case 3  :
        result = &state->r3;
        break;
    case 4  :
        result = &state->r4;
        break;
    case 5  :
        result = &state->r5;
        break;
    case 6  :
        result = &state->r6;
        break;
    case 7  :
        result = &state->r7;
        break;
    case 8  :
        result = &state->r8;
        break;
    case 9  :
        result = &state->r9;
        break;
    case 10  :
        result = &state->r10;
        break;
    case 11  :
        result = &state->r11;
        break;
    case 12  :
        result = &state->r12;
        break;
    case 13  :
        result = &state->r13;
        break;
    case 14  :
        result = &state->r14;
        break;
    case 15  :
        result = &state->r15;
        break;
    case 16  :
        result = &state->r16;
        break;
    case 17  :
        result = &state->r17;
        break;
    case 18  :
        result = &state->r18;
        break;
    case 19  :
        result = &state->r19;
        break;
    case 20  :
        result = &state->r20;
        break;
    case 21  :
        result = &state->r21;
        break;
    case 22  :
        result = &state->r22;
        break;
    case 23  :
        result = &state->r23;
        break;
    case 24  :
        result = &state->r24;
        break;
    case 25  :
        result = &state->r25;
        break;
    case 26  :
        result = &state->r26;
        break;
    case 27  :
        result = &state->r27;
        break;
    case 28  :
        result = &state->r28;
        break;
    case 29  :
        result = &state->r29;
        break;
    case 30  :
        result = &state->r30;
        break;
    case 31  :
        result = &state->r31;
        break;
    }

    return result;
}

#else
#define GET_GPR(info, r)             ((info)->gpr[(r)])
#define SET_GPR(info, r, value)     (((info)->gpr[(r)]) = (value))
//  int* getRegAddress(mstsave *save, int r) {
//    return &save->gpr[r];
//  }
#endif  

VM_BootRecord *theBootRecord;
#define VM_NULL 0

#include "../bootImageRunner.h" // In rvm/src/tools/bootImageRunner

/**** These are definitions of items declared in bootImageRunner.h ****/

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

/* TOC offset of VM_Runtime.deliverHardwareException */
VM_Offset DeliverHardwareExceptionOffset; 
VM_Offset DumpStackAndDieOffset;      // TOC offset of VM_Scheduler.dumpStackAndDie
static VM_Offset ProcessorsOffset;    // TOC offset of VM_Scheduler.processors[]
VM_Offset DebugRequestedOffset;       // TOC offset of VM_Scheduler.debugRequested

typedef void (*SIGNAL_HANDLER)(int); // Standard unix signal handler.

#include "../pthread-wrappers.h"     // We do not include 
                                     // pthread-wrappers.h until the last
                                     // possible minutes, since other include
                                     // files may include <pthread.h>.  We
                                     // will fail to compile if that happens.

pthread_t vm_pthreadid;         // pthread id of the main RVM pthread


static int 
inRVMAddressSpace(VM_Address addr) 
{
    /* get the boot record */
    VM_Address *heapRanges = theBootRecord->heapRanges;
    for (int which = 0; which < MAXHEAPS; which++) {
        VM_Address start = heapRanges[2 * which];
        VM_Address end = heapRanges[2 * which + 1];
        // Test against sentinel.
        if (start == ~(VM_Address) 0 && end == ~ (VM_Address) 0) break;
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
static int isVmSignal(VM_Address iar, VM_Address jtoc) 
{
    return inRVMAddressSpace(iar) && inRVMAddressSpace(jtoc);
}

#ifdef RVM_FOR_LINUX
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
     fprintf(stderr, "%s:%d Failed to grab context from signal stack frame!\n", __FILE__, __LINE__);
     for (int i = -16; i < 32; i++) {
       fprintf(stderr, "%12p %p arg3[%d]\n", (void *) ((void**) arg3)[i], ((void**) arg3) + i, i);
     }
     fprintf(stderr, "trap: %d link: %p nip: %p\n", context->regs->trap, context->regs->link, context->regs->nip);
     exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
   }

   return context->regs;
#endif
}
#endif

/* Handle software signals.

   Note: this code runs in a small "signal stack" allocated by "sigstack()"
   (see later).  Therefore care should be taken to keep the stack depth
   small.  If mysterious crashes seem to occur, consider the possibility that
   the signal stack might need to be made larger. [--DL].
*/
#ifdef RVM_FOR_LINUX
void 
cSignalHandler(int signum, siginfo_t* siginfo, void* arg3) 
{
    pt_regs *save = getLinuxSavedRegisters(signum, arg3);
    VM_Word iar  =  save->nip;
#endif
#if 0
} // so emacs knows to match up { and } nicely :)
#endif
#ifdef RVM_FOR_AIX
void 
cSignalHandler(int signum, int UNUSED zero, sigcontext *context) 
{
#ifdef RVM_FOR_32_ADDR
    mstsave *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/mstsave.h"
#endif
#ifdef RVM_FOR_64_ADDR
    context64 *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/context.h"
#endif
    VM_Word iar  =  save->iar;
#endif
#if 0
} // so emacs knows to match up { and } nicely :)
#endif
#if (defined RVM_FOR_OSX)
void
cSignalHandler(int signum, siginfo_t * UNUSED zero, struct ucontext *context)
{
    struct mcontext* info = context->uc_mcontext;
    ppc_thread_state_t *save = &info->ss;
    VM_Word iar  =  save->srr0;
#endif
    // UNUSED: // VM_Address jtoc =  GET_GPR(save, VM_Constants_JTOC_POINTER);
    
    if (signum == SIGALRM) {     
        processTimerTick();
#if defined RVM_FOR_OSX
        sigreturn((struct sigcontext*) context);
#endif
        return;
    }
    
    if (signum == SIGHUP) { /* asynchronous signal used to awaken external
                               debugger  */
        if (lib_verbose)
            fprintf(SysTraceFile, "%s: signal SIGHUP at ip=" FMTrvmPTR " ignored\n",
                    Me, rvmPTR_ARG(iar));
#if defined RVM_FOR_OSX
        sigreturn((struct sigcontext*)context);
#endif
        return;
    }
   
    if (signum == SIGQUIT) { /* asynchronous signal used to awaken internal
                                debugger  */
        /* Turn on the debug-request flag.
           
           Note that "jtoc" is not necessarily valid, because we might have
           interrupted C-library code, so we use the boot image's
           jtoc address (== VmToc) instead.  
        */
        unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
        if (*flag) {
            fprintf(SysTraceFile, "%s: debug request already in progress,"
                    " please wait\n", Me);
        } else {
            fprintf(SysTraceFile, "%s: debug requested, waiting for"
                    " a thread switch\n", Me);
            *flag = 1;
        }
#if defined RVM_FOR_OSX
        sigreturn((struct sigcontext*)context);
#endif
        return;
    }

    if (signum == SIGTERM) {

        fprintf(SysTraceFile, "%s: kill requested: (exiting)\n", Me);
        /* This process was killed from the command line with a DumpStack
           signal.

           Dump the stack by returning to VM_Scheduler.dumpStackAndDie(),
           passing it the FP of the current thread.

           Note that "jtoc" is not necessarily valid, because we might have
           interrupted C-library code, so we use the boot image's jtoc address
           (== VmToc) instead.
        */
        VM_Address dumpStack
            = *(VM_Address *)((char *)VmToc + DumpStackAndDieOffset);
#ifdef RVM_FOR_LINUX
        save->link = save->nip + 4; // +4 so it looks like a return address
        save->nip = dumpStack;
#elif defined RVM_FOR_OSX
        save->lr = save->srr0 + 4; // +4 so it looks like a return address
        save->srr0 = dumpStack;
#elif defined RVM_FOR_AIX
        save->lr = save->iar + 4; // +4 so it looks like a return address
        save->iar = dumpStack;
#endif
        SET_GPR(save, VM_Constants_FIRST_VOLATILE_GPR,
                GET_GPR(save, VM_Constants_FRAME_POINTER));
#if defined RVM_FOR_OSX
        sigreturn((struct sigcontext*)context);
#endif
        return;
    }
#if defined RVM_FOR_OSX
    sigreturn((struct sigcontext*)context);
#endif
}
 

/* Handle hardware traps.

   Note: this code runs in a small "signal stack" allocated by "sigstack()"
   (see later).  Therefore care should be taken to keep the stack depth
   small.  If mysterious crashes seem to occur, consider the possibility that
   the signal stack might  need to be made larger. [--DL].
*/

/* First an aux. routine: */
#ifdef RVM_FOR_AIX
uintptr_t testFaultingAddress = 0xdead1234;

int faultingAddressLocation = -1; // uninitialized
uintptr_t
#if defined RVM_FOR_32_ADDR
getFaultingAddress(mstsave *save) 
#elif defined RVM_FOR_64_ADDR
    getFaultingAddress(context64 *save)
#endif
{
    if (lib_verbose) {
#if (_AIX51)
        fprintf(SysTraceFile, "save->except[0]=" FMTrvmPTR "\n", 
                rvmPTR_ARG(save->except[0]));
#else
        fprintf(SysTraceFile, "save->o_vaddr=" FMTrvmPTR "\n", rvmPTR_ARG(save->o_vaddr));
#endif
    }

#if (_AIX51)
    if (faultingAddressLocation == -1) {
        if (save->except[0] == testFaultingAddress) 
            faultingAddressLocation = 0;
        else {
            fprintf(SysTraceFile, "Could not figure out where faulting address is stored - exiting\n");
            exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
        }
    }
    return save->except[0];
#else
    if (faultingAddressLocation == -1) {
        if (save->o_vaddr == testFaultingAddress) {
            faultingAddressLocation = 0;
        } else {
            fprintf(SysTraceFile, "Could not figure out where"
                    " faulting address is stored - exiting\n");
            exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
        }
    }
    return save->o_vaddr;
#endif
}
#endif // RVM_FOR_AIX




/** Now we define the start of the hardware trap handler.  It needs
 * a different prologue for each operating system. */

#ifdef RVM_FOR_LINUX
void 
cTrapHandler(int signum, siginfo_t *siginfo, void* arg3) 
{
    uintptr_t faultingAddress = (uintptr_t) siginfo->si_addr;
    pt_regs *save = getLinuxSavedRegisters(signum, arg3);
    uintptr_t ip = save->nip;
    uintptr_t lr = save->link;
    VM_Address jtoc =  save->gpr[VM_Constants_JTOC_POINTER];
#endif // RVM_FOR_LINUX
#if 0
} // for balancing braces { } in the text editor; ignore.
#endif

#if (defined RVM_FOR_OSX)
void
cTrapHandler(int signum, siginfo_t *siginfo, struct ucontext *context)
{
    struct mcontext* info = context->uc_mcontext;
    ppc_thread_state_t *save = &info->ss;
    ppc_float_state_t       *fs = &info->fs;
    unsigned ip  =  save->srr0;
    uintptr_t lr = save->lr;
    VM_Address jtoc =  GET_GPR(save, VM_Constants_JTOC_POINTER);
    if (isVmSignal(ip, jtoc)) {
        if ((signum == SIGSEGV || signum == SIGBUS) &&
            siginfo->si_addr == (void*)save->srr0) {
            siginfo->si_addr = (void*)context->uc_mcontext->es.dar;
        }
    }
    uintptr_t faultingAddress = (uintptr_t)siginfo->si_addr;
#endif // RVM_FOR_OSX
#if 0
} // for balancing braces { } in the text editor; ignore.
#endif
     
#ifdef RVM_FOR_AIX
void 
cTrapHandler(int signum, int UNUSED zero, sigcontext *context) 
{
    // See "/usr/include/sys/mstsave.h"
#if defined RVM_FOR_32_ADDR
    mstsave *save = &context->sc_jmpbuf.jmp_context; 
#endif
#if defined RVM_FOR_64_ADDR
    context64 *save = &context->sc_jmpbuf.jmp_context; 
#endif
    int firstFault = (faultingAddressLocation == -1);
    uintptr_t faultingAddress = getFaultingAddress(save);
    if (firstFault) { 
        save->iar += 4;  // skip faulting instruction used for auto-detect
        return; 
    }
    uintptr_t ip = save->iar;
    uintptr_t lr = save->lr;
    VM_Address jtoc =  save->gpr[VM_Constants_JTOC_POINTER];
#endif // RVM_FOR_AIX
    
    // fetch address of java exception handler
    //
    VM_Address javaExceptionHandler 
        = *(VM_Address *)((char *)jtoc + DeliverHardwareExceptionOffset);
    
    const int FP  = VM_Constants_FRAME_POINTER;
    const int P0  = VM_Constants_FIRST_VOLATILE_GPR;
    const int P1  = VM_Constants_FIRST_VOLATILE_GPR+1;
    
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
#if defined RVM_FOR_32_ADDR
      uintptr_t faultMask = 0xffff0000;
#elif defined RVM_FOR_64_ADDR
      uintptr_t faultMask = 0xffffffffffff0000;
#endif
      if (!(((faultingAddress & faultMask) == faultMask) || ((faultingAddress & faultMask) == 0))) {
	isNullPtrExn = 0;
      }
    }
      
    int isTrap = signum == SIGTRAP;
    int isRecoverable = isNullPtrExn | isTrap;
    
    unsigned instruction   = *((unsigned *)ip);
    
    if (lib_verbose || !isRecoverable) {
        fprintf(SysTraceFile,"            mem=" FMTrvmPTR "\n", 
                rvmPTR_ARG(faultingAddress));
        fprintf(SysTraceFile,"             fp=" FMTrvmPTR "\n", 
                rvmPTR_ARG(GET_GPR(save,FP)));
       fprintf(SysTraceFile,"             pr=" FMTrvmPTR "\n", 
               rvmPTR_ARG(GET_GPR(save,VM_Constants_PROCESSOR_REGISTER)));
       fprintf(SysTraceFile,"trap/exception: type=%s\n", strsignal(signum));
       fprintf(SysTraceFile,"             ip=" FMTrvmPTR "\n", rvmPTR_ARG(ip));
       fprintf(SysTraceFile,"          instr=0x%08x\n", instruction);
       
       fprintf(SysTraceFile,"    exn_handler=" FMTrvmPTR "\n", rvmPTR_ARG(javaExceptionHandler));
       fprintf(SysTraceFile,"             lr=" FMTrvmPTR "\n",  rvmPTR_ARG(lr));
       
#if (defined RVM_FOR_OSX)
       fprintf(SysTraceFile,"            dar=" FMTrvmPTR "\n", rvmPTR_ARG(context->uc_mcontext->es.dar ));
#else  // ! RVM_FOR_OSX:
       if (rvm_singleVirtualProcessor) 
           fprintf(SysTraceFile,"   pthread_self=" FMTrvmPTR "\n", rvmPTR_ARG(pthread_self()));
#endif // ! RVM_FOR_OSX
       
       if (isRecoverable) {
           fprintf(SysTraceFile,"%s: normal trap\n", Me);
       } else {
           fprintf(SysErrorFile, "%s: internal error trap\n", Me);
           if (--remainingFatalErrors <= 0)
               exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
       }
    }
    
   /* Copy the trapped register set into the current thread's "hardware
      exception registers" save area. */
    VM_Address thread =
      *(VM_Address *)(GET_GPR(save,VM_Constants_PROCESSOR_REGISTER) +
                      VM_Processor_activeThread_offset);
    VM_Address *registers = *(VM_Address **)
        ((char *)thread + VM_Thread_hardwareExceptionRegisters_offset);
    
    VM_Word *gprs
        = *(VM_Word **)((char *)registers + VM_Registers_gprs_offset);
    double   *fprs
        = *(double   **)((char *)registers + VM_Registers_fprs_offset);
    VM_Word *ipLoc
        =  (VM_Word  *)((char *)registers + VM_Registers_ip_offset);
    VM_Word *lrLoc
        =  (VM_Word  *)((char *)registers + VM_Registers_lr_offset);
    unsigned *inuse
        =  (unsigned  *)((char *)registers + VM_Registers_inuse_offset);
    
    if (*inuse) {
        fprintf(SysTraceFile, "%s: internal error: recursive use of hardware exception registers (exiting)\n", Me);
        /* Things went badly wrong, so attempt to generate a useful error
           dump before exiting by returning to VM_Scheduler.dumpStackAndDie,
           passing it the fp of the offending thread.
           
           We could try to continue, but sometimes doing so results in
           cascading failures and it's hard to tell what the real problem
           was. 
        */
        VM_Address dumpStack 
            = *(VM_Address *)((char *)jtoc + DumpStackAndDieOffset);
        SET_GPR(save, P0, GET_GPR(save, FP));
#ifdef RVM_FOR_LINUX
        save->link = save->nip + 4; // +4 so it looks like a return address
        save->nip = dumpStack;
#endif
#if (defined RVM_FOR_OSX)
        save->lr = save->srr0 + 4; // +4 so it looks like a return address
        save->srr0 = dumpStack;
#endif
#ifdef RVM_FOR_AIX
        save->lr = save->iar + 4; // +4 so it looks like a return address
        save->iar = dumpStack;
#endif
        return;
    } 
    
#ifdef RVM_FOR_LINUX
    for (int i = 0; i < NGPRS; ++i)
      gprs[i] = save->gpr[i];
    for (int i = 0; i < NFPRS; ++i) 
      fprs[i] = ((struct linux_sigregs*)save)->fp_regs[i];
    *ipLoc = save->nip + 4; // +4 so it looks like return address
    *lrLoc = save->link;
#endif
    
#ifdef RVM_FOR_OSX
    {
      for (int i = 0; i < NGPRS; ++i)
        gprs[i] = GET_GPR(save, i);
      for (int i=0; i < NFPRS; i++) 
        fprs[i] = fs->fpregs[i];
    }
    *ipLoc = save->srr0 + 4; // +4 so it looks like return address
    *lrLoc = save->lr;
#endif
    
#ifdef RVM_FOR_AIX
    for (int i = 0; i < NGPRS; ++i)
        gprs[i] = save->gpr[i];
    for (int i = 0; i < NFPRS; ++i)
        fprs[i] = save->fpr[i];
    *ipLoc = save->iar+ 4; // +4 so it looks like return address
    *lrLoc = save->lr;
#endif
    *inuse = 1;
    
    // Insert artificial stackframe at site of trap.
    // This frame marks the place where "hardware exception registers" were saved.
    //
    VM_Address   oldFp = GET_GPR(save, FP);
    VM_Address   newFp = oldFp - VM_Constants_STACKFRAME_HEADER_SIZE;
#ifdef RVM_FOR_LINUX
    *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->nip + 4; // +4 so it looks like return address
#endif
    
#ifdef RVM_FOR_OSX
    *(int *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->srr0 + 4; // +4 so it looks like return address
#endif
    
#ifdef RVM_FOR_AIX
    *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->iar + 4; // +4 so it looks like return address
#endif
    *(int *)(newFp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET)
        = HardwareTrapMethodId;
    *(VM_Address *)(newFp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET) 
        = oldFp;
    SET_GPR(save, FP, newFp);
    
    /* Set execution to resume in java exception handler rather than
       re-executing the instruction that caused the trap.
    */
    
    int      trapCode    = VM_Runtime_TRAP_UNKNOWN;
    int      trapInfo    = 0;
    int      haveFrame   = 1;
    
#if (defined RVM_FOR_OSX)
    if ((signum == SIGSEGV || signum == SIGBUS) &&
        siginfo->si_addr == (void *) save->srr0)
    {
        siginfo->si_addr = (void *) context->uc_mcontext->es.dar;
    }
#endif
    switch (signum) {
    case SIGSEGV:
        if (isNullPtrExn) {  // touched top segment of memory, presumably by wrapping negatively off 0
            if (lib_verbose) fprintf(SysTraceFile, "%s: null pointer trap\n", Me);
            trapCode = VM_Runtime_TRAP_NULL_POINTER;
            break;
        }
        if (lib_verbose) fprintf(SysTraceFile, "%s: unknown seg fault\n", Me);
        trapCode = VM_Runtime_TRAP_UNKNOWN;
        break;
        
    case SIGTRAP:
        if ((instruction & VM_Constants_ARRAY_INDEX_MASK) == VM_Constants_ARRAY_INDEX_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: array bounds trap\n", Me);
            trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
            trapInfo = gprs[(instruction & VM_Constants_ARRAY_INDEX_REG_MASK)
                            >> VM_Constants_ARRAY_INDEX_REG_SHIFT];
            break;
        } else if ((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_MASK) == VM_Constants_CONSTANT_ARRAY_INDEX_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: array bounds trap\n", Me);
            trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
            trapInfo = ((int)((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_INFO)<<16))>>16;
            break;
        } else if ((instruction & VM_Constants_DIVIDE_BY_ZERO_MASK) == VM_Constants_DIVIDE_BY_ZERO_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: divide by zero trap\n", Me);
            trapCode = VM_Runtime_TRAP_DIVIDE_BY_ZERO;
            break;
        } else if ((instruction & VM_Constants_MUST_IMPLEMENT_MASK) == VM_Constants_MUST_IMPLEMENT_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: must implement trap\n", Me);
            trapCode = VM_Runtime_TRAP_MUST_IMPLEMENT;
            break;
        } else if ((instruction & VM_Constants_STORE_CHECK_MASK) == VM_Constants_STORE_CHECK_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: objarray store check trap\n", Me);
            trapCode = VM_Runtime_TRAP_STORE_CHECK;
            break;
        } else if ((instruction & VM_Constants_CHECKCAST_MASK ) == VM_Constants_CHECKCAST_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: checkcast trap\n", Me);
            trapCode = VM_Runtime_TRAP_CHECKCAST;
            break;
        } else if ((instruction & VM_Constants_REGENERATE_MASK) == VM_Constants_REGENERATE_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: regenerate trap\n", Me);
            trapCode = VM_Runtime_TRAP_REGENERATE;
            break;
        } else if ((instruction & VM_Constants_NULLCHECK_MASK) == VM_Constants_NULLCHECK_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: null pointer trap\n", Me);
            trapCode = VM_Runtime_TRAP_NULL_POINTER;
            break;
        } else if ((instruction & VM_Constants_JNI_STACK_TRAP_MASK) == VM_Constants_JNI_STACK_TRAP) {
            if (lib_verbose) fprintf(SysTraceFile, "%s: resize stack for JNI call\n", Me);
            trapCode = VM_Runtime_TRAP_JNI_STACK;
            /* We haven't actually bought the stackframe yet, so pretend that
               we are actually trapping directly from the call instruction
               that invoked the native method that caused the stackoverflow
               trap. */
            haveFrame = 0;
            break;
        } else if ((instruction & VM_Constants_WRITE_BUFFER_OVERFLOW_MASK) == VM_Constants_WRITE_BUFFER_OVERFLOW_TRAP) {
            //!!TODO: someday use logic similar to stack guard page to force a gc
            if (lib_verbose) fprintf(SysTraceFile, "%s: write buffer overflow trap\n", Me);
            fprintf(SysErrorFile,"%s: write buffer overflow trap\n", Me);
            exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
        } else if (((instruction & VM_Constants_STACK_OVERFLOW_MASK) 
                    == VM_Constants_STACK_OVERFLOW_TRAP) 
                 || ((instruction & VM_Constants_STACK_OVERFLOW_MASK) 
                    == VM_Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP)) 
        {
            if (lib_verbose) fprintf(SysTraceFile, "%s: stack overflow trap\n", Me);
            trapCode = VM_Runtime_TRAP_STACK_OVERFLOW;
            haveFrame = ((instruction & VM_Constants_STACK_OVERFLOW_MASK) 
                         == VM_Constants_STACK_OVERFLOW_TRAP);
            
            /* Adjust the stack limit downward to give the exception handler
                some space in which to run.
            */
            VM_Address stackLimit = *(VM_Address *)((char *)thread + VM_Thread_stackLimit_offset);
            VM_Address stackStart = *(VM_Address *)((char *)thread + VM_Thread_stack_offset);
            stackLimit -= VM_Constants_STACK_SIZE_GUARD;
            if (stackLimit < stackStart) { 
                /* double fault - stack overflow exception handler used too
                   much stack space */
                fprintf(SysErrorFile, 
                        "%s: stack overflow exception (double fault)\n", Me);
                
                /* Go ahead and get all the stack space we need to generate
                 * the error dump (since we're crashing anyways) 
                 */
                *(VM_Address *)((char *)thread + VM_Thread_stackLimit_offset) = 0;
                
                /* Things are very badly wrong anyways, so attempt to generate
                   a useful error dump before exiting by returning to
                   VM_Scheduler.dumpStackAndDie, passing it the fp of the
                   offending thread. */  
                VM_Address dumpStack 
                    = *(VM_Address *)((char *)jtoc + DumpStackAndDieOffset);
                SET_GPR(save, P0, GET_GPR(save, FP));
#ifdef RVM_FOR_LINUX
                save->link = save->nip + 4; // +4 so it looks like a return address
                save->nip = dumpStack;
#elif (defined RVM_FOR_OSX)
                save->lr = save->srr0 + 4; // +4 so it looks like a return address
                save->srr0 = dumpStack;
#elif defined RVM_FOR_AIX
                save->lr = save->iar + 4; // +4 so it looks like a return address
                save->iar = dumpStack;
#endif
                return;
            }
            *(VM_Address *)((char *)thread + VM_Thread_stackLimit_offset)
                = stackLimit;
            VM_Address *limit_address =
                (VM_Address *)(GET_GPR(save,VM_Constants_PROCESSOR_REGISTER) +
                               VM_Processor_activeThreadStackLimit_offset);
            
            *limit_address = stackLimit;
            
            break;
        }
        if (lib_verbose) fprintf(SysTraceFile, "%s: unknown trap\n", Me);
        trapCode = VM_Runtime_TRAP_UNKNOWN;
        break;
        
    default:
        if (lib_verbose) fprintf(SysTraceFile, "%s: unknown trap\n", Me);
        trapCode = VM_Runtime_TRAP_UNKNOWN;
        break;
    }
    
    /* Pass arguments to the Java exception handler.
     */
    SET_GPR(save, P0, trapCode);
    SET_GPR(save, P1, trapInfo);
    
    if (haveFrame) {
        /* Set the link register to make it look as if the Java exception
           handler had been called directly from the exception site. 
        */
#ifdef RVM_FOR_LINUX
        if (save->nip == 0)
            do { /* A bad branch -- use the contents of the link register as
                   our best guess of the call site. */
            } while(0);
        else
            save->link = save->nip + 4; // +4 so it looks like a return address
#elif defined RVM_FOR_OSX
        if (save->srr0 == 0) { 
            do { /* A bad branch -- use the contents of the link register as
                   our best guess of the call site. */
            } while(0);
        } else {
            save->lr = save->srr0 + 4; // +4 so it looks like a return address
        }
#elif defined RVM_FOR_AIX
        if (save->iar == 0) {
            do { /* A bad branch -- use the contents of the link register as
                   our best guess of the call site. */
            } while(0);
        } else {
            save->lr = save->iar + 4; // +4 so it looks like a return address
        }
#endif
    } else {
        /* We haven't actually bought the stackframe yet, so pretend that
           we are actually trapping directly from the call instruction that 
           invoked the method whose prologue caused the stackoverflow.
        */
#ifdef RVM_FOR_LINUX
        *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->link;
#elif defined RVM_FOR_AIX || defined RVM_FOR_OSX
        *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->lr;
#endif
    }   
    
    /* Resume execution at the Java exception handler. 
     */
#ifdef RVM_FOR_LINUX
    save->nip = javaExceptionHandler;
#elif (defined RVM_FOR_OSX)
    save->srr0 = javaExceptionHandler;
#elif defined RVM_FOR_AIX
    save->iar = javaExceptionHandler;
#endif
    
#if defined RVM_FOR_OSX
    sigreturn((struct sigcontext*)context);
#endif
}


static void *bootThreadCaller(void *);

// A startup configuration option with default values.
// Declared in bootImageRunner.h
const char *bootFilename     = 0;

// The name of the program that will load and run the RVM.
// Declared in bootImageRunner.h
char *Me;

static size_t
pageRoundUp(size_t size) 
{
    size_t pageSize = 4096;
    return (size + pageSize - 1) / pageSize * pageSize;
}

// static unsigned 
// min(unsigned a, unsigned b) 
// {
//     return (a < b) ? a : b;
// }

// static unsigned 
// max(unsigned a, unsigned b) 
// {
//     return (a > b) ? a : b;
// }

int 
createVM(int vmInSeparateThread) 
{
    // arguments processing moved to runboot.c

    // don't buffer trace or error message output
    //
    setbuf(SysErrorFile, NULL);
    setbuf(SysTraceFile, NULL);
   
    // open image file
    //
    FILE *fin = fopen(bootFilename, "r");
    if (!fin) {
        fprintf(SysTraceFile, "%s: can't find boot image \"%s\"\n", Me, bootFilename);
        return 1;
    }

    // measure image size
    //
    if (lib_verbose) 
        fprintf(SysTraceFile, "%s: loading from \"%s\"\n", Me, bootFilename);
    fseek(fin, 0L, SEEK_END);
    size_t actualImageSize = ftell(fin);
    size_t roundedImageSize = pageRoundUp(actualImageSize);
    fseek(fin, 0L, SEEK_SET);
   

    // allocate memory regions in units of system page size
    //
    void    *bootRegion = 0;
   
#if USE_MMAP
/* Note: You probably always want to use MMAP_COPY_ON_WRITE.
   <p>
   On my sample
   machine (IBM Thinkpad T23), using MMAP_COPY_ON_WRITE we can run "Hello
   World" a lot faster.  These results are the averages after a settling-down
   period for the disk cache to fill up.  They are even more dramatic without
   the settling-down period:

		    With MMAP_COPY_ON_WRITE		Old Way
   BaseBaseCopyMS:      0.875 seconds			1.4 seconds
   FastAdaptiveCopyMS:	0.237 seconds			2.0   seconds

   The only disadvantage I can see here is that with copy-on-write, it means
   that if somebody rewrites the boot image file while you're running from that
   boot image, you will lose big.  However, we never did perform any boot
   image locking, so that if somebody had rewritten the boot image while you
   were loading it, you would have had the same problem.  Of course, now the
   window of vulnerability is much wider than two seconds; it's the entire run
   time of the program. */

#define MMAP_COPY_ON_WRITE
#ifdef MMAP_COPY_ON_WRITE
    bootRegion = mmap((void *) bootImageAddress, roundedImageSize,
		      PROT_READ | PROT_WRITE | PROT_EXEC,
		      MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE, 
		      fileno(fin), 0);
    if (bootRegion == (void *) MAP_FAILED) {
        fprintf(SysErrorFile, "%s: mmap failed (errno=%d): %e\n", 
		Me, errno, errno);
        return 1;
    }
    if (bootRegion != (void *) bootImageAddress) {
	fprintf(SysErrorFile, "%s: Attempted to mmap in the address %p; "
			     " got %p instead.  This should never happen.",
		bootRegion, bootImageAddress);
	/* Don't check the return value.  This is insane already.
	 * If we weren't part of a larger runtime system, I'd abort at this
	 * point.  */
	(void) munmap(bootRegion, roundedImageSize);
	return 1;
    }
#else
    bootRegion = mmap((void *) bootImageAddress, roundedImageSize,
                      PROT_READ | PROT_WRITE | PROT_EXEC, 
                      MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED, -1, 0);
    if (bootRegion == (void *)-1) {
        fprintf(SysErrorFile, "%s: mmap failed (errno=%d)\n", Me, errno);
        return 1;
    }
#endif
#else
    int id1 = shmget(IPC_PRIVATE, roundedImageSize, IPC_CREAT | IPC_EXCL | S_IRUSR | S_IWUSR);
    if (id1 == -1) {
        fprintf(SysErrorFile, "%s: shmget failed (errno=%d)\n", Me, errno);
        return 1;
    }
    bootRegion = shmat(id1, (const void *) bootImageAddress, 0);
    if (bootRegion == (void *)-1) {
        fprintf(SysErrorFile, "%s: shmat failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (shmctl(id1, IPC_RMID, 0)) {  // free shmid to avoid persistence
        fprintf(SysErrorFile, "%s: shmctl failed (errno=%d)\n", Me, errno);
        return 1;
    }
#endif

#ifndef MMAP_COPY_ON_WRITE
    // read image into memory segment
    //
    int cnt = fread(bootRegion, 1, actualImageSize, fin);
   
    if (cnt < 0 || (unsigned) cnt != actualImageSize) {
        fprintf(SysErrorFile, "%s: read of boot image failed (errno=%d)\n", Me, errno);
        return 1;
    }

    if (actualImageSize % 4 != 0) {  //Kris V: % 8 for 64_bit platforms
        fprintf(SysErrorFile, "%s: image format error: image size (%d) is not a word multiple\n", Me, actualImageSize);
        return 1;
    }
   
    if (fclose(fin) != 0) {
        fprintf(SysErrorFile, "%s: close of boot image failed (errno=%d)\n", Me, errno);
        return 1;
    }
#endif
  
    // fetch contents of boot record which is at beginning of boot image
    //
    theBootRecord               = (VM_BootRecord *) bootRegion;
    VM_BootRecord& bootRecord   = *theBootRecord;
   

    if ((bootRecord.spRegister % 4) != 0) {
        /* In the RISC6000 asm manual we read that sp had to be quad word
           aligned, but we don't align our stacks...yet. */
        fprintf(SysErrorFile, "%s: image format error: sp (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.spRegister));
        return 1;
    }
   
    if ((bootRecord.ipRegister % 4) != 0) {
        fprintf(SysErrorFile, "%s: image format error: ip (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.ipRegister));
        return 1;
    }
   
    if ((bootRecord.tocRegister % 4) != 0) {
        fprintf(SysErrorFile, "%s: image format error: toc (" FMTrvmPTR ") is not word aligned\n", Me, rvmPTR_ARG(bootRecord.tocRegister));
        return 1;
    }
   
    if (((int *)bootRecord.spRegister)[-1] != (int) 0xdeadbabe) {
        fprintf(SysErrorFile, "%s: image format error: missing stack sanity check marker (0x%08x)\n", Me, (unsigned) ((int *)bootRecord.spRegister)[-1]);
        return 1;
    }

    void * compiled_for =  (void *) bootRecord.bootImageStart;
    void * loaded_at = bootRegion;
    if (compiled_for !=  loaded_at) {
        fprintf(SysErrorFile, "%s: image load error: image was compiled for address " FMTrvmPTR " but loaded at " FMTrvmPTR "\n", 
                Me, rvmPTR_ARG(compiled_for), rvmPTR_ARG(loaded_at));
        return 1;
    }
  
    // remember jtoc location for later use by trap handler - but jtoc might change
    //
    VmToc = bootRecord.tocRegister;

    // set freespace information into boot record
    //
    bootRecord.initialHeapSize  = initialHeapSize;
    bootRecord.maximumHeapSize  = maximumHeapSize;
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
    bootRecord.initialStackSize = initialStackSize;
    bootRecord.stackGrowIncrement = stackGrowIncrement;
    bootRecord.maximumStackSize = maximumStackSize;
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES
    bootRecord.bootImageStart   = (VM_Address) bootRegion;
    bootRecord.bootImageEnd     = (VM_Address) bootRegion + roundedImageSize;
    bootRecord.verboseBoot      = verboseBoot;
    bootRecord.singleVirtualProcessor = rvm_singleVirtualProcessor;
  
    // set host o/s linkage information into boot record
    //
    if (lib_verbose) fprintf(SysTraceFile, "%s: setting linkage\n", Me);
    setLinkage(&bootRecord);

    // remember location of java exception handler
    //
    DeliverHardwareExceptionOffset = bootRecord.deliverHardwareExceptionOffset;
    HardwareTrapMethodId           = bootRecord.hardwareTrapMethodId;

    // remember JTOC offset of VM_Scheduler.dumpStackAndDie, VM_Scheduler.processors[],
    //    VM_Scheduler.threads[], VM_Scheduler.DebugRequested
    //
    DumpStackAndDieOffset = bootRecord.dumpStackAndDieOffset;
    ProcessorsOffset = bootRecord.processorsOffset;
    DebugRequestedOffset = bootRecord.debugRequestedOffset;
   
    if (lib_verbose) {
        fprintf(SysTraceFile, "%s: boot record contents:\n", Me);
        fprintf(SysTraceFile, "   bootImageStart:       " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageStart));
        fprintf(SysTraceFile, "   bootImageEnd:         " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.bootImageEnd));
        assert(sizeof bootRecord.initialHeapSize == 4);
        fprintf(SysTraceFile, "   initialHeapSize:      " FMTrvmPTR32 "\n",   rvmPTR32_ARG(bootRecord.initialHeapSize));
        assert(sizeof bootRecord.maximumHeapSize == 4);
        fprintf(SysTraceFile, "   maximumHeapSize:      " FMTrvmPTR32 "\n",   rvmPTR32_ARG(bootRecord.maximumHeapSize));
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
        assert(sizeof bootRecord.initialStackSize == 4);
        fprintf(SysTraceFile, "   initialStackSize:     " FMTrvmPTR32 "\n",   rvmPTR32_ARG(bootRecord.initialStackSize));
        assert(sizeof bootRecord.stackGrowIncrement == 4);
        fprintf(SysTraceFile, "   stackGrowIncrement:   " FMTrvmPTR32 "\n",   rvmPTR32_ARG(bootRecord.stackGrowIncrement));
        assert(sizeof bootRecord.maximumStackSize == 4);
        fprintf(SysTraceFile, "   maximumStackSize:     " FMTrvmPTR32 "\n",   rvmPTR32_ARG(bootRecord.maximumStackSize));
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES
        fprintf(SysTraceFile, "   tiRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.tiRegister));
        fprintf(SysTraceFile, "   spRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.spRegister));
        fprintf(SysTraceFile, "   ipRegister:           " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.ipRegister));
        fprintf(SysTraceFile, "   tocRegister:          " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.tocRegister));
        fprintf(SysTraceFile, "   sysTOC:               " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.sysTOC));
        fprintf(SysTraceFile, "   sysWriteCharIP:       " FMTrvmPTR   "\n",   rvmPTR_ARG(bootRecord.sysWriteCharIP));
    }

    // install a stack for cSignalHandler() and cTrapHandler() to run on
    //
    char *bottomOfSignalStack = new char[SIGNAL_STACKSIZE];
    char *topOfSignalStack = bottomOfSignalStack + SIGNAL_STACKSIZE;

#ifdef RVM_FOR_LINUX
    // Install hardware trap handler and signal stack
    //   mask all signals during signal handling
    //   exclude the signal used to poke pthreads
    //
    stack_t altstack;
    altstack.ss_sp = bottomOfSignalStack;
    altstack.ss_flags = 0;
    altstack.ss_size = SIGNAL_STACKSIZE;
    if (sigaltstack(&altstack, 0) < 0) {
        fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", Me, errno);
        return 1;
    }
    struct sigaction action;
    action.sa_sigaction = cTrapHandler;
    action.sa_flags     = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
    if (sigfillset(&(action.sa_mask)) ||
        sigdelset(&(action.sa_mask), SIGCONT)) {
        fprintf(SysErrorFile, "%s: sigfillset or sigdelset failed (errno=%d)\n", Me, errno);
        return 1;
    }
#endif
#if (defined RVM_FOR_OSX)
    struct sigaltstack stackInfo;
    if ((stackInfo.ss_sp = (char*)malloc(SIGSTKSZ)) == NULL) {
        fprintf(SysErrorFile, "%s: malloc failed (errno=%d)\n", Me, errno);
        return 1;
    }     /* error return */
    stackInfo.ss_size = SIGSTKSZ;
    stackInfo.ss_flags = 0;
    if (sigaltstack(&stackInfo, 0) < 0) {
        fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", Me, errno);
        return 1;
    }
    struct sigaction action;
    action.sa_handler = (SIGNAL_HANDLER)cTrapHandler;
    action.sa_flags   = SA_ONSTACK | SA_RESTART;
    action.sa_flags   |= SA_SIGINFO;
    if (sigfillset(&(action.sa_mask))) {
        fprintf(SysErrorFile, "%s: sigfillset failed (errno=%d)\n", Me, errno);
        return 1;
    }
    /* exclude the signal used to poke pthreads */
    if (sigdelset(&(action.sa_mask), SIGCONT)) {
        fprintf(SysErrorFile, "%s: sigdelset failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (sigaction(SIGBUS, &action, 0)) {
        // catch null pointer references 
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
#elif defined RVM_FOR_AIX
    struct sigstack stackInfo;
    stackInfo.ss_sp = topOfSignalStack;
    stackInfo.ss_onstack = 0;
    if (sigstack(&stackInfo, 0)) {
        fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", Me, errno);
        return 1;
    }
    // install hardware trap handler
    //
    struct sigaction action;
    action.sa_handler = (SIGNAL_HANDLER) cTrapHandler;
    action.sa_flags   = SA_ONSTACK | SA_RESTART;
    SIGFILLSET(action.sa_mask);
    SIGDELSET(action.sa_mask, SIGCONT);
#endif
    if (sigaction(SIGSEGV, &action, 0) // catch null pointer references
        || sigaction(SIGTRAP, &action, 0) // catch array bounds violations
        || sigaction(SIGILL, &action, 0)) /* catch vm errors (so we can try to
                                          give a traceback) */
    {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
   
    // install software signal handler
    //
#ifdef HAVE_SA_SIGACTION
    action.sa_sigaction = cSignalHandler;
#else
    action.sa_handler = (SIGNAL_HANDLER) cSignalHandler;
#endif
    if (sigaction(SIGALRM, &action, 0) /* catch timer ticks (so we can
                                          timeslice user level threads) */
        || sigaction(SIGHUP, &action, 0) /* catch signal to awaken external
                                            debugger */
        || sigaction(SIGQUIT, &action, 0) /* catch signal to awaken internal
                                             debugger */
        || sigaction(SIGTERM, &action, 0)) /* catch signal to dump stack and
                                              die */
    {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }

    // Ignore "write (on a socket) with nobody to read it" signals so
    // that sysWriteBytes() will get an EPIPE return code instead of trapping.
    //
    action.sa_handler = (SIGNAL_HANDLER) SIG_IGN;
    if (sigaction(SIGPIPE, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
      
    // set up initial stack frame
    //
    VM_Address  jtoc = bootRecord.tocRegister;
    VM_Address *processors = *(VM_Address **)(bootRecord.tocRegister +
                                              bootRecord.processorsOffset);
    VM_Address  pr = processors[VM_Scheduler_PRIMORDIAL_PROCESSOR_ID];
    VM_Address tid = bootRecord.tiRegister;
    VM_Address  ip = bootRecord.ipRegister;
    VM_Address  sp = bootRecord.spRegister;

    // initialize the thread id in the primordial processor object.
    // 
    *(unsigned int *) (pr + VM_Processor_threadId_offset) 
      = VM_Scheduler_PRIMORDIAL_THREAD_INDEX << VM_ThinLockConstants_TL_THREAD_ID_SHIFT;

    // Set up thread stack
    VM_Address  fp = sp - VM_Constants_STACKFRAME_HEADER_SIZE;  // size in bytes
    fp = fp & ~(VM_Constants_STACKFRAME_ALIGNMENT -1);     // align fp
        
    *(VM_Address *)(fp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = ip;
    *(int *)(fp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET) = VM_Constants_INVISIBLE_METHOD_ID;
    *(VM_Address *)(fp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET) = VM_Constants_STACKFRAME_SENTINEL_FP;

    // force any machine code within image that's still in dcache to be
    // written out to main memory so that it will be seen by icache when
    // instructions are fetched back
    //
    sysSyncCache(bootRegion, roundedImageSize);

#ifdef RVM_FOR_AIX
    if (lib_verbose) 
        fprintf(SysTraceFile, "Testing faulting-address location\n");
    *((uintptr_t *) testFaultingAddress) = 42;
    if (lib_verbose) 
        fprintf(SysTraceFile, "Done testing faulting-address location\n");
#endif

    // execute vm startup thread
    //
    if (vmInSeparateThread) {
        /* Try starting the VM in a separate pthread.  We need to synchronize
           before exiting. */
        startupRegs[0] = jtoc;
        startupRegs[1] = pr;
        startupRegs[2] = tid;
        startupRegs[3] = fp;
     
        // clear flag for synchronization
        bootRecord.bootCompleted = 0;

        if (rvm_singleVirtualProcessor) {
            fprintf(stderr, "%s: createVM(vmInSeparateThread = %d): Unsupported operation (no pthreads available)\n", Me, vmInSeparateThread);
            exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
        }
    
        pthread_create(&vm_pthreadid, NULL, bootThreadCaller, NULL);
     
        // wait for the JNIStartUp code to set the completion flag before returning
        while (!bootRecord.bootCompleted) {
#if (_AIX43 || RVM_FOR_LINUX || RVM_FOR_OSX)
            sched_yield();
#else
            pthread_yield();
#endif    
        }

        return 0;
    } else {
        if (lib_verbose) {
            fprintf(SysTraceFile, "%s: calling boot thread: jtoc = " FMTrvmPTR
                    "   pr = " FMTrvmPTR "   tid = %d   fp = " FMTrvmPTR "\n", 
                    Me, rvmPTR_ARG(jtoc), rvmPTR_ARG(pr), tid, rvmPTR_ARG(fp));
        }     
        bootThread(jtoc, pr, tid, fp);
        fprintf(SysErrorFile, "Unexpected return from bootThread\n");
        return 1;
    }

}


/*
 * Wrapper for bootThread for a new pthread to start up the VM
 *
 */
static void *
bootThreadCaller(void UNUSED *dummy) 
{
    uintptr_t jtoc = startupRegs[0];
    uintptr_t pr   = startupRegs[1];
    uintptr_t tid  = startupRegs[2];
    uintptr_t fp   = startupRegs[3];

    fprintf(SysErrorFile, "about to boot vm:\n");

    bootThread(jtoc, pr, tid, fp); 
  
    fprintf(SysErrorFile, "%s: Unexpected return from vm startup thread\n", 
            Me);
    return NULL;

}


// Get address of JTOC.
extern "C" void *
getJTOC() 
{
    return (void*) VmToc;
}

// Get offset of VM_Scheduler.processors in JTOC.
extern "C" VM_Offset 
getProcessorsOffset() 
{
    return ProcessorsOffset;
}


// We do not have this yet on the PowerPC
extern VM_Address
createJavaVM()
{
    fprintf(SysErrorFile, "Cannot CreateJavaVM on PowerPC yet");
    return 1;
}


