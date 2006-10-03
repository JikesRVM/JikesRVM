/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

// @author David Hovemeyer

// Wrapper functions for blocking system calls,
// and accessors for the real libc system call functions.

#ifndef SYSWRAP_H
#define SYSWRAP_H

#include <sys/time.h>
#include <sys/types.h>

/* Patterns for Functions from the C library. */
typedef int (*SelectFunc_t)(int, fd_set*, fd_set*, fd_set*, struct timeval*);
typedef int (*PollFunc_t)(struct pollfd*, long unsigned int, int);

// Init function for the syscall wrapper library.
extern "C" void initSyscallWrapperLibrary(void *jtoc, VM_Offset processorsOffset,
                                          int vmProcessorId /* Only used in single-virtual-processor mode. */);

// Accessor for real (libc) system call functions;
// allows bypassing our wrapper functions.
extern "C" SelectFunc_t getLibcSelect(void);

#endif // SYSWRAP_H
