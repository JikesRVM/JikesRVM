/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception handling.
 * The file "sys.C" contains the o/s support services required by the java class libraries.
 *
 * IA32 version for Linux
 *
 * @author Derek Lieber
 * @date 06 Apr 2000
 */

#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <sys/mman.h>
#include <errno.h>
#include <string.h>
#ifndef __USE_GNU         // Deal with current ucontext ugliness.  The current
#define __USE_GNU         // mess of asm/ucontext.h & sys/ucontext.h and their
#include <sys/ucontext.h> // mutual exclusion on more recent versions of gcc
#undef __USE_GNU          // dictates this ugly hack.
#else
#include <sys/ucontext.h>
#endif
#include <assert.h>
#if (defined __linuxsmp__)
#include <pthread.h>
#endif

extern pthread_key_t VmProcessorKey;

/* Interface to virtual machine data structures. */
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include <InterfaceDeclarations.h>

extern "C" void setLinkage(VM_BootRecord*);

/* Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile;
int SysErrorFd;

/* Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile;
int SysTraceFd;

/* Command line arguments to be passed to virtual machine. */
char **JavaArgs;
int JavaArgc;

/* Emit trace information? */
int lib_verbose = 0;

/* Location of jtoc within virtual machine image. */
unsigned VmToc;

/* TOC offset of VM_Scheduler.dumpStackAndDie */
int DumpStackAndDieOffset;

/* TOC offset of VM_Scheduler.processors[] */
int ProcessorsOffset;

/* TOC offset of VM_Scheduler.debugRequested */
int DebugRequestedOffset;

unsigned traceClassLoading = 0;

/* name of program that will load and run RVM */
char *me;

extern "C" int createJVM (int);
extern "C" int bootThread (int ip, int jtoc, int pr, int sp);


static int pageRoundUp(int size) {
    int pageSize = 4096;
    return (size + pageSize - 1) / pageSize * pageSize;
}

/*
 * Bootimage is loaded and ready for execution:
 * you can set a breakpoint here with gdb.
 */
int
boot (int ip, int jtoc, int pr, int sp)
{
  return bootThread (ip, jtoc, pr, sp);
}

#include <ihnpdsm.h>
extern "C" PARLIST *Disassemble(
  char *pHexBuffer,                /* output: hex dump of instruction bytes  */
  char *pMnemonicBuffer,           /* output: instruction mnemonic string    */
  char *pOperandBuffer,            /* output: operands string                */
  char *pDataBuffer,               /* input:  buffer of bytes to disassemble */
  int  *fInvalid,                  /* output: disassembly successful: 1 or 0 */
  int   WordSize);                 /* input:  Segment word size: 2 or 4      */ 
unsigned int
getInstructionFollowing (unsigned int faultingInstructionAddress)
{
  int Illegal = 0;
  char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256], AddrBuffer[256];
  PARLIST *p;

  p = Disassemble(HexBuffer,
                  MnemonicBuffer,
                  OperandBuffer,
                  (char *) faultingInstructionAddress,
                  &Illegal,
                  4);
  if (Illegal)
    return faultingInstructionAddress;
  else
  {
    if ( lib_verbose)
      fprintf(SysTraceFile, "failing instruction : %s %s\n",
		 MnemonicBuffer, OperandBuffer);
    return faultingInstructionAddress + p->retleng;    
  }
}

VM_BootRecord *bootRecord;


static int inRVMAddressSpace(unsigned int addr) {

   /* get the boot record */
   VM_Address *heapRanges = bootRecord->heapRanges;
   int MaxHeaps = 10;  // update to match VM_Heap.MAX_HEAPS  XXXXX

   for (int which = 0; ; which++) {
     assert(which <= 2 * (MaxHeaps + 1));
       VM_Address start = heapRanges[2 * which];
       VM_Address end = heapRanges[2 * which + 1];
       if (start == ~0 && end == ~0) break;
       if (start <= addr  && addr  < end)
	 return true;
   }
	
   return false;
}

static int isVmSignal(unsigned int ip, unsigned int jtoc) {
  return inRVMAddressSpace(ip) && inRVMAddressSpace(jtoc);
}


#include <pthread.h>
 
pthread_mutex_t exceptionLock = PTHREAD_MUTEX_INITIALIZER;

