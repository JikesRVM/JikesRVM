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
 * Architecture specific signal handling routines for ARM.
 *
 * Note: only Linux is supported right now.
 */
#include "sys.h"
#include <signal.h>
#include <sys/ucontext.h>

// See linux source file sigcontext.h

#define GET_GPR(context, r)            (*(&(((struct ucontext *)context)->uc_mcontext.arm_r0) + (r)))
#define SET_GPR(context, r, value)     (*(&(((struct ucontext *)context)->uc_mcontext.arm_r0) + (r)) = (value))
#define ARM_FP(context)                    (((struct ucontext *)context)->uc_mcontext.arm_fp)
#define ARM_SP(context)                    (((struct ucontext *)context)->uc_mcontext.arm_sp)
#define ARM_LR(context)                    (((struct ucontext *)context)->uc_mcontext.arm_lr)
#define ARM_PC(context)                    (((struct ucontext *)context)->uc_mcontext.arm_pc)

// Everything except for LR and PC
#define NUM_GPRS 14

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
  Address ip = ARM_PC(context);
  *instructionPtr = ip;
  *instructionFollowingPtr = ip + 4;
  *threadPtr = GET_GPR(context, Constants_THREAD_REGISTER);
  *jtocPtr = bootRecord->tocRegister; /* could use register holding JTOC on ARM */
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
  return ARM_FP(context);
}

