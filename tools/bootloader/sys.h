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

#include "cAttributePortability.h"
#include <stdio.h>
#include <stdint.h>
#include <string.h> // for strcmp
#include <jni.h>
#include <signal.h> // for siginfo

#ifdef __MACH__
#include <mach/mach_time.h>
#endif

#ifndef __SIZEOF_POINTER__
#  ifdef RVM_FOR_32_ADDR
#    define __SIZEOF_POINTER__ 4
#  else
#    define __SIZEOF_POINTER__ 8
#  endif
#endif

#ifdef RVM_FOR_32_ADDR
  #define Address uint32_t
  #define Offset int32_t
  #define Extent uint32_t
  #define Word uint32_t
#else
  #define Address uint64_t
  #define Offset int64_t
  #define Extent uint64_t
  #define Word uint64_t
#endif

#ifdef RVM_FOR_HARMONY
  #define TLS_KEY_TYPE hythread_tls_key_t
  #define GET_THREAD_LOCAL(key) hythread_tls_get(hythread_self(), key)
#else
  #include <pthread.h>
  #define TLS_KEY_TYPE pthread_key_t
  #define GET_THREAD_LOCAL(key) pthread_getspecific(key)
#endif

/** Page size determined at runtime */
extern Extent pageSize;
/** Sink for messages relating to serious errors detected by C runtime. */
extern FILE *SysErrorFile;
/* Sink for trace messages produced by VM.sysWrite(). */
extern FILE *SysTraceFile;
/** String used for name of RVM */
extern char *Me;
/** Number of Java args */
extern int JavaArgc;
/** Java args */
extern char **JavaArgs;
/** C access to shared C/Java boot record data structure */
extern struct BootRecord *bootRecord;
/** JVM datastructure used for JNI declared in jvm.c */
extern const struct JavaVM_ sysJavaVM;
/** Verbose command line option */
extern int verbose;
/** Verbose signal handling command line option */
extern int verboseSignalHandling;
/** Verbose boot up set */
extern int verboseBoot;
/** File name for part of boot image containing code */
extern char *bootCodeFilename;
/** File name for part of boot image containing data */
extern char *bootDataFilename;
/** File name for part of boot image containing the root map */
extern char *bootRMapFilename;

extern Extent initialHeapSize;
extern Extent maximumHeapSize;
extern Extent pageSize;

#define CONSOLE_PRINTF(...) fprintf(SysTraceFile, __VA_ARGS__)

#ifdef __cplusplus
#define EXTERNAL extern "C"
#else
#define EXTERNAL
#endif

#if (defined RVM_FOR_LINUX) && (defined RVM_FOR_HARMONY)
#define LINUX
#endif

#define ERROR_PRINTF(...) fprintf(SysErrorFile, __VA_ARGS__)

/** Trace execution of syscalls */
#define TRACE verbose
#define TRACE_PRINTF(...) if (TRACE) fprintf(SysTraceFile, __VA_ARGS__)

/** Conditional printouts for verbose signal handling */
#define VERBOSE_SIGNALS_PRINTF(...) if (verboseSignalHandling) fprintf(SysErrorFile, __VA_ARGS__)

/* String utilities */
#define STREQUAL(s1, s2) (strcmp(s1, s2) == 0)
#define STRNEQUAL(s1, s2, n) (strncmp(s1, s2, n) == 0)

extern void* checkMalloc(int length);
extern void* checkCalloc(int numElements, int sizeOfOneElement);
extern void checkFree(void* mem);

// sysAlignmentCheck
extern volatile int numEnableAlignCheckingCalls;
EXTERNAL void sysEnableAlignmentChecking();
EXTERNAL void sysDisableAlignmentChecking();
EXTERNAL void sysReportAlignmentChecking();
// sysConsole
EXTERNAL void sysConsoleWriteChar(unsigned value);
EXTERNAL void sysConsoleWriteInteger(int value, int hexToo);
EXTERNAL void sysConsoleWriteLong(long long value, int hexToo);
EXTERNAL void sysConsoleWriteDouble(double value,  int postDecimalDigits);
EXTERNAL void sysConsoleFlushErrorAndTrace();
// sysGCSpy
#ifdef RVM_WITH_GCSPY
EXTERNAL gcspy_gc_stream_t * gcspyDriverAddStream (gcspy_gc_driver_t *driver, int id);
EXTERNAL void gcspyDriverEndOutput (gcspy_gc_driver_t *driver);
EXTERNAL void gcspyDriverInit (gcspy_gc_driver_t *driver, int id, char *serverName, char *driverName,
                               char *title, char *blockInfo, int tileNum,
                               char *unused, int mainSpace);
