/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

// Wrapper functions for blocking system calls,
// and accessors for the real libc system call functions.

#ifndef SYSWRAP_H
#define SYSWRAP_H

#include <sys/time.h>
#include <sys/types.h>
#if !defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
# include <pthread.h>
#endif

typedef int (*SelectFunc)(int, fd_set*, fd_set*, fd_set*, struct timeval*);

// Init function for the syscall wrapper library.
#if defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
extern "C" void initSyscallWrapperLibrary(void *jtoc, int processorsOffset,
  int vmProcessorId);
#else
extern "C" void initSyscallWrapperLibrary(void *jtoc, int processorsOffset,
  pthread_key_t vmProcessorIdKey);
#endif

// Accessor for real (libc) system call functions;
// allows bypassing our wrapper functions.
extern "C" SelectFunc getLibcSelect(void);

#endif // SYSWRAP_H
