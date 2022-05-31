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

#include "sys.h"
#include <errno.h>
#include <string.h>

// Jikes RVM's signal handlers do more work than is generally expected
// for signal handlers. For example, when -X:verbose is set, the signal
// handlers will dump the registers. In that case, the signal handler
// on x64 Linux will take more stack space than the standard size provides.
// Other platforms might not need that much space. However, dumping the
// registers in the signal handler is not covered by the current (Nov 2015)
// set of regression tests. We play it safe and use a larger size
// for all platforms.
#define CUSTOM_SIGNAL_STACK_SIZE (2 * SIGSTKSZ)

/**
 * Is the given address within the RVM address space?
 *
 * Taken:     addr [in] address to check
 * Returned:  1 if in address space, else 0
 */
EXTERNAL int inRVMAddressSpace(Address addr)
{
  int which;
  /* get the boot record */
  Address *heapRanges = bootRecord->heapRanges;
  for (which = 0; which < MAXHEAPS; which++) {
    Address start = heapRanges[2 * which];
    Address end = heapRanges[2 * which + 1];
    // Test against sentinel.
    if (start == ~(Address) 0 && end == ~ (Address) 0) break;
    if (start <= addr  && addr < end) {
      return 1;
    }
  }
  return 0;
}

EXTERNAL void dumpProcessAddressSpace() {
  /** file descriptor of proc map */
  FILE* procMap;
  /** char */
  int c;

  ERROR_PRINTF("attempting to dump proc map ...\n");
#ifdef RVM_FOR_LINUX
  procMap = fopen("/proc/self/maps", "r");
  c = fgetc(procMap);
  while (c != EOF) {
    fputc(c, stdout);
    c = fgetc(procMap);
  }
  fclose(procMap);
#else
  #warning "dumpProcessAddressSpace() NYI"
  ERROR_PRINTF("... not implemented for this platform\n");
#endif
}

/**
 * External programs might block SIGQUIT. This is undesirable for the VM
 * because the software signal handler interprets SIGQUIT a as request
 * to trigger a thread dump. Therefore, unblock SIGQUIT.
 *
 * An example use case for this is triggering a thread dump from the bin/timedrun
 * script. It uses Perl's system() function to run the tests and system() blocks
 * SIGQUIT by default.
 */
EXTERNAL void unblockSIGQUIT() {
  sigset_t sigQuitSet;

  VERBOSE_SIGNALS_PRINTF("Attempting to unmask SIGQUIT...");
  sigemptyset(&sigQuitSet);
  sigaddset (&sigQuitSet, SIGQUIT);
  int rc = pthread_sigmask(SIG_UNBLOCK, &sigQuitSet, NULL);
  if (rc == 0) {
    VERBOSE_SIGNALS_PRINTF("SUCCESS\n");
  } else {
    VERBOSE_SIGNALS_PRINTF("FAILURE: %d %s\n", rc, strerror(rc));
  }
}

/**
 * Hardware trap handler
 *
 * Taken: signo   [in] signal raised
 *        si      [in] additional signal information
 *        context [in,out] register contents at the point of the signal
 */
