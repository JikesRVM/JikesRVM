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

/**
 * O/S support services required by the java class libraries.
 * See also: BootRecord.java
 */

// Aix and Linux version.  PowerPC and IA32.

#include "sys.h"

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//
#ifdef _AIX43
#include </usr/include/unistd.h>
EXTERNAL void profil(void *, uint, ulong, uint);
EXTERNAL int sched_yield(void);
#endif

#include <stdio.h>
#include <stdlib.h>      // getenv() and others
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/resource.h> // getpriority, setpriority and PRIO_PROCESS
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>               // nanosleep() and other
#include <utime.h>
#include <setjmp.h>

#ifdef __GLIBC__
/* use glibc internal longjmp to bypass fortify checks */
EXTERNAL void __libc_longjmp (jmp_buf buf, int val) \
                    __attribute__ ((__noreturn__));
#define rvm_longjmp(buf, ret) \
        __libc_longjmp(buf, ret)
#else
#define rvm_longjmp(buf, ret) \
        longjmp(buf, ret)
#endif /* !__GLIBC__ */



#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_SOLARIS) 
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#ifdef RVM_FOR_LINUX
#include <asm/ioctls.h>
#include <sys/syscall.h>
#endif

# include <sched.h>

/* OSX/Darwin */
#elif (defined __MACH__)
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

/* AIX/PowerPC */
#else
#include <sys/cache.h>
#include <sys/ioctl.h>
#endif

#include <sys/shm.h>        /* disclaim() */
#include <strings.h>        /* bzero() */
#include <sys/mman.h>       /* mmap & munmap() */
#include <sys/shm.h>
#include <errno.h>
#include <dlfcn.h>
#include <inttypes.h>           // uintptr_t

#ifdef _AIX
EXTERNAL timer_t gettimerid(int timer_type, int notify_type);
EXTERNAL int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#include <jni.h>

#ifdef RVM_FOR_HARMONY
#include "hythread.h"
#else
#include <pthread.h>
#endif

EXTERNAL Word sysMonitorCreate();
EXTERNAL void sysMonitorDestroy(Word);
EXTERNAL void sysMonitorEnter(Word);
EXTERNAL void sysMonitorExit(Word);
EXTERNAL void sysMonitorTimedWait(Word, long long);
EXTERNAL void sysMonitorWait(Word);
EXTERNAL void sysMonitorBroadcast(Word);

// #define DEBUG_SYS
// #define DEBUG_THREAD

//#define DEBUG_OPENJDK

// static int TimerDelay  =  10; // timer tick interval, in milliseconds     (10 <= delay <= 999)
// static int SelectDelay =   2; // pause time for select(), in milliseconds (0  <= delay <= 999)

static int openjdkVerbose = 0;

#ifdef RVM_FOR_HARMONY
EXTERNAL int sysThreadStartup(void *args);
#else
EXTERNAL void *sysThreadStartup(void *args);
#endif

EXTERNAL void hardwareTrapHandler(int signo, siginfo_t *si, void *context);

extern TLS_KEY_TYPE VmThreadKey;
TLS_KEY_TYPE TerminateJmpBufKey;

TLS_KEY_TYPE createThreadLocal() {
    TLS_KEY_TYPE key;
    int rc;
#ifdef RVM_FOR_HARMONY
    rc = hythread_tls_alloc(&key);
#else
    rc = pthread_key_create(&key, 0);
#endif
    if (rc != 0) {
        CONSOLE_PRINTF(SysErrorFile, "%s: alloc tls key failed (err=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    return key;
}

/** Create keys for thread-specific data. */
EXTERNAL void sysCreateThreadSpecificDataKeys(void)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysCreateThreadSpecificDataKeys\n", Me);
    int rc;

    // Create a key for thread-specific data so we can associate
    // the id of the Processor object with the pthread it is running on.
    VmThreadKey = createThreadLocal();
    TerminateJmpBufKey = createThreadLocal();
    TRACE_PRINTF(stderr, "%s: vm processor key=%lu\n", Me, VmThreadKey);
}

void setThreadLocal(TLS_KEY_TYPE key, void * value) {
#ifdef RVM_FOR_HARMONY
    int rc = hythread_tls_set(hythread_self(), key, value);
#else
    int rc = pthread_setspecific(key, value);
#endif
    if (rc != 0) {
        CONSOLE_PRINTF(SysErrorFile, "%s: set tls failed (err=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
}

EXTERNAL void sysStashVMThread(Address vmThread)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysStashVmProcessorInPthread %p\n", Me, vmThread);
    setThreadLocal(VmThreadKey, (void*)vmThread);
}

EXTERNAL void * getVmThread()
{
    return GET_THREAD_LOCAL(VmThreadKey);
}



Word DeathLock = NULL;

EXTERNAL void VMI_Initialize();

EXTERNAL void sysInitialize()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysInitialize\n", Me);
#ifdef RVM_FOR_HARMONY
    VMI_Initialize();
#endif
    DeathLock = sysMonitorCreate();
}

static bool systemExiting = false;

static const bool debugging = false;

/** Exit with a return code. */
EXTERNAL void sysExit(int value)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysExit %d\n", Me, value);
    // alignment checking: report info before exiting, then turn off checking
    #ifdef RVM_WITH_ALIGNMENT_CHECKING
    if (numEnableAlignCheckingCalls > 0) {
      sysReportAlignmentChecking();
      sysDisableAlignmentChecking();
    }
    #endif // RVM_WITH_ALIGNMENT_CHECKING

    if (lib_verbose & value != 0) {
        CONSOLE_PRINTF(SysErrorFile, "%s: exit %d\n", Me, value);
    }

    fflush(SysErrorFile);
    fflush(SysTraceFile);
    fflush(stdout);

    systemExiting = true;

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
    struct timespec req;
    const long long nanosPerSec = 1000LL * 1000 * 1000;
    TRACE_PRINTF(SysTraceFile, "%s: sysNanosleep %lld\n", Me, howLongNanos);
    req.tv_sec = howLongNanos / nanosPerSec;
    req.tv_nsec = howLongNanos % nanosPerSec;
    int ret = nanosleep(&req, (struct timespec *) NULL);
    if (ret < 0) {
        if (errno == EINTR)
            /* EINTR is expected, since we do use signals internally. */
            return;

        CONSOLE_PRINTF(SysErrorFile, "%s: nanosleep(<tv_sec=%ld,tv_nsec=%ld>) failed:"
                " %s (errno=%d)\n"
                "  That should never happen; please report it as a bug.\n",
                Me, req.tv_sec, req.tv_nsec,
                strerror( errno ), errno);
    }
    // Done.
}


