/*
 * (C) Copyright IBM Corp. 2001
 */
/** $Id$
 * AIX native implementation for creating and managing process
 * This is a dummy file, the actual code is in:
 * 	OsProcessExternal.c
 * 	registerExternal.c
 * 	memoryExternal.c
 * @author Ton Ngo   1/9/98
 */

#include <jni.h>
#include "Platform.h"
#include <stdio.h>
#include <unistd.h>
#include <procinfo.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/reg.h>
#include <sys/ptrace.h>
#include <sys/ldr.h>
#include <sys/errno.h>  
#include <sys/signal.h>

