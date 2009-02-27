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

 /* Handles "extern" declarations shared among files in
 * tools/bootImageRunner/.
 */
#include <stdio.h>
#include <inttypes.h>

#ifdef __MACH__
#include <assert.h>
#include <CoreServices/CoreServices.h>
#include <mach/mach.h>
#include <mach/mach_time.h>
#endif

#include <setjmp.h>

#ifdef __cplusplus
extern "C" {
#endif

#include "cAttributePortability.h"
#include "../../include/jni.h"

#ifdef RVM_FOR_32_ADDR
#define Offset int32_t
#else
#define Offset int64_t
#endif
// Sink for messages relating to serious errors detected by C runtime.
extern FILE *SysErrorFile;    // sink for serious error messages
extern FILE *SysErrorFile;	// libvm.C
// extern int SysErrorFd;	// in IA32 libvm.C, not in powerpc.

// Sink for trace messages produced by VM.sysWrite().
extern FILE *SysTraceFile;	// libvm.C
extern int   SysTraceFd;	// libvm.C

// Command line arguments to be passed to boot image.
//
extern const char **	JavaArgs;	// libvm.C
extern int	JavaArgc; 	// libvm.C

// Emit trace information?
//
extern int lib_verbose;		// libvm.C

// command line arguments
// startup configuration option with default values
extern const char *bootDataFilename;	/* Defined in libvm.C */
extern const char *bootCodeFilename;	/* Defined in libvm.C */
extern const char *bootRMapFilename;	/* Defined in libvm.C */
// name of program that will load and run RVM
extern char *Me;		// Defined in libvm.C

/* libvm.C and RunBootImage.C */
extern uint64_t initialHeapSize;
extern uint64_t maximumHeapSize;

/* defined in libvm.c, used in sys.C */
extern jmp_buf primordial_jb;

/* Defined in RunBootImage.C */
#ifdef __cplusplus
unsigned int parse_memory_size(
    const char *sizeName, const char *sizeFlag,
    const char *defaultFactor, unsigned roundTo,
    const char *token, const char *subtoken, bool *fastExit);
#endif

extern int verboseBoot;

/* define in sys.c, used in libvm.c */
extern void sysInitialize();

/* defined in sys.c, used in jvm.c */
extern void * getVmThread();

/* Defined in libvm.C; used in RunBootImage.C */
extern int createVM(int);

/* Used in libvm.C; Defined in sys.C */
extern int getArrayLength(void* ptr);

/* Used in libvm.C; Defined in sys.C */
extern int getTimeSlice_msec(void);

/* sys.C and RunBootImage.C */
extern void findMappable(void);

#ifdef RVM_FOR_POWERPC
/* Used in libvm.C, sys.C.  Defined in assembly code: */
extern void bootThread(int jtoc,int pr, int ti_or_ip, int fp); // assembler routine
#else
extern int bootThread(void *ip, void *pr, void *sp); // assembler routine
#endif

// These are defined in libvm.C.
extern void *getJTOC(void);

/* These are defined in sys.C; used in syswrap.C */
extern jint GetEnv(JavaVM *, void **, jint);

// Defined in sys.C.; used in libvm.C
extern void sysSyncCache(void *, size_t size);
// Defined in sys.C.  Used in libvm.C.
extern void processTimerTick(void);
// Defined in sys.C.  Used in libvm.C.
extern void* getThreadId();

#ifdef __MACH__
// Defined in sys.C; intiialized in RunBootImage.C
extern mach_timebase_info_data_t timebaseInfo;
#endif

// Defined in jvm.C. Used in harmony.c
extern struct JavaVM_ sysJavaVM;
#ifdef __cplusplus
}
#endif
