/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
// $Id$
/* Handles "extern" declarations shared among files in
 * rvm/src/tools/bootImageRunner/.
 * @author: Steven Augart, based on contributions from everyone who ever
 * touched files in that directory.
 *
 */
// #include "bootImageRunner.h"	// In rvm/src/tools/bootImageRunner
// Sink for messages relating to serious errors detected by C runtime.
//
#include <stdio.h>

extern FILE *SysErrorFile;    // sink for serious error messages
extern FILE *SysErrorFile;	// libvm.C
// extern int SysErrorFd;	// in IA32 libvm.C, not in powerpc.
 
// Sink for trace messages produced by VM.sysWrite().
extern FILE *SysTraceFile;	// libvm.C
extern int   SysTraceFd;	// libvm.C

// Command line arguments to be passed to boot image.
//
extern char **	JavaArgs;	// libvm.C
extern int	JavaArgc; 	// libvm.C

// Emit trace information?
//
extern int lib_verbose;		// libvm.C

// command line arguments
// startup configuration option with default values
extern char *bootFilename;	/* Defined in libvm.C */
// name of program that will load and run RVM
extern char *me;		// Defined in libvm.C


/* libvm.C and RunBootImage.C */
extern unsigned initialHeapSize;
extern unsigned maximumHeapSize;

/* Defined in libvm.C; used in RunBootImage.C */
extern "C" int createJVM(int);
/* Used in libvm.C; Defined in sys.C */
extern "C" int getArrayLength(void* ptr);

/* sys.C and RunBootImage.C */
extern "C" void findMappable();

#if defined(RVM_FOR_POWERPC)
/* Used in libvm.C, sys.C.  Declared in assembly code.; */
extern "C" void bootThread(int jtoc, int pr, int ti_or_ip, int fp); // assembler routine
#elif defined(RVM_FOR_IA32)
extern "C" int bootThread(int ti_or_ip, int jtoc, int pr, int sp); // assembler routine
#else
#error "Warning: Undefined configuration: neither IA32 nor POWERPC: won't declare bootThread()"
#endif
#if ! defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
extern "C" void processTimerTick();
#endif
// These are defined in libvm.C.
#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
extern "C" void *getJTOC();
extern "C" int getProcessorsOffset();
#endif // RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