/**
 * Read trap code from context of signal
 * See also: 
 *    compilers.common.arm.Assembler.java
 *    tools.header_gen.GenArch_arm.java
 *    arm.TrapConstants.java
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
  if (signo == SIGSEGV) {
    int instruction = *(((int*)instructionPtr) - 1); // Read instruction immediately before the segfault to determine the cause
    if (instruction == Constants_ARRAY_INDEX_TRAP) {
      int comparison = *(((int*)instructionPtr) - 2); // Read the CMP before that to find the register that stores the array index (see Assembler.java)
      int regnum = (comparison & Constants_ARRAY_INDEX_REG_MASK) >> Constants_ARRAY_INDEX_REG_SHIFT;
      *trapInfo = GET_GPR(context, regnum);
      return Runtime_TRAP_ARRAY_BOUNDS;
    }
    else if (instruction == Constants_DIVIDE_ZERO_TRAP) {
      return Runtime_TRAP_DIVIDE_BY_ZERO;
    }
    else if (instruction == Constants_STACK_OVERFLOW_TRAP) {
      return Runtime_TRAP_STACK_OVERFLOW;
    }
    else if (instruction == Constants_JNI_STACK_TRAP) {
      return Runtime_TRAP_JNI_STACK;
    }
    else if (instruction == Constants_NULLCHECK_TRAP) {
      return Runtime_TRAP_NULL_POINTER;
    }
    else if ((instruction & Constants_IGNORE_COND) == Constants_INTERFACE_TRAP) {
      return Runtime_TRAP_MUST_IMPLEMENT;
    }
    else if ((instruction & Constants_IGNORE_COND) == Constants_CHECKCAST_TRAP) {
      return Runtime_TRAP_CHECKCAST;
    }
    else {
      return Runtime_TRAP_NULL_POINTER;
    }
  } else {
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
  Offset DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;
  Address localJTOC = bootRecord->tocRegister;
  Address dumpStack = *(Address *)((char *)localJTOC + DumpStackAndDieOffset);
  ARM_LR(context) = ARM_PC(context) + 4; // +4 so it looks like a return address
  ARM_PC(context) = dumpStack;
  SET_GPR(context, Constants_FIRST_VOLATILE_GPR, ARM_FP(context));
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

  if (trapCode == Runtime_TRAP_STACK_OVERFLOW) {
     // Adjust the stack limit downward to give the exception handler some space
     // in which to run.
    Address stackLimit = *(Address *)(threadPtr + RVMThread_stackLimit_offset);
    Address stackStart = *(Address *)(threadPtr + RVMThread_stack_offset);
    stackLimit -= Constants_STACK_SIZE_GUARD;
    if (stackLimit < stackStart) {
      // double fault - stack overflow exception handler used too
      // much stack space
      ERROR_PRINTF(
        "%s: stack overflow exception (double fault)\n", Me);
      // Go ahead and get all the stack space we need to generate
      // the error dump (since we're crashing anyways)
      *(Address *)(threadPtr + RVMThread_stackLimit_offset) = 0;
      // Things are very badly wrong anyways, so attempt to generate
      // a useful error dump before exiting by returning to
      // Scheduler.dumpStackAndDie, passing it the fp of the
      // offending thread.
      setupDumpStackAndDie(context);
      return;
    }
    *(Address *)(threadPtr + RVMThread_stackLimit_offset) = stackLimit;
  }

  // Prepare Registers object
  Word *gprs = *(Word **)((char *)vmRegisters + Registers_gprs_offset);
  Word *lrLoc = (Word  *)((char *)vmRegisters + Registers_lr_offset);
  Word *ipLoc = (Word  *)((char *)vmRegisters + Registers_ip_offset);

  int i;
  for (i = 0; i < NUM_GPRS; ++i) {
    gprs[i] = GET_GPR(context, i); // Will copy everything (including FP and SP) except for LR and PC
  }
  *lrLoc = ARM_LR(context);
  *ipLoc = instructionFollowingPtr;

  /* Insert artificial stackframe at site of trap.
     This frame marks the place where "hardware exception registers" were saved. */
  Address oldFp = framePtr;
  Address newFp = ARM_SP(context) - Constants_STACKFRAME_PARAMETER_OFFSET;
  *(Address *)(newFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) =
      instructionFollowingPtr;

  int hardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
  *(int *)(newFp + Constants_STACKFRAME_METHOD_ID_OFFSET)
    = hardwareTrapMethodId;
  *(Address *)(newFp + Constants_STACKFRAME_FRAME_POINTER_OFFSET)
    = oldFp;
  ARM_FP(context) = newFp;
  ARM_SP(context) = newFp + Constants_STACKFRAME_END_OF_FRAME_OFFSET;

  /* Set contents of link register */
  int haveStackFrame;
  if (trapCode == Runtime_TRAP_JNI_STACK || trapCode == Runtime_TRAP_STACK_OVERFLOW) {
    haveStackFrame = 0;
  } else {
    haveStackFrame = 1;
  }

  /* Set parameters for RuntimeEntrypoints.deliverHardwareException */
  SET_GPR(context, Constants_FIRST_VOLATILE_GPR, trapCode);
  SET_GPR(context, Constants_FIRST_VOLATILE_GPR + 1, trapInfo);

  /* Compute address for RuntimeEntrypoints.deliverHardwareException */
  Offset deliverHardwareExceptionOffset = bootRecord->deliverHardwareExceptionOffset;
  Address javaExceptionHandler = *(Address*)((char *) jtocPtr + deliverHardwareExceptionOffset);

  if (haveStackFrame) {
    /* Set the link register to make it look as if the Java exception
       handler had been called directly from the exception site.
    */
    if (ARM_PC(context) == 0) {
      do {
        /* A bad branch -- use the contents of the link register as
               our best guess of the call site. */
      } while(0);
    } else {
      ARM_LR(context) = instructionFollowingPtr;
    }
  } else {
    /* We haven't actually bought the stackframe yet, so pretend that
       we are actually trapping directly from the call instruction that
       invoked the method whose prologue caused the stackoverflow.
    */
    *(Address *)(oldFp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = ARM_LR(context);
  }

  /* Resume execution at the Java exception handler. */
  ARM_PC(context) = javaExceptionHandler;
}

/**
 * Print the contents of context to the screen
 *
 * Taken:   context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
  int i;
  ERROR_PRINTF("             fp=%p\n", (void*)ARM_FP(context));
  ERROR_PRINTF("             tr=%p\n", (void*)GET_GPR(context, Constants_THREAD_REGISTER));
  ERROR_PRINTF("             ip=%p\n", (void*)ARM_PC(context));
  if (!inRVMAddressSpace(ARM_PC(context))) {
    ERROR_PRINTF("          instr=UNKNOWN - not dumping it because ip isn't in RVM address space\n");
  } else {
    ERROR_PRINTF("          instr=0x%08x\n", *((int*)ARM_PC(context)));
  }

  ERROR_PRINTF("             lr=%p\n",  (void*)ARM_LR(context));
  for (i = 0; i < NUM_GPRS; i++) {
    ERROR_PRINTF("            r%02d=%p\n", i, (void*)GET_GPR(context, i));
  }
}
