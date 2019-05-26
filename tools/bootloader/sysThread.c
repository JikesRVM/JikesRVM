/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include "sys.h"

#include <errno.h> // errno
#include <signal.h> // sigemptyset, sigaltstack
#include <stdlib.h>  // exit, abort
#include <string.h> // memset
#include <sys/resource.h> // getpriority, setpriority and PRIO_PROCESS
#include <setjmp.h> // jmp_buf, longjmp, ...
#include <unistd.h> // pause

#ifdef RVM_FOR_LINUX
#  include <sys/sysinfo.h> // get_nprocs
#  include <sys/ucontext.h>
#endif // def RVM_FOR_LINUX

/** Constant to show that the newly created thread is a child */
static const Address CHILD_THREAD = 0;
/**
 * Constant to show that the newly created thread is the main thread
 * and can terminate after execution (ie. the VM is running as a child
 * thread of a larger process).
 */
static const Address MAIN_THREAD_ALLOW_TERMINATE = 1;
/**
 * Constant to show that the newly created thread is the main thread
 * and shouldn't terminate. This is used when the VM isn't running as
 * part of a larger system and terminating the main thread will stop
 * the process.
 */
static const Address MAIN_THREAD_DONT_TERMINATE = 2;


#ifdef RVM_FOR_HARMONY
#include "hythread.h"
#else
#include <pthread.h>
#endif

#ifndef RVM_FOR_HARMONY
typedef struct {
  pthread_mutex_t mutex;
  pthread_cond_t cond;
} vmmonitor_t;
#endif

EXTERNAL void VMI_Initialize();

Word DeathLock = (Word) NULL;

static int systemExiting = 0;

static const int debugging = 0;

// #define DEBUG_SYS
// #define DEBUG_THREAD

#ifdef RVM_FOR_HARMONY
EXTERNAL int sysThreadStartup(void *args);
#else
EXTERNAL void *sysThreadStartup(void *args);
#endif

EXTERNAL void hardwareTrapHandler(int signo, siginfo_t *si, void *context);

extern TLS_KEY_TYPE VmThreadKey;
static TLS_KEY_TYPE threadDataKey;
static TLS_KEY_TYPE trKey;
static TLS_KEY_TYPE sigStackKey;