//-----------------------//
// Processor operations. //
//-----------------------//

#ifdef _AIX
#include <sys/systemcfg.h>
#endif

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
    TRACE_PRINTF(SysTraceFile, "%s: sysNumProcessors\n", Me);  

#ifdef __GNU_LIBRARY__      // get_nprocs is part of the GNU C library.
    /* get_nprocs_conf will give us a how many processors the operating
       system configured.  The number of processors actually online is what
       we want.  */
    // numCpus = get_nprocs_conf();
    errno = 0;
    numCpus = get_nprocs();
    // It is not clear if get_nprocs can ever return failure; assume it might.
    if (numCpus < 1) {
       if (firstRun) CONSOLE_PRINTF(SysTraceFile, "%s: WARNING: get_nprocs() returned %d (errno=%d)\n", Me, numCpus, errno);
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
            if (firstRun) CONSOLE_PRINTF(SysTraceFile, "%s: WARNING: sysctl(CTL_HW,HW_NCPU) failed;"
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
            if (firstRun) CONSOLE_PRINTF(SysTraceFile, "%s: WARNING: sysconf(_SC_NPROCESSORS_ONLN)"
                    " failed\n", Me);
        }
    }
#endif

#ifdef _AIX
    if (numCpus < 0) {
        numCpus = _system_configuration.ncpus;
        if (numCpus < 0) {
            if (firstRun) CONSOLE_PRINTF(SysTraceFile, "%s: WARNING: _system_configuration.ncpus"
                    " has the insane value %d\n" , Me, numCpus);
        }
    }
#endif

    if (numCpus < 0) {
        if (firstRun) TRACE_PRINTF(SysTraceFile, "%s: WARNING: Can not figure out how many CPUs"
                              " are online; assuming 1\n", Me);
        numCpus = 1;            // Default
    }

#ifdef DEBUG_SYS
    CONSOLE_PRINTF(SysTraceFile, "%s: sysNumProcessors: returning %d\n", Me, numCpus );
#endif
    firstRun = 0;
    return numCpus;
}

/**
 * Creates a native thread.
 * Taken:     register values to use for pthread startup
 * Returned:  virtual processor's OS handle
 */