EXTERNAL void gcspyDriverInitOutput (gcspy_gc_driver_t *driver);
EXTERNAL void gcspyDriverResize (gcspy_gc_driver_t *driver, int size);
EXTERNAL void gcspyDriverSetTileName (gcspy_gc_driver_t *driver, int tile, char *format, long value);
EXTERNAL void gcspyDriverSetTileNameRange (gcspy_gc_driver_t *driver, int tile, Address start, Address end);
EXTERNAL void gcspyDriverSpaceInfo (gcspy_gc_driver_t *driver, char *spaceInfo);
EXTERNAL void gcspyDriverStartComm (gcspy_gc_driver_t *driver);
EXTERNAL void gcspyDriverStream (gcspy_gc_driver_t *driver, int id, int len);
EXTERNAL void gcspyDriverStreamByteValue (gcspy_gc_driver_t *driver, int val);
EXTERNAL void gcspyDriverStreamShortValue (gcspy_gc_driver_t *driver, short val);
EXTERNAL void gcspyDriverStreamIntValue (gcspy_gc_driver_t *driver, int val);
EXTERNAL void gcspyDriverSummary (gcspy_gc_driver_t *driver, int id, int len);
EXTERNAL void gcspyDriverSummaryValue (gcspy_gc_driver_t *driver, int val);
EXTERNAL void gcspyIntWriteControl (gcspy_gc_driver_t *driver, int id, int len);
EXTERNAL gcspy_gc_driver_t * gcspyMainServerAddDriver (gcspy_main_server_t *server);
EXTERNAL void gcspyMainServerAddEvent (gcspy_main_server_t *server, int event, const char *name);
EXTERNAL gcspy_main_server_t * gcspyMainServerInit (int port, int len, const char *name, int verbose);
EXTERNAL int gcspyMainServerIsConnected (gcspy_main_server_t *server, int event);
EXTERNAL gcspyMainServerOuterLoop_t * gcspyMainServerOuterLoop ();
EXTERNAL void gcspyMainServerSafepoint (gcspy_main_server_t *server, int event);
EXTERNAL void gcspyMainServerSetGeneralInfo (gcspy_main_server_t *server, char *generalInfo);
EXTERNAL void gcspyMainServerStartCompensationTimer (gcspy_main_server_t *server);
EXTERNAL void gcspyMainServerStopCompensationTimer (gcspy_main_server_t *server);
EXTERNAL void gcspyStartserver (gcspy_main_server_t *server, int wait, void *loop);
EXTERNAL void gcspyStreamInit (gcspy_gc_stream_t *stream, int id, int dataType, char *streamName,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
                               char *stringPre, char *stringPost, int presentation, int paintStyle,
                               int indexMaxStream, int red, int green, int blue);
EXTERNAL void gcspyFormatSize (char *buffer, int size);
EXTERNAL int gcspySprintf(char *str, const char *format, char *arg);
#endif
// sysIO
EXTERNAL int sysReadByte(int fd);
EXTERNAL int sysWriteByte(int fd, int data);
EXTERNAL int sysReadBytes(int fd, char *buf, int cnt);
EXTERNAL int sysWriteBytes(int fd, char *buf, int cnt);
// sysLibrary
EXTERNAL void* sysDlopen(char *libname);
EXTERNAL void* sysDlsym(Address libHandler, char *symbolName);
// sysMath
EXTERNAL long long sysLongDivide(long long a, long long b);
EXTERNAL long long sysLongRemainder(long long a, long long b);
EXTERNAL double sysLongToDouble(long long a);
EXTERNAL float sysLongToFloat(long long a);
EXTERNAL int sysFloatToInt(float a);
EXTERNAL int sysDoubleToInt(double a);
EXTERNAL long long sysFloatToLong(float a);
EXTERNAL long long sysDoubleToLong(double a);
EXTERNAL double sysDoubleRemainder(double a, double b);
EXTERNAL float sysPrimitiveParseFloat(const char * buf);
EXTERNAL int sysPrimitiveParseInt(const char * buf);
EXTERNAL long long sysPrimitiveParseLong(const char * buf);
EXTERNAL double sysVMMathSin(double a);
EXTERNAL double sysVMMathCos(double a);
EXTERNAL double sysVMMathTan(double a);
EXTERNAL double sysVMMathAsin(double a);
EXTERNAL double sysVMMathAcos(double a);
EXTERNAL double sysVMMathAtan(double a);
EXTERNAL double sysVMMathAtan2(double a, double b);
EXTERNAL double sysVMMathCosh(double a);
EXTERNAL double sysVMMathSinh(double a);
EXTERNAL double sysVMMathTanh(double a);
EXTERNAL double sysVMMathExp(double a);
EXTERNAL double sysVMMathLog(double a);
EXTERNAL double sysVMMathSqrt(double a);
EXTERNAL double sysVMMathPow(double a, double b);
EXTERNAL double sysVMMathIEEEremainder(double a, double b);
EXTERNAL double sysVMMathCeil(double a);
EXTERNAL double sysVMMathFloor(double a);
EXTERNAL double sysVMMathRint(double a);
EXTERNAL double sysVMMathCbrt(double a);
EXTERNAL double sysVMMathExpm1(double a);
EXTERNAL double sysVMMathHypot(double a, double b);
EXTERNAL double sysVMMathLog10(double a);
EXTERNAL double sysVMMathLog1p(double a);
// sysMemory
EXTERNAL void * sysMalloc(int length);
EXTERNAL void * sysCalloc(int length);
EXTERNAL void sysFree(void *location);
EXTERNAL void sysZeroNT(void *dst, Extent cnt);
EXTERNAL void sysZero(void *dst, Extent cnt);
EXTERNAL void sysZeroPages(void *dst, int cnt);
EXTERNAL void * sysMMap(char *start , size_t length ,
                        int protection , int flags ,
                        int fd , Offset offset);
