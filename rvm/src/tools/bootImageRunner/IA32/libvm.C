/*
 * (C) Copyright IBM Corp 2001,2002, 2003
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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE		// so that string.h will include strsignal()
#endif
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <sys/mman.h>
#include <errno.h>
#include <string.h>
#include <stdarg.h>		// va_list, va_start(), va_end()
#ifndef __USE_GNU         // Deal with current ucontext ugliness.  The current
#define __USE_GNU         // mess of asm/ucontext.h & sys/ucontext.h and their
#include <sys/ucontext.h> // mutual exclusion on more recent versions of gcc
#undef __USE_GNU          // dictates this ugly hack.
#else
#include <sys/ucontext.h>
#endif
#include <assert.h>

#define __STDC_FORMAT_MACROS	// include PRIxPTR
#include <inttypes.h>		// PRIxPTR, uintptr_t

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
/* OK, here's the scoop with pthreads.  The GNU C library will provide us with
 * the pthread_mutex_lock and pthread_mutex_unlock implementations, WITHOUT
 * linking with -lpthread.    But the C library (libc) does not contain an
 * implementation of pthread_kill().  That's why we can get away with using
 * some of the pthread functions in a single-processor config, but not get
 * away with all of them. */
#include <pthread.h>
#endif

/* Interface to virtual machine data structures. */
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include <InterfaceDeclarations.h>

extern "C" void setLinkage(VM_BootRecord*);

#include "../bootImageRunner.h"	// In rvm/src/tools/bootImageRunner

// These are definitions of items declared in bootImageRunner.h
/* Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile = stderr;
static int SysErrorFd = 2;		// not used outside this file.
 
/* Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile = stderr;
int SysTraceFd = 2;

/* Command line arguments to be passed to virtual machine. */
char **JavaArgs;
int JavaArgc;

/* global; startup configuration option with default values */
const char *bootFilename = 0;

/* Emit trace information? */
int lib_verbose = 0;

/* Location of jtoc within virtual machine image. */
static unsigned VmToc;

/* TOC offset of VM_Scheduler.dumpStackAndDie */
static int DumpStackAndDieOffset;

/* TOC offset of VM_Scheduler.processors[] */
static int ProcessorsOffset;

/* TOC offset of VM_Scheduler.debugRequested */
static int DebugRequestedOffset;

/* name of program that will load and run RVM */
char *Me;

static VM_BootRecord *bootRecord;

static void vwriteFmt(int fd, const char fmt[], va_list ap)
    NONNULL(2) __attribute__((format (printf, 2, 0)));
static void vwriteFmt(int fd, size_t bufsz, const char fmt[], va_list ap)
    NONNULL(3) __attribute__((format (printf, 3, 0)));
#if 0				// this isn't needed right now, but may be in
				// the future.
static void writeTrace(const char fmt[], ...)
    NONNULL(1) __attribute__((format (printf, 1, 2)));
#endif
static void writeErr(const char fmt[], ...)
    NONNULL(1) __attribute__((format (printf, 1, 2)));


