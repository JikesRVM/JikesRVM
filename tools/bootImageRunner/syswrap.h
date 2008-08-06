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

// Wrapper functions for blocking system calls,
// and accessors for the real libc system call functions.

#ifndef SYSWRAP_H
#define SYSWRAP_H

#include <sys/time.h>
#include <sys/types.h>

/* Patterns for Functions from the C library. */
typedef int (*SelectFunc_t)(int, fd_set*, fd_set*, fd_set*, struct timeval*);
typedef int (*PollFunc_t)(struct pollfd*, long unsigned int, int);

// Accessor for real (libc) system call functions;
// allows bypassing our wrapper functions.
extern "C" SelectFunc_t getLibcSelect(void);

#endif // SYSWRAP_H