EXTERNAL Word sysThreadCreate(Address tr, Address ip, Address fp)
{
    Address    *sysThreadArguments;
    int            rc;
    
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadCreate %p %p %p\n", Me, tr, ip, fp);

    // create arguments
    //
    sysThreadArguments = (Address*) checkMalloc(sizeof(Address) * 3);
    sysThreadArguments[0] = tr;
    sysThreadArguments[1] = ip;
    sysThreadArguments[2] = fp;

#ifdef RVM_FOR_HARMONY
    hythread_t      sysThreadHandle;

    if ((rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0, sysThreadStartup, sysThreadArguments)))
    {
        CONSOLE_PRINTF(SysErrorFile, "%s: hythread_create failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#else
    pthread_attr_t sysThreadAttributes;
    pthread_t      sysThreadHandle;

    // create attributes
    //
    if ((rc = pthread_attr_init(&sysThreadAttributes))) {
        CONSOLE_PRINTF(SysErrorFile, "%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
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
        CONSOLE_PRINTF(SysErrorFile, "%s: pthread_create failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    if ((rc = pthread_detach(sysThreadHandle)))
    {
        CONSOLE_PRINTF(SysErrorFile, "%s: pthread_detach failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    TRACE_PRINTF(SysTraceFile, "%s: pthread_create 0x%08x\n", Me, (Address) sysThreadHandle);
#endif

    return (Word)sysThreadHandle;
}

EXTERNAL int sysThreadBindSupported()
{
  int result=0;
  TRACE_PRINTF(SysTraceFile, "%s: sysThreadBindSupported\n", Me);
#ifdef RVM_FOR_AIX
  result=1;
#endif
#ifdef RVM_FOR_LINUX
  result=1;
#endif
  return result;
}

EXTERNAL void sysThreadBind(int cpuId)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadBind\n", Me);
    // bindprocessor() seems to be only on AIX
#ifdef RVM_FOR_AIX
    int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
    CONSOLE_PRINTF(SysTraceFile, "%s: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", Me, pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

    if (rc) {
        CONSOLE_PRINTF(SysErrorFile, "%s: bindprocessor failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#ifndef RVM_FOR_HARMONY
#ifdef RVM_FOR_LINUX
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(cpuId, &cpuset);

    pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset);
#endif
#endif
}

#ifdef RVM_FOR_HARMONY
EXTERNAL int sysThreadStartup(void *args)
#else
EXTERNAL void * sysThreadStartup(void *args)
#endif
{
    /* install a stack for hardwareTrapHandler() to run on */
    stack_t stack;
    char *stackBuf;

    memset (&stack, 0, sizeof stack);
    stack.ss_sp = stackBuf = (char*) checkMalloc(sizeof(char) * SIGSTKSZ);
    stack.ss_flags = 0;
    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        CONSOLE_PRINTF(stderr,"sigaltstack failed (errno=%d)\n",errno);
        exit(1);
    }

    Address tr       = ((Address *)args)[0];

    jmp_buf *jb = (jmp_buf*)checkMalloc(sizeof(jmp_buf));
    if (setjmp(*jb)) {
        // this is where we come to terminate the thread
#ifdef RVM_FOR_HARMONY
        hythread_detach(NULL);
#endif
        checkFree(jb);
        *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;

        // disable the signal stack (first retreiving the current one)
        sigaltstack(0, &stack);
        stack.ss_flags = SS_DISABLE;
        sigaltstack(&stack, 0);

        // check if the signal stack is the one in stackBuf
        if (stack.ss_sp != stackBuf) {
            // no; release it as well
            checkFree(stack.ss_sp);
        }

        // release signal stack allocated here
        checkFree(stackBuf);
        // release arguments
        checkFree(args);
    } else {
        setThreadLocal(TerminateJmpBufKey, (void*)jb);

        Address ip       = ((Address *)args)[1];
        Address fp       = ((Address *)args)[2];

        TRACE_PRINTF(SysTraceFile, "%s: sysThreadStartup: pr=%p ip=%p fp=%p\n", Me, tr, ip, fp);

        // branch to vm code
        //
#ifndef RVM_FOR_POWERPC
        {
            *(Address *) (tr + Thread_framePointer_offset) = fp;
            Address sp = fp + Constants_STACKFRAME_BODY_OFFSET;
            bootThread((void*)ip, (void*)tr, (void*)sp);
        }
#else
        bootThread((int)(Word)getJTOC(), tr, ip, fp);
#endif

        // not reached
        //
        CONSOLE_PRINTF(SysTraceFile, "%s: sysThreadStartup: failed\n", Me);
    }
#ifdef RVM_FOR_HARMONY
    return 0;
#else
    return NULL;
#endif
}

// Routines to support sleep/wakeup of idle threads

/**
 * sysGetThreadId() just returns the thread ID of the current thread.
 *
 * This happens to be only called once, at thread startup time, but please
 * don't rely on that fact.
 *
 */
EXTERNAL Word sysGetThreadId()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadId\n", Me);
    return (Word)getThreadId();
}

EXTERNAL void* getThreadId()
{
    
#ifdef RVM_FOR_HARMONY
    void* thread = (void*)hythread_self();
#else
    void* thread = (void*)pthread_self();
#endif

    TRACE_PRINTF(SysTraceFile, "%s: getThreadId: thread %x\n", Me, thread);
    return thread;
}

/**
 * Perform some initialization related to
 * per-thread signal handling for that thread. (Block SIGCONT, set up a special
 * signal handling stack for the thread.)
 *
 * This is only called once, at thread startup time.
 */
EXTERNAL void sysSetupHardwareTrapHandler()
{
    int rc;                     // retval from subfunction.

#ifndef RVM_FOR_AIX
    /*
     *  Provide space for this pthread to process exceptions.  This is
     * needed on Linux because multiple pthreads can handle signals
     * concurrently, since the masking of signals during handling applies
     * on a per-pthread basis.
     */
    stack_t stack;

    memset (&stack, 0, sizeof stack);
    stack.ss_sp = (char*) checkMalloc(sizeof(char) * SIGSTKSZ);

    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        /* Only fails with EINVAL, ENOMEM, EPERM */
        CONSOLE_PRINTF (SysErrorFile, "sigaltstack failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }
#endif

    /*
     * Block the CONT signal.  This makes SIGCONT reach this
     * pthread only when this pthread performs a sigwait().
     */
    sigset_t input_set, output_set;
    sigemptyset(&input_set);
    sigaddset(&input_set, SIGCONT);

#ifdef RVM_FOR_AIX
    rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
    /* like pthread_sigmask, sigthreadmask can only return EINVAL, EFAULT, and
     * EPERM.  Again, these are all good reasons to complain and croak. */
#else
    rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
    /* pthread_sigmask can only return the following errors.  Either of them
     * indicates serious trouble and is grounds for aborting the process:
     * EINVAL EFAULT.  */
#endif
    if (rc) {
        CONSOLE_PRINTF (SysErrorFile, "pthread_sigmask or sigthreadmask failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }

}

/**
 * Yields execution back to o/s.
 */
EXTERNAL void sysThreadYield()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadYield\n", Me);

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
    struct sched_param param;
    int policy;
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
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadPriorityHandle\n", Me);
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
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadPriority\n", Me);
    // use pthread priority mechanisms where possible
    if (hasPthreadPriority(thread)) {
        struct sched_param param;
        int policy;
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
    TRACE_PRINTF(SysTraceFile, "%s: sysSetThreadPriority\n", Me);
    // fast path
    if (sysGetThreadPriority(thread, handle) == priority)
        return 0;

    // use pthread priority mechanisms where possible
    if (hasPthreadPriority(thread)) {
        struct sched_param param;
        int policy;
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

#ifndef RVM_FOR_HARMONY
typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} vmmonitor_t;
#endif

EXTERNAL Word sysMonitorCreate()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorCreate\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_t monitor;
    hythread_monitor_init_with_name(&monitor, 0, NULL);
#else
    vmmonitor_t *monitor = (vmmonitor_t*) checkMalloc(sizeof(vmmonitor_t));
    pthread_mutex_init(&monitor->mutex, NULL);
    pthread_cond_init(&monitor->cond, NULL);
#endif
    return (Word)monitor;
}

EXTERNAL void sysMonitorDestroy(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorDestroy\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_destroy((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_destroy(&monitor->mutex);
    pthread_cond_destroy(&monitor->cond);
    checkFree(monitor);
#endif
}

EXTERNAL void sysMonitorEnter(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorEnter\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_enter((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_lock(&monitor->mutex);
#endif
}

EXTERNAL void sysMonitorExit(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorExit\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_exit((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_unlock(&monitor->mutex);
#endif
}

EXTERNAL void sysMonitorTimedWaitAbsolute(Word _monitor, long long whenWakeupNanos)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorTimedWaitAbsolute\n", Me);
#ifdef RVM_FOR_HARMONY
    // syscall wait is absolute, but harmony monitor wait is relative.
    whenWakeupNanos -= sysNanoTime();
    if (whenWakeupNanos <= 0) return;
    hythread_monitor_wait_timed((hythread_monitor_t)_monitor, (I_64)(whenWakeupNanos / 1000000LL), (IDATA)(whenWakeupNanos % 1000000LL));
#else
    timespec ts;
    ts.tv_sec = (time_t)(whenWakeupNanos/1000000000LL);
    ts.tv_nsec = (long)(whenWakeupNanos%1000000000LL);
#ifdef DEBUG_THREAD
      CONSOLE_PRINTF(stderr, "starting wait at %lld until %lld (%ld, %ld)\n",
             sysNanoTime(),whenWakeupNanos,ts.tv_sec,ts.tv_nsec);
      fflush(stderr);
#endif
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    int rc = pthread_cond_timedwait(&monitor->cond, &monitor->mutex, &ts);
#ifdef DEBUG_THREAD
      CONSOLE_PRINTF(stderr, "returned from wait at %lld instead of %lld with res = %d\n",
             sysNanoTime(),whenWakeupNanos,rc);
      fflush(stderr);
#endif
#endif
}

EXTERNAL void sysMonitorWait(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorWait\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_wait((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_cond_wait(&monitor->cond, &monitor->mutex);
#endif
}

EXTERNAL void sysMonitorBroadcast(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorBroadcast\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_notify_all((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_cond_broadcast(&monitor->cond);
#endif
}

EXTERNAL void sysThreadTerminate()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadTerminate\n", Me);
#ifdef RVM_FOR_POWERPC
    asm("sync");
#endif
    jmp_buf *jb = (jmp_buf*)GET_THREAD_LOCAL(TerminateJmpBufKey);
    if (jb==NULL) {
        jb=&primordial_jb;
    }
    rvm_longjmp(*jb,1);
}


//----------------//
// JNI operations //
//----------------//


EXTERNAL int getArrayLength(void* ptr)
{
    return *(int*)(((char *)ptr) + ObjectModel_ARRAY_LENGTH_OFFSET);
}

///////////////////////////JVM_Native interfaces///////////////////

EXTERNAL {

#define JVM_INTERFACE_VERSION (4)

JNIEXPORT jint JNICALL
JVM_GetInterfaceVersion(void)
{
	return JVM_INTERFACE_VERSION;
}

JNIEXPORT jint JNICALL
JVM_IHashCode(JNIEnv *env, jobject obj)
{
	printf("JVM_IHashCode(JNIEnv *env, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms)
{
	printf("JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms)");
}

JNIEXPORT void JNICALL
JVM_MonitorNotify(JNIEnv *env, jobject obj)
{
	printf("JVM_MonitorNotify(JNIEnv *env, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_MonitorNotifyAll(JNIEnv *env, jobject obj)
{
	printf("JVM_MonitorNotifyAll(JNIEnv *env, jobject obj)");
}

JNIEXPORT jobject JNICALL
JVM_Clone(JNIEnv *env, jobject obj)
{
	printf("JVM_Clone(JNIEnv *env, jobject obj)");
}

JNIEXPORT jstring JNICALL
JVM_InternString(JNIEnv *env, jstring str)
{
	printf("JVM_InternString(JNIEnv *env, jstring str)");

}

JNIEXPORT jlong JNICALL
JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored)
{
	printf("JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored)");
}

JNIEXPORT jlong JNICALL
JVM_NanoTime(JNIEnv *env, jclass ignored)
{
	printf("JVM_NanoTime(JNIEnv *env, jclass ignored)");
}

JNIEXPORT void JNICALL
JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length)
{
	printf("JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_InitProperties(JNIEnv *env, jobject p)
{
	printf("JVM_InitProperties(JNIEnv *env, jobject p)");
}

JNIEXPORT void JNICALL
JVM_OnExit(void (*func)(void))
{
	printf("JVM_OnExit(void (*func)(void))");
}

JNIEXPORT void JNICALL
JVM_Exit(jint code)
{
	printf("JVM_Exit(jint code)");
}

JNIEXPORT void JNICALL
JVM_Halt(jint code)
{
	printf("JVM_Halt(jint code)");
}

JNIEXPORT void JNICALL
JVM_GC(void)
{
	printf("JVM_GC(void)");
}

JNIEXPORT jlong JNICALL
JVM_MaxObjectInspectionAge(void)
{
	printf("JVM_MaxObjectInspectionAge(void)");
}

JNIEXPORT void JNICALL
JVM_TraceInstructions(jboolean on)
{
	printf("JVM_TraceInstructions(jboolean on)");
}

JNIEXPORT void JNICALL
JVM_TraceMethodCalls(jboolean on)
{
	printf("JVM_TraceMethodCalls(jboolean on)");
}

JNIEXPORT jlong JNICALL
JVM_TotalMemory(void)
{
	printf("JVM_TotalMemory(void)");
}

JNIEXPORT jlong JNICALL
JVM_FreeMemory(void)
{
	printf("JVM_FreeMemory(void)");
}

JNIEXPORT jlong JNICALL
JVM_MaxMemory(void)
{
	printf("JVM_MaxMemory(void)");
}

JNIEXPORT jint JNICALL
JVM_ActiveProcessorCount(void)
{
	printf("JVM_ActiveProcessorCount(void)");
}

JNIEXPORT void * JNICALL
JVM_LoadLibrary(const char *name)
{
	printf("JVM_LoadLibrary(const char *name)");
}

JNIEXPORT void JNICALL
JVM_UnloadLibrary(void * handle)
{
	printf("JVM_UnloadLibrary(void * handle)");
}

JNIEXPORT void * JNICALL
JVM_FindLibraryEntry(void *handle, const char *name)
{
	printf("JVM_FindLibraryEntry(void *handle, const char *name)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSupportedJNIVersion(jint version)
{
	printf("JVM_IsSupportedJNIVersion(jint version)");
}

JNIEXPORT jboolean JNICALL
JVM_IsNaN(jdouble d)
{
	printf("JVM_IsNaN(jdouble d)");
}

JNIEXPORT void JNICALL
JVM_FillInStackTrace(JNIEnv *env, jobject throwable)
{
	printf("JVM_FillInStackTrace(JNIEnv *env, jobject throwable)");
}

JNIEXPORT void JNICALL
JVM_PrintStackTrace(JNIEnv *env, jobject throwable, jobject printable)
{
	printf("JVM_PrintStackTrace(JNIEnv *env, jobject throwable, jobject printable)");
}

JNIEXPORT jint JNICALL
JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable)
{
	printf("JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable)");
}

JNIEXPORT jobject JNICALL
JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index)
{
	printf("JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index)");
}

JNIEXPORT void JNICALL
JVM_InitializeCompiler (JNIEnv *env, jclass compCls)
{
	printf("JVM_InitializeCompiler (JNIEnv *env, jclass compCls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSilentCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_IsSilentCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT jboolean JNICALL
JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls)
{
	printf("JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname)
{
	printf("JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname)");
}

JNIEXPORT jobject JNICALL
JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg)
{
	printf("JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg)");
}

JNIEXPORT void JNICALL
JVM_EnableCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_EnableCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT void JNICALL
JVM_DisableCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_DisableCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT void JNICALL
JVM_StartThread(JNIEnv *env, jobject thread)
{
	printf("JVM_StartThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_StopThread(JNIEnv *env, jobject thread, jobject exception)
{
	printf("JVM_StopThread(JNIEnv *env, jobject thread, jobject exception)");
}

JNIEXPORT jboolean JNICALL
JVM_IsThreadAlive(JNIEnv *env, jobject thread)
{
	printf("JVM_IsThreadAlive(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_SuspendThread(JNIEnv *env, jobject thread)
{
	printf("JVM_SuspendThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_ResumeThread(JNIEnv *env, jobject thread)
{
	printf("JVM_ResumeThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio)
{
	printf("JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio)");
}

JNIEXPORT void JNICALL
JVM_Yield(JNIEnv *env, jclass threadClass)
{
	printf("JVM_Yield(JNIEnv *env, jclass threadClass)");
}

JNIEXPORT void JNICALL
JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis)
{
	printf("JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis)");
}

JNIEXPORT jobject JNICALL
JVM_CurrentThread(JNIEnv *env, jclass threadClass)
{
	printf("JVM_CurrentThread(JNIEnv *env, jclass threadClass)");
}

JNIEXPORT jint JNICALL
JVM_CountStackFrames(JNIEnv *env, jobject thread)
{
	printf("JVM_CountStackFrames(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_Interrupt(JNIEnv *env, jobject thread)
{
	printf("JVM_Interrupt(JNIEnv *env, jobject thread)");
}

JNIEXPORT jboolean JNICALL
JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted)
{
	printf("JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted)");
}

JNIEXPORT jboolean JNICALL
JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj)
{
	printf("JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_DumpAllStacks(JNIEnv *env, jclass unused)
{
	printf("JVM_DumpAllStacks(JNIEnv *env, jclass unused)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetAllThreads(JNIEnv *env, jclass dummy)
{
	printf("JVM_GetAllThreads(JNIEnv *env, jclass dummy)");
}

JNIEXPORT jobjectArray JNICALL
JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads)
{
	printf("JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads)");
}

JNIEXPORT jclass JNICALL
JVM_CurrentLoadedClass(JNIEnv *env)
{
	printf("JVM_CurrentLoadedClass(JNIEnv *env)");
}

JNIEXPORT jobject JNICALL
JVM_CurrentClassLoader(JNIEnv *env)
{
	printf("JVM_CurrentClassLoader(JNIEnv *env)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassContext(JNIEnv *env)
{
	printf("JVM_GetClassContext(JNIEnv *env)");
}

JNIEXPORT jint JNICALL
JVM_ClassDepth(JNIEnv *env, jstring name)
{
	printf("JVM_ClassDepth(JNIEnv *env, jstring name)");
}

JNIEXPORT jint JNICALL
JVM_ClassLoaderDepth(JNIEnv *env)
{
	printf("JVM_ClassLoaderDepth(JNIEnv *env)");
}

JNIEXPORT jstring JNICALL
JVM_GetSystemPackage(JNIEnv *env, jstring name)
{
	printf("JVM_GetSystemPackage(JNIEnv *env, jstring name)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetSystemPackages(JNIEnv *env)
{
	printf("JVM_GetSystemPackages(JNIEnv *env)");
}

JNIEXPORT jobject JNICALL
JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass, jclass initClass)
{
	printf("JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass, jclass initClass)");
}

JNIEXPORT jobject JNICALL
JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length)
{
	printf("JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_LatestUserDefinedLoader(JNIEnv *env)
{
	printf("JVM_LatestUserDefinedLoader(JNIEnv *env)");
}

JNIEXPORT jclass JNICALL
JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass, jstring currClassName)
{
	printf("JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass, jstring currClassName)");
}

JNIEXPORT jint JNICALL
JVM_GetArrayLength(JNIEnv *env, jobject arr)
{
	printf("JVM_GetArrayLength(JNIEnv *env, jobject arr)");
}

JNIEXPORT jobject JNICALL
JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index)
{
	printf("JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index)");
}

JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode)
{
	printf("JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode)");
}

JNIEXPORT void JNICALL
JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val)
{
	printf("JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val)");
}

JNIEXPORT void JNICALL
JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode)
{
	printf("JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode)");
}

JNIEXPORT jobject JNICALL
JVM_NewArray(JNIEnv *env, jclass eltClass, jint length)
{
	printf("JVM_NewArray(JNIEnv *env, jclass eltClass, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim)
{
	printf("JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim)");
}

JNIEXPORT jclass JNICALL
JVM_GetCallerClass(JNIEnv *env, int n)
{
	printf("JVM_GetCallerClass(JNIEnv *env, int n)");
}

JNIEXPORT jclass JNICALL
JVM_FindPrimitiveClass(JNIEnv *env, const char *utf)
{
	printf("JVM_FindPrimitiveClass(JNIEnv *env, const char *utf)");
}

JNIEXPORT void JNICALL
JVM_ResolveClass(JNIEnv *env, jclass cls)
{
	printf("JVM_ResolveClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init, jobject loader, jboolean throwError)
{
	printf("JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init, jobject loader, jboolean throwError)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromBootLoader(JNIEnv *env, const char *name)
{
	printf("JVM_FindClassFromBootLoader(JNIEnv *env, const char *name)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init, jclass from)
{
	printf("JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init, jclass from)");
}

JNIEXPORT jclass JNICALL
JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name)
{
	printf("JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd)
{
	printf("JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source)
{
	printf("JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jboolean verify)
{
	printf("JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jboolean verify)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithCP(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jobjectArray constants)
{
	printf("JVM_DefineClassWithCP(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jobjectArray constants)");
}

JNIEXPORT jstring JNICALL
JVM_GetClassName(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassName(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassInterfaces(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassInterfaces(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassLoader(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassLoader(JNIEnv *env, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsInterface(JNIEnv *env, jclass cls)
{
	printf("JVM_IsInterface(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassSigners(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassSigners(JNIEnv *env, jclass cls)");
}

JNIEXPORT void JNICALL
JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers)
{
	printf("JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers)");
}

JNIEXPORT jobject JNICALL
JVM_GetProtectionDomain(JNIEnv *env, jclass cls)
{
	printf("JVM_GetProtectionDomain(JNIEnv *env, jclass cls)");
}

JNIEXPORT void JNICALL
JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain)
{
	printf("JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain)");
}

JNIEXPORT jboolean JNICALL
JVM_IsArrayClass(JNIEnv *env, jclass cls)
{
	printf("JVM_IsArrayClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsPrimitiveClass(JNIEnv *env, jclass cls)
{
	printf("JVM_IsPrimitiveClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jclass JNICALL
JVM_GetComponentType(JNIEnv *env, jclass cls)
{
	printf("JVM_GetComponentType(JNIEnv *env, jclass cls)");
}

JNIEXPORT jint JNICALL
JVM_GetClassModifiers(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassModifiers(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass)
{
	printf("JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass)");
}

JNIEXPORT jclass JNICALL
JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass)
{
	printf("JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass)");
}

JNIEXPORT jstring JNICALL
JVM_GetClassSignature(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassSignature(JNIEnv *env, jclass cls)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetClassAnnotations(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassAnnotations(JNIEnv *env, jclass cls)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetFieldAnnotations(JNIEnv *env, jobject field)
{
	printf("JVM_GetFieldAnnotations(JNIEnv *env, jobject field)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodAnnotations(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodAnnotations(JNIEnv *env, jobject method)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jint JNICALL
JVM_GetClassAccessFlags(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassAccessFlags(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassConstantPool(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassConstantPool(JNIEnv *env, jclass cls)");
}

JNIEXPORT jint JNICALL 
JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool)
{
	printf("JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool)");
}

JNIEXPORT jclass JNICALL 
JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jclass JNICALL 
JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL 
JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobjectArray JNICALL 
JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jint JNICALL
JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jlong JNICALL 
JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jfloat JNICALL 
JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jdouble JNICALL 
JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jstring JNICALL 
JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jstring JNICALL 
JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException)
{
  jobject result;
  printf("##Trap to RVM:JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException)");
  result = env->functions->RVM_DoPrivileged(env,cls,action,context,&wrapException);
  printf("##Retrn from JVM_DoPrivileged, result is %p\n",result);

}

JNIEXPORT jobject JNICALL
JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls)
{
	printf("JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls)
{
	printf("JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls)");
}

JNIEXPORT void * JNICALL
JVM_RegisterSignal(jint sig, void *handler)
{
	printf("JVM_RegisterSignal(jint sig, void *handler)");
}

JNIEXPORT jboolean JNICALL
JVM_RaiseSignal(jint sig)
{
	printf("JVM_RaiseSignal(jint sig)");
}

JNIEXPORT jint JNICALL
JVM_FindSignal(const char *name)
{
	printf("JVM_FindSignal(const char *name)");
}

JNIEXPORT jboolean JNICALL
JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls)
{
	printf("JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused)
{
	printf("JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused)");
}

JNIEXPORT jboolean JNICALL
JVM_SupportsCX8(void)
{
	printf("JVM_SupportsCX8(void)");
	return true;
}

JNIEXPORT jboolean JNICALL
JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fldID, jlong oldVal, jlong newVal)
{
	printf("JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fldID, jlong oldVal, jlong newVal)");
}



/*
 * Structure to pass one probe description to JVM.
 *
 * The VM will overwrite the definition of the referenced method with
 * code that will fire the probe.
 */
typedef struct {
    jmethodID method;
    jstring   function;
    jstring   name;
    void*     reserved[4];     // for future use
} JVM_DTraceProbe;

/**
 * Encapsulates the stability ratings for a DTrace provider field
 */
typedef struct {
    jint nameStability;
    jint dataStability;
    jint dependencyClass;
} JVM_DTraceInterfaceAttributes;

/*
 * Structure to pass one provider description to JVM
 */
typedef struct {
    jstring                       name;
    JVM_DTraceProbe*              probes;
    jint                          probe_count;
    JVM_DTraceInterfaceAttributes providerAttributes;
    JVM_DTraceInterfaceAttributes moduleAttributes;
    JVM_DTraceInterfaceAttributes functionAttributes;
    JVM_DTraceInterfaceAttributes nameAttributes;
    JVM_DTraceInterfaceAttributes argsAttributes;
    void*                         reserved[4]; // for future use
} JVM_DTraceProvider;

JNIEXPORT jint JNICALL
JVM_DTraceGetVersion(JNIEnv* env)
{
	printf("JVM_DTraceGetVersion(JNIEnv* env)");
}

JNIEXPORT jlong JNICALL
JVM_DTraceActivate(JNIEnv* env, jint version, jstring module_name, jint providers_count, JVM_DTraceProvider* providers)
{
	printf("JVM_DTraceActivate(JNIEnv* env, jint version, jstring module_name, jint providers_count, JVM_DTraceProvider* providers)");
}

JNIEXPORT jboolean JNICALL
JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method)
{
	printf("JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method)");
}

JNIEXPORT void JNICALL
JVM_DTraceDispose(JNIEnv* env, jlong handle)
{
	printf("JVM_DTraceDispose(JNIEnv* env, jlong handle)");
}

JNIEXPORT jboolean JNICALL
JVM_DTraceIsSupported(JNIEnv* env)
{
	printf("JVM_DTraceIsSupported(JNIEnv* env)");
}

JNIEXPORT const char * JNICALL
JVM_GetClassNameUTF(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassNameUTF(JNIEnv *env, jclass cb)");
}

JNIEXPORT void JNICALL
JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types)
{
	printf("JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types)");
}

JNIEXPORT jint JNICALL
JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT jint JNICALL
JVM_GetClassFieldsCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassFieldsCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT jint JNICALL
JVM_GetClassMethodsCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassMethodsCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index, unsigned short *exceptions)
{
	printf("JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index, unsigned short *exceptions)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index)
{
	printf("JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index)");
}

JNIEXPORT void JNICALL
JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index, unsigned char *code)
{
	printf("JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index, unsigned char *code)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index)
{
	printf("JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index)");
}

/*
 * A structure used to a capture exception table entry in a Java method.
 */
typedef struct {
    jint start_pc;
    jint end_pc;
    jint handler_pc;
    jint catchType;
} JVM_ExceptionTableEntryType;


JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index, jint entry_index, JVM_ExceptionTableEntryType *entry)
{
	printf("JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index, jint entry_index, JVM_ExceptionTableEntryType *entry)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jboolean JNICALL
JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT const char * JNICALL
JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT jint JNICALL
JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)
{
	printf("JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)");
}

JNIEXPORT jint JNICALL
JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)
{
	printf("JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)");
}

JNIEXPORT void JNICALL
JVM_ReleaseUTF(const char *utf)
{
	printf("JVM_ReleaseUTF(const char *utf)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2)
{
	printf("JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2)");
}

JNIEXPORT jint JNICALL
JVM_GetLastErrorString(char *buf, int len)
{
	printf("JVM_GetLastErrorString(char *buf, int len)\n");
	if (errno == 0) {
	  return 0;
	} else {
	  const char *s = strerror(errno);
	  printf("LastErrorString is %s\n",s);
	  int n = strlen(s);
	  if (n >= len) n = len - 1;
	  strncpy(buf, s, n);
	  buf[n] = '\0';
	  return n;
	}
}

JNIEXPORT char * JNICALL
JVM_NativePath(char * path)
{
  //  printf("JVM_NativePath path:%s\n",path);
  return path;
}

JNIEXPORT jint JNICALL
JVM_Open(const char *fname, jint flags, jint mode)
{

	int result = open(fname,flags,mode);
	if (result < 0){
	  result = -1;
	  if (errno == EEXIST)
	    result = -100;//JVM_EXIST
	}
#ifdef DEBUG_OPENJDK	
	printf("JVM_Open(%s,%d,%d):%d\n",fname,flags,mode,result);
#endif
	return result;	   	
}

JNIEXPORT jint JNICALL
JVM_Close(jint fd)
{
#ifdef DEBUG_OPENJDK
	printf("JVM_Close(jint fd)\n");
#endif
	return close(fd);
}

JNIEXPORT jint JNICALL
JVM_Read(jint fd, char *buf, jint nbytes)
{
  if (openjdkVerbose)
    printf("JVM_Read(jint fd, char *buf, jint nbytes)\n");
  return read(fd,buf,nbytes);
}

JNIEXPORT jint JNICALL
JVM_Write(jint fd, char *buf, jint nbytes)
{
  if (openjdkVerbose)
    printf("JVM_Write(jint fd, char *buf, jint nbytes)");
  write(fd,buf,nbytes);
}

JNIEXPORT jint JNICALL
JVM_Available(jint fd, jlong *pbytes)
{
	jlong cur, end;
	int mode;
	struct stat bufStat;

	if (fstat(fd, &bufStat) >= 0) {
	  mode = bufStat.st_mode;
	  if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
            int n;
            if (ioctl(fd, FIONREAD, &n) >= 0) {
	      //	      printf("JVM_Available: fd %d, return %d\n",n);
	      *pbytes = n;
	      return 1;
            }
	  }
	}
	if ((cur = lseek(fd, 0, SEEK_CUR)) == -1) {
	  return 0;
	} else if ((end = lseek(fd, 0, SEEK_END)) == -1) {
	  return 0;
#ifndef __MACH__
	} else if (lseek64(fd, cur, SEEK_SET) == -1) {
	  return 0;
#endif
	}
	*pbytes = end - cur;
	//	printf("JVM_Available: fd %d, return %d\n",end - cur);
	return 1;	
}

JNIEXPORT jlong JNICALL
JVM_Lseek(jint fd, jlong offset, jint whence)
{
  if (openjdkVerbose)
    printf("JVM_Lseek(jint fd, jlong offset, jint whence)\n");
  return lseek(fd, offset, whence);
}

JNIEXPORT jint JNICALL
JVM_SetLength(jint fd, jlong length)
{
	printf("JVM_SetLength(jint fd, jlong length)\n");
	return ftruncate(fd, length);
}

JNIEXPORT jint JNICALL
JVM_Sync(jint fd)
{
	printf("JVM_Sync(jint fd)\n");
	return fsync(fd);
	
}

JNIEXPORT jint JNICALL
JVM_InitializeSocketLibrary(void)
{
	printf("JVM_InitializeSocketLibrary(void)");
}

JNIEXPORT jint JNICALL
JVM_Socket(jint domain, jint type, jint protocol)
{
	printf("JVM_Socket(jint domain, jint type, jint protocol)");
}

JNIEXPORT jint JNICALL
JVM_SocketClose(jint fd)
{
	printf("JVM_SocketClose(jint fd)");
}

JNIEXPORT jint JNICALL
JVM_SocketShutdown(jint fd, jint howto)
{
	printf("JVM_SocketShutdown(jint fd, jint howto)");
}

JNIEXPORT jint JNICALL
JVM_Recv(jint fd, char *buf, jint nBytes, jint flags)
{
	printf("JVM_Recv(jint fd, char *buf, jint nBytes, jint flags)");
}

JNIEXPORT jint JNICALL
JVM_Send(jint fd, char *buf, jint nBytes, jint flags)
{
	printf("JVM_Send(jint fd, char *buf, jint nBytes, jint flags)");
}

JNIEXPORT jint JNICALL
JVM_Timeout(int fd, long timeout)
{
	printf("JVM_Timeout(int fd, long timeout)");
}

JNIEXPORT jint JNICALL
JVM_Listen(jint fd, jint count)
{
	printf("JVM_Listen(jint fd, jint count)");
}

JNIEXPORT jint JNICALL
JVM_Connect(jint fd, struct sockaddr *him, jint len)
{
	printf("JVM_Connect(jint fd, struct sockaddr *him, jint len)");
}

JNIEXPORT jint JNICALL
JVM_Bind(jint fd, struct sockaddr *him, jint len)
{
	printf("JVM_Bind(jint fd, struct sockaddr *him, jint len)");
}

JNIEXPORT jint JNICALL
JVM_Accept(jint fd, struct sockaddr *him, jint *len)
{
	printf("JVM_Accept(jint fd, struct sockaddr *him, jint *len)");
}

JNIEXPORT jint JNICALL
JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen)
{
	printf("JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen)");
}

JNIEXPORT jint JNICALL
JVM_SendTo(jint fd, char *buf, int len,int flags, struct sockaddr *to, int tolen)
{
	printf("JVM_SendTo(jint fd, char *buf, int len,int flags, struct sockaddr *to, int tolen)");
}

JNIEXPORT jint JNICALL
JVM_SocketAvailable(jint fd, jint *result)
{
	printf("JVM_SocketAvailable(jint fd, jint *result)");
}

JNIEXPORT jint JNICALL
JVM_GetSockName(jint fd, struct sockaddr *him, int *len)
{
	printf("JVM_GetSockName(jint fd, struct sockaddr *him, int *len)");
}

JNIEXPORT jint JNICALL
JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen)
{
	printf("JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen)");
}

JNIEXPORT jint JNICALL
JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen)
{
	printf("JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen)");
}

JNIEXPORT struct protoent * JNICALL
JVM_GetProtoByName(char* name)
{
	printf("JVM_GetProtoByName(char* name)");
}

JNIEXPORT struct hostent* JNICALL
JVM_GetHostByAddr(const char* name, int len, int type)
{
	printf("JVM_GetHostByAddr(const char* name, int len, int type)");
}

JNIEXPORT struct hostent* JNICALL
JVM_GetHostByName(char* name)
{
	printf("JVM_GetHostByName(char* name)");
}

JNIEXPORT int JNICALL
JVM_GetHostName(char* name, int namelen)
{
	printf("JVM_GetHostName(char* name, int namelen)");
}

JNIEXPORT void * JNICALL
JVM_RawMonitorCreate(void)
{
  //	printf("JVM_RawMonitorCreate(void)\n");
	return (void *)sysMonitorCreate();	
}

JNIEXPORT void JNICALL
JVM_RawMonitorDestroy(void *mon)
{
  //	printf("JVM_RawMonitorDestroy(void *mon)\n");
	sysMonitorDestroy((Word)mon);
}

JNIEXPORT jint JNICALL
JVM_RawMonitorEnter(void *mon)
{
  //	printf("JVM_RawMonitorEnter(void *mon)\n");
	sysMonitorEnter((Word)mon);
	return 0;
}

JNIEXPORT void JNICALL
JVM_RawMonitorExit(void *mon)
{
  //	printf("JVM_RawMonitorExit(void *mon)\n");
	sysMonitorExit((Word)mon);
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassFields(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassFields(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassMethods(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassMethods(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassConstructors(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassConstructors(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassField(JNIEnv *env, jclass cls, jstring name, jint which)
{
	printf("JVM_GetClassField(JNIEnv *env, jclass cls, jstring name, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassMethod(JNIEnv *env, jclass cls, jstring name, jobjectArray types, jint which)
{
	printf("JVM_GetClassMethod(JNIEnv *env, jclass cls, jstring name, jobjectArray types, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassConstructor(JNIEnv *env, jclass cls, jobjectArray types, jint which)
{
	printf("JVM_GetClassConstructor(JNIEnv *env, jclass cls, jobjectArray types, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_NewInstance(JNIEnv *env, jclass cls)
{
	printf("JVM_NewInstance(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetField(JNIEnv *env, jobject field, jobject obj)
{
	printf("JVM_GetField(JNIEnv *env, jobject field, jobject obj)");
}

JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveField(JNIEnv *env, jobject field, jobject obj, unsigned char wCode)
{
	printf("JVM_GetPrimitiveField(JNIEnv *env, jobject field, jobject obj, unsigned char wCode)");
}

JNIEXPORT void JNICALL
JVM_SetField(JNIEnv *env, jobject field, jobject obj, jobject val)
{
	printf("JVM_SetField(JNIEnv *env, jobject field, jobject obj, jobject val)");
}

JNIEXPORT void JNICALL
JVM_SetPrimitiveField(JNIEnv *env, jobject field, jobject obj, jvalue v, unsigned char vCode)
{
	printf("JVM_SetPrimitiveField(JNIEnv *env, jobject field, jobject obj, jvalue v, unsigned char vCode)");
}

JNIEXPORT jobject JNICALL
JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0)
{
	printf("JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0)");
}

JNIEXPORT jobject JNICALL
JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0)
{
	printf("JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0)");
}

JNIEXPORT void* JNICALL
JVM_GetManagement(jint version)
{
	printf("JVM_GetManagement(jint version)");
}

JNIEXPORT jobject JNICALL
JVM_InitAgentProperties(JNIEnv *env, jobject agent_props)
{
	printf("JVM_InitAgentProperties(JNIEnv *env, jobject agent_props)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass)
{
	printf("JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass)");
}

JNIEXPORT jintArray JNICALL
JVM_GetThreadStateValues(JNIEnv* env, jint javaThreadState)
{
	printf("JVM_GetThreadStateValues(JNIEnv* env, jint javaThreadState)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetThreadStateNames(JNIEnv* env, jint javaThreadState, jintArray values)
{
	printf("JVM_GetThreadStateNames(JNIEnv* env, jint javaThreadState, jintArray values)");
}


/* =========================================================================
 * The following defines a private JVM interface that the JDK can query
 * for the JVM version and capabilities.  sun.misc.Version defines
 * the methods for getting the VM version and its capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JVM_GetVersionInfo and Abstract_VM_Version class
 *    SDK:  Version class
 *
 * Similary, a private JDK interface JDK_GetVersionInfo0 is defined for
 * JVM to query for the JDK version and capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JDK_Version class
 *    SDK:  JDK_GetVersionInfo0
 *
 * ==========================================================================
 */
typedef struct {
    /* HotSpot Express VM version string:
     * <major>.<minor>-bxx[-<identifier>][-<debug_flavor>]
     */
    unsigned int jvm_version; /* Consists of major.minor.0.build */
    unsigned int update_version : 8;         /* 0 in HotSpot Express VM */
    unsigned int special_update_version : 8; /* 0 in HotSpot Express VM */
    unsigned int reserved1 : 16;
    unsigned int reserved2;

    /* The following bits represents JVM supports that JDK has dependency on.
     * JDK can use these bits to determine which JVM version
     * and support it has to maintain runtime compatibility.
     *
     * When a new bit is added in a minor or update release, make sure
     * the new bit is also added in the main/baseline.
     */
    unsigned int is_attachable : 1;
    unsigned int is_kernel_jvm : 1;
    unsigned int : 30;
    unsigned int : 32;
    unsigned int : 32;
} jvm_version_info;

JNIEXPORT void JNICALL
JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size)
{
	printf("JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size)");
}
}
