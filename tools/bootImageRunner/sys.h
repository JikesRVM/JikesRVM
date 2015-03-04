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

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_EXIT_STATUS_CODES
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In tools/bootImageRunner.
#include "cAttributePortability.h"

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

#endif // RVM_SYSCALL_DEFINITIONS