static int 
pageRoundUp(int size) 
{
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
getInstructionFollowing(unsigned int faultingInstructionAddress)
{
    int Illegal = 0;
    char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256];
    //, AddrBuffer[256];
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

static int 
inRVMAddressSpace(unsigned int addr) 
{
    /* get the boot record */
    VM_Address *heapRanges = bootRecord->heapRanges;
    int MaxHeaps = 10;  // update to match VM_Heap.MAX_HEAPS  XXXXX

    for (int which = 0; ; which++) {
	assert(which <= 2 * (MaxHeaps + 1));
	VM_Address start = heapRanges[2 * which];
	VM_Address end = heapRanges[2 * which + 1];
	if (start == ~(VM_Address) 0 && end == ~ (VM_Address) 0) break;
	if (start <= addr  && addr  < end)
	    return true;
    }
	
    return false;
}

static int 
isVmSignal(unsigned int ip, unsigned int jtoc) 
{
    return inRVMAddressSpace(ip) && inRVMAddressSpace(jtoc);
}

#if 0				// this isn't needed right now, but may be in
				// the future.
static void
writeTrace(const char fmt[], ...)
{
    va_list ap;
    va_start(ap, fmt);
    vwriteFmt(SysTraceFd, fmt, ap);
    va_end(ap);
}
#endif


static void
writeErr(const char fmt[], ...)
{
    va_list ap;
    va_start(ap, fmt);
    vwriteFmt(SysErrorFd, fmt, ap);
    va_end(ap);
}

static void
vwriteFmt(int fd, const char fmt[], va_list ap)
{
    vwriteFmt(fd, 100, fmt, ap);
}


/* Does the real work. */
static void
vwriteFmt(int fd, size_t bufsz, const char fmt[], va_list ap)
{
    char buf[bufsz];
    int nchars
	= vsnprintf(buf, bufsz, fmt, ap);
    size_t count;
    if (nchars < 0) {
	const char badmsg[] = "vsnprintf() returned a negative number;  impossible (or very old C library version)\n";
	write(fd, badmsg, sizeof badmsg - 1);
	nchars = sizeof buf - 1;
    }
    if ((unsigned) nchars >= bufsz) {
	vwriteFmt(fd, bufsz + 100, fmt, ap);
	return;
    }
    count = nchars;
    char *bp = buf;
    ssize_t nwrote = 0;
    bool num_rerun = 0;

    while (nchars > 0) {
	nwrote = write(fd, buf, nchars);
	if (nwrote < 0) {
	    /* XXX We should handle this in some intelligent fashion.   But
	     * how?  Do we have any means of complaining left? */
	    if (errno == EINTR)
		continue;
	    // Any other way to handle the rest?
	    break;
	}
	if (nwrote == 0) {
	    if (++num_rerun > 20) {
		break;		// too many reruns.  Any other error indicator?
	    }
	    continue;
	}
	num_rerun = 0;		// we made progress.
	bp += nwrote;
	nchars -= nwrote;
    }	
}




void
hardwareTrapHandler(int signo, siginfo_t *si, void *context)
{
    unsigned int localInstructionAddress;
#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    static pthread_mutex_t exceptionLock = PTHREAD_MUTEX_INITIALIZER;

    pthread_mutex_lock( &exceptionLock );
#endif // RVM_FOR_SINGLE_VIRTUAL_PROCESSOR

    unsigned int localVirtualProcessorAddress;
    unsigned int localFrameAddress;
    unsigned int localJTOC = VmToc;

    int isRecoverable;

    unsigned int instructionFollowing;

    ucontext_t *uc = (ucontext_t *) context;  // user context
    mcontext_t *mc = &(uc->uc_mcontext);      // machine context
    greg_t  *gregs = mc->gregs;               // general purpose registers

    /*
     * fill local working variables from context saved by OS trap handler
     * mechanism
     */
    localInstructionAddress	= gregs[REG_EIP];
    localVirtualProcessorAddress = gregs[REG_ESI];

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
	else
	    writeErr("%s: WHOOPS.  Got a signal (%s; #%d) that the hardware signal handler wasn't prepared for.\n", Me,  strsignal(signo), signo);
    } else {
	writeErr("%s: TROUBLE.  Got a signal (%s; #%d) from outside the VM's address space.\n", Me,  strsignal(signo), signo);
    }
    



    if (lib_verbose || !isRecoverable)
    {
	writeErr("%s:%s trapped signal %d (%s)\n", Me, 
		 isRecoverable? "" : " UNRECOVERABLE", 
		 signo, strsignal(signo));

 	writeErr("handler stack 0x%x\n", (unsigned) &localInstructionAddress);
	if (signo == SIGSEGV)
	    writeErr("si->si_addr   0x%08x\n", (unsigned) si->si_addr);
	writeErr("gs            0x%08x\n", gregs[REG_GS]);
	writeErr("fs            0x%08x\n", gregs[REG_FS]);
	writeErr("es            0x%08x\n", gregs[REG_ES]);
	writeErr("ds            0x%08x\n", gregs[REG_DS]);
	writeErr("edi -- JTOC?  0x%08x\n", gregs[REG_EDI]);
	writeErr("esi -- PR/VP  0x%08x\n", gregs[REG_ESI]);
	writeErr("ebp -- FP?    0x%08x\n", gregs[REG_EBP]);
	writeErr("esp -- SP     0x%08x\n", gregs[REG_ESP]);
	writeErr("ebx           0x%08x\n", gregs[REG_EBX]);
	writeErr("edx -- T1?    0x%08x\n", gregs[REG_EDX]);
	writeErr("ecx -- S0?    0x%08x\n", gregs[REG_ECX]);
	writeErr("eax -- T0?    0x%08x\n", gregs[REG_EAX]);
	writeErr("trapno        0x%08x\n", gregs[REG_TRAPNO]);
	writeErr("err           0x%08x\n", gregs[REG_ERR]);
	writeErr("eip           0x%08x\n", gregs[REG_EIP]);
	writeErr("cs            0x%08x\n", gregs[REG_CS]);
	writeErr("eflags        0x%08x\n", gregs[REG_EFL]);
	writeErr("esp_at_signal 0x%08x\n", gregs[REG_UESP]);
	writeErr("ss            0x%08x\n", gregs[REG_SS]);
/* null if fp registers haven't been used yet */
	writeErr("fpstate       0x%08x\n",
					(unsigned) mc->fpregs);
	writeErr("oldmask       0x%08lx\n",
					(unsigned long) mc->oldmask);
	writeErr("cr2           0x%08lx\n", 
					/* seems to contain mem address that
					 * faulting instruction was trying to
					 * access */  
					(unsigned long) mc->cr2);	

	/*
	 * There are 8 floating point registers, each 10 bytes wide.
	 * See /usr/include/asm/sigcontext.h
	 */
	if (mc->fpregs) {
	    struct _libc_fpreg *fpreg = mc->fpregs->_st;

	    for (int i = 0; i < 8; ++i) {
		writeErr("fp%d 0x%04x%04x%04x%04x%04x\n",
				i,
				fpreg[i].significand[0] & 0xffff,
				fpreg[i].significand[1] & 0xffff,
				fpreg[i].significand[2] & 0xffff,
				fpreg[i].significand[3] & 0xffff,
				fpreg[i].exponent & 0xffff);
	    }
	}

	if (isRecoverable) {
	    fprintf(SysTraceFile, "%s: normal trap\n", Me);
	} else {
	    fprintf(SysTraceFile, "%s: internal error\n", Me);
	}
    }

    /* test validity of virtual processor address */
    {
	unsigned int vp_hn;  /* the high nibble of the vp address value */
	vp_hn = localVirtualProcessorAddress >> 28;  
	if (vp_hn < 3 || !inRVMAddressSpace(localVirtualProcessorAddress)) 
	{
	    writeErr("invalid vp address (not an address - high nibble %d)\n", 
		     vp_hn);
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
	    writeErr("invalid frame address %x"
	    " (not an address - high nibble %d)\n", 
				 localFrameAddress, fp_hn);
	    exit(-1);
	}
    }


    int HardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
    unsigned int javaExceptionHandlerAddress = 
	*(unsigned int *) (localJTOC + bootRecord->deliverHardwareExceptionOffset);

    DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

    /* get the active thread id */
    unsigned int threadObjectAddress = 
        *(unsigned int*) (localVirtualProcessorAddress + VM_Processor_activeThread_offset);

    /* then get its hardware exception registers */
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

    /* Test for recursive errors -- if so, take one final stacktrace and exit
     */ 
    if (*vmr_inuse || !isRecoverable) {
	if (*vmr_inuse)
	    fprintf(SysTraceFile, 
		     "%s: internal error: recursive use of"
		    " hardware exception registers (exiting)\n", Me);
	/*
	 * Things went badly wrong, so attempt to generate a useful error dump
	 * before exiting by returning to VM_Scheduler.dumpStackAndDie passing
	 * it the fp of the offending thread.
	 *
	 * We could try to continue, but sometimes doing so results 
	 * in cascading failures
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

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
	pthread_mutex_unlock( &exceptionLock );
#endif

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
     * Advance ESP to the guard region of the stack. 
     * Enables opt compiler to have ESP point to somewhere 
     * other than the bottom of the frame at a PEI (see bug 2570).
     *
     * We'll execute the entire code sequence for
     * VM_Runtime.deliverHardwareException et al. in the guard region of the
     * stack to avoid bashing stuff in the bottom opt-frame. 
     */
    sp = (long unsigned int *) gregs[REG_ESP];
    uintptr_t stackLimit
	= *(unsigned *)(threadObjectAddress + VM_Thread_stackLimit_offset);
    if ((uintptr_t) sp <= stackLimit - 384) {
	writeErr("sp (0x%08" PRIxPTR ")too far below stackLimit (0x%08" PRIxPTR ")to recover\n", (uintptr_t) sp, stackLimit);
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
    *(int *) ((char *) fp + VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET)
	= localFrameAddress;
    *(int *) ((char *) fp + VM_Constants_STACKFRAME_METHOD_ID_OFFSET) 
	= HardwareTrapMethodId;
    *(int *) ((char *) fp + VM_Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) 
	= instructionFollowing;

    /* fill in call to "deliverHardwareException" */
    sp = (long unsigned int *) ((char *) sp - 4);	/* first parameter is type of trap */

    if (signo == SIGSEGV) {
	*(int *) sp = VM_Runtime_TRAP_NULL_POINTER;

	/* an int immediate instruction produces a SIGSEGV signal.  
	   An int 3 instruction a trap fault */
	if (*(unsigned char *)(localInstructionAddress) == 0xCD) {
	    // is INT imm instruction
	    unsigned char code = *(unsigned char*)(localInstructionAddress+1);
	    code -= VM_Constants_RVM_TRAP_BASE;
	    *(int *) sp = code;
	}
    }

    else if (signo == SIGFPE) {
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
  
    /* set up context block to look like the artificial stack frame is
     * returning  */ 
    gregs[REG_ESP] = (int) sp;
    gregs[REG_EBP] = (int) fp;
    *(unsigned int *) 
	(localVirtualProcessorAddress + VM_Processor_framePointer_offset) 
	= (int) fp;

    /* setup to return to deliver hardware exception routine */
    gregs[REG_EIP] = javaExceptionHandlerAddress;

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    pthread_mutex_unlock( &exceptionLock );
#endif
}


static void 
softwareSignalHandler(int signo, 
		      siginfo_t UNUSED *si, 
		      void *context) 
{
#ifdef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    if (signo == SIGALRM) {	/* asynchronous signal used for time slicing */
	processTimerTick();
	return;
    }
#endif

    // asynchronous signal used to awaken internal debugger
    if (signo == SIGQUIT) { 
	// Turn on debug-request flag.
	// Note that "jtoc" is not necessarily valid, because we might have interrupted
	// C-library code, so we use boot image jtoc address (== VmToc) instead.
	// !!TODO: if vm moves table, it must tell us so we can update "VmToc".
	// For now, we assume table is fixed in boot image and never moves.
	//
	unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
	write(SysTraceFd, Me, strlen(Me));
	if (*flag) {
	    static const char *message = ": debug request already in progress, please wait\n";
	    write(SysTraceFd, message, strlen(message));
	} else {
	    static const char *message = ": debug requested, waiting for a thread switch\n";
	    write(SysTraceFd, message, strlen(message));
	    *flag = 1;
	}
	return;
    }

    if (signo == SIGTERM) {
	// Presumably we received this signal because someone wants us
	// to shut down.  Exit directly (unless the lib_verbose flag is set).
	if (!lib_verbose)
	    _exit(-1);

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
	return;
    }

    /* Default case. */
    fprintf(SysTraceFile, "%s: got an unexpected software signal (# %d)", Me, signo);
#if defined __GLIBC__ && defined _GNU_SOURCE
    fprintf(SysTraceFile, " %s", strsignal(signo));
#endif    
    fprintf(SysTraceFile, "; ignoring it.\n");
}


/* Returns 1 upon any errors; the only time it can return is if there's an
   error. */
int
createJVM(int UNUSED vmInSeparateThread)
{
    /* don't buffer trace or error message output */
    setbuf (SysErrorFile, 0);
    setbuf (SysTraceFile, 0);

    if (lib_verbose)
    {
	fprintf(SysTraceFile, "IA32 linux build");
#ifdef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
	fprintf(SysTraceFile, " for single virtual processor\n");
#else
	fprintf(SysTraceFile, " for SMP\n");
#endif
    }

    /* open and mmap the image file. 
     * create bootRegion
     */
    FILE *fin = fopen (bootFilename, "r");
    if (!fin) {
	fprintf(SysTraceFile, "%s: can't find bootimage \"%s\"\n", Me, bootFilename);
	return 1;
    }

    /* measure image size */
    if (lib_verbose)
	fprintf(SysTraceFile, "%s: loading from \"%s\"\n", Me, bootFilename);
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
	fprintf(SysErrorFile, "%s: mmap failed (errno=%d)\n", Me, errno);
	return 1;
    }

    /* read image into memory segment */
    int cnt = fread (bootRegion, 1, actualImageSize, fin);
    if (cnt < 0 || (unsigned) cnt != actualImageSize) {
	fprintf(SysErrorFile, "%s: read failed (errno=%d)\n", Me, errno);
	return 1;
    }

    if (actualImageSize % 4 != 0) {
	fprintf(SysErrorFile, "%s: image format error: image size (%d) is not a word multiple\n",
		 Me, actualImageSize);
	return 1;
    }

    if (fclose (fin) != 0) {
	fprintf(SysErrorFile, "%s: close failed (errno=%d)\n", Me, errno);
	return 1;
    }


    /* validate contents of boot record */
    bootRecord = (VM_BootRecord *) bootRegion;

    if (bootRecord->bootImageStart != (unsigned) bootRegion) {
	fprintf(SysErrorFile, "%s: image load error: built for 0x%08x but loaded at 0x%08x\n",
		 Me, bootRecord->bootImageStart, (unsigned) bootRegion);
	return 1;
    }

    if ((bootRecord->spRegister % 4) != 0) {
	fprintf(SysErrorFile, "%s: image format error: sp (0x%08x) is not word aligned\n",
		 Me, bootRecord->spRegister);
	return 1;
    }

    if ((bootRecord->ipRegister % 4) != 0) {
	fprintf(SysErrorFile, "%s: image format error: ip (0x%08x) is not word aligned\n",
		 Me, bootRecord->ipRegister);
	return 1;
    }

    if (((u_int32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
	fprintf(SysErrorFile,
		 "%s: image format error: missing stack sanity check marker (0x%08x)\n",
		 Me, ((int *) bootRecord->spRegister)[-1]);
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
    bootRecord->initialHeapSize  = initialHeapSize;
    bootRecord->maximumHeapSize  = maximumHeapSize;
    bootRecord->bootImageStart   = (int) bootRegion;
    bootRecord->bootImageEnd     = (int) bootRegion + roundedImageSize;
    bootRecord->verboseBoot      = verboseBoot;
  
    /* write sys.C linkage information into boot record */
    setLinkage(bootRecord);
    if (lib_verbose) {
	fprintf(SysTraceFile, "%s: boot record contents:\n", Me);
	fprintf(SysTraceFile, "   bootImageStart:       0x%08x\n", 
		bootRecord->bootImageStart);
	fprintf(SysTraceFile, "   bootImageEnd:         0x%08x\n", 
		bootRecord->bootImageEnd);
	fprintf(SysTraceFile, "   initialHeapSize:      0x%08x\n", 
		bootRecord->initialHeapSize);
	fprintf(SysTraceFile, "   maximumHeapSize:      0x%08x\n", 
		bootRecord->maximumHeapSize);
	fprintf(SysTraceFile, "   tiRegister:           0x%08x\n", 
		bootRecord->tiRegister);
	fprintf(SysTraceFile, "   spRegister:           0x%08x\n", 
		bootRecord->spRegister);
	fprintf(SysTraceFile, "   ipRegister:           0x%08x\n", 
		bootRecord->ipRegister);
	fprintf(SysTraceFile, "   tocRegister:          0x%08x\n", 
		bootRecord->tocRegister);
	fprintf(SysTraceFile, "   sysWriteCharIP:       0x%08x\n", 
		bootRecord->sysWriteCharIP);
	fprintf(SysTraceFile, "   ...etc...                   \n");
    }

    /* install a stack for hardwareTrapHandler() to run on */
    stack_t stack;
   
    memset (&stack, 0, sizeof stack);
    stack.ss_sp = new char[SIGSTKSZ];
   
    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
	fprintf(SysErrorFile, "%s: sigaltstack failed (errno=%d)\n", 
		Me, errno);
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
	fprintf(SysErrorFile, "%s: sigfillset failed (errno=%d)\n", Me, errno);
	return 1;
    }
    /*
     * exclude the signal used to wake up the daemons
     */
    if (sigdelset(&(action.sa_mask), SIGCONT)) {
	fprintf(SysErrorFile, "%s: sigdelset failed (errno=%d)\n", Me, errno);
	return 1;
    }

    action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
    if (sigaction (SIGSEGV, &action, 0)) {
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }
    if (sigaction (SIGFPE, &action, 0)) {
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }
    if (sigaction (SIGTRAP, &action, 0)) {
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }

    /* install software signal handler */
    action.sa_sigaction = &softwareSignalHandler;
    if (sigaction (SIGALRM, &action, 0)) {	/* catch timer ticks (so we can timeslice user level threads) */
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }
    if (sigaction (SIGQUIT, &action, 0)) { /* catch QUIT to invoke debugger
					    * thread */ 
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }
    if (sigaction (SIGTERM, &action, 0)) { /* catch TERM to dump and die */
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }

    // Ignore "write (on a socket) with nobody to read it" signals so
    // that sysWriteBytes() will get an EPIPE return code instead of trapping.
    //
    memset (&action, 0, sizeof action);
    action.sa_handler = SIG_IGN;
    if (sigaction(SIGPIPE, &action, 0)) {
	fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
	return 1;
    }

    /* set up initial stack frame */
    int ip   = bootRecord->ipRegister;
    int jtoc = bootRecord->tocRegister;
    int pr;
    int *sp  = (int *) bootRecord->spRegister;
    {
	unsigned *processors 
	    = *(unsigned **) (bootRecord->tocRegister 
			      + bootRecord->processorsOffset);
	pr	= processors[VM_Scheduler_PRIMORDIAL_PROCESSOR_ID];

	/* initialize the thread id jtoc, and framepointer fields in the primordial
	 * processor object.
	 */
	*(unsigned int *) (pr + VM_Processor_threadId_offset) 
	    = VM_Scheduler_PRIMORDIAL_THREAD_INDEX 
		<< VM_ThinLockConstants_TL_THREAD_ID_SHIFT;
	*(unsigned int *) (pr + VM_Processor_jtoc_offset) = jtoc;
	*(unsigned int *) (pr + VM_Processor_framePointer_offset) 
	    = (int)sp - 8;
    } 

    *--sp = 0xdeadbabe;		/* STACKFRAME_RETURN_ADDRESS_OFFSET */
    *--sp = VM_Constants_STACKFRAME_SENTINEL_FP; /* STACKFRAME_FRAME_POINTER_OFFSET */
    *--sp = VM_Constants_INVISIBLE_METHOD_ID; /* STACKFRAME_METHOD_ID_OFFSET */
    *--sp = 0; /* STACKFRAME_NEXT_INSTRUCTION_OFFSET (for AIX compatability) */

    // fprintf(SysTraceFile, "%s: here goes...\n", Me);
    int rc = boot (ip, jtoc, pr, (int) sp);

    fprintf(SysErrorFile, "%s: CreateJVM(): boot() returned; failed to create a VM.  rc=%d.  Bye.\n", Me, rc);
    return 1;
}


// Get address of JTOC.
extern "C" void *
getJTOC(void) 
{
    return (void*) VmToc;
}

// Get offset of VM_Scheduler.processors in JTOC.
extern "C" int 
getProcessorsOffset(void) 
{
    return ProcessorsOffset;
}
