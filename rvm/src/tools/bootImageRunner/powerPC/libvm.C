/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception handling.
 * The file "sys.C" contains the o/s support services required by the java class libraries.
 *
 * PowerPC version for AIX and Linux
 *
 * @author  Derek Lieber
 * @modified Perry Cheng
 * @date    03 Feb 1998
 * 17 Oct 2000 Splitted from the original RunBootImage, contains everything except the 
 *             command line parsing in main
 */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <strings.h> 
#include <assert.h>
#include <sys/mman.h>
#include <sys/shm.h>
#include <pthread.h>
#define SIGNAL_STACKSIZE (16 * 1024)    // in bytes

// There are several ways to allocate large areas of virtual memory:
// 1. malloc() is simplest, but doesn't allow a choice of address, thus requireing address relocation of the boot image.  Unsupported.
// 2. mmap() is simple to use but, on AIX, uses 2x amount of physical memory requested (per Al Chang)
// 3. shmat() doesn't have the 2x problem, but is limited to 256M per allocation on AIX 4.1 (unlimited on 4.3)
// [--DL]
//

#ifdef RVM_FOR_LINUX
#define USE_MMAP 1 // choose mmap() for Linux --SB
#include <asm/cache.h>
#include <ucontext.h>
#include <signal.h>
#include <errno.h>
#include <unistd.h>
#define NGPRS 32
// linux on ppc does not save FPRs - is this true still?
#define NFPRS  0
#define GETCONTEXT_IMPLEMENTED 0

// work out the compilation problem
typedef unsigned long ulong_t;

#endif

#ifdef RVM_FOR_AIX
#define USE_MMAP 0 // choose shmat() otherwise --DL
#include <sys/cache.h>
#include <sys/context.h>
extern "C" char *sys_siglist[];
#endif


extern "C" int createJVM(int);
extern "C" void processTimerTick(void);
extern "C" void sysSyncCache(caddr_t, int size);

extern "C" int getArrayLength(void* ptr);


// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include <InterfaceDeclarations.h>
VM_BootRecord *theBootRecord;
extern "C" void setLinkage(VM_BootRecord*);
#define VM_NULL 0
#define MAXHEAPS 20  // update to auto-generate from (VM_BootRecord.heapRange.length / 2)

// Local Declarations
//
FILE *SysErrorFile = stderr;   // Sink for messages relating to serious errors detected by C runtime.
FILE *SysTraceFile = stderr;   // Sink for trace messages produced by VM.sysWrite().
int SysTraceFd = 2;
int lib_verbose = 0;           // Emit trace information?
int remainingFatalErrors = 3;  // Terminate execution of vm if 3 or more fatal errors occur,
                               //   preventing infinitely (recursive) fatal errors.

char **	JavaArgs;              // Command line arguments to be passed to boot image.
int	JavaArgc;
static ulong_t startupRegs[4];        // used to pass jtoc, pr, tid, fp to bootThread.s
ulong_t VmToc;                 // Location of VM's JTOC
int     HardwareTrapMethodId;  // Method id for inserting stackframes at sites of hardware traps.
int DeliverHardwareExceptionOffset;  // TOC offset of VM_Runtime.deliverHardwareException
int DumpStackAndDieOffset;           // TOC offset of VM_Scheduler.dumpStackAndDie
int ProcessorsOffset;                // TOC offset of VM_Scheduler.processors[]
int ThreadsOffset;                   // TOC offset of VM_Scheduler.threads[]
int DebugRequestedOffset;            // TOC offset of VM_Scheduler.debugRequested
int AttachThreadRequestedOffset;     // TOC offset of VM_Scheduler.attachThreadRequested

typedef void (*SIGNAL_HANDLER)(int);  // Standard unix signal handler.
pthread_t vm_pthreadid;               // pthread id of the main RVM pthread

static int inRVMAddressSpace(unsigned int addr) {
   VM_Address *heapRanges = theBootRecord->heapRanges;
   for (int which = 0; which < MAXHEAPS; which++) {
     VM_Address start = heapRanges[2 * which];
     VM_Address end = heapRanges[2 * which + 1];
     if (start <= addr  && addr < end) {
       return true;
     }
   }
   return false;
}

// Was unix signal raised while vm code was running?
// Taken:    contents of "iar" and "jtoc" registers at time of signal
// Returned: 1 --> vm code was running
//           0 --> non-vm (eg. C library) code was running
//
static int isVmSignal(unsigned iar, unsigned jtoc) {
  return inRVMAddressSpace(iar) && inRVMAddressSpace(jtoc);
}