void
hardwareTrapHandler (int signo, siginfo_t *si, void *context)
{
    pthread_mutex_lock( &exceptionLock );

  unsigned int localInstructionAddress;
  unsigned int localVirtualProcessorAddress;
  unsigned int localFrameAddress;
  unsigned int localJTOC = VmToc;

  int isRecoverable;

  unsigned int instructionFollowing;
  char buf[100];

  ucontext_t *uc = (ucontext_t *) context;  // user context
  mcontext_t *mc = &(uc->uc_mcontext);      // machine context
  greg_t  *gregs = mc->gregs;               // general purpose registers

  /*
   * fill local working variables from context saved by OS trap handler
   * mechanism
   */
  localInstructionAddress	= gregs[REG_EIP];
  localVirtualProcessorAddress	= gregs[REG_ESI];

   // We are prepared to handle these kinds of "recoverable" traps:
   //
   //  1. SIGSEGV - a null object dereference of the form "obj[-fieldOffset]"
   //               that wraps around to segment 0xf0000000.
   //
   //  2. SIGFPE  - interger divide by zero trap
   //
   //  3. SIGTRAP - stack overflow trap
   //
   // Anything else indicates some sort of unrecoverable vm error.
   //                                                                                        
  isRecoverable = 0;

  if (isVmSignal(localInstructionAddress, localVirtualProcessorAddress))
  {
    if (signo == SIGSEGV /*&& check the adddress TODO */)
      isRecoverable = 1;

    else if (signo == SIGFPE)
      isRecoverable = 1;
   
    else if (signo == SIGTRAP)
      isRecoverable = 0;
  }


  if (lib_verbose || !isRecoverable)
  {
    write (SysErrorFd, buf, sprintf (buf, 
		"%s: trap %d (%s)\n", 
		me, signo, 
		signo < _NSIG ? sys_siglist[signo] : "Unrecognized signal"));

    write (SysErrorFd, buf, sprintf (buf, "handler stack 0x%x\n", &buf));
    if (signo == SIGSEGV)
      write (SysErrorFd, buf, sprintf (buf, "si->si_addr   0x%08x\n", si->si_addr));
    write (SysErrorFd, buf, sprintf (buf, "gs            0x%08x\n", gregs[REG_GS]));
    write (SysErrorFd, buf, sprintf (buf, "fs            0x%08x\n", gregs[REG_FS]));
    write (SysErrorFd, buf, sprintf (buf, "es            0x%08x\n", gregs[REG_ES]));
    write (SysErrorFd, buf, sprintf (buf, "ds            0x%08x\n", gregs[REG_DS]));
    write (SysErrorFd, buf, sprintf (buf, "edi -- JTOC?  0x%08x\n", gregs[REG_EDI]));
    write (SysErrorFd, buf, sprintf (buf, "esi -- PR/VP  0x%08x\n", gregs[REG_ESI]));
    write (SysErrorFd, buf, sprintf (buf, "ebp -- FP?    0x%08x\n", gregs[REG_EBP]));
    write (SysErrorFd, buf, sprintf (buf, "esp -- SP     0x%08x\n", gregs[REG_ESP]));
    write (SysErrorFd, buf, sprintf (buf, "ebx           0x%08x\n", gregs[REG_EBX]));
    write (SysErrorFd, buf, sprintf (buf, "edx -- T1?    0x%08x\n", gregs[REG_EDX]));
    write (SysErrorFd, buf, sprintf (buf, "ecx -- S0?    0x%08x\n", gregs[REG_ECX]));
    write (SysErrorFd, buf, sprintf (buf, "eax -- T0?    0x%08x\n", gregs[REG_EAX]));
    write (SysErrorFd, buf, sprintf (buf, "trapno        0x%08x\n", gregs[REG_TRAPNO]));
    write (SysErrorFd, buf, sprintf (buf, "err           0x%08x\n", gregs[REG_ERR]));
    write (SysErrorFd, buf, sprintf (buf, "eip           0x%08x\n", gregs[REG_EIP]));
    write (SysErrorFd, buf, sprintf (buf, "cs            0x%08x\n", gregs[REG_CS]));
    write (SysErrorFd, buf, sprintf (buf, "eflags        0x%08x\n", gregs[REG_EFL]));
    write (SysErrorFd, buf, sprintf (buf, "esp_at_signal 0x%08x\n", gregs[REG_UESP]));
    write (SysErrorFd, buf, sprintf (buf, "ss            0x%08x\n", gregs[REG_SS]));
    write (SysErrorFd, buf, sprintf (buf, "fpstate       0x%08x\n", mc->fpregs));	/* null if fp registers haven't been used yet */
    write (SysErrorFd, buf, sprintf (buf, "oldmask       0x%08x\n", mc->oldmask));
    write (SysErrorFd, buf, sprintf (buf, "cr2           0x%08x\n", mc->cr2));	/* seems to contain mem address that faulting instruction was trying to access */

    /*
     * There are 8 floating point registers, each 10 bytes wide.
     * See /usr/include/asm/sigcontext.h
     */
    if (mc->fpregs) {
      struct _libc_fpreg *fpreg = mc->fpregs->_st;

      for (int i = 0; i < 8; ++i)
        write (SysErrorFd, buf, sprintf (buf, "fp%d 0x%04x%04x%04x%04x%04x\n",
				       i,
				       fpreg[i].significand[0] & 0xffff,
				       fpreg[i].significand[1] & 0xffff,
				       fpreg[i].significand[2] & 0xffff,
				       fpreg[i].significand[3] & 0xffff,
				       fpreg[i].exponent & 0xffff
	     ));
    }

    if (isRecoverable)
      fprintf (SysTraceFile, "vm: normal trap\n");
    else
    {
      fprintf (SysTraceFile, "vm: internal error\n");
    }
  }

  /* get the boot record */
  void *bootRegion = (void *) bootImageAddress;
  bootRecord = (VM_BootRecord *) bootRegion;

  /* test validity of virtual processor address */
  {
  unsigned int vp_hn;  /* the high nibble of the vp address value */
  vp_hn = localVirtualProcessorAddress >> 28;  
  if (vp_hn < 3 || !inRVMAddressSpace(localVirtualProcessorAddress)) 
    {
      write (SysErrorFd, buf,
             sprintf (buf,
                   "invalid vp address (not an address - high nibble %d)\n", vp_hn) );
      exit(-1);
    }
  }


  /* get the frame pointer from processor object  */
  localFrameAddress = 
	*(unsigned *) (localVirtualProcessorAddress + VM_Processor_framePointer_offset);

  /* test validity of frame address */
  {
  unsigned int fp_hn;
  fp_hn = localFrameAddress >> 28;  
  if (fp_hn < 3 || !inRVMAddressSpace(localFrameAddress)) 
    {
      write (SysErrorFd, buf,
             sprintf (buf,
                   "invalid frame address %x (not an address - high nibble %d)\n", localFrameAddress, fp_hn) );
      exit (1);
    }
  }


  int HardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
  unsigned int javaExceptionHandlerAddress = 
		*(unsigned int *) (localJTOC + bootRecord->deliverHardwareExceptionOffset);

  DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

  /* get the thread id */
  int threadID = *(int *) (localVirtualProcessorAddress + VM_Processor_threadId_offset);
  /* get the index of the thread in the threads array multiplied by the array element
     size (4) */
  int threadIndex = threadID >> (VM_ThinLockConstants_TL_THREAD_ID_SHIFT - 2);

  /* get the address of the threads array  */
  unsigned int threadsArrayAddress =
		*(unsigned int *) (localJTOC + bootRecord->threadsOffset);
  /* the thread object itself */
  unsigned int threadObjectAddress = 
		*(unsigned int *) (threadsArrayAddress + threadIndex);
  unsigned int registers = 
		*(unsigned int *) (threadObjectAddress +
			       VM_Thread_hardwareExceptionRegisters_offset);

  /* get the addresses of the gps and other fields in the VM_Registers object */
  unsigned *vmr_gprs  = *(unsigned **) ((char *) registers + VM_Registers_gprs_offset);
  unsigned *vmr_ip    =  (unsigned *)  ((char *) registers + VM_Registers_ip_offset);
  unsigned *vmr_fp    =  (unsigned *)  ((char *) registers + VM_Registers_fp_offset);
  unsigned *vmr_inuse =  (unsigned *)  ((char *) registers + VM_Registers_inuse_offset);

  long unsigned int *sp;
  long unsigned int *fp;

  /* test for recursive errors- if so, take one final stacktrace and exit */
  if (*vmr_inuse || !isRecoverable) {
    if (*vmr_inuse)
      fprintf (SysTraceFile, 
	       "vm: internal error: recursive use of hardware exception registers (exiting)\n");
    /*
     * Things went badly wrong, so attempt to generate a useful error dump
     * before exiting by returning to VM_Scheduler.dumpStackAndDie passing it the fp
     * of the offending thread.
     * We could try to continue, but sometimes doing so results in cascading failures
     * and it's hard to tell what the real problem was.
     */
    int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);

    /* setup stack frame to contain the frame pointer */
    sp = (long unsigned int *) gregs[REG_ESP];

    /* put fp as a  parameter on the stack  */
    gregs[REG_ESP] = gregs[REG_ESP] - 4;
    sp = (long unsigned int *) gregs[REG_ESP];
    *sp = localFrameAddress;
    gregs[REG_EAX] = localFrameAddress; // must pass localFrameAddress in first param register!

    /* put a return address of zero on the stack */
    gregs[REG_ESP] = gregs[REG_ESP] - 4;
    sp = (long unsigned int *) gregs[REG_ESP];
    *sp = 0;

    /* set up to goto dumpStackAndDie routine ( in VM_Scheduler) as if called */
    gregs[REG_EIP] = dumpStack;
    *vmr_inuse = false;

    pthread_mutex_unlock( &exceptionLock );

    return;
  }

  *vmr_inuse = 1;			/* mark in use to avoid infinite loop */

  /* move gp registers to VM_Registers object */
  vmr_gprs[VM_Constants_EAX] = gregs[REG_EAX];
  vmr_gprs[VM_Constants_ECX] = gregs[REG_ECX];
  vmr_gprs[VM_Constants_EDX] = gregs[REG_EDX];
  vmr_gprs[VM_Constants_EBX] = gregs[REG_EBX];
  vmr_gprs[VM_Constants_ESP] = gregs[REG_ESP];
  vmr_gprs[VM_Constants_EBP] = gregs[REG_EBP];
  vmr_gprs[VM_Constants_ESI] = gregs[REG_ESI];
  vmr_gprs[VM_Constants_EDI] = gregs[REG_EDI];

  /* set the next instruction for the failing frame */
  instructionFollowing = getInstructionFollowing(localInstructionAddress);


  /* 
   * advance ESP to the guard region of the stack. 
   * enables opt compiler to have ESP point to somewhere 
   * other than the bottom of the frame at a PEI (see bug 2570).
   * We'll execute the entire code sequence for VM_Runtime.deliverHardwareException et al
   * in the guard region of the stack to avoid bashing stuff in the bottom opt-frame.
   */
  sp = (long unsigned int *) gregs[REG_ESP];
  unsigned stackLimit = *(unsigned *)(threadObjectAddress + VM_Thread_stackLimit_offset);
  if ((long unsigned)sp <= stackLimit - 384) {
    write (SysErrorFd, buf,
	   sprintf (buf,
		    "sp too far below stackLimit to recover\n"));
      exit (2);
  }
  sp = (long unsigned int *)stackLimit - 384;
  stackLimit -= VM_Constants_STACK_SIZE_GUARD;
  *(unsigned *)(threadObjectAddress + VM_Thread_stackLimit_offset) = stackLimit;
  *(unsigned *)(gregs[REG_ESI] + VM_Processor_activeThreadStackLimit_offset) = stackLimit;
  
  /* Insert artificial stackframe at site of trap. */
  /* This frame marks the place where "hardware exception registers" were saved. */
  sp = (long unsigned int *) ((char *) sp - VM_Constants_STACKFRAME_HEADER_SIZE);
  fp = (long unsigned int *) ((char *) sp - 4 - VM_Constants_STACKFRAME_BODY_OFFSET);	/*  4 = wordsize  */

  /* fill in artificial stack frame */
  *(int *) ((char *) fp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET) = localFrameAddress;
  *(int *) ((char *) fp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET) = HardwareTrapMethodId;
  *(int *) ((char *) fp + VM_Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = instructionFollowing;

  /* fill in call to "deliverHardwareException" */
  sp = (long unsigned int *) ((char *) sp - 4);	/* first parameter is type of trap */

  if (signo == SIGSEGV) {
    *(int *) sp = VM_Runtime_TRAP_NULL_POINTER;

    /* an int immediate instruction produces a SIGSEGV signal.  An int 3 instruction a
       trap fault */
    if (*(unsigned char *)(localInstructionAddress) == 0xCD) {
      // is INT imm instruction
      unsigned char code = *(unsigned char*)(localInstructionAddress+1);
      code -= VM_Constants_RVM_TRAP_BASE;
      *(int *) sp = code;
    }
  }

  else if (signo == SIGFPE)
  {
    *(int *) sp = VM_Runtime_TRAP_DIVIDE_BY_ZERO;
  }

  else if (signo == SIGTRAP) {
    *(int *) sp = VM_Runtime_TRAP_UNKNOWN;

    //fprintf(SysTraceFile, "op code is 0x%x",*(unsigned char *)(localInstructionAddress));
    //fprintf(SysTraceFile, "next code is 0x%x",*(unsigned char *)(localInstructionAddress+1));
    if (*(unsigned char *)(localInstructionAddress - 1) == 0xCC) {
      // is INT 3 instruction
    }

  }

  else {
      *(int *) sp = VM_Runtime_TRAP_UNKNOWN;
  }

  gregs[REG_EAX] = *(int *)sp; // also pass first param in EAX.
  if (lib_verbose)
    fprintf(SysTraceFile, "Trap code is 0x%x\n", gregs[REG_EAX]);

  sp = (long unsigned int *) ((char *) sp - 4);	/* next parameter is info for array bounds trap */
  *(int *) sp = *(unsigned *) (localVirtualProcessorAddress + VM_Processor_arrayIndexTrapParam_offset);
  gregs[REG_EDX] = *(int *)sp; // also pass second param in EDX.
  sp = (long unsigned int *) ((char *) sp - 4);	/* return address - looks like called from failing instruction */
  *(int *) sp = instructionFollowing;

  /* store instructionFollowing and fp in VM_Registers,ip and VM_Registers.fp */
  *vmr_ip = instructionFollowing;
  *vmr_fp = localFrameAddress;

  if (lib_verbose)
    fprintf(SysTraceFile, "Set vmr_fp to 0x%x\n", localFrameAddress);
  
  /* set up context block to look like the artificial stack frame is returning  */
  gregs[REG_ESP] = (int) sp;
  gregs[REG_EBP] = (int) fp;
  *(unsigned int *) (localVirtualProcessorAddress + VM_Processor_framePointer_offset) = (int) fp;

  /* setup to return to deliver hardware exception routine */
  gregs[REG_EIP] = javaExceptionHandlerAddress;

  #ifdef DEBUG_TRAP_HANDLER
  debug_in_use = 0;
  #endif

  pthread_mutex_unlock( &exceptionLock );

}



