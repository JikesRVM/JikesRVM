/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception handling.
 * The file "sys.C" contains the o/s support services required by the java class libraries.
 *
 * PowerPC version for AIX and Linux
 *
 * @author  Derek Lieber
 * @date    03 Feb 1998
 * 17 Oct 2000 Splitted from the original RunBootImage, contains everything except the 
 *             command line parsing in main
 */


#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <strings.h> // bzero ()

#ifdef __linux__
#include <pthread.h>
#include <asm/cache.h>
#include <ucontext.h>
#include <signal.h>
#include <errno.h>
#include <unistd.h>
#define NGPRS 32
#define NFPRS  0
#define GETCONTEXT_IMPLEMENTED 0
#else
#include <sys/cache.h>
#include <sys/context.h>
extern "C" char *sys_siglist[];
#endif


extern "C" int createJVM(int);

// There are several ways to allocate large areas of virtual memory:
// 1. malloc() is simplest, but doesn't allow a choice of address and so requires address relocation for references appearing in boot image
// 2. mmap() is simple to use but, on AIX, uses 2x amount of physical memory requested (per Al Chang)
// 3. shmat() doesn't have the 2x problem, but is limited to 256M per allocation on AIX 4.1 (unlimited on 4.3)
// [--DL]
//

#if (defined __linux__) || (defined GCTk)
#define USE_MMAP 1 // choose mmap() for Linux --SB
#else
#define USE_MMAP 0 // choose shmat() --DL
#endif

#if USE_MMAP
#include <sys/mman.h>
#else
#include <sys/shm.h>
#endif


// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include "AixLinkageLayout.h"
#include <InterfaceDeclarations.h>
VM_BootRecord *theBootRecord;

// Sink for messages relating to serious errors detected by C runtime.
//
FILE *SysErrorFile = stderr;

// Sink for trace messages produced by VM.sysWrite().
//
FILE *SysTraceFile = stderr;
int   SysTraceFd = 2;

// Command line arguments to be passed to boot image.
//
char **	JavaArgs;
int	JavaArgc;

// Emit trace information?
//
int lib_verbose = 0;

// place to save and pass jtoc, pr, tid, fp for starting up the boot image
int startupRegs[4];

// Terminate execution of vm if (recursive) fatal errors are encountered.
//
int FatalErrors = 0;

// Boundaries of memory region(s) in which virtual machine image is running.
//
unsigned VmBottom; // lowest address      == start of boot image
unsigned VmMiddle; //                     == end   of boot image
unsigned VmTop;    // highest address + 1 == end of large object heap

// Location of jtoc within virtual machine image.
//
unsigned VmToc;

// Method id for inserting stackframes at sites of hardware traps.
//
int HardwareTrapMethodId;

// TOC offset of VM_Runtime.deliverHardwareException
//
int DeliverHardwareExceptionOffset;

// TOC offset of VM_Scheduler.dumpStackAndDie
// 
int DumpStackAndDieOffset;

// TOC offset of VM_Scheduler.processors[]
//
int ProcessorsOffset;

// TOC offset of VM_Scheduler.threads[]
//
int ThreadsOffset;

// TOC offset of VM_Scheduler.debugRequested
//
int DebugRequestedOffset;

// TOC offset of VM_Scheduler.attachThreadRequested
//
int AttachThreadRequestedOffset;

// Standard unix signal handler.
//
typedef void (*SIGNAL_HANDLER)(int);

// pthread id of the main RVM pthread
pthread_t vm_pthreadid;


// Was unix signal raised while vm code was running?
// Taken:    contents of "iar" and "jtoc" registers at time of signal
// Returned: 1 --> vm code was running
//           0 --> non-vm (eg. C library) code was running
//
static int
isVmSignal(unsigned iar, unsigned jtoc)
   {
   /* get the boot record */
   void *region1address = (void *) bootImageAddress;
   VM_BootRecord & bootRecord = *(VM_BootRecord *) region1address;

    return VmBottom <= iar   && iar  < bootRecord.heapEnd &&
           VmBottom <= jtoc  && jtoc < bootRecord.heapEnd;
   }

