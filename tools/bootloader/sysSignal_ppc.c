/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Architecture specific signal handling routines for PowerPC.
 *
 * Note: only Linux is supported right now.
 */
#include "sys.h"
#include <signal.h>
#include <sys/ucontext.h>

#define MAKE_INFO(info, context)                                        \
  struct ucontext *info = ((struct ucontext *)context)
#define MAKE_SAVE(save, info)                                           \
  struct pt_regs *save = info->uc_mcontext.regs
#define GET_GPR(save, r)             ((save)->gpr[(r)])
#define SET_GPR(save, r, value)     (((save)->gpr[(r)]) = (value))
#define PPC_IAR(save)                  save->nip
#define PPC_LR(save)                   save->link
#define PPC_FP(save)                   save->gpr[1]

#define NUM_GPRS 32

/**
 * Read the addresses of pointers for important values out of the context
 *
 * Taken: context [in] context to read from
 *        instructionPtr [out] pointer to instruction
 *        instructionFollowingPtr [out] pointer to instruction following this
 *        threadPtr [out] ptr to current VM thread object
 *        tocPtr [out] ptr to JTOC
 */
EXTERNAL void readContextInformation(void *context, Address *instructionPtr,
                                     Address *instructionFollowingPtr,
                                     Address *threadPtr, Address *jtocPtr)
{
  MAKE_INFO(info, context);
  MAKE_SAVE(save, info);
  Address ip = PPC_IAR(save);
  *instructionPtr = ip;
  *instructionFollowingPtr = ip + 4;
  *threadPtr = GET_GPR(save, Constants_THREAD_REGISTER);
  *jtocPtr = bootRecord->tocRegister; /* could use register holding JTOC on PPC */
}

/**
 * Read frame pointer at point of the signal
 *
 * Taken:     context [in] context to read from
 *            threadPtr [in] address of thread information
 * Returned:  address of stack frame
 */
EXTERNAL Address readContextFramePointer(void *context, Address UNUSED threadPtr)
{
  MAKE_INFO(info, context);
  MAKE_SAVE(save, info);
  return GET_GPR(save, Constants_FRAME_POINTER);
}

/**
 * Read trap code from context of signal
 *
 * Taken:     context   [in] context to read from
 *            threadPtr [in] address of thread information
 *            signo     [in] signal number
 *            instructionPtr [in] address of instruction
 *            trapInfo  [out] extra information about trap
 * Returned:  trap code
 */