EXTERNAL void * sysMMapErrno(char *start , size_t length ,
                             int protection , int flags ,
                             int fd , Offset offset);
EXTERNAL int sysMProtect(char *start, size_t length, int prot);
EXTERNAL void sysCopy(void *dst, const void *src, Extent cnt);
EXTERNAL void sysMemmove(void *dst, const void *src, Extent cnt);
EXTERNAL void sysSyncCache(void *address, size_t size);
// sysMisc
EXTERNAL int sysArg(int argno, char *buf, int buflen);
EXTERNAL int sysGetenv(const char *varName, char *buf, int limit);
EXTERNAL jlong sysParseMemorySize(const char *sizeName, const char *sizeFlag,
                                  const char *defaultFactor, int roundTo,
                                  const char *token, const char *subtoken);
EXTERNAL Extent parse_memory_size(const char *sizeName, /*  "initial heap" or "maximum heap" or
                 "initial stack" or "maximum stack"
                   */
         const char *sizeFlag, // "-Xms" or "-Xmx" or
         // "-Xss" or "-Xsg" or "-Xsx"
         const char *defaultFactor, // We now always default to bytes ("")
         Extent roundTo,  // Round to PAGE_SIZE_BYTES or to 4.
         const char *token /* e.g., "-Xms200M" or "-Xms200" */,
         const char *subtoken /* e.g., "200M" or "200" */,
         int *fastExit);
// sysPerfEvent
EXTERNAL void sysPerfEventInit(int events);
EXTERNAL void sysPerfEventCreate(int id, const char *eventName);
EXTERNAL void sysPerfEventEnable();
EXTERNAL void sysPerfEventDisable();
EXTERNAL void sysPerfEventRead(int id, long long *values);
// sysSignal
EXTERNAL int inRVMAddressSpace(Address addr);
EXTERNAL void dumpProcessAddressSpace();
EXTERNAL void unblockSIGQUIT();
EXTERNAL void hardwareTrapHandler(int signo, siginfo_t *si, void *context);
EXTERNAL void softwareSignalHandler(int signo, siginfo_t UNUSED *si, void *context);
EXTERNAL void* sysStartMainThreadSignals();
EXTERNAL void* sysStartChildThreadSignals();
EXTERNAL void sysEndThreadSignals(void *stackBuf);
// sysSignal - architecture specific
EXTERNAL void readContextInformation(void *context, Address *instructionPtr,
                                     Address *instructionFollowingPtr,
                                     Address *threadPtr, Address *jtocPtr);
EXTERNAL Address readContextFramePointer(void *context, Address UNUSED threadPtr);
EXTERNAL int readContextTrapCode(void *context, Address threadPtr, int signo, Address instructionPtr, Word *trapInfo);
EXTERNAL void setupDumpStackAndDie(void *context);
EXTERNAL void setupDeliverHardwareException(void *context, Address vmRegisters,
             int trapCode, Word trapInfo,
             Address instructionPtr,
             Address instructionFollowingPtr,
             Address threadPtr, Address jtocPtr,
             Address framePtr, int signo);
EXTERNAL void dumpContext(void *context);
// sysTestcases
EXTERNAL void sysStackAlignmentTest();
EXTERNAL void sysArgumentPassingTest(long long firstLong, long long secondLong, long long thirdLong, long long fourthLong,
    long long fifthLong, long long sixthLong, long long seventhLong, long long eightLong, double firstDouble, double secondDouble,
  double thirdDouble, double fourthDouble, double fifthDouble, double sixthDouble, double seventhDouble,
  double eightDouble, int firstInt, long long ninthLong, const char * firstByteArray, double ninthDouble, Address firstAddress);