#ifdef __linux__
// The following code is factored out while getcontext() remains
// unimplemented for ppc linux.
sigcontext* getLinuxSavedContext(int signum, void* arg3)
   {
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

// Handle software signals.
// Note: this code runs in a small "signal stack" allocated by "sigstack()" (see later).
// Therefore care should be taken to keep the stack depth small. If mysterious
// crashes seem to occur, consider the possibility that the signal stack might
// need to be made larger. [--DL].
//
#ifdef __linux__
void cSignalHandler(int signum, siginfo_t* siginfo, void* arg3)
   {
   sigcontext* context = getLinuxSavedContext(signum, arg3);
   pt_regs *save = context->regs;
   unsigned iar  =  save->nip;
#else
void cSignalHandler(int signum, int zero, sigcontext *context)
   {
   mstsave *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/mstsave.h"
   unsigned iar  =  save->iar;
#endif
   unsigned jtoc =  save->gpr[VM_Constants_JTOC_POINTER];

// #define ANNOUNCE_TICK(message) write(SysTraceFd, message, strlen(message))
// #define ANNOUNCE(message) write(SysTraceFd, message, strlen(message))
   #define ANNOUNCE(message) 
   #define ANNOUNCE_TICK(message)

   if (signum == SIGALRM)
      { // asynchronous signal used for time slicing
      if (!VM_Configuration_BuildForThreadSwitchUsingControlRegisterBit)
         { 
         // Turn on thread-switch flag in each virtual processor.
         // Note that "jtoc" is not necessairly valid, because we might have interrupted
         // C-library code, so we use boot image jtoc address (== VmToc) instead.
         // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
         // For now, we assume table is fixed in boot image and never moves.
         //
         unsigned *processors = *(unsigned **)((char *)VmToc + ProcessorsOffset);
         unsigned  cnt        =  processors[-1];
         int	   i;
	 int epoch = *(int *) ((char *) VmToc + VM_Processor_epoch_offset);
	 *(int *) ((char *) VmToc + VM_Processor_epoch_offset) = epoch + 1;

#ifndef RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// line added here - ndp is now the last processor = and cnt includes it
				 cnt = cnt - 1;
	 // check for gc in progress: if so, return
	 // 
	 if ((*theBootRecord).lockoutProcessor == 0x0CCCCCCC) return;

	 int       val;
	 int       sendit = 0;
	 int       MISSES = -2;			// tuning parameter
         for (i = VM_Scheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt ; ++i)
	     {
             val = *(int      *)((char *)processors[i] + VM_Processor_threadSwitchRequested_offset);
	     if (val <= MISSES) sendit++;
	     *(int      *)((char *)processors[i] + VM_Processor_threadSwitchRequested_offset) = val - 1;
             }
	 if (sendit != 0) // some processor "stuck in native"
	 { 
	   if (processors[i] != 0 /*null*/ ) {  // have a NativeDaemon Processor (the last one)
	   ANNOUNCE(" got NDprocessor value\n ");
	   int pthread_id = *(int *)((char *)processors[i] + VM_Processor_pthread_id_offset);
	   ANNOUNCE(" got pthread_id  value\n ");
	   pthread_t thread = (pthread_t)pthread_id;
	   pthread_kill(thread, SIGCONT);
	 }
	 }
#else
         for (i = VM_Scheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt; ++i)
           *(unsigned *)((char *)processors[i] + VM_Processor_threadSwitchRequested_offset) = (unsigned) -1; // -1: all bits on
#endif
         }
      else
         { 
         // Turn on thread-switch bit in condition register of current virtual processor.
         // Note that we do this only if we've interrupted vm code (ie. if we've interrupted
         // C-library code, we cannot touch the condition registers).
         //
         if (isVmSignal(iar, jtoc))
            {
#ifdef __linux__
            save->ccr |= (0x80000000 >> VM_Constants_THREAD_SWITCH_BIT);
#else
            save->cr |= (0x80000000 >> VM_Constants_THREAD_SWITCH_BIT);
#endif
            ANNOUNCE_TICK("[tick]\n");
            }
         else
            {
            ANNOUNCE_TICK("[miss]\n");
            }
         }
      return;
      }

   if (signum == SIGHUP)
      { // asynchronous signal used to awaken external debugger
   // fprintf(SysTraceFile, "vm: signal SIGHUP at ip=0x%08x ignored\n", iar);
      return;
      }
   
   if (signum == SIGQUIT)
      { // asynchronous signal used to awaken internal debugger
      
      // Turn on debug-request flag.
      // Note that "jtoc" is not necessairly valid, because we might have interrupted
      // C-library code, so we use boot image jtoc address (== VmToc) instead.
      // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
      // For now, we assume table is fixed in boot image and never moves.
      //
      unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
      if (*flag)
         {
         static char *message = "vm: debug request already in progress, please wait\n";
         write(SysTraceFd, message, strlen(message));
         }
      else
         {
         static char *message = "vm: debug requested, waiting for a thread switch\n";
         write(SysTraceFd, message, strlen(message));
         *flag = 1;
         }
      return;
      }

   if (signum == SIGTERM)
      {
      fprintf(SysTraceFile, "vm: kill requested: (exiting)\n");
      // Process was killed from command line with a DumpStack signal
      // Dump stack by returning to VM_Scheduler.dumpStackAndDie passing
      // it the fp of the current thread.
      // Note that "jtoc" is not necessairly valid, because we might have interrupted
      // C-library code, so we use boot image jtoc address (== VmToc) instead.
      // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
      // For now, we assume table is fixed in boot image and never moves.
      //
      int dumpStack = *(int *)((char *)VmToc + DumpStackAndDieOffset);
      save->gpr[VM_Constants_FIRST_VOLATILE_GPR] =
         save->gpr[VM_Constants_FRAME_POINTER];
#ifdef __linux__
      save->link = save->nip + 4; // +4 so it looks like a return address
      save->nip = dumpStack;
#else
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
#ifdef __linux__
void cTrapHandler(int signum, siginfo_t *siginfo, void* arg3)
   {
   sigcontext* context = getLinuxSavedContext(signum, arg3);
   pt_regs *save = context->regs;
   unsigned iar  =  save->nip;
#else
void cTrapHandler(int signum, int zero, sigcontext *context)
   {
   mstsave *save = &context->sc_jmpbuf.jmp_context; // see "/usr/include/sys/mstsave.h"
   unsigned iar  =  save->iar;
#endif
   unsigned jtoc =  save->gpr[VM_Constants_JTOC_POINTER];
   
   const int TID = VM_Constants_THREAD_ID_REGISTER;
   const int FP  = VM_Constants_FRAME_POINTER;
   const int P0  = VM_Constants_FIRST_VOLATILE_GPR;
   const int P1  = VM_Constants_FIRST_VOLATILE_GPR+1;

   // fetch address of java exception handler
   //
   int javaExceptionHandlerAddress = *(int *)((char *)jtoc + DeliverHardwareExceptionOffset);

   // We are prepared to handle these kinds of "recoverable" traps:
   //
   //  1. SIGSEGV - a null object dereference of the form "obj[-fieldOffset]"
   //               that wraps around to segment 0xf0000000.
   //
   //  2. SIGTRAP - an array bounds trap
   //               or integer divide by zero trap
   //               or stack overflow trap
   //
   // Anything else indicates some sort of unrecoverable vm error.
   //
   int isRecoverable = 0;
   
   if (isVmSignal(iar, jtoc))
      {
#if __linux__
      if (signum == SIGSEGV && (unsigned)(siginfo->si_addr) == 0)
#else
      if (signum == SIGSEGV && (save->o_vaddr & 0x80000000) == 0x80000000)
#endif
         isRecoverable = 1;

      else if (signum == SIGTRAP)
         isRecoverable = 1;
      }

   if (lib_verbose || !isRecoverable)
      {
#ifdef __linux__
      fprintf(SysTraceFile,"exception: type=%s\n", signum < NSIG ? sys_siglist[signum] : "?");
      fprintf(SysTraceFile,"            mem=0x%08x\n", siginfo->si_addr);
      fprintf(SysTraceFile,"          instr=0x%08x\n", *(unsigned *)(save->nip));
      fprintf(SysTraceFile,"             ip=0x%08x\n", save->nip);
      fprintf(SysTraceFile,"             lr=0x%08x\n", save->link);
      fprintf(SysTraceFile,"             fp=0x%08x\n", save->gpr[FP]);
      fprintf(SysTraceFile,"            tid=0x%08x\n", save->gpr[TID]);
      fprintf(SysTraceFile,"             pr=0x%08x\n", save->gpr[VM_Constants_PROCESSOR_REGISTER]);
      fprintf(SysTraceFile,"        handler=0x%08x\n", javaExceptionHandlerAddress);
#else
      fprintf(SysTraceFile,"exception: type=%s\n", signum < NSIG ? sys_siglist[signum] : "?");
      fprintf(SysTraceFile,"            mem=0x%08x\n", save->o_vaddr);
      fprintf(SysTraceFile,"          instr=0x%08x\n", *(unsigned *)save->iar);
      fprintf(SysTraceFile,"             ip=0x%08x\n", save->iar);
      fprintf(SysTraceFile,"             lr=0x%08x\n", save->lr);
      fprintf(SysTraceFile,"             fp=0x%08x\n", save->gpr[FP]);
      fprintf(SysTraceFile,"            tid=0x%08x\n", save->gpr[TID]);
      fprintf(SysTraceFile,"             pr=0x%08x\n", save->gpr[VM_Constants_PROCESSOR_REGISTER]);
      fprintf(SysTraceFile,"        handler=0x%08x\n", javaExceptionHandlerAddress);
#endif
      if (isRecoverable)
         {
         fprintf(SysTraceFile,"vm: normal trap\n");
         }
      else
         {
         fprintf(SysErrorFile, "vm: internal error trap\n");
         if (++FatalErrors > 3)
            exit(1); // attempt to let vm print an error message, but three strikes and you're out
         }
      }

   // Copy trapped register set into current thread's "hardware exception registers" save area.
   //
   int       threadOffset = save->gpr[TID] >> (VM_ThinLockConstants_TL_THREAD_ID_SHIFT - 2);
   unsigned  threads      = *(unsigned  *)((char *)jtoc + ThreadsOffset);
   unsigned *thread       = *(unsigned **)((char *)threads + threadOffset);
   unsigned *registers    = *(unsigned **)((char *)thread + VM_Thread_hardwareExceptionRegisters_offset);
   
   unsigned *gprs         = *(unsigned **)((char *)registers + VM_Registers_gprs_offset);
   double   *fprs         = *(double   **)((char *)registers + VM_Registers_fprs_offset);
   unsigned *ip           =  (unsigned  *)((char *)registers + VM_Registers_ip_offset);
   unsigned *lr           =  (unsigned  *)((char *)registers + VM_Registers_lr_offset);
   unsigned *inuse        =  (unsigned  *)((char *)registers + VM_Registers_inuse_offset);
   
   if (*inuse) {
      fprintf(SysTraceFile, "vm: internal error: recursive use of hardware exception registers (exiting)\n");
      // Things went badly wrong, so attempt to generate a useful error dump 
      // before exiting by returning to VM_Scheduler.dumpStackAndDie passing it the fp 
      // of the offending thread.
      // We could try to continue, but sometimes doing so results in cascading failures
      // and it's hard to tell what the real problem was.
      int dumpStack = *(int *)((char *)jtoc + DumpStackAndDieOffset);
      save->gpr[P0] = save->gpr[FP];
#ifdef __linux__
      save->link = save->nip + 4; // +4 so it looks like a return address
      save->nip = dumpStack;
#else
      save->lr = save->iar + 4; // +4 so it looks like a return address
      save->iar = dumpStack;
#endif
      return;
   } 

#ifdef __linux__
   {
     int i;
     for (i = 0; i < NGPRS; ++i)
       gprs[i] = save->gpr[i];
#warning Linux does not save the fp registers
     //     for (i = 0; i < NFPRS; ++i)
     //       fprs[i] = -1.0;   
   }
   *ip = save->nip + 4; // +4 so it looks like return address
   *lr = save->link;
#else
   {
     int i;
     for (i = 0; i < NGPRS; ++i)
       gprs[i] = save->gpr[i];
     for (i = 0; i < NFPRS; ++i)
       fprs[i] = save->fpr[i];
   }
   *ip = save->iar+ 4; // +4 so it looks like return address
   *lr = save->lr;
#endif
   *inuse = 1;
   
   // Insert artificial stackframe at site of trap.
   // This frame marks the place where "hardware exception registers" were saved.
   //
   int   oldFp = save->gpr[FP];
   int   newFp = oldFp - VM_Constants_STACKFRAME_HEADER_SIZE;
#ifdef __linux__
   *(int *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->nip + 4; // +4 so it looks like return address
#else
   *(int *)(oldFp + VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = save->iar + 4; // +4 so it looks like return address
#endif
   *(int *)(newFp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET)        = HardwareTrapMethodId;
   *(int *)(newFp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET)    = oldFp;
   save->gpr[FP] = newFp;

   // Set execution to resume in java exception handler rather than re-executing instruction
   // that caused the trap.
   //
   #define ANNOUNCE_TRAP(message) if (lib_verbose) write(SysTraceFd, message, strlen(message))

   int      trapCode    = VM_Runtime_TRAP_UNKNOWN;
   int      trapInfo    = 0;
#ifdef __linux__
   unsigned instruction = *(unsigned *)(save->nip);
#else
   unsigned instruction = *(unsigned *)save->iar;
#endif

   switch (signum)
      {
      case SIGSEGV:
#ifdef __linux__
      if ((unsigned)(siginfo->si_addr) == 0)
         { // touched top segment of memory, presumably by wrapping negatively off 0
         ANNOUNCE_TRAP("vm: null pointer trap\n");
         trapCode = VM_Runtime_TRAP_NULL_POINTER;
         break;
         }
      ANNOUNCE_TRAP("vm: unknown trap\n");
      trapCode = VM_Runtime_TRAP_UNKNOWN;
#else
      if ((save->o_vaddr & 0x80000000) == 0x80000000)
         { // touched top segment of memory, presumably by wrapping negatively off 0
         ANNOUNCE_TRAP("vm: null pointer trap\n");
         trapCode = VM_Runtime_TRAP_NULL_POINTER;
         break;
         }
      ANNOUNCE_TRAP("vm: unknown trap\n");
      trapCode = VM_Runtime_TRAP_UNKNOWN;
#endif
      break;

      case SIGTRAP:
      if ((instruction & VM_Constants_ARRAY_INDEX_MASK) == VM_Constants_ARRAY_INDEX_TRAP)
         {
         ANNOUNCE_TRAP("vm: array bounds trap\n");
         trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
         trapInfo = gprs[(instruction & VM_Constants_ARRAY_INDEX_REG_MASK)
                         >> VM_Constants_ARRAY_INDEX_REG_SHIFT];
         break;
         }
      if ((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_MASK) == VM_Constants_CONSTANT_ARRAY_INDEX_TRAP)
         {
         ANNOUNCE_TRAP("vm: array bounds trap\n");
         trapCode = VM_Runtime_TRAP_ARRAY_BOUNDS;
         trapInfo = ((int)((instruction & VM_Constants_CONSTANT_ARRAY_INDEX_INFO)<<16))>>16;
         break;
         }
      if ((instruction & VM_Constants_DIVIDE_BY_ZERO_MASK) == VM_Constants_DIVIDE_BY_ZERO_TRAP)
         {
         ANNOUNCE_TRAP("vm: divide by zero trap\n");
         trapCode = VM_Runtime_TRAP_DIVIDE_BY_ZERO;
         break;
         }
      if ((instruction & VM_Constants_MUST_IMPLEMENT_MASK) == VM_Constants_MUST_IMPLEMENT_TRAP)
         {
         ANNOUNCE_TRAP("vm: must implement trap\n");
         trapCode = VM_Runtime_TRAP_MUST_IMPLEMENT;
         break;
         }
      if ((instruction & VM_Constants_CHECKCAST_MASK ) == VM_Constants_CHECKCAST_TRAP)
         {
         ANNOUNCE_TRAP("vm: checkcast trap\n");
         trapCode = VM_Runtime_TRAP_CHECKCAST;
         break;
         }
      if ((instruction & VM_Constants_REGENERATE_MASK) == VM_Constants_REGENERATE_TRAP)
         {
         ANNOUNCE_TRAP("vm: regenerate trap\n");
         trapCode = VM_Runtime_TRAP_REGENERATE;
         break;
         }
      if ((instruction & VM_Constants_NULLCHECK_MASK) == VM_Constants_NULLCHECK_TRAP)
         {
         ANNOUNCE_TRAP("vm: null pointer trap\n");
         trapCode = VM_Runtime_TRAP_NULL_POINTER;
         break;
         }
      if ((instruction & VM_Constants_JNI_STACK_TRAP_MASK) == VM_Constants_JNI_STACK_TRAP)
         {
         ANNOUNCE_TRAP("vm: resize stack for JNI call\n");
         trapCode = VM_Runtime_TRAP_JNI_STACK;
         break;
         }
      if ((instruction & VM_Constants_WRITE_BUFFER_OVERFLOW_MASK) == VM_Constants_WRITE_BUFFER_OVERFLOW_TRAP)
         { //!!TODO: someday use logic similar to stack guard page to force a gc
         ANNOUNCE_TRAP("vm: write buffer overflow trap\n");
         fprintf(SysErrorFile,"vm: write buffer overflow trap\n");
         exit(1);
         }
      if ((instruction & VM_Constants_STACK_OVERFLOW_MASK) == VM_Constants_STACK_OVERFLOW_TRAP)
         {
         ANNOUNCE_TRAP("vm: stack overflow trap\n");
         trapCode = VM_Runtime_TRAP_STACK_OVERFLOW;

         // adjust stack limit downward to give exception handler some space in which to run
         //
         unsigned stackLimit = *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset);
         unsigned stackStart = *(unsigned *)((char *)thread + VM_Thread_stack_offset);
         stackLimit -= VM_Constants_STACK_SIZE_GUARD;
         if (stackLimit < stackStart)
            { // double fault - stack overflow exception handler used too much stack space
            fprintf(SysErrorFile, "vm: stack overflow exception (double fault)\n");

	    // Go ahead and get all the stack space we need to generate the error dump (since we're crashing anyways)
	    *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset) = 0;
	    
	    // Things are very badly wrong anyways, so attempt to generate 
	    // a useful error dump before exiting by returning to VM_Scheduler.dumpStackAndDie
	    // passing it the fp of the offending thread.
	    int dumpStack = *(int *)((char *)jtoc + DumpStackAndDieOffset);
	    save->gpr[P0] = save->gpr[FP];
#ifdef __linux__
	    save->link = save->nip + 4; // +4 so it looks like a return address
	    save->nip = dumpStack;
#else
	    save->lr = save->iar + 4; // +4 so it looks like a return address
	    save->iar = dumpStack;
#endif
	    return;
            }
         *(unsigned *)((char *)thread + VM_Thread_stackLimit_offset) = stackLimit;
	 *(unsigned *)(save->gpr[VM_Constants_PROCESSOR_REGISTER] + VM_Processor_activeThreadStackLimit_offset) = stackLimit;

         break;
         }
      ANNOUNCE_TRAP("vm: unknown trap\n");
      trapCode = VM_Runtime_TRAP_UNKNOWN;
      break;

      default:
      ANNOUNCE_TRAP("vm: unknown trap\n");
      trapCode = VM_Runtime_TRAP_UNKNOWN;
      break;
      }
   
   // pass arguments to java exception handler
   //
   save->gpr[P0] = trapCode;
   save->gpr[P1] = trapInfo;

   // set link register to make it look like java exception handler was called from exception site
   //
#ifdef __linux__
   if (save->nip == 0)
      { // bad branch - use contents of link register as best guess of call site
      ;
      }
   else
      {
      save->link = save->nip + 4; // +4 so it looks like a return address
      }
#else
   if (save->iar == 0)
      { // bad branch - use contents of link register as best guess of call site
      ;
      }
   else
      {
      save->lr = save->iar + 4; // +4 so it looks like a return address
      }
#endif
   
   // resume execution at java exception handler
   //
#ifdef __linux__
   save->nip = javaExceptionHandlerAddress;
#else
   save->iar = javaExceptionHandlerAddress;
#endif

// fprintf(SysTraceFile, "vm: delivering trap code %d\n", trapCode);
   }



extern "C" void bootThread(int jtoc, int pr, int ti, int fp); // assembler routine
void *bootThreadCaller(void *);
#include <pthread.h>

// startup configuration option with default values
char *bootFilename     = 0;
unsigned smallHeapSize = 20 * 1024 * 1024; // megs
// make large heap size a percent of small heap, or what was specified
// unsigned largeHeapSize = 10 * 1024 * 1024; // megs
//
unsigned largeHeapSize = 0;                // megs

unsigned nurserySize   = 10 * 1024 * 1024; // megs
unsigned permanentHeapSize = 0;

// name of program that will load and run RVM
char *me;

int
createJVM(int vmInSeparateThread)
   {

   // arguments processing moved to runboot.c

   // don't buffer trace or error message output
   //
#ifdef __linux__
   setvbuf(SysErrorFile, 0, _IONBF, 0);
   setvbuf(SysTraceFile, 0, _IONBF, 0);
#else
   setbuf(SysErrorFile, 0);
   setbuf(SysTraceFile, 0);
#endif
   
   // open image file
   //
   FILE *fin = fopen(bootFilename, "r");
   if (!fin)
      {
      fprintf(SysTraceFile, "%s: can't find boot image \"%s\"\n", me, bootFilename);
      return 1;
      }

   // measure image size
   //
   if (lib_verbose) fprintf(SysTraceFile, "%s: loading from \"%s\"\n", me, bootFilename);
   fseek(fin, 0L, SEEK_END);
   unsigned imageSize = ftell(fin);
   unsigned permaHeap = imageSize;
   if (permanentHeapSize > permaHeap)
      permaHeap = permanentHeapSize;
   fseek(fin, 0L, SEEK_SET);

   // Now check that permaHeap + smallHeapSize + largeHeapSize
   // doesn't spill over into seg 8 (Java doesn't have logical
   // arithmetic so allocators will fail
   // CRA:
   //
   if ((permaHeap + smallHeapSize + largeHeapSize) >= 0x50000000)
     {
	do
	{
	  largeHeapSize = largeHeapSize >> 1;
	  if (largeHeapSize < (10 * 1024 * 1024)) break; 	 
        } while ((permaHeap + smallHeapSize + largeHeapSize) >= 0x50000000);

	if (largeHeapSize < (10 * 1024 * 1024))
	{ fprintf(SysTraceFile, "specified heap sizes too large\n");
	  return 1;
        }
     }

    fprintf(SysTraceFile, "small heap = %d, large heap = %d\n", 
		smallHeapSize, largeHeapSize);
	

   // allocate memory regions in units of 4k (== system page size)
   //
   void    *region1address = (void *) bootImageAddress; // address used by boot image writer
   unsigned region1size = (((permaHeap + smallHeapSize) / 4096) + 1) * 4096;
#if USE_MMAP
#ifdef GCTk
   unsigned mmapsize = ((imageSize + 4095) & ~4095);
#else
   unsigned mmapsize = region1size;
#endif
#ifdef __linux__
   void *region1 = mmap(region1address, mmapsize, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED, -1, 0);
#else
   void *region1 = mmap(region1address, mmapsize, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
#endif
   if (region1 == (void *)-1)
      {
      fprintf(SysErrorFile, "%s: mmap failed (errno=%d)\n", me, errno);
      return 1;
      }
#else
   int id1 = shmget(IPC_PRIVATE, region1size, IPC_CREAT | IPC_EXCL | S_IRUSR | S_IWUSR);
   if (id1 == -1)
      {
      fprintf(SysErrorFile, "%s: shmget failed (errno=%d)\n", me, errno);
      return 1;
      }
   void *region1 = shmat(id1, region1address, 0);
   if (region1 == (void *)-1)
      {
      fprintf(SysErrorFile, "%s: shmat failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (shmctl(id1, IPC_RMID, 0))
      {
      fprintf(SysErrorFile, "%s: shmctl failed (errno=%d)\n", me, errno);
      return 1;
      }
#endif

   void    *region2address = (void *)0; // any address is ok
   unsigned region2size    = ((largeHeapSize / 4096) + 1) * 4096;
   void    *region2        = region2address;
#if USE_MMAP
#ifdef GCTk
   // we don't use region2 in GCTk, so don't mmap anything
   region2 = (void *) ((unsigned int) region1 + (unsigned int) mmapsize + 4096);
#else
#ifdef __linux__
   region2address = (void *) ((unsigned int) region1address + 0x10000000);  // using MAP_FIXED
   region2 = mmap(region2address, region2size, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED, -1, 0);
#else
   region2 = mmap(region2address, region2size, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_VARIABLE, -1, 0);
#endif
#endif
   if (region2 == (void *)-1)
      {
      fprintf(SysErrorFile, "%s: mmap failed (errno=%d)\n", me, errno);
      return 1;
      }
#else
   int id2 = shmget(IPC_PRIVATE, region2size, IPC_CREAT | IPC_EXCL | S_IRUSR | S_IWUSR);
   if (id2 == -1)
      {
      fprintf(SysErrorFile, "%s: shmget failed (errno=%d)\n", me, errno);
      return 1;
      }
   region2 = shmat(id2, region2address, 0);
   if (region2 == (void *)-1)
      {
      fprintf(SysErrorFile, "%s: shmat failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (shmctl(id2, IPC_RMID, 0))
      {
      fprintf(SysErrorFile, "%s: shmctl failed (errno=%d)\n", me, errno);
      return 1;
      }
#endif

   if (lib_verbose)
      {
      fprintf(SysTraceFile, "%s: 1st memory region: [0x%08x...0x%08x]\n", me, region1, (int)region1 + region1size);
      fprintf(SysTraceFile, "%s: 2nd memory region: [0x%08x...0x%08x]\n", me, region2, (int)region2 + region2size);
      }

   // read image into memory segment
   //
   int cnt = fread(region1, 1, imageSize, fin);
   
   if (cnt != imageSize)
      {
      fprintf(SysErrorFile, "%s: read failed (errno=%d)\n", me, errno);
      return 1;
      }
   
   if (imageSize % 4 != 0)
      {
      fprintf(SysErrorFile, "%s: image format error: image size (%d) is not a word multiple\n", me, imageSize);
      return 1;
      }

   if (fclose(fin) != 0)
      {
      fprintf(SysErrorFile, "%s: close failed (errno=%d)\n", me, errno);
      return 1;
      }
  
   // fetch contents of boot record
   //
   VM_BootRecord& bootRecord	= *(VM_BootRecord *)region1;
   theBootRecord		= (VM_BootRecord *) region1;
   
/******* NOT CURRENTLY IN USE
 * // see if fields make sense
 * //
 * if ((bootRecord.relocaterAddress % 4) != 0)
 *    {
 *    fprintf(SysErrorFile, "%s: image format error: relocaterAddress (0x%08x) is not word aligned\n", me, bootRecord.relocaterAddress);
 *    return 1;
 *    }
 *
 * if (! ( (bootRecord.relocaterAddress >= 0) && (bootRecord.relocaterAddress < bootRecord.endAddress)))
 *    {
 *    fprintf(SysErrorFile, "%s: image format error: relocaterAddress (0x%08x) is not within image\n", me, bootRecord.relocaterAddress);
 *    return 1;
 *    }
 *
 * if (! ( (bootRecord.relocaterLength >= 0) && (bootRecord.relocaterLength + bootRecord.relocaterAddress < bootRecord.endAddress)))
 *    {
 *    fprintf(SysErrorFile, "%s: image format error: relocaterLength (0x%08x) is not within image\n", me, bootRecord.relocaterLength);
 *    return 1;
 *    }
 *
 * if (bootRecord.startAddress != (int)region1)
 *    {
 *    // Relocate the image addresses
 *    //
 *    int delta = ((int)region1 - bootRecord.startAddress) * sizeof(int);  // new_base - old_base
 *    int map_offset = (bootRecord.relocaterAddress - bootRecord.startAddress)/4;  // Byte address to word offset in image
 *    int map_length = bootRecord.relocaterLength;
 *    if (lib_verbose) fprintf(SysErrorFile, "%s: delta=0x%08x, map_offset=0x%08x, map_length:=%d\n", me, delta, map_offset, map_length);
 *    int offset_of_address;
 *    int i;
 *    for (i = 0 ; i < map_length; i++)
 *       {
 *       offset_of_address = ((int *)region1)[i+map_offset];
 *       if (((int *)region1)[offset_of_address] != 0) ((int *)region1)[offset_of_address] +=  delta; // old + new_base - old_base
 *       }
 *
 *    // These fields are not treated as addresses in the BootImageWriter and so do not appear in the relocation map.
 *    //
 *    bootRecord.startAddress     += delta;
 *    bootRecord.freeAddress      += delta;
 *    bootRecord.endAddress       += delta;
 *    bootRecord.relocaterAddress += delta;
 *    bootRecord.spRegister       += delta;
 *    bootRecord.ipRegister       += delta;
 *    bootRecord.tocRegister      += delta;
 *
 *    if (lib_verbose) fprintf(SysErrorFile, "%s: image relocated to start address (0x%08x)\n", me, region1);
 *    }
 ******** NOT CURRENTLY IN USE */
   
   if ((bootRecord.spRegister % 4) != 0)
      {
      // In the RISC6000 asm manual we read that sp had to be quad word aligned, but we don't align our stacks...yet.
      fprintf(SysErrorFile, "%s: image format error: sp (0x%08x) is not word aligned\n", me, bootRecord.spRegister);
      return 1;
      }
   
   if ((bootRecord.ipRegister % 4) != 0)
      {
      fprintf(SysErrorFile, "%s: image format error: ip (0x%08x) is not word aligned\n", me, bootRecord.ipRegister);
      return 1;
      }
   
   if ((bootRecord.tocRegister % 4) != 0)
      {
      fprintf(SysErrorFile, "%s: image format error: toc (0x%08x) is not word aligned\n", me, bootRecord.tocRegister);
      return 1;
      }
   
   if (((int *)bootRecord.spRegister)[-1] != 0xdeadbabe)
      {
      fprintf(SysErrorFile, "%s: image format error: missing stack sanity check marker (0x%08x)\n", me, ((int *)bootRecord.spRegister)[-1]);
      return 1;
      }

   if (bootRecord.startAddress != (int)region1)
      {
      fprintf(SysErrorFile, "%s: image load error: image was compiled for address (0x%08x) but loaded at (0x%08x)\n", me, bootRecord.startAddress, region1);
      return 1;
      }
  
   // remember vm memory boundaries for later use by trap handler and instruction sampler
   //
   VmBottom = (unsigned)region1;                // start of boot image
   VmMiddle = (unsigned)region1 + imageSize;    // end of boot image
   VmTop    = (unsigned)region2 + region2size;  // end of heap
   if (VmTop < VmBottom)
      {
      fprintf(SysErrorFile, "%s: image load error: memory segments allocated in unexpected order\n", me);
      fprintf(SysErrorFile, "   bottom=0x%08x middle=0x%08x top=0x%08x\n", VmBottom, VmMiddle, VmTop);
      return 1;
      }

   // remember jtoc location for later use by trap handler
   //
   VmToc = bootRecord.tocRegister;

   // set freespace information into boot record
   //
   bootRecord.permaAddress= (int)region1 + imageSize;
   bootRecord.freeAddress = (int)region1 + permaHeap; 
   bootRecord.endAddress  = (int)region1 + region1size;
   bootRecord.largeStart  = (int)region2;
   bootRecord.largeSize   = region2size;
   #ifdef GCTk
   bootRecord.heapEnd = (int)region1 + permaHeap;
   #else
   bootRecord.heapEnd     = (int)region2 + region2size;
   #endif
   bootRecord.nurserySize = nurserySize;
   
#if 0
   // prepage freespace to avoid pagefaults during benchmarks
   //
   bzero((void *)bootRecord.freeAddress, bootRecord.endAddress - bootRecord.freeAddress);
   bzero((void *)bootRecord.largeStart, bootRecord.largeSize);
#endif
   
   // set host o/s linkage information into boot record
   //
   bootRecord.setLinkage();

   // remember location of java exception handler
   //
   DeliverHardwareExceptionOffset = bootRecord.deliverHardwareExceptionOffset;
   HardwareTrapMethodId           = bootRecord.hardwareTrapMethodId;

   // remember JTOC offset of VM_Scheduler.dumpStackAndDie
   DumpStackAndDieOffset = bootRecord.dumpStackAndDieOffset;

   // remember JTOC offset of VM_Scheduler.processors[]
   //
   ProcessorsOffset = bootRecord.processorsOffset;

#ifndef RVM_WITH_DEDICATED_NATIVE_PROCESSORS
   if (ProcessorsOffset == 0) ANNOUNCE(" IN LIBVM, processorsoffset = 0 ");
#endif
   
   // remember JTOC offset of VM_Scheduler.threads[]
   //
   ThreadsOffset = bootRecord.threadsOffset;
   
   // remember JTOC offset of VM_Scheduler.DebugRequested
   // 
   DebugRequestedOffset = bootRecord.debugRequestedOffset;
   
   // remember JTOC offset of VM_Scheduler.attachThreadRequested
   //
   AttachThreadRequestedOffset = bootRecord.attachThreadRequestedOffset;
   
   if (lib_verbose)
      {
      fprintf(SysTraceFile, "%s: boot record contents:\n", me);
      fprintf(SysTraceFile, "   startAddress:         0x%08x\n",   bootRecord.startAddress);
      fprintf(SysTraceFile, "   permaAddress:         0x%08x\n",   bootRecord.permaAddress);
      fprintf(SysTraceFile, "   freeAddress:          0x%08x\n",   bootRecord.freeAddress);
      fprintf(SysTraceFile, "   endAddress:           0x%08x\n",   bootRecord.endAddress);
      fprintf(SysTraceFile, "   largeStart:           0x%08x\n",   bootRecord.largeStart);
      fprintf(SysTraceFile, "   largeSize:            0x%08x\n",   bootRecord.largeSize);
      fprintf(SysTraceFile, "   tiRegister:           0x%08x\n",   bootRecord.tiRegister);
      fprintf(SysTraceFile, "   spRegister:           0x%08x\n",   bootRecord.spRegister);
      fprintf(SysTraceFile, "   ipRegister:           0x%08x\n",   bootRecord.ipRegister);
      fprintf(SysTraceFile, "   tocRegister:          0x%08x\n",   bootRecord.tocRegister);
      fprintf(SysTraceFile, "   sysTOC:               0x%08x\n",   bootRecord.sysTOC);
      fprintf(SysTraceFile, "   sysWriteCharIP:       0x%08x\n",   bootRecord.sysWriteCharIP);
      fprintf(SysTraceFile, "   ...etc...                   \n");
      }

   // install a stack for cSignalHandler() and cTrapHandler() to run on
   //
#if __linux__
   stack_t altstack;
   char ss_sp[SIGSTKSZ];
   altstack.ss_sp = &ss_sp;
   altstack.ss_flags = 0;
   altstack.ss_size = SIGSTKSZ;
   if (sigaltstack(&altstack, 0) < 0)
      {
      fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", me, errno);
      return 1;
      }
   // install hardware trap handler
   //
   struct sigaction action;
   action.sa_sigaction = cTrapHandler;
   action.sa_flags     = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
   /* mask all signals during signal handling */
   if (sigfillset(&(action.sa_mask)))
   {
     fprintf(SysErrorFile, "%s: sigfillset failed (errno=%d)\n", me, errno);
     return 1;
   }
   /* exclude the signal used to poke pthreads */
   if (sigdelset(&(action.sa_mask), SIGCONT))
   {
     fprintf(SysErrorFile, "%s: sigdelset failed (errno=%d)\n", me, errno);
     return 1;
   }
#else
   struct sigstack stackInfo;
   #define STACKSIZE (4*4096)
   stackInfo.ss_sp = new char[STACKSIZE] + STACKSIZE; // hi end of stack
   stackInfo.ss_onstack = 0;
   if (sigstack(&stackInfo, 0))
      {
      fprintf(SysErrorFile, "%s: sigstack failed (errno=%d)\n", me, errno);
      return 1;
      }
   
   // install hardware trap handler
   //
   struct sigaction action;
   action.sa_handler = (SIGNAL_HANDLER)cTrapHandler;
   action.sa_flags   = SA_ONSTACK | SA_RESTART;
   SIGFILLSET(action.sa_mask);
   SIGDELSET(action.sa_mask, SIGCONT);
#endif
   if (sigaction(SIGSEGV, &action, 0)) // catch null pointer references
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (sigaction(SIGTRAP, &action, 0)) // catch array bounds violations
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (sigaction(SIGILL, &action, 0)) // catch vm errors (so we can try to give a traceback)
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }

   // install software signal handler
   //
#ifdef __linux__
   action.sa_sigaction = cSignalHandler;
#else
   action.sa_handler = (SIGNAL_HANDLER)cSignalHandler;
#endif
   if (sigaction(SIGALRM, &action, 0)) // catch timer ticks (so we can timeslice user level threads)
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (sigaction(SIGHUP, &action, 0)) // catch signal to awaken external debugger
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (sigaction(SIGQUIT, &action, 0)) // catch signal to awaken internal debugger
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
   if (sigaction(SIGTERM, &action, 0)) // catch signal to dump stack and die
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }

   // Ignore "write (on a socket) with nobody to read it" signals so
   // that sysWriteBytes() will get an EPIPE return code instead of trapping.
   //
   action.sa_handler = (SIGNAL_HANDLER)SIG_IGN;
   if (sigaction(SIGPIPE, &action, 0))
      {
      fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
      return 1;
      }
      
   // set up initial stack frame
   //
   int  jtoc = bootRecord.tocRegister;
   int pr;
   {
   unsigned *processors = *(unsigned **)(bootRecord.tocRegister +
				         bootRecord.processorsOffset);
   pr   = processors[VM_Scheduler_PRIMORDIAL_PROCESSOR_ID];
   }
   int  tid  = bootRecord.tiRegister;
   int  ip   = bootRecord.ipRegister;
   int *sp   = (int *)bootRecord.spRegister;
     *--sp   = ip;                                   // STACKFRAME_NEXT_INSTRUCTION_OFFSET
     *--sp   = VM_Constants_INVISIBLE_METHOD_ID;     // STACKFRAME_METHOD_ID_OFFSET
     *--sp   = VM_Constants_STACKFRAME_SENTINAL_FP;  // STACKFRAME_FRAME_POINTER_OFFSET
   int  fp   = (int)sp;
   
   // force any machine code within image that's still in dcache to be
   // written out to main memory so that it will be seen by icache when
   // instructions are fetched back
   //
   sysSyncCache((caddr_t) region1, imageSize);

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

#if (defined __linux__) && (!defined __linuxsmp__)
     fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
     exit(-1);
#else

     pthread_create(&vm_pthreadid, NULL, bootThreadCaller, NULL);
     
     // wait for the JNIStartUp code to set the completion flag before returning
     while (!bootRecord.bootCompleted) {
#if (_AIX43 || __linux__)
    sched_yield();
#else
    pthread_yield();
#endif    
     }
#endif

     return (0);
   } 

   else {
     bootThread(jtoc, pr, tid, fp);

     // not reached
     //
    fprintf(SysErrorFile, "RVM startup thread\n");
     return(1);
   }

   }


/*
 * Wrapper for bootThread for a new pthread to start up the VM
 *
 */
void *bootThreadCaller(void *dummy) {
  
  int jtoc, pr, tid, fp;
  jtoc = startupRegs[0];
  pr   = startupRegs[1];
  tid  = startupRegs[2];
  fp   = startupRegs[3];

  fprintf(SysErrorFile, "about to boot vm: \n");

  bootThread(jtoc, pr, tid, fp); 
  
  fprintf(SysErrorFile, "Unexpected return from vm startup thread\n");
  return NULL;

}