#ifdef RVM_FOR_LINUX
// The following code is factored out while getcontext() remains
// unimplemented for ppc linux.
sigcontext* getLinuxSavedContext(int signum, void* arg3) {
#if GETCONTEXT_IMPLEMENTED
   ucontext_t uc;
   getcontext(&uc);
   return &(uc.uc_mcontext);
#else
   // Unfortunately, getcontext() (/usr/include/ucontext.h) is not
   // implemented in Linux at time of writing (<= 2.4.3), but some
   // analysis of the signal stack frames reveals that the info we
   // want is on the signal stack.  We do a simple check (get the
   // signum value from the structure) to try to make sure we've got
   // it right.
#warning Linux does not support getcontext()
#define SIGCONTEXT_MAGIC 5
   sigcontext* context = (sigcontext *) (((void **) arg3) + SIGCONTEXT_MAGIC);
   if (context->signal != signum) {
     // We're in trouble.  Try to produce some useful debugging info
     fprintf(stderr, "%s:%d Failed to grab context from signal stack frame!\n", __FILE__, __LINE__);
     for (int i = -16; i < 32; i++) {
       fprintf(stderr, "%12p %p arg3[%d]\n", (void *) ((void**) arg3)[i], ((void**) arg3) + i, i);
     }
     fprintf(stderr, "trap: %d link: %p nip: %p\n", context->regs->trap, context->regs->link, context->regs->nip);
     exit(-1);
   }

   return context;
#endif
   }
#endif


extern "C" void processTimerTick() {
  
   // if gc in progress, return
   if ((*theBootRecord).lockoutProcessor == 0x0CCCCCCC) return;     
      
   // Turn on thread-switch flag in each virtual processor.
   // Note that "jtoc" is not necessairly valid, because we might have interrupted
   // C-library code, so we use boot image jtoc address (== VmToc) instead.
   // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
   // For now, we assume table is fixed in boot image and never moves.
   //
   unsigned *processors = *(unsigned **)((char *)VmToc + ProcessorsOffset);
   unsigned  cnt        =  getArrayLength(processors);
   cnt = cnt - 1;       // line added here - ndp is now the last processor = and cnt includes it
   int *epochLoc = (int *) ((char *) VmToc + VM_Processor_epoch_offset);
   (*epochLoc)++;

   int	   i;
   int       sendit = 0;
   int       MISSES = -2;			// tuning parameter
   for (i = VM_Scheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt ; ++i) {
     int *tsrLoc = (int *)((char *)processors[i] + VM_Processor_threadSwitchRequested_offset);
     if (*tsrLoc <= MISSES) sendit++;
     (*tsrLoc)--;
   }
   if (sendit != 0) { // some processor "stuck in native"
     if (processors[i] != VM_NULL) {  // Have a NativeDaemon Processor (the last one)
 #if (defined RVM_FOR_LINUX) && (!defined __linuxsmp__)
       // we're hosed because we don't support pthreads
       fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
       exit(-1);
#else
       int pthread_id = *(int *)((char *)processors[i] + VM_Processor_pthread_id_offset);
       pthread_t thread = (pthread_t) pthread_id;
       pthread_kill(thread, SIGCONT);
#endif
     }
   }
}