// #define ANNOUNCE_TICK(message) write(SysTraceFd, message, strlen(message))
// #define ANNOUNCE(message) write(SysTraceFd, message, strlen(message))
   #define ANNOUNCE(message)
   #define ANNOUNCE_TICK(message)

extern "C" void processTimerTick() {
  void *bootRegion = (void *) bootImageAddress;
  VM_BootRecord *bootRecord = (VM_BootRecord *) bootRegion;

  /*
   * Turn on thread-switch flag in each virtual processor.
   * Note that "jtoc" is not necessairly valid, because we might have interrupted
   * C-library code, so we use boot image jtoc address (== VmToc) instead.
   * !!TODO: if vm moves table, it must tell us so we can update "VmToc".
   * For now, we assume table is fixed in boot image and never moves.
   */
  unsigned *processors = *(unsigned **) ((char *) VmToc + ProcessorsOffset);
  unsigned cnt = processors[-1];

  int epoch = *(int *) ((char *) VmToc + VM_Processor_epoch_offset);
  *(int *) ((char *) VmToc + VM_Processor_epoch_offset) = epoch + 1;

  /* with new (7/2001) threading the last processor entry is for 
   * the NativeDaemonThread
   * and may be null (IA32 without pthreads or if compiled for 
   * a single processor),
   * so allow for possibility of a null entry in the processors array
   */
  int     i;

  // line added here - ndp is now the last processor - and cnt includes it
  cnt = cnt - 1;
  // check for gc in progress: if so, return
  //
  if (bootRecord -> lockoutProcessor == 0x0CCCCCCC) return;
  
  int       val;
  int       sendit = 0;
  int       MISSES = -2;     // tuning parameter
  for (i = VM_Scheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt ; ++i) {
    val = *(int *)((char *)processors[i] + 
		   VM_Processor_threadSwitchRequested_offset);
    if (val <= MISSES) sendit++;
    *(int *)((char *)processors[i] + 
	     VM_Processor_threadSwitchRequested_offset) = val - 1;
  }
  if (sendit != 0) {
    // some processor "stuck in native"
    if (processors[i] != 0 /*null*/ ) {  
      // have a NativeDaemon Processor (the last one) and can use it to recover
      int pthread_id = *(int *)((char *)processors[i] + VM_Processor_pthread_id_offset) ;
#ifdef __linuxsmp__
      pthread_t thread = (pthread_t)pthread_id;
      int i_thread = (int)thread;
      pthread_kill(thread, SIGCONT);
#endif
    } else {
      if (val <= -25) {
	fprintf(stderr, "WARNING: Virtual processor has ignored timer interrupt for %d ms.\n", 10 * (-val));
	fprintf(stderr, "This may indicate that a blocking system call has occured and the JVM is deadlocked\n");
      }
    }
  }
}