EXTERNAL int readContextTrapCode(void *context, Address threadPtr, int signo, Address instructionPtr, Word *trapInfo)
{
  int instruction;

  switch (signo) {
    case SIGSEGV:
      return Runtime_TRAP_NULL_POINTER;
    case SIGTRAP:
      instruction = *((int*)instructionPtr);
      if ((instruction & Constants_ARRAY_INDEX_MASK) == Constants_ARRAY_INDEX_TRAP) {
        MAKE_INFO(info, context);
        MAKE_SAVE(save, info);
        *trapInfo = GET_GPR(save,
                            (instruction & Constants_ARRAY_INDEX_REG_MASK)
                            >> Constants_ARRAY_INDEX_REG_SHIFT);
        return Runtime_TRAP_ARRAY_BOUNDS;
      }
      if ((instruction & Constants_CONSTANT_ARRAY_INDEX_MASK) == Constants_CONSTANT_ARRAY_INDEX_TRAP) {
        *trapInfo = ((instruction & Constants_CONSTANT_ARRAY_INDEX_INFO)<<16)>>16;
        return Runtime_TRAP_ARRAY_BOUNDS;
      }
      if ((instruction & Constants_DIVIDE_BY_ZERO_MASK) == Constants_DIVIDE_BY_ZERO_TRAP) {
        return Runtime_TRAP_DIVIDE_BY_ZERO;
      }
      if ((instruction & Constants_MUST_IMPLEMENT_MASK) == Constants_MUST_IMPLEMENT_TRAP)  {
        return Runtime_TRAP_MUST_IMPLEMENT;
      }
      if ((instruction & Constants_STORE_CHECK_MASK) == Constants_STORE_CHECK_TRAP) {
        return Runtime_TRAP_STORE_CHECK;
      }
      if ((instruction & Constants_CHECKCAST_MASK) == Constants_CHECKCAST_TRAP) {
        return Runtime_TRAP_CHECKCAST;
      }
      if ((instruction & Constants_REGENERATE_MASK) == Constants_REGENERATE_TRAP) {
        return Runtime_TRAP_REGENERATE;
      }
      if ((instruction & Constants_NULLCHECK_MASK) == Constants_NULLCHECK_TRAP) {
        return Runtime_TRAP_NULL_POINTER;
      }
      if ((instruction & Constants_JNI_STACK_TRAP_MASK) == Constants_JNI_STACK_TRAP) {
        return Runtime_TRAP_JNI_STACK;
      }
      if ((instruction & Constants_STACK_OVERFLOW_MASK) == Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP) {
        return Runtime_TRAP_STACK_OVERFLOW;
      }
      if ((instruction & Constants_STACK_OVERFLOW_MASK) == Constants_STACK_OVERFLOW_TRAP) {
        *trapInfo = Constants_STACK_OVERFLOW_TRAP_INFO_SET_HAVE_FRAME;
        return Runtime_TRAP_STACK_OVERFLOW;
      }
      ERROR_PRINTF("%s: Unexpected hardware trap 0x%x from instruction 0x%0x\n", Me, signo, instruction);
      return Runtime_TRAP_UNKNOWN;
    case SIGFPE:
      return Runtime_TRAP_DIVIDE_BY_ZERO;
    default:
      ERROR_PRINTF("%s: Unexpected hardware trap signal 0x%x\n", Me, signo);
      return Runtime_TRAP_UNKNOWN;
  }
}

/**
 * Set up the context to invoke RVMThread.dumpStackAndDie
 *
 * Taken:   context [in,out] registers at point of signal/trap
 */
EXTERNAL void setupDumpStackAndDie(void *context)
{
  MAKE_INFO(info, context);
  MAKE_SAVE(save, info);
  Offset DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;
  Address localJTOC = bootRecord->tocRegister;
  Address dumpStack = *(Address *)((char *)localJTOC + DumpStackAndDieOffset);
  PPC_LR(save) = PPC_IAR(save) + 4; // +4 so it looks like a return address
  PPC_IAR(save) = dumpStack;
  SET_GPR(save, Constants_FIRST_VOLATILE_GPR, GET_GPR(save, Constants_FRAME_POINTER));
}

/**
 * Set up the context to invoke RuntimeEntrypoints.deliverHardwareException.
 *
 * Taken:   context  [in,out] registers at point of signal/trap
 *          vmRegisters [out]
 */