void createThreadLocal(TLS_KEY_TYPE *key) {
  int rc;
#ifdef RVM_FOR_HARMONY
  rc = hythread_tls_alloc(key);
#else
  rc = pthread_key_create(key, 0);
#endif
  if (rc != 0) {
    ERROR_PRINTF("%s: alloc tls key failed (err=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
}

/** Create keys for thread-specific data. */
static void createThreadSpecificDataKeys()
{
  TRACE_PRINTF("%s: createThreadSpecificDataKeys\n", Me);

  // Create a key for thread-specific data so we can associate
  // the id of the Processor object with the pthread it is running on.
  createThreadLocal(&VmThreadKey);
  createThreadLocal(&threadDataKey);
  createThreadLocal(&trKey);
  createThreadLocal(&sigStackKey);
  TRACE_PRINTF("%s: vm thread key=%lu\n", Me, (long unsigned int)VmThreadKey);
  TRACE_PRINTF("%s: thread data key key=%lu\n", Me, (long unsigned int)threadDataKey);
  TRACE_PRINTF("%s: thread register key=%lu\n", Me, (long unsigned int)trKey);
  TRACE_PRINTF("%s: sigStack key=%lu\n", Me, (long unsigned int)sigStackKey);
}

void setThreadLocal(TLS_KEY_TYPE key, void * value) {
#ifdef RVM_FOR_HARMONY
  int rc = hythread_tls_set(hythread_self(), key, value);
#else
  int rc = pthread_setspecific(key, value);
#endif
  if (rc != 0) {
    ERROR_PRINTF("%s: set tls failed (err=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
}

EXTERNAL void sysStashVMThread(Address vmThread)
{
  TRACE_PRINTF("%s: sysStashVmProcessorInPthread %p\n", Me, (void*)vmThread);
  setThreadLocal(VmThreadKey, (void*)vmThread);
}

EXTERNAL void * getVmThread()
{
  return GET_THREAD_LOCAL(VmThreadKey);
}

EXTERNAL void sysInitialize()
{
  TRACE_PRINTF("%s: sysInitialize\n", Me);
#ifdef RVM_FOR_HARMONY
  VMI_Initialize();
#endif
  DeathLock = sysMonitorCreate();
}

/** Exit with a return code. */
EXTERNAL void sysExit(int value)
{
  TRACE_PRINTF("%s: sysExit %d\n", Me, value);
  // alignment checking: report info before exiting, then turn off checking
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  if (numEnableAlignCheckingCalls > 0) {
    sysReportAlignmentChecking();
    sysDisableAlignmentChecking();
  }
#endif // RVM_WITH_ALIGNMENT_CHECKING

  if (verbose & (value != 0)) {
    TRACE_PRINTF("%s: exit %d\n", Me, value);
  }

  fflush(SysErrorFile);
  fflush(SysTraceFile);
  fflush(stdout);

  systemExiting = 1;

  if (DeathLock) sysMonitorEnter(DeathLock);
  if (debugging && value!=0) {
    abort();
  }
  exit(value);
}


/**
 * Routine to sleep for a number of nanoseconds (howLongNanos).  This is
 * ridiculous on regular Linux, where we actually only sleep in increments of
 * 1/HZ (1/100 of a second on x86).  Luckily, Linux will round up.
 *
 * This is just used internally in the scheduler, but we might as well make
 * the function work properly even if it gets used for other purposes.
 *
 * We don't return anything, since we don't need to right now.  Just try to
 * sleep; if interrupted, return.
 */
EXTERNAL void sysNanoSleep(long long howLongNanos)
{
  struct timespec req = {0};
  const long long nanosPerSec = 1000LL * 1000 * 1000;
  TRACE_PRINTF("%s: sysNanosleep %lld\n", Me, howLongNanos);
  req.tv_sec = howLongNanos / nanosPerSec;
  req.tv_nsec = howLongNanos % nanosPerSec;
  int ret = nanosleep(&req, (struct timespec *) NULL);
  if (ret < 0) {
    if (errno == EINTR)
      /* EINTR is expected, since we do use signals internally. */
      return;

    ERROR_PRINTF("%s: nanosleep(<tv_sec=%ld,tv_nsec=%ld>) failed:"
                 " %s (errno=%d)\n"
                 "  That should never happen; please report it as a bug.\n",
                 Me, req.tv_sec, req.tv_nsec,
                 strerror( errno ), errno);
  }
  // Done.
}

/**
 * How many physical cpu's are present and actually online?
 * Assume 1 if no other good answer.
 * Taken:     nothing
 * Returned:  number of cpu's
 *
 * Note: this function is only called once.  If it were called more often
 * than that, we would want to use a static variable to indicate that we'd
 * already printed the WARNING messages and were not about to print any more.
 */
EXTERNAL int sysNumProcessors()
{
  static int firstRun = 1;
  int numCpus = -1;  /* -1 means failure. */
  TRACE_PRINTF("%s: sysNumProcessors\n", Me);

#ifdef __GNU_LIBRARY__      // get_nprocs is part of the GNU C library.
  /* get_nprocs_conf will give us a how many processors the operating
     system configured.  The number of processors actually online is what
     we want.  */
  // numCpus = get_nprocs_conf();
  errno = 0;
  numCpus = get_nprocs();
  // It is not clear if get_nprocs can ever return failure; assume it might.
  if (numCpus < 1) {
    if (firstRun) ERROR_PRINTF("%s: WARNING: get_nprocs() returned %d (errno=%d)\n", Me, numCpus, errno);
    /* Continue on.  Try to get a better answer by some other method, not
       that it's likely, but this should not be a fatal error. */
  }
#endif

#if defined(CTL_HW) && defined(HW_NCPU)
  if (numCpus < 1) {
    int mib[2];
    size_t len;
    mib[0] = CTL_HW;
    mib[1] = HW_NCPU;
    len = sizeof(numCpus);
    errno = 0;
    if (sysctl(mib, 2, &numCpus, &len, NULL, 0) < 0) {
      if (firstRun) ERROR_PRINTF("%s: WARNING: sysctl(CTL_HW,HW_NCPU) failed;"
                                     " errno = %d\n", Me, errno);
      numCpus = -1;       // failed so far...
    };
  }
#endif

#if defined(_SC_NPROCESSORS_ONLN)
  if (numCpus < 0) {
    /* This alternative is probably the same as
     *  _system_configuration.ncpus.  This one says how many CPUs are
     *  actually on line.  It seems to be supported on AIX, at least; I
     *  yanked this out of sysVirtualProcessorBind.
     */
    numCpus = sysconf(_SC_NPROCESSORS_ONLN); // does not set errno
    if (numCpus < 0) {
      if (firstRun) CONSOLE_PRINTF("%s: WARNING: sysconf(_SC_NPROCESSORS_ONLN)"
                                     " failed\n", Me);
      }
  }
#endif

  if (numCpus < 0) {
    if (firstRun) TRACE_PRINTF("%s: WARNING: Can not figure out how many CPUs"
                                 " are online; assuming 1\n", Me);
    numCpus = 1;            // Default
  }

#ifdef DEBUG_SYS
  CONSOLE_PRINTF("%s: sysNumProcessors: returning %d\n", Me, numCpus );
#endif
  firstRun = 0;
  return numCpus;
}


/**
 * Create main thread
 *
 * Taken:     vmInSeperateThread [in] should the VM be placed in a
 *              separate thread
 *            ip [in] address of VM.boot method
 *            sp [in,out] address of stack
 *            tr [in,out] address of thread data structure
 *            jtoc [in,out] address of jtoc
 */
EXTERNAL void sysStartMainThread(jboolean vmInSeparateThread, Address ip, Address sp, Address tr, Address jtoc, uint32_t *bootCompleted)
{
  Address        *sysThreadArguments;
#ifndef RVM_FOR_HARMONY
  pthread_attr_t sysThreadAttributes;
  pthread_t      sysThreadHandle;
#else
  hythread_t     sysThreadHandle;
#endif
  int            rc;
  TRACE_PRINTF("%s: sysStartMainThread %d\n", Me, vmInSeparateThread);

  createThreadSpecificDataKeys();

  unblockSIGQUIT();

  /* Set up thread stack - TODO: move to bootimagewriter */
#ifdef RVM_FOR_IA32
  *(Address *) (tr + Thread_framePointer_offset) = (Address)sp - (2*__SIZEOF_POINTER__);
  sp-=__SIZEOF_POINTER__;
  *(uint32_t*)sp = 0xdeadbabe;         /* STACKFRAME_RETURN_ADDRESS_OFFSET */
  sp -= __SIZEOF_POINTER__;
  *(Address*)sp = Constants_STACKFRAME_SENTINEL_FP; /* STACKFRAME_FRAME_POINTER_OFFSET */
  sp -= __SIZEOF_POINTER__;
  ((Address *)sp)[0] = Constants_INVISIBLE_METHOD_ID;    /* STACKFRAME_METHOD_ID_OFFSET */
#else
  Address  fp = sp - Constants_STACKFRAME_HEADER_SIZE;  // size in bytes
  fp = fp & ~(Constants_STACKFRAME_ALIGNMENT -1);     // align fp
  *(Address *)(fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET) = ip;
  *(int *)(fp + Constants_STACKFRAME_METHOD_ID_OFFSET) = Constants_INVISIBLE_METHOD_ID;
  *(Address *)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET) = Constants_STACKFRAME_SENTINEL_FP;
  sp = fp;
#endif

  /* create arguments - memory reclaimed in sysThreadStartup */
  sysThreadArguments = (Address *)checkMalloc(sizeof(Address) * 5);
  sysThreadArguments[0] = ip;
  sysThreadArguments[1] = sp;
  sysThreadArguments[2] = tr;
  sysThreadArguments[3] = jtoc;
  if (!vmInSeparateThread) {
    sysThreadArguments[4] = MAIN_THREAD_DONT_TERMINATE;
    sysThreadStartup(sysThreadArguments);
  } else {
    *bootCompleted = 0;
    sysThreadArguments[4] = MAIN_THREAD_ALLOW_TERMINATE;
#ifndef RVM_FOR_HARMONY
    // create attributes
    rc = pthread_attr_init(&sysThreadAttributes);
    if (rc) {
      ERROR_PRINTF("%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
    pthread_attr_setscope(&sysThreadAttributes, PTHREAD_SCOPE_SYSTEM);
#endif
    // create native thread
#ifdef RVM_FOR_HARMONY
    rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0,
                         (hythread_entrypoint_t)sysThreadStartup,
                         sysThreadArguments);
#else
    rc = pthread_create(&sysThreadHandle,
                        &sysThreadAttributes,
                        sysThreadStartup,
                        sysThreadArguments);
#endif
    if (rc)
    {
      ERROR_PRINTF("%s: thread_create failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#ifndef RVM_FOR_HARMONY
    rc = pthread_detach(sysThreadHandle);
    if (rc)
    {
      ERROR_PRINTF("%s: pthread_detach failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif
    /* exit start up when VM has booted */
    while (*bootCompleted == 0) {
      sysThreadYield();
    }
  }
}


/**
 * Creates a native thread.
 * Taken:     ip [in] address of first instruction
 *            fp [in] address of stack
 *            tr [in] address for thread register
 *            jtoc [in] address for the jtoc register
 * Returned:  OS handle
 */
EXTERNAL Address sysThreadCreate(Address ip, Address fp, Address tr, Address jtoc)
{
  Address    *sysThreadArguments;
  int            rc;

  TRACE_PRINTF("%s: sysThreadCreate %p %p %p %p\n", Me,
      (void*)ip, (void*)fp, (void*)tr, (void*)jtoc);

  // create arguments
  //
  sysThreadArguments = (Address*) checkMalloc(sizeof(Address) * 5);
  sysThreadArguments[0] = ip;
  sysThreadArguments[1] = fp;
  sysThreadArguments[2] = tr;
  sysThreadArguments[3] = jtoc;
  sysThreadArguments[4] = CHILD_THREAD;


#ifdef RVM_FOR_HARMONY
  hythread_t      sysThreadHandle;

  if ((rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0, sysThreadStartup, sysThreadArguments)))
  {
    ERROR_PRINTF("%s: hythread_create failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#else
  pthread_attr_t sysThreadAttributes;
  pthread_t      sysThreadHandle;

  // create attributes
  //
  if ((rc = pthread_attr_init(&sysThreadAttributes))) {
    ERROR_PRINTF("%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

  // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
  //
  pthread_attr_setscope(&sysThreadAttributes, PTHREAD_SCOPE_SYSTEM);

  // create native thread
  //
  if ((rc = pthread_create(&sysThreadHandle,
                           &sysThreadAttributes,
                           sysThreadStartup,
                           sysThreadArguments)))
  {
    ERROR_PRINTF("%s: pthread_create failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

  if ((rc = pthread_detach(sysThreadHandle)))
  {
    ERROR_PRINTF("%s: pthread_detach failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  TRACE_PRINTF("%s: pthread_create %p\n", Me, (void*)sysThreadHandle);
#endif

  return (Word)sysThreadHandle;
}

EXTERNAL int sysThreadBindSupported()
{
  int result=0;
  TRACE_PRINTF("%s: sysThreadBindSupported\n", Me);
#ifdef RVM_FOR_LINUX
  result=1;
#endif
  return result;
}

EXTERNAL void sysThreadBind(int cpuId)
{
  TRACE_PRINTF("%s: sysThreadBind\n", Me);
#ifndef RVM_FOR_HARMONY
#ifdef RVM_FOR_LINUX
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);
  CPU_SET(cpuId, &cpuset);

  pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset);
#endif
#endif
}

/**
 * Function called by pthread startup
 *
 * Taken:     args [in] encoded of initial register values and main thread
 *              controls
 * Returned:  ignored
 */
#ifdef RVM_FOR_HARMONY
EXTERNAL int sysThreadStartup(void *args)
#else
EXTERNAL void * sysThreadStartup(void *args)
#endif
{
  Address jtoc, tr, ip, fp, threadData;
  void* sigStack;

  ip = ((Address *)args)[0];
  fp = ((Address *)args)[1];
  tr = ((Address *)args)[2];
  jtoc = ((Address *)args)[3];
  threadData = ((Address *)args)[4];
  TRACE_PRINTF("%s: sysThreadStartup: ip=%p fp=%p tr=%p jtoc=%p data=%d\n",
        Me, (void*)ip, (void*)fp, (void*)tr, (void*)jtoc, (int)threadData);
  checkFree(args);

  setThreadLocal(threadDataKey, (void *)threadData);
  setThreadLocal(trKey, (void *)tr);
  if (threadData == CHILD_THREAD) {
    sigStack = sysStartChildThreadSignals();
#ifdef RVM_FOR_IA32 /* TODO: refactor */
    *(Address *)(tr + Thread_framePointer_offset) = fp;
    fp = fp + Constants_STACKFRAME_BODY_OFFSET;
#endif
  } else {
    sigStack = sysStartMainThreadSignals();
  }
  setThreadLocal(sigStackKey, (void *)sigStack);
  TRACE_PRINTF("%s: sysThreadStartup: booting\n", Me);

  // branch to vm code
  bootThread((void*)ip, (void*)tr, (void*)fp, (void*)jtoc);
  // not reached
  ERROR_PRINTF("%s: sysThreadStartup: failed\n", Me);
#ifdef RVM_FOR_HARMONY
return 0;
#else
return NULL;
#endif
}

// Routines to support sleep/wakeup of idle threads

EXTERNAL void* getThreadId()
{

#ifdef RVM_FOR_HARMONY
  void* thread = (void*)hythread_self();
#else
  void* thread = (void*)pthread_self();
#endif

  TRACE_PRINTF("%s: getThreadId: thread %p\n", Me, thread);
  return thread;
}

/**
 * sysGetThreadId() just returns the thread ID of the current thread.
 *
 * This happens to be only called once, at thread startup time, but please
 * don't rely on that fact.
 *
 */
EXTERNAL Word sysGetThreadId()
{
  TRACE_PRINTF("%s: sysGetThreadId\n", Me);
  return (Word) getThreadId();
}

/**
 * Yields execution back to o/s.
 */
EXTERNAL void sysThreadYield()
{
  TRACE_PRINTF("%s: sysThreadYield\n", Me);

  /** According to the Linux manpage, sched_yield()'s presence can be
   *  tested for by using the #define _POSIX_PRIORITY_SCHEDULING, and if
   *  that is not present to use the sysconf feature, searching against
   *  _SC_PRIORITY_SCHEDULING.  However, this may not be reliable, since
   *  the AIX 5.1 include files include this definition:
   *      ./unistd.h:#undef _POSIX_PRIORITY_SCHEDULING
   *  so it is likely that this is not implemented properly.
   */
#ifdef RVM_FOR_HARMONY
  hythread_yield();
#else
  sched_yield();
#endif
}

/**
 * Determine if a given thread can use pthread_setschedparam to
 * configure its priority, this is based on the current priority
 * of the thread.
 *
 * The result will be true on all systems other than Linux where
 * pthread_setschedparam cannot be used with SCHED_OTHER policy.
 */
static int hasPthreadPriority(Word thread_id)
{
  struct sched_param param = {0};
  int policy = 0;
  if (!pthread_getschedparam((pthread_t)thread_id, &policy, &param)) {
    int min = sched_get_priority_min(policy);
    int max = sched_get_priority_max(policy);
    if (min || max) {
      return 1;
    }
  }
  return 0;
}

/**
 * Return a handle which can be used to manipulate a threads priority
 * on Linux this will be the kernel thread_id, on other systems the
 * standard thread id.
 */
EXTERNAL Word sysGetThreadPriorityHandle()
{
  TRACE_PRINTF("%s: sysGetThreadPriorityHandle\n", Me);
  // gettid() syscall is Linux specific, detect its syscall number macro
#ifdef SYS_gettid
  pid_t tid = (pid_t) syscall(SYS_gettid);
  if (tid != -1)
    return (Word) tid;
#endif /* SYS_gettid */
  return (Word) getThreadId();
}

/**
 * Compute the default (or middle) priority for a given policy.
 */
static int defaultPriority(int policy)
{
  int min = sched_get_priority_min(policy);
  int max = sched_get_priority_max(policy);
  return min + ((max - min) / 2);
}

/**
 * Get the thread priority as an offset from the default.
 */
EXTERNAL int sysGetThreadPriority(Word thread, Word handle)
{
  TRACE_PRINTF("%s: sysGetThreadPriority\n", Me);
  // use pthread priority mechanisms where possible
  if (hasPthreadPriority(thread)) {
    struct sched_param param = {0};
    int policy = 0;
    if (!pthread_getschedparam((pthread_t)thread, &policy, &param)) {
      return param.sched_priority - defaultPriority(policy);
    }
  } else if (thread != handle) {
    // fallback to setpriority if handle is valid
    // i.e. handle is tid from gettid()
    int result;
    errno = 0; // as result can be legally be -1
    result = getpriority(PRIO_PROCESS, (int) handle);
    if (errno == 0) {
      // default priority is 0, low number -> high priority
      return -result;
    }

  }
  return 0;
}

/**
 * Set the thread priority as an offset from the default.
 */
EXTERNAL int sysSetThreadPriority(Word thread, Word handle, int priority)
{
  TRACE_PRINTF("%s: sysSetThreadPriority\n", Me);
  // fast path
  if (sysGetThreadPriority(thread, handle) == priority)
    return 0;

  // use pthread priority mechanisms where possible
  if (hasPthreadPriority(thread)) {
    struct sched_param param = {0};
    int policy = 0;
    int result = pthread_getschedparam((pthread_t)thread, &policy, &param);
    if (!result) {
      param.sched_priority = defaultPriority(policy) + priority;
      return pthread_setschedparam((pthread_t)thread, policy, &param);
    } else {
      return result;
    }
  } else if (thread != handle) {
    // fallback to setpriority if handle is valid
    // i.e. handle is tid from gettid()
    // default priority is 0, low number -> high priority
    return setpriority(PRIO_PROCESS, (int) handle, -priority);
  }
  return -1;
}

////////////// Pthread mutex and condition functions /////////////

EXTERNAL Word sysMonitorCreate()
{
#ifdef RVM_FOR_HARMONY
  hythread_monitor_t monitor;
  hythread_monitor_init_with_name(&monitor, 0, NULL);
#else
  vmmonitor_t *monitor = (vmmonitor_t*) checkMalloc(sizeof(vmmonitor_t));
  pthread_mutex_init(&monitor->mutex, NULL);
  pthread_cond_init(&monitor->cond, NULL);
#endif
  TRACE_PRINTF("%s: sysMonitorCreate %p\n", Me, (void*)monitor);
  return (Word)monitor;
}

EXTERNAL void sysMonitorDestroy(Word _monitor)
{
#ifdef RVM_FOR_HARMONY
  hythread_monitor_destroy((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_mutex_destroy(&monitor->mutex);
  pthread_cond_destroy(&monitor->cond);
  checkFree(monitor);
#endif
  TRACE_PRINTF("%s: sysMonitorDestroy %p\n", Me, (void*)_monitor);
}

EXTERNAL int sysMonitorEnter(Word _monitor)
{
  TRACE_PRINTF("%s: sysMonitorEnter %p\n", Me, (void*)_monitor);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_enter((hythread_monitor_t)_monitor);
  return 0;
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  return pthread_mutex_lock(&monitor->mutex);
#endif
}

EXTERNAL int sysMonitorExit(Word _monitor)
{
  TRACE_PRINTF("%s: sysMonitorExit %p\n", Me, (void*)_monitor);
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  return pthread_mutex_unlock(&monitor->mutex);
}

EXTERNAL void sysMonitorTimedWaitAbsolute(Word _monitor, long long whenWakeupNanos)
{
  TRACE_PRINTF("%s: sysMonitorTimedWaitAbsolute\n", Me);
  struct timespec ts = {0};
  ts.tv_sec = (time_t)(whenWakeupNanos/1000000000LL);
  ts.tv_nsec = (long)(whenWakeupNanos%1000000000LL);
#ifdef DEBUG_THREAD
  TRACE_PRINTF("starting wait at %lld until %lld (%ld, %ld)\n",
               sysNanoTime(),whenWakeupNanos,ts.tv_sec,ts.tv_nsec);
  fflush(NULL);
#endif
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_cond_timedwait(&monitor->cond, &monitor->mutex, &ts);
#ifdef DEBUG_THREAD
  TRACE_PRINTF("returned from wait at %lld instead of %lld with res = %d\n",
               sysNanoTime(),whenWakeupNanos,rc);
  fflush(NULL);
#endif
}

EXTERNAL void sysMonitorWait(Word _monitor)
{
  TRACE_PRINTF("%s: sysMonitorWait\n", Me);
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_cond_wait(&monitor->cond, &monitor->mutex);
}

EXTERNAL void sysMonitorBroadcast(Word _monitor)
{
  TRACE_PRINTF("%s: sysMonitorBroadcast\n", Me);
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_cond_broadcast(&monitor->cond);
}

EXTERNAL void sysThreadTerminate()
{
  TRACE_PRINTF("%s: sysThreadTerminate\n", Me);
#ifdef RVM_FOR_POWERPC
  asm("sync");
#endif
  Address tr = (Address) GET_THREAD_LOCAL(trKey);
  *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;
  Address threadData = (Address) GET_THREAD_LOCAL(threadDataKey);
  void * sigStack = (void *) GET_THREAD_LOCAL(sigStackKey);
  sysEndThreadSignals(sigStack);
  if (threadData == MAIN_THREAD_DONT_TERMINATE) {
    while(1) pause();
  }
  pthread_exit(NULL);
}