void softwareSignalHandler (int signo, siginfo_t * si, void *context) {

#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
  if (signo == SIGALRM) {	/* asynchronous signal used for time slicing */
    processTimerTick();
  }
  else
#endif

  if (signo == SIGQUIT)
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
      }

  else if (signo == SIGTERM) {
      // Presumably we received this signal because someone wants us
      // to shut down.  Exit directly (unless the lib_verbose flag is set).
      if (!lib_verbose)
	_exit(1);

      DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

      unsigned int localJTOC = VmToc;
      ucontext_t *uc = (ucontext_t *) context;  // user context
      mcontext_t *mc = &(uc->uc_mcontext);      // machine context
      greg_t  *gregs = mc->gregs;              // general purpose registers
      int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);
   
      /* get the frame pointer from processor object  */
      unsigned int localVirtualProcessorAddress	= gregs[REG_ESI];
      unsigned int localFrameAddress = 
	  *(unsigned *) (localVirtualProcessorAddress + VM_Processor_framePointer_offset);

      /* setup stack frame to contain the frame pointer */
      long unsigned int *sp = (long unsigned int *) gregs[REG_ESP];

      /* put fp as a  parameter on the stack  */
      gregs[REG_ESP] = gregs[REG_ESP] - 4;
      sp = (long unsigned int *) gregs[REG_ESP];
      *sp = localFrameAddress;
      // must pass localFrameAddress in first param register!
      gregs[REG_EAX] = localFrameAddress;

      /* put a return address of zero on the stack */
      gregs[REG_ESP] = gregs[REG_ESP] - 4;
      sp = (long unsigned int *) gregs[REG_ESP];
      *sp = 0;

      /* goto dumpStackAndDie routine (in VM_Scheduler) as if called */
      gregs[REG_EIP] = dumpStack;
  }
}