EXTERNAL void sysArgumentPassingSeveralLongsAndSeveralDoubles(long long firstLong, long long secondLong, long long thirdLong, long long fourthLong,
    long long fifthLong, long long sixthLong, long long seventhLong, long long eightLong, double firstDouble, double secondDouble,
  double thirdDouble, double fourthDouble, double fifthDouble, double sixthDouble, double seventhDouble, double eightDouble);
EXTERNAL void sysArgumentPassingSeveralFloatsAndSeveralInts(float firstFloat, float secondFloat, float thirdFloat, float fourthFloat,
    float fifthFloat, float sixthFloat, float seventhFloat, float eightFloat, int firstInt, int secondInt,
  int thirdInt, int fourthInt, int fifthInt, int sixthInt, int seventhInt, int eightInt);
// sysThread
EXTERNAL void sysInitialize();
EXTERNAL Word sysMonitorCreate();
EXTERNAL void sysMonitorDestroy(Word);
EXTERNAL int sysMonitorEnter(Word);
EXTERNAL int sysMonitorExit(Word);
EXTERNAL void sysMonitorTimedWait(Word, long long);
EXTERNAL void sysMonitorWait(Word);
EXTERNAL void sysMonitorBroadcast(Word);
EXTERNAL int sysNumProcessors();
EXTERNAL Address sysThreadCreate(Address ip, Address fp, Address tr, Address jtoc);
EXTERNAL void sysStartMainThread(jboolean vmInSeparateThread, Address ip, Address sp, Address tr, Address jtoc, uint32_t *bootCompleted);
EXTERNAL void sysCreateThreadSpecificDataKeys(void);
EXTERNAL void sysStashVMThread(Address vmThread);
EXTERNAL int sysThreadBindSupported();
EXTERNAL void sysThreadBind(int cpuId);
EXTERNAL void * sysThreadStartup(void *args);
EXTERNAL Word sysGetThreadId();
EXTERNAL void sysThreadTerminate();
EXTERNAL void sysMonitorTimedWaitAbsolute(Word _monitor, long long whenWakeupNanos);
EXTERNAL void sysNanoSleep(long long howLongNanos);
EXTERNAL void sysThreadYield();
EXTERNAL Word sysGetThreadPriorityHandle();
EXTERNAL int sysGetThreadPriority(Word thread, Word handle);
EXTERNAL int sysSetThreadPriority(Word thread, Word handle, int priority);
// sysThread - architecture specific
// parameters are architecture specific too.
EXTERNAL void bootThread(void *, void *, void *, void *);
// sysTime
EXTERNAL long long sysCurrentTimeMillis();
EXTERNAL long long sysNanoTime();
// sysVarArgs
EXTERNAL va_list* sysVaCopy(va_list ap);
EXTERNAL void sysVaEnd(va_list *ap);
EXTERNAL jboolean sysVaArgJboolean(va_list *ap);
EXTERNAL jbyte sysVaArgJbyte(va_list *ap);
EXTERNAL jchar sysVaArgJchar(va_list *ap);
EXTERNAL jshort sysVaArgJshort(va_list *ap);
EXTERNAL jint sysVaArgJint(va_list *ap);
EXTERNAL jlong sysVaArgJlong(va_list *ap);
EXTERNAL jfloat sysVaArgJfloat(va_list *ap);
EXTERNAL jdouble sysVaArgJdouble(va_list *ap);
EXTERNAL jobject sysVaArgJobject(va_list *ap);

/** Only called externally from Java programs. */
EXTERNAL void sysExit(int) NORETURN;

/* Routines used elsewhere within bootloader */
EXTERNAL void findMappable();
EXTERNAL Extent pageRoundUp(Extent size, Extent pageSize);

/**
 * FIXME The rest of the file consists of includes for non-linux systems
 * and old systems. Anyone that has access to such a system could help us
 * out by deleting unnecessary definitions from here and moving what is
 * needed to the respective files.
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

/* Extra declarations automatically generated by the generateInterfaceDeclarations */
#define NEED_BOOT_RECORD_DECLARATIONS 1
#define NEED_MEMORY_MANAGER_DECLARATIONS 1
#define NEED_VIRTUAL_MACHINE_DECLARATIONS 1
#define NEED_EXIT_STATUS_CODES 1
#include "InterfaceDeclarations.h"

#endif // RVM_SYSCALL_DEFINITIONS
