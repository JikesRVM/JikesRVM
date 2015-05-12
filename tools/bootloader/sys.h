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

/** Page size determined at runtime */
extern uint64_t pageSize;
/** Sink for messages relating to serious errors detected by C runtime. */
extern FILE *SysErrorFile;
/* Sink for trace messages produced by VM.sysWrite(). */
extern FILE *SysTraceFile;

#define CONSOLE_PRINTF(...) fprintf(SysTraceFile, __VA_ARGS__)

#ifdef __cplusplus
#define EXTERNAL extern "C"
#else
#define EXTERNAL
#endif

#if (defined RVM_FOR_LINUX) && (defined RVM_FOR_HARMONY)
#define LINUX
#endif

#ifdef RVM_WITH_ALIGNMENT_CHECKING
extern volatile int numEnableAlignCheckingCalls;
EXTERNAL void sysEnableAlignmentChecking();
EXTERNAL void sysDisableAlignmentChecking();
EXTERNAL void sysReportAlignmentChecking();
#endif


#define ERROR_PRINTF(...) fprintf(SysErrorFile, __VA_ARGS__)

/** Trace execution of syscalls */
#define TRACE 0
#define TRACE_PRINTF(...) if(TRACE) fprintf(SysTraceFile, __VA_ARGS__)

extern void* checkMalloc(int length);
extern void* checkCalloc(int numElements, int sizeOfOneElement);
extern void checkFree(void* mem);
EXTERNAL int pageRoundUp(uint64_t size, uint64_t pageSize);

/** Only called externally from Java programs. */
EXTERNAL void sysExit(int) NORETURN;

/* Routines used elsewhere within bootloader */
EXTERNAL void findMappable();


/**
 * FIXME The rest of the file consists of includes for non-linux systems
 * and old systems. Anyone that has access to such a system could help us
 * out by deleting unnecessary definitions from here and moving what is
 * needed to the respective sys*.cpp files.
 */

#if (defined RVM_FOR_SOLARIS)
#include <netinet/in.h>
#endif

/* OSX/Darwin */
#if (defined __MACH__)
#include <sys/stat.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <mach-o/dyld.h>
#include <mach/host_priv.h>
#include <mach/mach_init.h>
#include <mach/mach_host.h>
#include <mach/vm_map.h>
#include <mach/processor_info.h>
#include <mach/processor.h>
#include <mach/thread_act.h>
#include <sys/types.h>
#include <sys/sysctl.h>
/* As of 10.4, dlopen comes with the OS */
#include <dlfcn.h>
#define MAP_ANONYMOUS MAP_ANON
#include <sched.h>
#endif

#endif // RVM_SYSCALL_DEFINITIONS