// Handle software signals.
// Note: this code runs in a small "signal stack" allocated by "sigstack()" (see later).
// Therefore care should be taken to keep the stack depth small. If mysterious
// crashes seem to occur, consider the possibility that the signal stack might
// need to be made larger. [--DL].
//
#ifdef RVM_FOR_LINUX
void cSignalHandler(int signum, siginfo_t* siginfo, void* arg3) {
   sigcontext* context = getLinuxSavedContext(signum, arg3);
   pt_regs *save = context->regs;
   unsigned iar  =  save->nip;
#endif
#ifdef RVM_FOR_AIX
void cSignalHandler(int signum, int zero, sigcontext *context) {
#ifdef RVM_FOR_32_ADDR
   mstsave *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/mstsave.h"
#endif
#ifdef RVM_FOR_64_ADDR
    __context64 *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/context.h"
#endif
   unsigned iar  =  save->iar;
#endif
   unsigned jtoc =  save->gpr[VM_Constants_JTOC_POINTER];

   if (signum == SIGALRM) {     
     processTimerTick();
     return;
   }

   if (signum == SIGHUP) { // asynchronous signal used to awaken external debugger
     // fprintf(SysTraceFile, "vm: signal SIGHUP at ip=0x%08x ignored\n", iar);
     return;
   }
   
   if (signum == SIGQUIT) { // asynchronous signal used to awaken internal debugger
      
      // Turn on debug-request flag.
      // Note that "jtoc" is not necessairly valid, because we might have interrupted
      // C-library code, so we use boot image jtoc address (== VmToc) instead.
      // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
      // For now, we assume table is fixed in boot image and never moves.
      //
      unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
      if (*flag) {
	fprintf(SysTraceFile, "vm: debug request already in progress, please wait\n");
      }
      else {
	fprintf(SysTraceFile, "vm: debug requested, waiting for a thread switch\n");
	*flag = 1;
      }
      return;
   }

   if (signum == SIGTERM) {

     fprintf(SysTraceFile, "vm: kill requested: (exiting)\n");
     // Process was killed from command line with a DumpStack signal
     // Dump stack by returning to VM_Scheduler.dumpStackAndDie passing
     // it the fp of the current thread.
     // Note that "jtoc" is not necessairly valid, because we might have interrupted
     // C-library code, so we use boot image jtoc address (== VmToc) instead.
     // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
     // For now, we assume table is fixed in boot image and never moves.
     //
     VM_Address dumpStack = *(VM_Address *)((char *)VmToc + DumpStackAndDieOffset);
     save->gpr[VM_Constants_FIRST_VOLATILE_GPR] =
       save->gpr[VM_Constants_FRAME_POINTER];
#ifdef RVM_FOR_LINUX
     save->link = save->nip + 4; // +4 so it looks like a return address
     save->nip = dumpStack;
#endif
#ifdef RVM_FOR_AIX
     save->lr = save->iar + 4; // +4 so it looks like a return address
     save->iar = dumpStack;
#endif
     return;
   }
}
 

// Handle hardware traps.
// Note: this code runs in a small "signal stack" allocated by "sigstack()" (see later).
// Therefore care should be taken to keep the stack depth small. If mysterious
// crashes seem to occur, consider the possibility that the signal stack might
// need to be made larger. [--DL].
//
#ifdef RVM_FOR_LINUX
 void cTrapHandler(int signum, siginfo_t *siginfo, void* arg3) {
   sigcontext* context = getLinuxSavedContext(signum, arg3);
   ulong_t faultingAddress = (ulong_t)siginfo->si_addr;
   pt_regs *save = context->regs;
   ulong_t ip = save->nip;
   ulong_t lr = save->link;
#endif

#ifdef RVM_FOR_AIX
  ulong_t testFaultingAddress = 0xdead1234;
  int faultingAddressLocation = -1; // uninitialized
  ulong_t getFaultingAddress(mstsave *save) {
    if (faultingAddressLocation == -1) {
      if (save->o_vaddr == testFaultingAddress) faultingAddressLocation = 0;
      else if (save->except[0] == testFaultingAddress) faultingAddressLocation = 1;
      else {
	fprintf(SysTraceFile, "Could not figure out where faulting address is stored - exiting\n");
	exit(-1);
      }
    }
    if (faultingAddressLocation == 0)
      return save->o_vaddr;
    else if (faultingAddressLocation == 1)
      return save->except[0];
    exit(-1);
  }

void cTrapHandler(int signum, int zero, sigcontext *context) {
   // See "/usr/include/sys/mstsave.h"
   mstsave *save = &context->sc_jmpbuf.jmp_context; 
   int firstFault = (faultingAddressLocation == -1);
   ulong_t faultingAddress = getFaultingAddress(save);
   if (firstFault) { 
     save->iar += 4;  // skip faulting instruction used for auto-detect
     return; 
   }
   ulong_t ip = save->iar;
   ulong_t lr = save->lr;
#endif

   // fetch address of java exception handler
   //
   unsigned jtoc =  save->gpr[VM_Constants_JTOC_POINTER];
   VM_Address javaExceptionHandler = *(VM_Address *)((char *)jtoc + DeliverHardwareExceptionOffset);
   
   const int TID = VM_Constants_THREAD_ID_REGISTER;
   const int FP  = VM_Constants_FRAME_POINTER;
   const int P0  = VM_Constants_FIRST_VOLATILE_GPR;
   const int P1  = VM_Constants_FIRST_VOLATILE_GPR+1;

   // We are prepared to handle these kinds of "recoverable" traps.
   // (Anything else indicates some sort of unrecoverable vm error)
   //
   //  1. SIGSEGV - a null object dereference of the form "obj[-fieldOffset]"
   //               that wraps around to segment 0xf0000000.
   //
   //  2. SIGTRAP - an array bounds trap
   //               or integer divide by zero trap
   //               or stack overflow trap
   //
   int isNullPtrExn = (signum == SIGSEGV) && (isVmSignal(ip, jtoc)) &&
#ifdef RVM_FOR_32_ADDR
                      ((faultingAddress & 0xffff0000) == 0xffff0000);
#endif
#ifdef RVM_FOR_64_ADDR
                      ((faultingAddress & 0xffffffffffff0000) == 0xffffffffffff0000);
#endif
   int isTrap = signum == SIGTRAP;
   int isRecoverable = isNullPtrExn | isTrap;

   if (lib_verbose || !isRecoverable) {
     fprintf(SysTraceFile,"exception: type=%s\n", signum < NSIG ? sys_siglist[signum] : "?");
     fprintf(SysTraceFile,"             ip=0x%08lx\n", ip);
     fprintf(SysTraceFile,"            mem=0x%08lx\n", faultingAddress);
#ifdef RVM_FOR_AIX
     fprintf(SysTraceFile,"             pr=0x%08lx\n", save->gpr[VM_Constants_PROCESSOR_REGISTER]);
#endif
     fprintf(SysTraceFile,"             fp=0x%08lx\n", save->gpr[FP]);
     fprintf(SysTraceFile,"            tid=0x%08lx\n", save->gpr[TID]);
     fprintf(SysTraceFile,"             pr=0x%08lx\n", save->gpr[VM_Constants_PROCESSOR_REGISTER]);
#if RVM_FOR_LINUX
     fprintf(SysTraceFile,"             lr=0x%08lx\n", save->link);
#endif
#if RVM_FOR_AIX
     fprintf(SysTraceFile,"             lr=0x%08lx\n", save->lr);
#endif
     fprintf(SysTraceFile,"    exn_handler=0x%08lx\n", javaExceptionHandler);
     fprintf(SysTraceFile,"   pthread_self=0x%08lx\n", pthread_self());
     fprintf(SysTraceFile,"          instr=0x%08lx\n", *(unsigned *)ip);
     if (isRecoverable)
       fprintf(SysTraceFile,"vm: normal trap\n");
     else {
       fprintf(SysErrorFile, "vm: internal error trap\n");
       if (--remainingFatalErrors <= 0)
	 exit(-1); 
     }
   }

   // Copy trapped register set into current thread's "hardware exception registers" save area.
   //
   unsigned instruction   = *((unsigned *)ip);
   int       threadOffset = save->gpr[TID] >> (VM_ThinLockConstants_TL_THREAD_ID_SHIFT - 2);
   unsigned  threads      = *(unsigned  *)((char *)jtoc + ThreadsOffset);
   unsigned *thread       = *(unsigned **)((char *)threads + threadOffset);
   unsigned *registers    = *(unsigned **)((char *)thread + VM_Thread_hardwareExceptionRegisters_offset);
   
   unsigned *gprs         = *(unsigned **)((char *)registers + VM_Registers_gprs_offset);
   double   *fprs         = *(double   **)((char *)registers + VM_Registers_fprs_offset);
   unsigned *ipLoc        =  (unsigned  *)((char *)registers + VM_Registers_ip_offset);
   unsigned *lrLoc        =  (unsigned  *)((char *)registers + VM_Registers_lr_offset);
   unsigned *inuse        =  (unsigned  *)((char *)registers + VM_Registers_inuse_offset);
   
   if (*inuse) {
      fprintf(SysTraceFile, "vm: internal error: recursive use of hardware exception registers (exiting)\n");
      // Things went badly wrong, so attempt to generate a useful error dump 
      // before exiting by returning to VM_Scheduler.dumpStackAndDie passing it the fp 
      // of the offending thread.
      // We could try to continue, but sometimes doing so results in cascading failures
      // and it's hard to tell what the real problem was.
      VM_Address dumpStack = *(VM_Address *)((char *)jtoc + DumpStackAndDieOffset);
      save->gpr[P0] = save->gpr[FP];
#ifdef RVM_FOR_LINUX
      save->link = save->nip + 4; // +4 so it looks like a return address
      save->nip = dumpStack;
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
   for (int i = 0; i < NFPRS; ++i)  // linux on PPC does not save FPRs ?
     fprs[i] = -1.0;   
   *ipLoc = save->nip + 4; // +4 so it looks like return address
   *lrLoc = save->link;
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
   int   oldFp = save->gpr[FP];
   int   newFp = oldFp - VM_Constants_STACKFRAME_HEADER_SIZE;
#ifdef RVM_FOR_LINUX
   *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->nip + 4; // +4 so it looks like return address
#endif
#ifdef RVM_FOR_AIX
   *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->iar + 4; // +4 so it looks like return address
#endif
   *(VM_Address *)(newFp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET)        = HardwareTrapMethodId;
   *(VM_Address *)(newFp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET)    = oldFp;
   save->gpr[FP] = newFp;

   // Set execution to resume in java exception handler rather than re-executing instruction
   // that caused the trap.
   //

   int      trapCode    = VM_Runtime_TRAP_UNKNOWN;
   int      trapInfo    = 0;
   int      haveFrame   = 1;

   switch (signum) {
      case SIGSEGV:
	if (isNullPtrExn) {  // touched top segment of memory, presumably by wrapping negatively off 0
	  if (lib_verbose) fprintf(SysTraceFile, "vm: null pointer trap\n");
	  trapCode = VM_Runtime_TRAP_NULL_POINTER;
	  break;
	}
	if (lib_verbose) fprintf(SysTraceFile, "vm: unknown seg fault\n");
	trapCode = VM_Runtime_TRAP_UNKNOWN;
	break;

      case SIGTRAP:
	if ((instruction & VM_Constants_ARRAY_INDEX_MASK) == VM_Constants_ARRAY_INDEX_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: array bounds trap\n");
	  trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
	  trapInfo = gprs[(instruction & VM_Constants_ARRAY_INDEX_REG_MASK)
			  >> VM_Constants_ARRAY_INDEX_REG_SHIFT];
	  break;
	} else if ((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_MASK) == VM_Constants_CONSTANT_ARRAY_INDEX_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: array bounds trap\n");
	  trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
	  trapInfo = ((int)((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_INFO)<<16))>>16;
	  break;
	} else if ((instruction & VM_Constants_DIVIDE_BY_ZERO_MASK) == VM_Constants_DIVIDE_BY_ZERO_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: divide by zero trap\n");
	  trapCode = VM_Runtime_TRAP_DIVIDE_BY_ZERO;
	  break;
	} else if ((instruction & VM_Constants_MUST_IMPLEMENT_MASK) == VM_Constants_MUST_IMPLEMENT_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: must implement trap\n");
	  trapCode = VM_Runtime_TRAP_MUST_IMPLEMENT;
	  break;
	} else if ((instruction & VM_Constants_STORE_CHECK_MASK) == VM_Constants_STORE_CHECK_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: objarray store check trap\n");
	  trapCode = VM_Runtime_TRAP_STORE_CHECK;
	  break;
	} else if ((instruction & VM_Constants_CHECKCAST_MASK ) == VM_Constants_CHECKCAST_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: checkcast trap\n");
	  trapCode = VM_Runtime_TRAP_CHECKCAST;
	  break;
	} else if ((instruction & VM_Constants_REGENERATE_MASK) == VM_Constants_REGENERATE_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: regenerate trap\n");
	  trapCode = VM_Runtime_TRAP_REGENERATE;
	  break;
	} else if ((instruction & VM_Constants_NULLCHECK_MASK) == VM_Constants_NULLCHECK_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: null pointer trap\n");
	  trapCode = VM_Runtime_TRAP_NULL_POINTER;
	  break;
	} else if ((instruction & VM_Constants_JNI_STACK_TRAP_MASK) == VM_Constants_JNI_STACK_TRAP) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: resize stack for JNI call\n");
	  trapCode = VM_Runtime_TRAP_JNI_STACK;
	  // We haven't actually bought the stackframe yet, so pretend that
	  // we are actually trapping directly from the call instruction that invoked the 
	  // native method that caused the stackoverflow trap.
	  haveFrame = 0;
	  break;
	} else if ((instruction & VM_Constants_WRITE_BUFFER_OVERFLOW_MASK) == VM_Constants_WRITE_BUFFER_OVERFLOW_TRAP) {
	  //!!TODO: someday use logic similar to stack guard page to force a gc
	  if (lib_verbose) fprintf(SysTraceFile, "vm: write buffer overflow trap\n");
	  fprintf(SysErrorFile,"vm: write buffer overflow trap\n");
	  exit(-1);
	} else if (((instruction & VM_Constants_STACK_OVERFLOW_MASK) == VM_Constants_STACK_OVERFLOW_TRAP) ||
		   ((instruction & VM_Constants_STACK_OVERFLOW_MASK) == VM_Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP)) {
	  if (lib_verbose) fprintf(SysTraceFile, "vm: stack overflow trap\n");
	  trapCode = VM_Runtime_TRAP_STACK_OVERFLOW;
	  haveFrame = ((instruction & VM_Constants_STACK_OVERFLOW_MASK) == VM_Constants_STACK_OVERFLOW_TRAP);

	  // adjust stack limit downward to give exception handler some space in which to run
	  //
	  unsigned stackLimit = *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset);
	  unsigned stackStart = *(unsigned *)((char *)thread + VM_Thread_stack_offset);
	  stackLimit -= VM_Constants_STACK_SIZE_GUARD;
	  if (stackLimit < stackStart) { 
	    // double fault - stack overflow exception handler used too much stack space
	    fprintf(SysErrorFile, "vm: stack overflow exception (double fault)\n");
	      
	    // Go ahead and get all the stack space we need to generate the error dump (since we're crashing anyways)
	    *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset) = 0;
	      
	    // Things are very badly wrong anyways, so attempt to generate 
	    // a useful error dump before exiting by returning to VM_Scheduler.dumpStackAndDie
	    // passing it the fp of the offending thread.
	    VM_Address dumpStack = *(VM_Address *)((char *)jtoc + DumpStackAndDieOffset);
	    save->gpr[P0] = save->gpr[FP];
#ifdef RVM_FOR_LINUX
	    save->link = save->nip + 4; // +4 so it looks like a return address
	    save->nip = dumpStack;
#endif
#ifdef RVM_FOR_AIX
	    save->lr = save->iar + 4; // +4 so it looks like a return address
	    save->iar = dumpStack;
#endif
	    return;
	  }
	  *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset) = stackLimit;
	  *(unsigned *)(save->gpr[VM_Constants_PROCESSOR_REGISTER] + VM_Processor_activeThreadStackLimit_offset) = stackLimit;
	  
	  break;
	}
	if (lib_verbose) fprintf(SysTraceFile, "vm: unknown trap\n");
	trapCode = VM_Runtime_TRAP_UNKNOWN;
	break;

      default:
	if (lib_verbose) fprintf(SysTraceFile, "vm: unknown trap\n");
	trapCode = VM_Runtime_TRAP_UNKNOWN;
	break;
      }
   
   // pass arguments to java exception handler
   //
   save->gpr[P0] = trapCode;
   save->gpr[P1] = trapInfo;

   if (haveFrame) {
     // set link register to make it look like java exception handler
     // was called from exception site
#ifdef RVM_FOR_LINUX
     if (save->nip == 0)
       ; // bad branch - use contents of link register as best guess of call site
     else
       save->link = save->nip + 4; // +4 so it looks like a return address
#endif
#ifdef RVM_FOR_AIX
     if (save->iar == 0)
       ; // bad branch - use contents of link register as best guess of call site
     else
       save->lr = save->iar + 4; // +4 so it looks like a return address
#endif
   } else {
     // We haven't actually bought the stackframe yet, so pretend that
     // we are actually trapping directly from the call instruction that 
     // invoked the method whose prologue caused the stackoverflow.
#if RVM_FOR_LINUX
     *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->link;
#endif
#if RVM_FOR_AIX
     *(VM_Address *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->lr;
#endif
   }   

   // resume execution at java exception handler
   //
#ifdef RVM_FOR_LINUX
   save->nip = javaExceptionHandler;
#endif
#ifdef RVM_FOR_AIX
   save->iar = javaExceptionHandler;
#endif

}