EXTERNAL void hardwareTrapHandler(int signo, siginfo_t *si, void *context)
{
  /** instruction causing trap */
  Address instructionPtr;
  /** instruction following one that trapped */
  Address instructionFollowingPtr;
  /** current thread pointer */
  Address threadPtr;
  /** JTOC ponter */
  Address jtocPtr;
  /** stack frame pointer */
  Address framePtr;
  /** description of trap */
  int trapCode;
  /** extra information such as array index for out-of-bounds traps */
  Word trapInfo = (Word)si->si_addr;

  readContextInformation(context, &instructionPtr, &instructionFollowingPtr,
                         &threadPtr, &jtocPtr);
  VERBOSE_SIGNALS_PRINTF("%s: hardwareTrapHandler %d %p - %p %p %p %p\n", Me, signo,
               (void*)context, (void*)instructionPtr, (void*)instructionFollowingPtr,
               (void*)threadPtr, (void*)jtocPtr);

  VERBOSE_SIGNALS_PRINTF("%s: hardwareTrapHandler: trap context:\n", Me);
  if (verboseSignalHandling) dumpContext(context);

  /* die if the signal didn't originate from the RVM */
  if (!inRVMAddressSpace(instructionPtr) || !inRVMAddressSpace(threadPtr)) {
    ERROR_PRINTF("%s: unexpected hardware trap outside of RVM address space - %p %p\n",
                 Me, (void*)instructionPtr, (void*)threadPtr);
    ERROR_PRINTF("fault address %p\n", (void *)trapInfo);
    dumpContext(context);
    dumpProcessAddressSpace();
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  /* get frame pointer checking its validity */
  framePtr = readContextFramePointer(context, threadPtr);
  if (!inRVMAddressSpace(framePtr)) {
    ERROR_PRINTF("%s: unexpected hardware trap with frame pointer outside of RVM address space - %p\n",
                 Me, (void *)framePtr);
    ERROR_PRINTF("fault address %p\n", (void *)trapInfo);
    dumpContext(context);
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  /* get trap code and trap info */
  trapCode = readContextTrapCode(context, threadPtr, signo, instructionPtr, &trapInfo);

  Address vmRegisters = *(Address *)((char *)threadPtr + RVMThread_exceptionRegisters_offset);
  unsigned char *inuse = ((unsigned  char*)vmRegisters + Registers_inuse_offset);
  if (*inuse) {
    /* unexpected VM registers in use.. dump VM and die */
    VERBOSE_SIGNALS_PRINTF("%s: VM registers in use whilst delivering hardware trap\n", Me);
    setupDumpStackAndDie(context);
  } else {
    *inuse = 1; /* mark in use */
    setupDeliverHardwareException(context, vmRegisters, trapCode, trapInfo,
         instructionPtr, instructionFollowingPtr,
         threadPtr, jtocPtr, framePtr, signo);
  }
  VERBOSE_SIGNALS_PRINTF("%s: hardwareTrapHandler: trap context on exit:\n", Me);
  if (verboseSignalHandling) dumpContext(context);
}

/**
 * Software signal handler
 *
 * Taken:     signo   [in] signal raised
 *            si      [in] additional signal information
 *            context [in,out] register contents at the point of the signal
 */
EXTERNAL void softwareSignalHandler(int signo, siginfo_t UNUSED *si, void *context)
{
  VERBOSE_SIGNALS_PRINTF("%s: softwareSignalHandler %d %p\n", Me, signo, context);

  // asynchronous signal used to awaken internal debugger
  if (signo == SIGQUIT) {
    // Turn on debug-request flag.
    unsigned *flag = (unsigned *)((char *)bootRecord->tocRegister + bootRecord->debugRequestedOffset);
    if (*flag) {
      VERBOSE_SIGNALS_PRINTF("%s: debug request already in progress, please wait\n", Me);
    } else {
      VERBOSE_SIGNALS_PRINTF("%s: debug requested, waiting for a thread switch\n", Me);
      *flag = 1;
    }
    return;
  }

  /* We need to adapt this code so that we run the exit handlers
   * appropriately.
   */

  if (signo == SIGTERM) {
    // Presumably we received this signal because someone wants us
    // to shut down.  Exit directly (unless the verbose flag is set).
    // TODO: Run the shutdown hooks instead.
    if (!verboseSignalHandling) {
      /* Now reraise the signal.  We reactivate the signal's
         default handling, which is to terminate the process.
         We could just call `exit' or `abort',
         but reraising the signal sets the return status
         from the process correctly.
         TODO: Go run shutdown hooks before we re-raise the signal. */
      signal(signo, SIG_DFL);
      raise(signo);
    }

    VERBOSE_SIGNALS_PRINTF("%s: kill requested: invoking dumpStackAndDie\n", Me);
    setupDumpStackAndDie(context);
    return;
  }

  /* Default case. */
  VERBOSE_SIGNALS_PRINTF("%s: got an unexpected software signal (# %d)", Me, signo);
#if defined __GLIBC__ && defined _GNU_SOURCE
  VERBOSE_SIGNALS_PRINTF(" %s", strsignal(signo));
#endif
  VERBOSE_SIGNALS_PRINTF("; ignoring it.\n");
}


/**
 * Set up signals for the main thread
 *
 * Returned:  data to be provided when this main thread terminates
 */
EXTERNAL void* sysStartMainThreadSignals()
{
  /* install a stack for hardwareTrapHandler() to run on */
  stack_t stack = {0};
  void *stackBuf;
  stackBuf = (void *)checkMalloc(CUSTOM_SIGNAL_STACK_SIZE);
  stack.ss_sp = stackBuf;
  stack.ss_size = CUSTOM_SIGNAL_STACK_SIZE;
  if (sigaltstack (&stack, 0)) {
    ERROR_PRINTF("%s: sigaltstack failed (errno=%d)\n",  Me, errno);
    sysFree(stackBuf);
    return NULL;
  }
  /* install hardware trap signal handler */
  struct sigaction action = {{0}};
  action.sa_sigaction = hardwareTrapHandler;
  /*
   * mask all signal from reaching the signal handler while the signal
   * handler is running
   */
  if (sigfillset(&(action.sa_mask))) {
    ERROR_PRINTF("%s: sigfillset failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  /*
   * exclude the signal used to wake up the daemons
   */
  if (sigdelset(&(action.sa_mask), SIGCONT)) {
    ERROR_PRINTF("%s: sigdelset failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
  if (sigaction (SIGSEGV, &action, 0) ||
      sigaction (SIGFPE, &action, 0) ||
      sigaction (SIGTRAP, &action, 0) ||
      sigaction (SIGBUS, &action, 0) ||
      /* for PPC */
      sigaction (SIGILL, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }

  /* install software signal handler */
  action.sa_sigaction = softwareSignalHandler;
  if (sigaction (SIGALRM, &action, 0) || /* catch timer ticks (so we can timeslice user level threads) */
      sigaction (SIGQUIT, &action, 0) || /* catch QUIT to invoke debugger thread */
      sigaction (SIGTERM, &action, 0)) { /* catch TERM to dump and die */
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }

  /* ignore "write (on a socket) with nobody to read it" signals so
   * that sysWriteBytes() will get an EPIPE return code instead of
   * trapping.
   */
  memset (&action, 0, sizeof action);
  action.sa_handler = SIG_IGN;
  if (sigaction(SIGPIPE, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  return stackBuf;
}


/**
 * Set up signals for a child thread
 *
 * Returned:  data to be provided when this child thread terminate
 */
EXTERNAL void* sysStartChildThreadSignals()
{
  stack_t stack = {0};
  void *stackBuf;
  int rc;

  TRACE_PRINTF("%s: sysSetupChildThreadSignals\n", Me);

  stackBuf = (void *)checkMalloc(CUSTOM_SIGNAL_STACK_SIZE);
  stack.ss_sp = stackBuf;
  stack.ss_flags = 0;
  stack.ss_size = CUSTOM_SIGNAL_STACK_SIZE;
  if (sigaltstack (&stack, 0)) {
    ERROR_PRINTF("sigaltstack failed (errno=%d)\n",errno);
    sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
  }
  /*
   * Block the CONT signal.  This makes SIGCONT reach this
   * pthread only when this pthread performs a sigwait().
   */
  sigset_t input_set, output_set;
  sigemptyset(&input_set);
  sigaddset(&input_set, SIGCONT);

  rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
  /* pthread_sigmask can only return the following errors.  Either of them
   * indicates serious trouble and is grounds for aborting the process:
   * EINVAL EFAULT.  */
  if (rc) {
    ERROR_PRINTF ("pthread_sigmask or sigthreadmask failed (errno=%d):", errno);
    sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
  }
  return stackBuf;
}



/**
 * Finish use of signals for a child thread
 *
 * Taken:   stackBuf [in] data provided by sysStartChildThreadSignals
 */
EXTERNAL void sysEndThreadSignals(void *stackBuf)
{
  stack_t stack;
  memset (&stack, 0, sizeof stack);
  stack.ss_flags = SS_DISABLE;
  sigaltstack(&stack, 0);
  checkFree(stackBuf);
}
