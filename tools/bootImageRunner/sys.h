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

#ifndef RVM_SYSCALL_DEFINITIONS
#define RVM_SYSCALL_DEFINITIONS

// Enable syscall on Linux / glibc
#ifdef RVM_FOR_LINUX
#define _GNU_SOURCE
#endif

//Solaris needs BSD_COMP to be set to enable the FIONREAD ioctl
#if defined (__SVR4) && defined (__sun)
#define BSD_COMP
#endif

#define NEED_VIRTUAL_MACHINE_DECLARATIONS 1
#define NEED_EXIT_STATUS_CODES 1
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In tools/bootImageRunner.
#include "cAttributePortability.h"

/** Sink for messages relating to serious errors detected by C runtime. */
extern FILE *SysErrorFile;
/* Sink for trace messages produced by VM.sysWrite(). */
extern FILE *SysTraceFile;

#define CONSOLE_PRINTF(...) fprintf(__VA_ARGS__)

#ifdef __cplusplus
#define EXTERNAL extern "C"
#else
#define EXTERNAL
#endif

#if (defined RVM_FOR_LINUX) && (defined RVM_FOR_HARMONY)
#define LINUX
#endif

/** Trace execution of syscalls */
#define TRACE 0
#define TRACE_PRINTF if(TRACE)fprintf

extern void* checkMalloc(int length);
extern void* checkCalloc(int numElements, int sizeOfOneElement);
extern void checkFree(void* mem);

/** Only called externally from Java programs. */
EXTERNAL void sysExit(int) NORETURN;

#endif // RVM_SYSCALL_DEFINITIONS