extern "C" void bootThread(int jtoc, int pr, int ti, int fp); // assembler routine
void *bootThreadCaller(void *);
#include <pthread.h>

// startup configuration option with default values
char *bootFilename     = 0;
extern unsigned initialHeapSize;
extern unsigned maximumHeapSize;

unsigned traceClassLoading = 0;
 
// name of program that will load and run RVM
char *me;

static int pageRoundUp(int size) {
    int pageSize = 4096;
    return (size + pageSize - 1) / pageSize * pageSize;
}

static unsigned min(unsigned a, unsigned b) {
    return (a < b) ? a : b;
}

static unsigned max(unsigned a, unsigned b) {
    return (a > b) ? a : b;
}

int createJVM(int vmInSeparateThread) {

   // arguments processing moved to runboot.c

   // don't buffer trace or error message output
   //
#ifdef RVM_FOR_LINUX
   setvbuf(SysErrorFile, 0, _IONBF, 0);
   setvbuf(SysTraceFile, 0, _IONBF, 0);
#endif
#ifdef RVM_FOR_AIX
   setbuf(SysErrorFile, 0);
   setbuf(SysTraceFile, 0);
#endif
   
   // open image file
   //
   FILE *fin = fopen(bootFilename, "r");
   if (!fin) {
       fprintf(SysTraceFile, "%s: can't find boot image \"%s\"\n", me, bootFilename);
       return 1;
   }

   // measure image size
   //
   if (lib_verbose) fprintf(SysTraceFile, "%s: loading from \"%s\"\n", me, bootFilename);
   fseek(fin, 0L, SEEK_END);
   unsigned actualImageSize = ftell(fin);
   unsigned roundedImageSize = pageRoundUp(actualImageSize);
   fseek(fin, 0L, SEEK_SET);
   

   // allocate memory regions in units of system page size
   //
   void    *bootRegion = 0;
   
#if USE_MMAP
   bootRegion = mmap((void *) bootImageAddress, roundedImageSize,
		     PROT_READ | PROT_WRITE | PROT_EXEC, 
		     MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED, -1, 0);
   if (bootRegion == (void *)-1) {
       fprintf(SysErrorFile, "%s: mmap failed (errno=%d)\n", me, errno);
       return 1;
   }
#else
   int id1 = shmget(IPC_PRIVATE, roundedImageSize, IPC_CREAT | IPC_EXCL | S_IRUSR | S_IWUSR);
   if (id1 == -1) {
       fprintf(SysErrorFile, "%s: shmget failed (errno=%d)\n", me, errno);
       return 1;
   }
   bootRegion = shmat(id1, (const void *) bootImageAddress, 0);
   if (bootRegion == (void *)-1) {
       fprintf(SysErrorFile, "%s: shmat failed (errno=%d)\n", me, errno);
       return 1;
   }
   if (shmctl(id1, IPC_RMID, 0)) {  // free shmid to avoid persistence
       fprintf(SysErrorFile, "%s: shmctl failed (errno=%d)\n", me, errno);
       return 1;
   }
#endif

   // read image into memory segment
   //
   int cnt = fread(bootRegion, 1, actualImageSize, fin);
   
   if (actualImageSize % 4 != 0) {
      fprintf(SysErrorFile, "%s: image format error: image size (%d) is not a word multiple\n", me, actualImageSize);
      return 1;
   }

   if (cnt != actualImageSize) {
      fprintf(SysErrorFile, "%s: read of boot image failed (errno=%d)\n", me, errno);
      return 1;
   }
   
   if (fclose(fin) != 0) {
      fprintf(SysErrorFile, "%s: close of boot image failed (errno=%d)\n", me, errno);
      return 1;
   }
  
   // fetch contents of boot record which is at beginning of boot image
   //
   theBootRecord		= (VM_BootRecord *) bootRegion;
   VM_BootRecord& bootRecord	= *theBootRecord;
   

   if ((bootRecord.spRegister % 4) != 0)
      {
      // In the RISC6000 asm manual we read that sp had to be quad word aligned, but we don't align our stacks...yet.
      fprintf(SysErrorFile, "%s: image format error: sp (0x%08x) is not word aligned\n", me, bootRecord.spRegister);
      return 1;
      }
   
   if ((bootRecord.ipRegister % 4) != 0) {
     fprintf(SysErrorFile, "%s: image format error: ip (0x%08x) is not word aligned\n", me, bootRecord.ipRegister);
     return 1;
   }
   
   if ((bootRecord.tocRegister % 4) != 0) {
     fprintf(SysErrorFile, "%s: image format error: toc (0x%08x) is not word aligned\n", me, bootRecord.tocRegister);
     return 1;
   }
   
   if (((int *)bootRecord.spRegister)[-1] != 0xdeadbabe) {
     fprintf(SysErrorFile, "%s: image format error: missing stack sanity check marker (0x%08x)\n", me, ((int *)bootRecord.spRegister)[-1]);
     return 1;
   }

   if (bootRecord.bootImageStart != (int)bootRegion) {
     fprintf(SysErrorFile, "%s: image load error: image was compiled for address (0x%08x) but loaded at (0x%08x)\n", me, bootRecord.bootImageStart, bootRegion);
     return 1;
   }
  
   // remember jtoc location for later use by trap handler - but jtoc might change
   //
   VmToc = bootRecord.tocRegister;

   // set freespace information into boot record
   //
   bootRecord.initialHeapSize  = initialHeapSize;
   bootRecord.maximumHeapSize  = maximumHeapSize;
   bootRecord.bootImageStart   = (int) bootRegion;
   bootRecord.bootImageEnd     = (int) bootRegion + roundedImageSize;
  
   bootRecord.traceClassLoading = traceClassLoading;

   // set host o/s linkage information into boot record
   //
   if (lib_verbose) fprintf(SysTraceFile, "%s: setting linkage\n", me);
   setLinkage(&bootRecord);

   // remember location of java exception handler
   //
   DeliverHardwareExceptionOffset = bootRecord.deliverHardwareExceptionOffset;
   HardwareTrapMethodId           = bootRecord.hardwareTrapMethodId;

   // remember JTOC offset of VM_Scheduler.dumpStackAndDie, VM_Scheduler.processors[],
   //    VM_Scheduler.threads[], VM_Scheduler.DebugRequested, VM_Scheduler.attachThreadRequested
   //
   DumpStackAndDieOffset = bootRecord.dumpStackAndDieOffset;
   ProcessorsOffset = bootRecord.processorsOffset;
   ThreadsOffset = bootRecord.threadsOffset;
   DebugRequestedOffset = bootRecord.debugRequestedOffset;
   AttachThreadRequestedOffset = bootRecord.attachThreadRequestedOffset;
   
   if (lib_verbose) {
      fprintf(SysTraceFile, "%s: boot record contents:\n", me);
      fprintf(SysTraceFile, "   bootImageStart:       0x%08lx\n",   bootRecord.bootImageStart);
      fprintf(SysTraceFile, "   bootImageEnd:         0x%08lx\n",   bootRecord.bootImageEnd);
      fprintf(SysTraceFile, "   initialHeapSize:      0x%08lx\n",   bootRecord.initialHeapSize);
      fprintf(SysTraceFile, "   maximumHeapSize:      0x%08lx\n",   bootRecord.maximumHeapSize);
      fprintf(SysTraceFile, "   tiRegister:           0x%08lx\n",   bootRecord.tiRegister);
      fprintf(SysTraceFile, "   spRegister:           0x%08lx\n",   bootRecord.spRegister);
      fprintf(SysTraceFile, "   ipRegister:           0x%08lx\n",   bootRecord.ipRegister);
      fprintf(SysTraceFile, "   tocRegister:          0x%08lx\n",   bootRecord.tocRegister);
      fprintf(SysTraceFile, "   sysTOC:               0x%08lx\n",   bootRecord.sysTOC);
      fprintf(SysTraceFile, "   sysWriteCharIP:       0x%08lx\n",   bootRecord.sysWriteCharIP);
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
     fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", me, errno);
     return 1;
   }
   struct sigaction action;
   action.sa_sigaction = cTrapHandler;
   action.sa_flags     = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
   if (sigfillset(&(action.sa_mask)) ||
       sigdelset(&(action.sa_mask), SIGCONT)) {
     fprintf(SysErrorFile, "%s: sigfillset or sigdelset failed (errno=%d)\n", me, errno);
     return 1;
   }
#endif
#ifdef RVM_FOR_AIX
   struct sigstack stackInfo;
   stackInfo.ss_sp = topOfSignalStack;
   stackInfo.ss_onstack = 0;
   if (sigstack(&stackInfo, 0)) {
     fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", me, errno);
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
   if (sigaction(SIGSEGV, &action, 0) || // catch null pointer references
       sigaction(SIGTRAP, &action, 0) || // catch array bounds violations
       sigaction(SIGILL, &action, 0)) {  // catch vm errors (so we can try to give a traceback)
     fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
     return 1;
   }

   // install software signal handler
   //
#ifdef RVM_FOR_LINUX
   action.sa_sigaction = cSignalHandler;
#endif
#ifdef RVM_FOR_AIX
   action.sa_handler = (SIGNAL_HANDLER) cSignalHandler;
#endif
   if (sigaction(SIGALRM, &action, 0) || // catch timer ticks (so we can timeslice user level threads)
       sigaction(SIGHUP, &action, 0)  || // catch signal to awaken external debugger
       sigaction(SIGQUIT, &action, 0) || // catch signal to awaken internal debugger
       sigaction(SIGTERM, &action, 0)) { // catch signal to dump stack and die
     fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
     return 1;
   }

   // Ignore "write (on a socket) with nobody to read it" signals so
   // that sysWriteBytes() will get an EPIPE return code instead of trapping.
   //
   action.sa_handler = (SIGNAL_HANDLER)SIG_IGN;
   if (sigaction(SIGPIPE, &action, 0)) {
     fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
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

   VM_Address  fp = sp - VM_Constants_STACKFRAME_HEADER_SIZE;  // size in bytes
               fp = fp & ~(VM_Constants_STACKFRAME_ALIGNMENT -1);     // align fp
	
   *(VM_Address *)(fp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = ip;
   *(VM_Address *)(fp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET) = VM_Constants_INVISIBLE_METHOD_ID;
   *(VM_Address *)(fp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET) = VM_Constants_STACKFRAME_SENTINEL_FP;
   
   // force any machine code within image that's still in dcache to be
   // written out to main memory so that it will be seen by icache when
   // instructions are fetched back
   //
   sysSyncCache((caddr_t) bootRegion, roundedImageSize);

#ifdef RVM_FOR_AIX
   if (lib_verbose) 
     fprintf(SysTraceFile, "Testing faulting-address location\n");
   *((int *) testFaultingAddress) = 42;
   if (lib_verbose) 
     fprintf(SysTraceFile, "Done testing faulting-address location\n");
#endif

   // execute vm startup thread
   //
   if (vmInSeparateThread) {
     // Try starting VM in separate pthread, need to synchronize before exiting
     startupRegs[0] = jtoc;
     startupRegs[1] = pr;
     startupRegs[2] = tid;
     startupRegs[3] = fp;
     
     // clear flag for synchronization
     bootRecord.bootCompleted = 0;

#if (defined RVM_FOR_LINUX) && (!defined __linuxsmp__)
     fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
     exit(-1);
#else

     pthread_create(&vm_pthreadid, NULL, bootThreadCaller, NULL);
     
     // wait for the JNIStartUp code to set the completion flag before returning
     while (!bootRecord.bootCompleted) {
#if (_AIX43 || RVM_FOR_LINUX)
    sched_yield();
#else
    pthread_yield();
#endif    
     }
#endif

     return 0;
   } 

   else {

     if (lib_verbose) 
       fprintf(SysTraceFile, "%s: calling boot thread: jtoc = 0x%x   pr = 0x%x   tid = %d   fp = 0x%x\n", 
	       me, jtoc, pr, tid, fp);
     bootThread(jtoc, pr, tid, fp);
     fprintf(SysErrorFile, "Unexpected return from bootThread\n");
     return 1;
   }

}


/*
 * Wrapper for bootThread for a new pthread to start up the VM
 *
 */
void *bootThreadCaller(void *dummy) {
  
  ulong_t jtoc = startupRegs[0];
  ulong_t pr   = startupRegs[1];
  ulong_t tid  = startupRegs[2];
  ulong_t fp   = startupRegs[3];

  fprintf(SysErrorFile, "about to boot vm: \n");

  bootThread(jtoc, pr, tid, fp); 
  
  fprintf(SysErrorFile, "Unexpected return from vm startup thread\n");
  return NULL;

}


// Get address of JTOC.
extern "C" void *getJTOC() {
  return (void*) VmToc;
}

// Get offset of VM_Scheduler.processors in JTOC.
extern "C" int getProcessorsOffset() {
  return ProcessorsOffset;
}


// we do not have this yet of PowerPC
extern int createJavaVM() {
  fprintf(SysErrorFile, "Cannot CreateJavaVM on PowerPC yet");
  return -1;
}