EXTERNAL void setupDeliverHardwareException(void *context, Address vmRegisters,
             int trapCode, Word trapInfo,
             Address instructionPtr,
             Address instructionFollowingPtr,
             Address threadPtr, Address jtocPtr,
             Address framePtr, int signo)
{
  MAKE_INFO(info, context);
  MAKE_SAVE(save, info);

  if (trapCode == Runtime_TRAP_STACK_OVERFLOW) {
    /* Adjust the stack limit downward to give the exception handler some space
     * in which to run. */
    Address stackLimit = *(Address *)(threadPtr + RVMThread_stackLimit_offset);
    Address stackStart = *(Address *)(threadPtr + RVMThread_stack_offset);
    stackLimit -= Constants_STACK_SIZE_GUARD;
    if (stackLimit < stackStart) {
      /* double fault - stack overflow exception handler used too
         much stack space */
      ERROR_PRINTF(
        "%s: stack overflow exception (double fault)\n", Me);
      /* Go ahead and get all the stack space we need to generate
       * the error dump (since we're crashing anyways) */
      *(Address *)(threadPtr + RVMThread_stackLimit_offset) = 0;
      /* Things are very badly wrong anyways, so attempt to generate
         a useful error dump before exiting by returning to
         Scheduler.dumpStackAndDie, passing it the fp of the
         offending thread. */
      setupDumpStackAndDie(context);
      return;
    }
    *(Address *)(threadPtr + RVMThread_stackLimit_offset) = stackLimit;
  }

  /* Prepare Registers object */
  Word *gprs = *(Word **)((char *)vmRegisters + Registers_gprs_offset);
  Word *ipLoc = (Word  *)((char *)vmRegisters + Registers_ip_offset);
  Word *lrLoc = (Word  *)((char *)vmRegisters + Registers_lr_offset);

  int i;
  for (i = 0; i < NUM_GPRS; ++i) {
    gprs[i] = GET_GPR(save, i);
  }
  *ipLoc = instructionFollowingPtr;
  *lrLoc = PPC_LR(save);

  /* Insert artificial stackframe at site of trap.
     This frame marks the place where "hardware exception registers" were saved. */
  Address oldFp = framePtr;
  Address newFp = oldFp - Constants_STACKFRAME_HEADER_SIZE;
  *(Address *)(oldFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) =
      instructionFollowingPtr;

  int hardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
  *(int *)(newFp + Constants_STACKFRAME_METHOD_ID_OFFSET)
    = hardwareTrapMethodId;
  *(Address *)(newFp + Constants_STACKFRAME_FRAME_POINTER_OFFSET)
    = oldFp;
  SET_GPR(save, Constants_FRAME_POINTER, newFp);

  /* Set contents of link register */
  int haveStackFrame;
  if (trapCode == Runtime_TRAP_JNI_STACK) {
    haveStackFrame = 0;
  } else if (trapCode == Runtime_TRAP_STACK_OVERFLOW) {
    haveStackFrame = (trapInfo == Constants_STACK_OVERFLOW_TRAP_INFO_SET_HAVE_FRAME);
  } else {
    haveStackFrame = 1;
  }

  /* Set parameters for RuntimeEntrypoints.deliverHardwareException */
  SET_GPR(save, Constants_FIRST_VOLATILE_GPR, trapCode);
  SET_GPR(save, Constants_FIRST_VOLATILE_GPR + 1, trapInfo);

  /* Compute address for RuntimeEntrypoints.deliverHardwareException */
  Offset deliverHardwareExceptionOffset = bootRecord->deliverHardwareExceptionOffset;
  Address javaExceptionHandler = *(Address*)((char *) jtocPtr + deliverHardwareExceptionOffset);

  if (haveStackFrame) {
    /* Set the link register to make it look as if the Java exception
       handler had been called directly from the exception site.
    */
    if (PPC_IAR(save) == 0) {
      do {
        /* A bad branch -- use the contents of the link register as
               our best guess of the call site. */
      } while(0);
    } else {
      PPC_LR(save) = instructionFollowingPtr;
    }
  } else {
    /* We haven't actually bought the stackframe yet, so pretend that
       we are actually trapping directly from the call instruction that
       invoked the method whose prologue caused the stackoverflow.
    */
    *(Address *)(oldFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = PPC_LR(save);
  }

  /* Resume execution at the Java exception handler. */
  PPC_IAR(save) = javaExceptionHandler;
}

/**
 * Print the contents of context to the screen
 *
 * Taken:   context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
  int i;
  MAKE_INFO(info, context);
  MAKE_SAVE(save, info);
  ERROR_PRINTF("             fp=%p\n", (void*)PPC_FP(save));
  ERROR_PRINTF("             tr=%p\n", (void*)GET_GPR(save, Constants_THREAD_REGISTER));
  ERROR_PRINTF("             ip=%p\n", (void*)PPC_IAR(save));
  if (!inRVMAddressSpace(PPC_IAR(save))) {
    ERROR_PRINTF("          instr=UNKNOWN - not dumping it because ip isn't in RVM address space\n");
  } else {
    ERROR_PRINTF("          instr=0x%08x\n", *((int*)PPC_IAR(save)));
  }

  ERROR_PRINTF("             lr=%p\n",  (void*)PPC_LR(save));
  for (i = 0; i < NUM_GPRS; i++) {
    ERROR_PRINTF("            r%02d=%p\n", i, (void*)GET_GPR(save, i));
  }
}