/* startup configuration option with default values */
char *bootFilename = 0;
int verboseGC = 0;
unsigned smallHeapSize = 20 * 1024 * 1024;	/* megs */
unsigned largeHeapSize = 10 * 1024 * 1024;	/* megs */

unsigned nurserySize = 10 * 1024 * 1024;	/* megs */
unsigned permanentHeapSize = 0;

/* timer tick interval, in milliseconds     (10 <= delay <= 999) */
static int TimerDelay = 10;

int
createJVM (int vmInSeparateThread)
{
  SysErrorFile = stderr;
  SysErrorFd = 2;
  SysTraceFile = stderr;
  SysTraceFd = 2;

  /* don't buffer trace or error message output */
  setbuf (SysErrorFile, 0);
  setbuf (SysTraceFile, 0);

  if (lib_verbose)
  {
  fprintf(SysTraceFile, "IA32 linux build");
  #if (defined __linuxsmp__)
    fprintf(SysTraceFile, " for SMP\n");
  #else
    fprintf(SysTraceFile, "\n");
  #endif
  }

  /* open and mmap the image file. 
   * create bootRegion
   */
  FILE *fin = fopen (bootFilename, "r");
  if (!fin) {
    fprintf (SysTraceFile, "%s: can't find bootimage \"%s\"\n", me, bootFilename);
    return 1;
  }

  /* measure image size */
  if (lib_verbose)
    fprintf (SysTraceFile, "%s: loading from \"%s\"\n", me, bootFilename);
  fseek (fin, 0L, SEEK_END);
   unsigned actualImageSize = ftell(fin);
   unsigned roundedImageSize = pageRoundUp(actualImageSize);
  fseek (fin, 0L, SEEK_SET);


  void *bootRegion = 0;
  // allocate region 1 and 2
  bootRegion = mmap ((void *) bootImageAddress, roundedImageSize,
		     PROT_READ | PROT_WRITE | PROT_EXEC,
		     MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED, -1, 0);

  if (bootRegion == (void *) -1) {
    fprintf (SysErrorFile, "%s: mmap failed (errno=%d)\n", me, errno);
    return 1;
  }

  /* read image into memory segment */
  int cnt = fread (bootRegion, 1, actualImageSize, fin);
  if (cnt != actualImageSize) {
    fprintf (SysErrorFile, "%s: read failed (errno=%d)\n", me, errno);
    return 1;
  }

  if (actualImageSize % 4 != 0) {
    fprintf (SysErrorFile, "%s: image format error: image size (%d) is not a word multiple\n",
			me, actualImageSize);
    return 1;
  }

  if (fclose (fin) != 0) {
    fprintf (SysErrorFile, "%s: close failed (errno=%d)\n", me, errno);
    return 1;
  }


  /* validate contents of boot record */
  bootRecord = (VM_BootRecord *) bootRegion;

  if (bootRecord->bootImageStart != (int) bootRegion) {
    fprintf (SysErrorFile, "%s: image load error: built for 0x%08x but loaded at 0x%08x\n",
		me, bootRecord->bootImageStart, bootRegion);
    return 1;
  }

  if ((bootRecord->spRegister % 4) != 0) {
    fprintf (SysErrorFile, "%s: image format error: sp (0x%08x) is not word aligned\n",
		me, bootRecord->spRegister);
    return 1;
  }

  if ((bootRecord->ipRegister % 4) != 0) {
    fprintf (SysErrorFile, "%s: image format error: ip (0x%08x) is not word aligned\n",
		me, bootRecord->ipRegister);
    return 1;
  }

  if (((int *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
    fprintf (SysErrorFile,
	"%s: image format error: missing stack sanity check marker (0x%08x)\n",
	me, ((int *) bootRecord->spRegister)[-1]);
    return 1;
  }

  /* remember jtoc location for later use by trap handler */
  VmToc = bootRecord->tocRegister;

  /* get and remember JTOC offset of VM_Scheduler.processors[] */
  ProcessorsOffset = bootRecord->processorsOffset;

  // remember JTOC offset of VM_Scheduler.DebugRequested
  // 
  DebugRequestedOffset = bootRecord->debugRequestedOffset;

  /* write freespace information into boot record */
   bootRecord->verboseGC        = verboseGC;
   bootRecord->nurserySize      = nurserySize;
   bootRecord->smallSpaceSize   = smallHeapSize;
   bootRecord->largeSpaceSize   = largeHeapSize;
   bootRecord->bootImageStart   = (int) bootRegion;
   bootRecord->bootImageEnd     = (int) bootRegion + roundedImageSize;
  
   bootRecord->traceClassLoading = traceClassLoading;

  /* write sys.C linkage information into boot record */
  setLinkage(bootRecord);
  if (lib_verbose) {
    fprintf (SysTraceFile, "%s: boot record contents:\n", me);
    fprintf (SysTraceFile, "   bootImageStart:       0x%08x\n", bootRecord->bootImageStart);
    fprintf (SysTraceFile, "   bootImageEnd:         0x%08x\n", bootRecord->bootImageEnd);
    fprintf (SysTraceFile, "   smallSpaceSize:       0x%08x\n", bootRecord->smallSpaceSize);
    fprintf (SysTraceFile, "   largeSpaceSize:       0x%08x\n", bootRecord->largeSpaceSize);
    fprintf (SysTraceFile, "   nurserySize:          0x%08x\n", bootRecord->nurserySize);
    fprintf (SysTraceFile, "   tiRegister:           0x%08x\n", bootRecord->tiRegister);
    fprintf (SysTraceFile, "   spRegister:           0x%08x\n", bootRecord->spRegister);
    fprintf (SysTraceFile, "   ipRegister:           0x%08x\n", bootRecord->ipRegister);
    fprintf (SysTraceFile, "   tocRegister:          0x%08x\n", bootRecord->tocRegister);
    fprintf (SysTraceFile, "   sysWriteCharIP:       0x%08x\n", bootRecord->sysWriteCharIP);
    fprintf (SysTraceFile, "   ...etc...                   \n");
  }

  /* install a stack for hardwareTrapHandler() to run on */
  stack_t stack;
   
  memset (&stack, 0, sizeof stack);
  stack.ss_sp = new char[SIGSTKSZ];
   
  stack.ss_size = SIGSTKSZ;
  if (sigaltstack (&stack, 0)) {
    fprintf (SysErrorFile, "%s: sigaltstack failed (errno=%d)\n", me, errno);
    return 1;
  }

  /* install hardware trap signal handler */
  struct sigaction action;

  memset (&action, 0, sizeof action);
  action.sa_sigaction = &hardwareTrapHandler;
  /*
   * mask all signal from reaching the signal handler while the signal
   * handler is running
   */
  if (sigfillset(&(action.sa_mask))) {
    fprintf (SysErrorFile, "%s: sigfillset failed (errno=%d)\n", me, errno);
    return 1;
  }
  /*
   * exclude the signal used to wake up the daemons
   */
  if (sigdelset(&(action.sa_mask), SIGCONT)) {
    fprintf (SysErrorFile, "%s: sigdelset failed (errno=%d)\n", me, errno);
    return 1;
  }

  action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
  if (sigaction (SIGSEGV, &action, 0)) {
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }
  if (sigaction (SIGFPE, &action, 0)) {
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }
  if (sigaction (SIGTRAP, &action, 0)) {
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }

  /* install software signal handler */
  action.sa_sigaction = &softwareSignalHandler;
  if (sigaction (SIGALRM, &action, 0)) {	/* catch timer ticks (so we can timeslice user level threads) */
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }
  if (sigaction (SIGQUIT, &action, 0)) {	/* catch QUIT to invoke debugger thread */
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }
  if (sigaction (SIGTERM, &action, 0)) {	/* catch TERM to dump and die */
    fprintf (SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
    return 1;
  }

  // Ignore "write (on a socket) with nobody to read it" signals so
  // that sysWriteBytes() will get an EPIPE return code instead of trapping.
  //
  memset (&action, 0, sizeof action);
  action.sa_handler = SIG_IGN;
  if (sigaction(SIGPIPE, &action, 0))
     {
     fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", me, errno);
     return 1;
     }

  /* set up initial stack frame */
  int ip   = bootRecord->ipRegister;
  int jtoc = bootRecord->tocRegister;
  int pr;
  int *sp  = (int *) bootRecord->spRegister;
  {
  unsigned *processors = *(unsigned **)(bootRecord->tocRegister +
                                        bootRecord->processorsOffset);
  pr       = processors[VM_Scheduler_PRIMORDIAL_PROCESSOR_ID];

  /* initialize the thread id jtoc, and framepointer fields in the primordial
   * processor object.
   */
  *(unsigned int *) (pr + VM_Processor_threadId_offset) =
		VM_Scheduler_PRIMORDIAL_THREAD_INDEX << VM_ThinLockConstants_TL_THREAD_ID_SHIFT;
  *(unsigned int *) (pr + VM_Processor_jtoc_offset) = jtoc;
  *(unsigned int *) (pr + VM_Processor_framePointer_offset) = (int)sp - 8;
  } 

  *--sp = 0xdeadbabe;		/* STACKFRAME_RETURN_ADDRESS_OFFSET */
  *--sp = VM_Constants_STACKFRAME_SENTINAL_FP;	/* STACKFRAME_FRAME_POINTER_OFFSET */
  *--sp = VM_Constants_INVISIBLE_METHOD_ID;	/* STACKFRAME_METHOD_ID_OFFSET */
  *--sp = 0;			/* STACKFRAME_NEXT_INSTRUCTION_OFFSET (for AIX compatability) */

  // fprintf (SysTraceFile, "%s: here goes...\n", me);
  int rc = boot (ip, jtoc, pr, (int) sp);

  fprintf (SysTraceFile, "%s: we're back! rc=%d. bye.\n", me, rc);
  return 1;
}


// Get address of JTOC.
extern "C" void *getJTOC() {
  return (void*) VmToc;
}

// Get offset of VM_Scheduler.processors in JTOC.
extern "C" int getProcessorsOffset() {
  return ProcessorsOffset;
}
