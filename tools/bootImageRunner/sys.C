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

/**
 * O/S support services required by the java class libraries.
 * See also: BootRecord.java
 */

// Aix and Linux version.  PowerPC and IA32.

// Only called externally from Java programs.
extern "C" void sysExit(int) __attribute__((noreturn));

//Solaris needs BSD_COMP to be set to enable the FIONREAD ioctl
#if defined (__SVR4) && defined (__sun)
#define BSD_COMP
#endif

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//
#ifdef _AIX43
#include </usr/include/unistd.h>
extern "C" void profil(void *, uint, ulong, uint);
extern "C" int sched_yield(void);
#endif

#include <stdio.h>
#include <stdlib.h>      // getenv() and others
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>               // nanosleep() and other
#include <utime.h>

#ifdef RVM_WITH_PERFCTR
#  include "perfctr.h"
#endif

#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_SOLARIS) 
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#ifdef RVM_FOR_LINUX
#include <asm/ioctls.h>
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
extern "C" timer_t gettimerid(int timer_type, int notify_type);
extern "C" int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_EXIT_STATUS_CODES
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In tools/bootImageRunner.
#include <pthread.h>

#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_SOLARIS)
# define HAVE_SYSWRAP 1
# include "syswrap.h"
#endif

// #define DEBUG_SYS
#define VERBOSE_PTHREAD lib_verbose

// static int TimerDelay  =  10; // timer tick interval, in milliseconds     (10 <= delay <= 999)
// static int SelectDelay =   2; // pause time for select(), in milliseconds (0  <= delay <= 999)

static void *sysVirtualProcessorStartup(void *args);

/* This routine is not yet used by all of the functions that return strings in
 * buffers, but I hope that it will be one day. */
static int loadResultBuf(char * buf, int limit, const char *result);


/*
 * Network addresses are sensible, that is big endian, and the intel
 * hardware is the opposite.  Hence, when reading and writing network
 * addresses, use these mangle routines to swap bytes as needed.
 */
#ifdef __i386__
#define MANGLE32(i) ({ unsigned int r = 0; r |= (i&0xFF)<<24; r |= (i&0xFF00)<<8; r |= (i&0xFF0000)>>8; r |= (i&0xFF000000)>>24; r; })
#define MANGLE16(i) ({ unsigned short r = 0; r |= (i&0xFF)<<8; r |= (i&0xFF00)>>8; r; })
#else
#define MANGLE32(x) x
#define MANGLE16(x) x
#endif

// Console write (java character).
//
extern "C" void
sysConsoleWriteChar(unsigned value)
{
    char c = (value > 127) ? '?' : (char)value;
    // use high level stdio to ensure buffering policy is observed
    fprintf(SysTraceFile, "%c", c);
}

// Console write (java integer).
//
extern "C" void
sysConsoleWriteInteger(int value, int hexToo)
{
    if (hexToo==0 /*false*/)
        fprintf(SysTraceFile, "%d", value);
    else if (hexToo==1 /*true - also print in hex*/)
        fprintf(SysTraceFile, "%d (0x%08x)", value, value);
    else    /* hexToo==2 for only in hex */
        fprintf(SysTraceFile, "0x%08x", value);
}

// Console write (java long).
//
extern "C" void
sysConsoleWriteLong(long long value, int hexToo)
{
    if (hexToo==0 /*false*/)
        fprintf(SysTraceFile, "%lld", value);
    else if (hexToo==1 /*true - also print in hex*/) {
        int value1 = (value >> 32) & 0xFFFFFFFF;
        int value2 = value & 0xFFFFFFFF;
        fprintf(SysTraceFile, "%lld (0x%08x%08x)", value, value1, value2);
    } else { /* hexToo==2 for only in hex */
        int value1 = (value >> 32) & 0xFFFFFFFF;
        int value2 = value & 0xFFFFFFFF;
        fprintf(SysTraceFile, "0x%08x%08x", value1, value2);
    }
}

// Console write (java double).
//
extern "C" void
sysConsoleWriteDouble(double value,  int postDecimalDigits)
{
    if (value != value) {
        fprintf(SysTraceFile, "NaN");
    } else {
        if (postDecimalDigits > 9) postDecimalDigits = 9;
        char tmp[5] = {'%', '.', '0'+postDecimalDigits, 'f', 0};
        fprintf(SysTraceFile, tmp, value);
    }
}

// alignment checking: hardware alignment checking variables and functions

#ifdef RVM_WITH_ALIGNMENT_CHECKING

volatile int numNativeAlignTraps;
volatile int numEightByteAlignTraps;
volatile int numBadAlignTraps;

static volatile int numEnableAlignCheckingCalls = 0;
static volatile int numDisableAlignCheckingCalls = 0;

extern "C" void sysEnableAlignmentChecking() {
  numEnableAlignCheckingCalls++;
  if (numEnableAlignCheckingCalls > numDisableAlignCheckingCalls) {
    asm("pushf\n\t"
        "orl $0x00040000,(%esp)\n\t"
        "popf");
  }
}

extern "C" void sysDisableAlignmentChecking() {
  numDisableAlignCheckingCalls++;
  asm("pushf\n\t"
      "andl $0xfffbffff,(%esp)\n\t"
      "popf");
}

extern "C" void sysReportAlignmentChecking() {
  fprintf(SysTraceFile, "\nAlignment checking report:\n\n");
  fprintf(SysTraceFile, "# native traps (ignored by default):             %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access traps (ignored by default):      %d\n", numEightByteAlignTraps);
  fprintf(SysTraceFile, "# bad access traps (throw exception by default): %d (should be zero)\n\n", numBadAlignTraps);
  fprintf(SysTraceFile, "# calls to sysEnableAlignmentChecking():         %d\n", numEnableAlignCheckingCalls);
  fprintf(SysTraceFile, "# calls to sysDisableAlignmentChecking():        %d\n\n", numDisableAlignCheckingCalls);
  fprintf(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);

  // cause a native trap to see if traps are enabled
  volatile int dummy[2];
  volatile int prevNumNativeTraps = numNativeAlignTraps;
  *(int*)((char*)dummy + 1) = 0x12345678;
  int enabled = (numNativeAlignTraps != prevNumNativeTraps);

  fprintf(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);
  fprintf(SysTraceFile, "Current status of alignment checking:            %s (should be on)\n\n", (enabled ? "on" : "off"));
}

#else

extern "C" void sysEnableAlignmentChecking() { }
extern "C" void sysDisableAlignmentChecking() { }
extern "C" void sysReportAlignmentChecking() { }

#endif // RVM_WITH_ALIGNMENT_CHECKING

// Static fields offset from the JTOC. Not generated as part of generate
// interface declarations to avoid races causing different static field
// layouts
Offset GCStatusOffset;
Offset TimerTicksOffset;
Offset ReportedTimerTicksOffset;

extern "C" void
sysRegisterStaticFieldOffsets(int gcOffset, int ttOffset, int rttOffset)
{
	GCStatusOffset = (Offset)gcOffset;
	ReportedTimerTicksOffset = (Offset)rttOffset;
	TimerTicksOffset = (Offset)ttOffset;
}

pthread_mutex_t DeathLock = PTHREAD_MUTEX_INITIALIZER;

static bool systemExiting = false;

// Exit with a return code.
//
extern "C" void
sysExit(int value)
{
    // alignment checking: report info before exiting, then turn off checking
    #ifdef RVM_WITH_ALIGNMENT_CHECKING
    if (numEnableAlignCheckingCalls > 0) {
      sysReportAlignmentChecking();
      sysDisableAlignmentChecking();
    }
    #endif // RVM_WITH_ALIGNMENT_CHECKING
	
    if (lib_verbose & value != 0) {
        fprintf(SysErrorFile, "%s: exit %d\n", Me, value);
    }

    fflush(SysErrorFile);
    fflush(SysTraceFile);
    fflush(stdout);

    systemExiting = true;

    pthread_mutex_lock( &DeathLock );
    exit(value);
}

// Access host o/s command line arguments.
// Taken:    -1
//           null
// Returned: number of arguments
//
// /or/
//
// Taken:    arg number sought
//           buffer to fill
// Returned: number of bytes written to buffer (-1: arg didn't fit, buffer too small)
//
extern "C" int
sysArg(int argno, char *buf, int buflen)
{
    if (argno == -1) { // return arg count
        return JavaArgc;
        /***********
      for (int i = 0;;++i)
         if (JavaArgs[i] == 0)
            return i;
        **************/
    } else { // return i-th arg
        const char *src = JavaArgs[argno];
        for (int i = 0;; ++i)
        {
            if (*src == 0)
                return i;
            if (i == buflen)
                return -1;
            *buf++ = *src++;
        }
    }
    /* NOTREACHED */
}

/** Get the value of an enviroment variable.  (This refers to the C
    per-process environment.)   Used, indirectly, by VMSystem.getenv()

    Taken:    VARNAME, name of the envar we want.
         BUF, a buffer in which to place the value of that envar
              LIMIT, the size of BUF

    Returned: See the convention documented in loadResultBuf().

         0: A return value of 0 indicates that the envar was set with a
         zero-length value.   (Distinguised from unset, see below)

         -2: Indicates that the envar was unset.  This is distinguished
        from a zero-length value (see above).
*/
extern "C" int
sysGetenv(const char *varName, char *buf, int limit)
{
    return loadResultBuf(buf, limit, getenv(varName));
}



/* Copy SRC, a null-terminated string or a NULL pointer, into DEST, a buffer
 * with LIMIT characters capacity.   This is a helper function used by
 * sysGetEnv() and, later on, to be used by other functions returning strings
 * to Java.
 *
 * Handle the error handling for running out of space in BUF, in accordance
 * with the C '99 specification for snprintf() -- see sysGetEnv().
 *
 * Returned:  -2 if SRC is a NULL pointer.
 * Returned:  If enough space, the number of bytes copied to DEST.
 *
 *            If there is not enough space, we write what we can and return
 *            the # of characters that WOULD have been written to the final
 *            string BUF if enough space had been available, excluding any
 *            trailing '\0'.  This error handling is consistent with the C '99
 *            standard's behavior for the snprintf() system library function.
 *
 *            Note that this is NOT consistent with the behavior of most of
 *            the functions in this file that return strings to Java.
 *
 *            That should change with time.
 *
 *            This function will append a trailing '\0', if there is enough
 *            space, even though our caller does not need it nor use it.
 */
static int
loadResultBuf(char * dest, int limit, const char *src)
{
    if ( ! src )         // Is it set?
   return -2;      // Tell caller it was unset.

    for (int i = 0;; ++i) {
   if ( i < limit ) // If there's room for the next char of the value ...
       dest[i] = src[i];   // ... write it into the destination buffer.
   if (src[i] == '\0')
       return i;      // done, return # of chars needed for SRC
    }
}

/*
 * Performance counter support using the 'perfctr' system.
 * 
 * The implementations are 'out of line' in perfctr.C
 */

extern "C" int
sysPerfCtrInit(int metric)
{
#ifdef RVM_WITH_PERFCTR
  return perfCtrInit(metric);
#else
  return 0;
#endif
}

extern "C" long long
sysPerfCtrReadCycles()
{
#ifdef RVM_WITH_PERFCTR
  return perfCtrReadCycles();
#else
  return 0;
#endif
}

extern "C" long long
sysPerfCtrReadMetric()
{
#ifdef RVM_WITH_PERFCTR
  return perfCtrReadMetric();
#else
  return 0;
#endif
}

/*
 * The following is unused at present
 */
extern "C" int
sysPerfCtrRead(char *str)
{
#ifdef RVM_WITH_PERFCTR
  return perfCtrRead(str);
#else
  return 0;
#endif
}

//------------------------//
// Filesystem operations. //
//------------------------//

// Get file status.
// Taken:    null terminated filename
//           kind of info desired (see FileSystem.STAT_XXX)
// Returned: status (-1=error)
//
// As of August 2003, this is never used. --Steve Augart
extern "C" int
sysStat(char *name, int kind)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: stat %s\n", Me, name);
#endif

    struct stat info;

    if (stat(name, &info))
        return -1; // does not exist, or other trouble

    switch (kind) {
    case FileSystem_STAT_EXISTS:
        return 1;                              // exists
    case FileSystem_STAT_IS_FILE:
        return S_ISREG(info.st_mode) != 0; // is file
    case FileSystem_STAT_IS_DIRECTORY:
        return S_ISDIR(info.st_mode) != 0; // is directory
    case FileSystem_STAT_IS_READABLE:
        return (info.st_mode & S_IREAD) != 0; // is readable by owner
    case FileSystem_STAT_IS_WRITABLE:
        return (info.st_mode & S_IWRITE) != 0; // is writable by owner
    case FileSystem_STAT_LAST_MODIFIED:
        return info.st_mtime;   // time of last modification
    case FileSystem_STAT_LENGTH:
        return info.st_size;    // length
    }
    return -1; // unrecognized request
}

// Check user's perms.
// Taken:    null terminated filename
//           kind of access perm to check for (see FileSystem.ACCESS_W_OK)
// Returned: 0 on success (-1=error)
//
extern "C" int
sysAccess(char *name, int kind)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: access %s\n", Me, name);
#endif

    return access(name, kind);
}

// How many bytes can be read from file/socket without blocking?
// Taken:    file/socket descriptor
// Returned: >=0: count, ThreadIOConstants_FD_INVALID: bad file descriptor,
//          -1: other error
//
extern "C" int
sysBytesAvailable(int fd)
{
    int count = 0;
    if (ioctl(fd, FIONREAD, &count) == -1)
    {
        bool badFD = (errno == EBADF);
        fprintf(SysErrorFile, "%s: FIONREAD ioctl on %d failed: %s (errno=%d)\n", Me, fd, strerror( errno ), errno);
        return badFD ? ThreadIOConstants_FD_INVALID : -1;
    }
// fprintf(SysTraceFile, "%s: available fd=%d count=%d\n", Me, fd, count);
    return count;
}

extern "C" int
sysSyncFile(int fd)
{
    if (fsync(fd) != 0) {
        // some kinds of files cannot be sync'ed, so don't print error message
        // however, do return error code in case some application cares
        return -1;
    }

    return 0;
}

// Read one byte from file.
// Taken:    file descriptor
// Returned: data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
//
extern "C" int
sysReadByte(int fd)
{
    unsigned char ch;
    int rc;

again:
    switch ( rc = read(fd, &ch, 1))
    {
    case  1:
        /*fprintf(SysTraceFile, "%s: read (byte) ch is %d\n", Me, (int) ch);*/
        return (int) ch;
    case  0:
        /*fprintf(SysTraceFile, "%s: read (byte) rc is 0\n", Me);*/
        return -1;
    default:
        /*fprintf(SysTraceFile, "%s: read (byte) rc is %d\n", Me, rc);*/
        if (errno == EAGAIN)
            return -2;  // Read would have blocked
        else if (errno == EINTR)
            goto again; // Read was interrupted; try again
        else
            return -3;  // Some other error
    }
}

// Write one byte to file.
// Taken:    file descriptor
//           data to write
// Returned: -2 operation would block, -1: error, 0: success
//
extern "C" int
sysWriteByte(int fd, int data)
{
    char ch = data;
again:
    int rc = write(fd, &ch, 1);
    if (rc == 1)
        return 0; // success
    else if (errno == EAGAIN)
        return -2; // operation would block
    else if (errno == EINTR)
        goto again; // interrupted by signal; try again
    else {
        fprintf(SysErrorFile, "%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                fd, errno, strerror(errno));
        return -1; // some kind of error
    }
}

// Read multiple bytes from file or socket.
// Taken:    file or socket descriptor
//           buffer to be filled
//           number of bytes requested
// Returned: number of bytes delivered (-2: error, -1: socket would have blocked)
//
extern "C" int
sysReadBytes(int fd, char *buf, int cnt)
{
    //fprintf(SysTraceFile, "%s: read %d 0x%08x %d\n", Me, fd, buf, cnt);
again:
    int rc = read(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        // fprintf(SysTraceFile, "%s: read on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    else if (err == EINTR)
        goto again; // interrupted by signal; try again
    fprintf(SysTraceFile, "%s: read error %d (%s) on %d\n", Me,
            err, strerror(err), fd);
    return -2;
}

// Write multiple bytes to file or socket.
// Taken:    file or socket descriptor
//           buffer to be written
//           number of bytes to write
// Returned: number of bytes written (-2: error, -1: socket would have blocked,
//           -3 EPIPE error)
//
extern "C" int
sysWriteBytes(int fd, char *buf, int cnt)
{
// fprintf(SysTraceFile, "%s: write %d 0x%08x %d\n", Me, fd, buf, cnt);
again:
    int rc = write(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        // fprintf(SysTraceFile, "%s: write on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    if (err == EINTR)
        goto again; // interrupted by signal; try again
    if (err == EPIPE)
    {
        //fprintf(SysTraceFile, "%s: write on %d with nobody to read it\n", Me, fd);
        return -3;
    }
    fprintf(SysTraceFile, "%s: write error %d (%s) on %d\n", Me,
            err, strerror( err ), fd);
    return -2;
}

// Close file or socket.
// Taken:    file/socket descriptor
// Returned:  0: success
//           -1: file/socket not currently open
//           -2: i/o error
//
static int sysClose(int fd)
{
    if ( -1 == fd ) return -1;
    int rc = close(fd);
    if (rc == 0) return 0; // success
    if (errno == EBADF) return -1; // not currently open
    return -2; // some other error
}

// Set the close-on-exec flag for given file descriptor.
//
// Taken: the file descriptor
// Returned: 0 if sucessful, nonzero otherwise
//
extern "C" int
sysSetFdCloseOnExec(int fd)
{
    return fcntl(fd, F_SETFD, FD_CLOEXEC);
}

//--------------------------//
// System timer operations. //
//--------------------------//

#ifdef _AIX
#include <mon.h>
#endif

static void *timeSlicerThreadMain(void *) __attribute__((noreturn));

static void *
timeSlicerThreadMain(void *arg)
{
    long ns = (long) arg;
#ifdef DEBUG_SYS
    fprintf(SysErrorFile, "time slice interval %dns\n", ns);
#endif
    for (;;) {
        struct timespec howLong;
        struct timespec remaining;

        howLong.tv_sec = 0;
        howLong.tv_nsec = ns;
        errno = 0;
        int errorCode = nanosleep( &howLong, &remaining );
        if (errorCode) {
            if (errno == EINTR) {
                // We were blocked by a signal; might as well go on.
                // XXX I believe processTimerTick() calls things that do
                // polling, but I haven't strictly verified this.  --augart
                ;
            } else if (errno == EINVAL) {
                fprintf(SysErrorFile, "%s: nanosleep failed: %s (errno=%d): ",
                        Me, strerror(errno), errno);
                // XXX As of this writing (August 2003), SysErrorFile is
                // always identical to stderr.  Unlike SysTraceFile,
                // SysErrorFile is never reset.  (So why does it exist?)
                perror(NULL);   // use perror() since strerror() is not
                                // thread-safe, and GNU strerror() is
                                // incompatible with SUSv3.
                sysExit(EXIT_STATUS_TIMER_TROUBLE);
            }
        }
        if (systemExiting)
            pthread_exit(0);
        processTimerTick();
    }
    // NOTREACHED
}

/*
 * Actions to take on a timer tick
 */
extern "C" void processTimerTick(void) {

    Address VmToc = (Address) getJTOC();

    /*
     * Increment Processor.timerTicks
     */
    if (TimerTicksOffset == 0) return; // static field offsets not yet known
    int* ttp = (int *) ((char *) VmToc + TimerTicksOffset);
    *ttp = *ttp + 1;

    /*
     * Check to see if a gc is in progress.
     * If it is then simply return (ignore timer tick).
     */
    int gcStatus = *(int *) ((char *) VmToc + GCStatusOffset);
    if (gcStatus != 0) return;

    /*
     * Increment Processor.reportedTimerTicks
     */
    int* rttp = (int *) ((char *) VmToc + ReportedTimerTicksOffset);
    *rttp = *rttp + 1;

    /*
     * Turn on thread-switch flag in each virtual processor.
     * Note that "jtoc" is not necessarily valid, because we might have
     * interrupted C-library code, so we use boot image
     * jtoc address (== VmToc) instead.
     */
    Address *processors = *(Address **) ((char *) VmToc + getProcessorsOffset());
    unsigned cnt = getArrayLength(processors);
    unsigned longest_stuck_ticks = 0;
    for (unsigned i = GreenScheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt ; i++) {
        // Set takeYieldpoint field to 1; decrement timeSliceExpired field;
        // See how many ticks this VP has ignored, if too many have passed we will issue a warning below
        *(int *)((char *)processors[i] + Processor_takeYieldpoint_offset) = 1;
        int val = (*(int *)((char *)processors[i] + Processor_timeSliceExpired_offset))--;

        if (longest_stuck_ticks < (unsigned) -val)
            longest_stuck_ticks = -val;
    }

#ifndef RVM_WITH_GCSPY
    /*
     * After 1000 timer intervals (often == 10 seconds), print a message
     * every 100 timer intervals (often == 2 second), so we don't
     * just appear to be hung.
     */
#ifndef RVM_FOR_GCTRACE
    if (longest_stuck_ticks > 5001) {
        fprintf(stderr, "%s: Exiting VM due to suspected deadlock\n", Me);
        sysExit(EXIT_STATUS_TIMER_TROUBLE); }
#endif
    if (longest_stuck_ticks >= 1000 && (longest_stuck_ticks % 100) == 0) {
      /* When performing tracing, delays will often last more than 10
       * seconds and can take much, much longer (on a fairly fast
       * machine I've seen delays above 1 minute).  This is due to a
       * GC possibly needing to include additional processing and
       * outputting a large amount of information which will
       * presumably be saved.  Unfortunately, these warnings will appear
       * in the midst of the trace and cause them to be very difficult to
       * parse.  As this is a normal condition during tracing, and causes
       * a lot of problems to tracing, we elide the warning.
       */
#ifndef RVM_FOR_GCTRACE
        fprintf(stderr, "%s: WARNING: Virtual processor has ignored timer interrupt for %d ms.\n",
                Me, getTimeSlice_msec() * longest_stuck_ticks);
        fprintf(stderr, "This may indicate that a blocking system call has occured and the VM is deadlocked\n");
#endif
    }
#endif
}


// Start/stop interrupt generator for thread timeslicing.
// The interrupt will be delivered to whatever virtual processor
// happens to be running when the timer fires.
//
// Taken:    interrupt interval, in milliseconds (0: "disable timer")
// Returned: nothing
//
static void
setTimeSlicer(int msTimerDelay)
{
  pthread_t timeSlicerThread; // timeSlicerThread is a write-only dummy
                                    // variable.
  int nsTimerDelay = msTimerDelay * 1000 * 1000;
  int errorCode = pthread_create(&timeSlicerThread, NULL,
                                       timeSlicerThreadMain, (void*)nsTimerDelay);
  if (errorCode) {
    fprintf(SysErrorFile, "%s: Unable to create the Time Slicer thread: %s\n", Me, strerror(errorCode));
    sysExit(EXIT_STATUS_TIMER_TROUBLE);
  }
  // fprintf(SysTraceFile, "%s: timeslice is %dms\n", Me, msTimerDelay);
}

static int timeSlice_msec;

extern "C" void
sysVirtualProcessorEnableTimeSlicing(int timeSlice)
{
    if (lib_verbose)
        fprintf(stderr,"Using a time-slice of %d ms\n", timeSlice);
    // timeSlice could be less than 1!
    if (timeSlice < 1 || timeSlice > 999) {
        fprintf(SysErrorFile, "%s: timeslice of %d msec is outside range 1 msec ..999 msec\n",
                Me, timeSlice);
        sysExit(EXIT_STATUS_TIMER_TROUBLE);
    }
    timeSlice_msec = timeSlice;
    setTimeSlicer(timeSlice);
}

int
getTimeSlice_msec(void)
{
    return timeSlice_msec;
}

extern "C" long long
sysCurrentTimeMillis()
{
    int rc;
    long long returnValue;
    struct timeval tv;
    struct timezone tz;

    returnValue = 0;

    rc = gettimeofday(&tv, &tz);
    if (rc != 0) {
        returnValue = rc;
    } else {
        returnValue = ((long long) tv.tv_sec * 1000) + tv.tv_usec/1000;
    }

    return returnValue;
}


#ifdef __MACH__
mach_timebase_info_data_t timebaseInfo;
#endif

extern "C" long long
sysNanoTime()
{
	long long retVal;
#ifndef __MACH__
	struct timespec tp;
    int rc = clock_gettime(CLOCK_REALTIME, &tp);
	if (rc != 0) {
		retVal = rc;
	    if (lib_verbose) {
	        fprintf(stderr, "sysNanoTime: Non-zero return code %d from clock_gettime\n", rc);
	    }
	} else {
		retVal = (((long long) tp.tv_sec) * 1000000000) + tp.tv_nsec;
	}
#else
        Nanoseconds nanoTime;
	unsigned long long high;
	unsigned long long low;

	low = mach_absolute_time();

	high = low >> 32;
	low &= 0xffffffff;

	high *= timebaseInfo.numer;
	low *= timebaseInfo.numer;

	retVal = (high / timebaseInfo.denom) << 32;
	retVal += (low + ((high % timebaseInfo.denom) << 32)) / timebaseInfo.denom;
#endif
    return retVal;
}


/** Routine to sleep for a number of nanoseconds (howLongNanos).  This is
 * ridiculous on regular Linux, where we actually only sleep in increments of
 * 1/HZ (1/100 of a second on x86).  Luckily, Linux will round up.
 *
 * This is just used internally in the scheduler, but we might as well make
 * the function work properly even if it gets used for other purposes.
 *
 * We don't return anything, since we don't need to right now.  Just try to
 * sleep; if interrupted, return.
 */
extern "C" void
sysNanosleep(long long howLongNanos)
{
    struct timespec req;
    const long long nanosPerSec = 1000LL * 1000 * 1000;
    req.tv_sec = howLongNanos / nanosPerSec;
    req.tv_nsec = howLongNanos % nanosPerSec;
    int ret = nanosleep(&req, (struct timespec *) NULL);
    if (ret < 0) {
        if (errno == EINTR)
            /* EINTR is expected, since we do use signals internally. */
            return;

        fprintf(SysErrorFile, "%s: nanosleep(<tv_sec=%ld,tv_nsec=%ld>) failed:"
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

// How many physical cpu's are present and actually online?
// Assume 1 if no other good ansewr.
// Taken:    nothing
// Returned: number of cpu's
//
// Note: this function is only called once.  If it were called more often
// than that, we would want to use a static variable to indicate that we'd
// already printed the WARNING messages and were not about to print any more.

extern "C" int
sysNumProcessors()
{
    int numCpus = -1;  /* -1 means failure. */

#ifdef __GNU_LIBRARY__      // get_nprocs is part of the GNU C library.
    /* get_nprocs_conf will give us a how many processors the operating
       system configured.  The number of processors actually online is what
       we want.  */
    // numCpus = get_nprocs_conf();
    errno = 0;
    numCpus = get_nprocs();
    // It is not clear if get_nprocs can ever return failure; assume it might.
    if (numCpus < 1) {
       fprintf(SysTraceFile, "%s: WARNING: get_nprocs() returned %d (errno=%d)\n", Me, numCpus, errno);
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
            fprintf(SysTraceFile, "%s: WARNING: sysctl(CTL_HW,HW_NCPU) failed;"
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
            fprintf(SysTraceFile, "%s: WARNING: sysconf(_SC_NPROCESSORS_ONLN)"
                    " failed\n", Me);
        }
    }
#endif

#ifdef _AIX
    if (numCpus < 0) {
        numCpus = _system_configuration.ncpus;
        if (numCpus < 0) {
            fprintf(SysTraceFile, "%s: WARNING: _system_configuration.ncpus"
                    " has the insane value %d\n" , Me, numCpus);
        }
    }
#endif

    if (numCpus < 0) {
        fprintf(SysTraceFile, "%s: WARNING: Can not figure out how many CPUs"
                " are online; assuming 1\n");
        numCpus = 1;            // Default
    }

#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: sysNumProcessors: returning %d\n", Me, numCpus );
#endif
    return numCpus;
}

// Create a virtual processor (aka "unix kernel thread", "pthread").
// Taken:    register values to use for pthread startup
// Returned: virtual processor's o/s handle
//
extern "C" Address
sysVirtualProcessorCreate(Address pr, Address ip, Address fp)
{

    Address    *sysVirtualProcessorArguments;
    pthread_attr_t sysVirtualProcessorAttributes;
    pthread_t      sysVirtualProcessorHandle;
    int            rc;

    // create arguments
    //
    sysVirtualProcessorArguments = new Address[3];
    sysVirtualProcessorArguments[0] = pr;
    sysVirtualProcessorArguments[1] = ip;
    sysVirtualProcessorArguments[2] = fp;

    // create attributes
    //
    if ((rc = pthread_attr_init(&sysVirtualProcessorAttributes))) {
        fprintf(SysErrorFile, "%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
    //
    pthread_attr_setscope(&sysVirtualProcessorAttributes, PTHREAD_SCOPE_SYSTEM);

    // create virtual processor
    //
    if ((rc = pthread_create(&sysVirtualProcessorHandle,
                             &sysVirtualProcessorAttributes,
                             sysVirtualProcessorStartup,
                             sysVirtualProcessorArguments)))
    {
        fprintf(SysErrorFile, "%s: pthread_create failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    if (VERBOSE_PTHREAD)
        fprintf(SysTraceFile, "%s: pthread_create 0x%08x\n", Me, (Address) sysVirtualProcessorHandle);

    return (Address)sysVirtualProcessorHandle;
}

static void *
sysVirtualProcessorStartup(void *args)
{
    Address pr       = ((Address *)args)[0];
    Address ip       = ((Address *)args)[1];
    Address fp       = ((Address *)args)[2];

    if (VERBOSE_PTHREAD)
#ifndef RVM_FOR_32_ADDR
        fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: pr=0x%016llx ip=0x%016llx fp=0x%016llx\n", Me, pr, ip, fp);
#else
        fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: pr=0x%08x ip=0x%08x fp=0x%08x\n", Me, pr, ip, fp);
#endif
    // branch to vm code
    //
#ifndef RVM_FOR_POWERPC
    {
        *(Address *) (pr + Processor_framePointer_offset) = fp;
        Address sp = fp + Constants_STACKFRAME_BODY_OFFSET;
        bootThread((void*)ip, (void*)pr, (void*)sp);
    }
#else
    bootThread((int)(Word)getJTOC(), pr, ip, fp);
#endif

    // not reached
    //
    fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: failed\n", Me);
    return 0;
}


// Bind execution of current virtual processor to specified physical cpu.
// Taken:    physical cpu id (0, 1, 2, ...)
// Returned: nothing
//
extern "C" void
sysVirtualProcessorBind(int UNUSED cpuId)
{
    int numCpus = sysNumProcessors();
    if (VERBOSE_PTHREAD)
      fprintf(SysTraceFile, "%s: %d cpu's\n", Me, numCpus);

    // bindprocessor() seems to be only on AIX
#ifdef RVM_FOR_AIX
    if (numCpus == -1) {
        fprintf(SysErrorFile, "%s: sysconf failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    cpuId = cpuId % numCpus;

    int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
    fprintf(SysTraceFile, "%s: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", Me, pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

    if (rc) {
        fprintf(SysErrorFile, "%s: bindprocessor failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#ifdef RVM_FOR_LINUX
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(cpuId % numCpus, &cpuset);

    pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset);
#endif
}

/* These are unused in single virtual procesor mode: */
pthread_cond_t VirtualProcessorStartup = PTHREAD_COND_INITIALIZER;
pthread_cond_t MultithreadingStartup = PTHREAD_COND_INITIALIZER;

pthread_mutex_t VirtualProcessorStartupLock = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t MultithreadingStartupLock = PTHREAD_MUTEX_INITIALIZER;

int VirtualProcessorsLeftToStart;
int VirtualProcessorsLeftToWait;

// Thread-specific data key in which to stash the id of
// the pthread's Processor.  This allows the system call library
// to find the Processor object at runtime.
extern pthread_key_t VmProcessorKey;
extern pthread_key_t IsVmProcessorKey;

// Create keys for thread-specific data.
extern "C" void
sysCreateThreadSpecificDataKeys(void)
{
    int rc1, rc2;

    // Create a key for thread-specific data so we can associate
    // the id of the Processor object with the pthread it is running on.
    rc1 = pthread_key_create(&VmProcessorKey, 0);
    if (rc1 != 0) {
        fprintf(SysErrorFile, "%s: pthread_key_create(&VMProcessorKey,0) failed (err=%d)\n", Me, rc1);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    rc2 = pthread_key_create(&IsVmProcessorKey, 0);
    if (rc2 != 0) {
        fprintf(SysErrorFile, "%s: pthread_key_create(&IsVMProcessorKey,0) failed (err=%d)\n", Me, rc2);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

#ifdef DEBUG_SYS
    fprintf(stderr, "%s: vm processor key=%u\n", Me, VmProcessorKey);
#endif
}

extern "C" void
sysInitializeStartupLocks(int howMany)
{
    VirtualProcessorsLeftToStart = howMany;
    VirtualProcessorsLeftToWait = howMany;
}

extern "C" void
sysWaitForVirtualProcessorInitialization()
{
    pthread_mutex_lock( &VirtualProcessorStartupLock );
    if (--VirtualProcessorsLeftToStart == 0)
        pthread_cond_broadcast( &VirtualProcessorStartup );
    else
        pthread_cond_wait(&VirtualProcessorStartup, &VirtualProcessorStartupLock);
    pthread_mutex_unlock( &VirtualProcessorStartupLock );
}

extern "C" void
sysWaitForMultithreadingStart()
{
    pthread_mutex_lock( &MultithreadingStartupLock );
    if (--VirtualProcessorsLeftToWait == 0)
        pthread_cond_broadcast( &MultithreadingStartup );
    else
        pthread_cond_wait(&MultithreadingStartup, &MultithreadingStartupLock);
    pthread_mutex_unlock( &MultithreadingStartupLock );
}

// Routines to support sleep/wakeup of idle threads:
// CRA, Maria
// 09/14/00
//

/*
  sysPthreadSelf() just returns the thread ID of
  the current thread.

  This happens to be only called once, at thread startup time, but please
  don't rely on that fact.
*/
extern "C" int
sysPthreadSelf()
{
    int thread;

    thread = (int)pthread_self();

    if (VERBOSE_PTHREAD)
        fprintf(SysTraceFile, "%s: sysPthreadSelf: thread %d\n", Me, thread);

    return thread;
}

/* Perform some initialization related to
  per-thread signal handling for that thread. (Block SIGCONT, set up a special
  signal handling stack for the thread.)

  This is only called once, at thread startup time. */
extern "C" void
sysPthreadSetupSignalHandling()
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
    stack.ss_sp = new char[SIGSTKSZ];

    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        /* Only fails with EINVAL, ENOMEM, EPERM */
        fprintf (SysErrorFile, "sigaltstack failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }
#endif

    /*
     * Block the CONT signal.  This makes SIGCONT reach this
     * pthread only when this pthread performs a sigwait().
     * --Maria
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
        fprintf (SysErrorFile, "pthread_sigmask or sigthreadmask failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }

}



//
extern "C" int
sysPthreadSignal(int pthread)
{
    pthread_t thread;
    thread = (pthread_t)pthread;

    pthread_kill(thread, SIGCONT);
    return 0;
}

//
extern "C" int
sysPthreadJoin(int pthread)
{
    pthread_t thread;
    thread = (pthread_t)pthread;
    // fprintf(SysTraceFile, "%s: pthread %d joins %d\n", Me, pthread_self(), thread);
    pthread_join(thread, NULL);
    return 0;
}

//
extern "C" void
sysPthreadExit()
{
    // fprintf(SysTraceFile, "%s: pthread %d exits\n", Me, pthread_self());
    pthread_exit(NULL);
}

//
// Yield execution of current virtual processor back to o/s.
// Taken:    nothing
// Returned: nothing
//
extern "C" void
sysVirtualProcessorYield()
{
    /** According to the Linux manpage, sched_yield()'s presence can be
     *  tested for by using the #define _POSIX_PRIORITY_SCHEDULING, and if
     *  that is not present to use the sysconf feature, searching against
     *  _SC_PRIORITY_SCHEDULING.  However, I don't really trust it, since
     *  the AIX 5.1 include files include this definition:
     *      ./unistd.h:#undef _POSIX_PRIORITY_SCHEDULING
     *  so my trust that it is implemented properly is scanty.  --augart
     */
    sched_yield();
}

//
// Taken -- address of an integer lockword
//       -- value to store in the lockword to 'release' the lock
// Release the lockout word by storing the value in it
// and wait for a signal.
extern "C" int
sysPthreadSigWait( int * lockwordAddress,
                   int  lockReleaseValue )
{
    sigset_t input_set, output_set;
    int      sig;
    int rc;                     // retval from subfunction

    *lockwordAddress = lockReleaseValue;

    sigemptyset(&input_set);
    sigaddset(&input_set, SIGCONT);
#ifndef RVM_FOR_AIX
    rc = pthread_sigmask(SIG_BLOCK, NULL, &output_set);
#else
    rc = sigthreadmask(SIG_BLOCK, NULL, &output_set);
#endif
    if (rc) {
        fprintf (SysErrorFile, "pthread_sigmask or sigthreadmask failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }

    rc = sigwait(&input_set, &sig);
    if (rc) {
        fprintf (SysErrorFile, "sigwait failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }

    // if status has been changed to BLOCKED_IN_SIGWAIT (because of GC)
    // sysYield until unblocked
    //
    while ( *lockwordAddress == 5 /*Processor.BLOCKED_IN_SIGWAIT*/ )
        sysVirtualProcessorYield();

    return 0;
}

// Stash address of the Processor object in the thread-specific
// data for the current pthread.  This allows us to get a handle
// on the Processor (and its associated state) from arbitrary
// native code.
//
extern "C" int
sysStashVmProcessorInPthread(Address vmProcessor)
{
    //fprintf(SysErrorFile, "stashing vm processor = %d, self=%u\n", vmProcessor, pthread_self());
    int rc = pthread_setspecific(VmProcessorKey, (void*) vmProcessor);
    int rc2 = pthread_setspecific(IsVmProcessorKey, (void*) 1);
    if (rc != 0 || rc2 != 0) {
        fprintf(SysErrorFile, "%s: pthread_setspecific() failed (err=%d,%d)\n", Me, rc, rc2);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    return 0;
}

//------------------------//
// Arithmetic operations. //
//------------------------//

extern "C" long long
sysLongDivide(long long a, long long b)
{
    return a/b;
}

extern "C" long long
sysLongRemainder(long long a, long long b)
{
    return a % b;
}

extern "C" double
sysLongToDouble(long long a)
{
    return (double)a;
}

extern "C" float
sysLongToFloat(long long a)
{
    return (float)a;
}

double maxlong = 0.5 + (double)0x7fffffffffffffffLL;
double maxint  = 0.5 + (double)0x7fffffff;

extern "C" int
sysFloatToInt(float a)
{
    if (maxint <= a) return 0x7fffffff;
    if (a <= -maxint) return 0x80000000;
    if (a != a) return 0; // NaN => 0
    return (int)a;
}

extern "C" int
sysDoubleToInt(double a)
{
    if (maxint <= a) return 0x7fffffff;
    if (a <= -maxint) return 0x80000000;
    if (a != a) return 0; // NaN => 0
    return (int)a;
}

extern "C" long long
sysFloatToLong(float a)
{
    if (maxlong <= a) return 0x7fffffffffffffffLL;
    if (a <= -maxlong) return 0x8000000000000000LL;
    return (long long)a;
}

extern "C" long long
sysDoubleToLong(double a)
{
    if (maxlong <= a) return 0x7fffffffffffffffLL;
    if (a <= -maxlong) return 0x8000000000000000LL;
    return (long long)a;
}

// sysDoubleRemainder is only used on PPC
#include <math.h>
extern "C" double
sysDoubleRemainder(double a, double b)
{
    double tmp = remainder(a, b);
    if (a > 0.0) {
        if (b > 0.0) {
            if (tmp < 0.0) {
                tmp += b;
            }
        } else if (b < 0.0) {
            if (tmp < 0.0) {
                tmp -= b;
            }
        }
    } else {
        if (b > 0.0) {
            if (tmp > 0.0) {
                tmp -= b;
            }
        } else {
            if (tmp > 0.0) {
                tmp += b;
            }
        }
    }
    return tmp;
}

/* Used to parse command line arguments that are
   doubles and floats early in booting before it
   is safe to call Float.valueOf or Double.valueOf.   This is only used in
   parsing command-line arguments, so we can safely print error messages that
   assume the user specified this number as part of a command-line argument. */
extern "C" float
sysPrimitiveParseFloat(const char * buf)
{
    if (! buf[0] ) {
   fprintf(SysErrorFile, "%s: Got an empty string as a command-line"
      " argument that is supposed to be a"
      " floating-point number\n", Me);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    char *end;         // This prototype is kinda broken.  It really
            // should be char *.  But isn't.
    errno = 0;
    float f = (float)strtod(buf, &end);
    if (errno) {
   fprintf(SysErrorFile, "%s: Trouble while converting the"
      " command-line argument \"%s\" to a"
      " floating-point number: %s\n", Me, buf, strerror(errno));
   exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (*end != '\0') {
        fprintf(SysErrorFile, "%s: Got a command-line argument that"
      " is supposed to be a floating-point value,"
      " but isn't: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    return f;
}

// Used to parse command line arguments that are
// ints and bytes early in booting before it
// is safe to call Integer.parseInt and Byte.parseByte
// This is only used in
// parsing command-line arguments, so we can safely print error messages that
// assume the user specified this number as part of a command-line argument.
extern "C" int
sysPrimitiveParseInt(const char * buf)
{
    if (! buf[0] ) {
   fprintf(SysErrorFile, "%s: Got an empty string as a command-line"
      " argument that is supposed to be an integer\n", Me);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    char *end;
    errno = 0;
    long l = strtol(buf, &end, 0);
    if (errno) {
   fprintf(SysErrorFile, "%s: Trouble while converting the"
      " command-line argument \"%s\" to an integer: %s\n",
      Me, buf, strerror(errno));
   exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (*end != '\0') {
        fprintf(SysErrorFile, "%s: Got a command-line argument that is supposed to be an integer, but isn't: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    int32_t ret = l;
    if ((long) ret != l) {
        fprintf(SysErrorFile, "%s: Got a command-line argument that is supposed to be an integer, but its value does not fit into a Java 32-bit integer: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    return ret;
}

/** Parse memory sizes.
    @return negative values to indicate errors. */
extern "C" jlong
sysParseMemorySize(const char *sizeName, /*  "initial heap" or "maximum heap"
                                            or "initial stack" or
                                            "maximum stack" */
                   const char *sizeFlag, // e.g., "ms" or "mx" or "ss" or "sg" or "sx"
                   const char *defaultFactor, // "M" or "K" are used
                   int roundTo,  // Round to PAGE_SIZE_BYTES or to 4.
                   const char *token /* e.g., "-Xms200M" or "-Xms200" */,
                   const char *subtoken /* e.g., "200M" or "200" */)
{
    bool fastExit = false;
    unsigned ret_uns=  parse_memory_size(sizeName, sizeFlag, defaultFactor,
                                         (unsigned) roundTo, token, subtoken,
                                         &fastExit);
    if (fastExit)
        return -1;
    else
        return (jlong) ret_uns;
}



//-------------------//
// Memory operations //
//-------------------//

// Memory to memory copy.
//
extern "C" void
sysCopy(void *dst, const void *src, Extent cnt)
{
    memcpy(dst, src, cnt);
}

// Allocate memory.
//
extern "C" void *
sysMalloc(int length)
{
    return malloc(length);
}

extern "C" void *
sysCalloc(int length)
{
  return calloc(1, length);
}

// Release memory.
//
extern "C" void
sysFree(void *location)
{
    free(location);
}

// Zero a range of memory bytes.
//
extern "C" void
sysZero(void *dst, Extent cnt)
{
    memset(dst, 0x00, cnt);
}

// Zero a range of memory pages.
// Taken:    start of range (must be a page boundary)
//           size of range, in bytes (must be multiple of page size, 4096)
// Returned: nothing
//
extern "C" void
sysZeroPages(void *dst, int cnt)
{
    // uncomment one of the following
    //
#define STRATEGY 1 /* for optimum pBOB numbers */
// #define STRATEGY 2 /* for more realistic workload */
// #define STRATEGY 3 /* as yet untested */

#if (STRATEGY == 1)
    // Zero memory by touching all the bytes.
    // Advantage:    fewer page faults during mutation
    // Disadvantage: more page faults during collection, at least until
    //               steady state working set is achieved
    //
    sysZero(dst, cnt);
#endif

#if (STRATEGY == 2)
    // Zero memory by using munmap() followed by mmap().
    // This assumes that bootImageRunner.C has used mmap()
    // to acquire memory for the VM bootimage and heap.
    // Advantage:    fewer page faults during collection
    // Disadvantage: more page faults during mutation
    //
    int rc = munmap(dst, cnt);
    if (rc != 0)
    {
        fprintf(SysErrorFile, "%s: munmap failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
    if (addr == (void *)-1)
    {
        fprintf(SysErrorFile, "%s: mmap failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#if (STRATEGY == 3)
    // Zero memory by using disclaim().
    // This assumes that bootImageRunner.C has used malloc()
    // to acquire memory for the VM bootimage and heap and requires use of
    // the binder option -bmaxdata:0x80000000 which allows large malloc heaps
    // Advantage:    ? haven't tried this strategy yet
    // Disadvantage: ? haven't tried this strategy yet
    //
    int rc = disclaim((char *)dst, cnt, ZERO_MEM);
    if (rc != 0)
    {
        fprintf(SysErrorFile, "%s: disclaim failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#undef STRATEGY
}

// Synchronize caches: force data in dcache to be written out to main memory
// so that it will be seen by icache when instructions are fetched back.
//
// Taken:    start of address range
//           size of address range (bytes)
// Returned: nothing
//
//
extern "C" void
sysSyncCache(void UNUSED *address, size_t UNUSED  size)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: sync 0x%08x %d\n", Me, (unsigned)address, size);
#endif

#ifdef RVM_FOR_POWERPC
  #ifdef RVM_FOR_AIX
    _sync_cache_range((caddr_t) address, size);
  #else
    if (size < 0) {
      fprintf(SysErrorFile, "%s: tried to sync a region of negative size!\n", Me);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
    uintptr_t start = (uintptr_t)address;
    uintptr_t end = start + size;
    uintptr_t addr;

    /* update storage */
    /* Note: if one knew the cache line size, one could write a better loop */
    for (addr=start; addr < end; ++addr)
      asm("dcbst 0,%0" : : "r" (addr) );

    /* wait for update to commit */
    asm("sync");

    /* invalidate icache */
    /* Note: if one knew the cache line size, one could write a better loop */
    for (addr=start; addr<end; ++addr)
      asm("icbi 0,%0" : : "r" (addr) );

    /* context synchronization */
    asm("isync");
  #endif
#endif
}

//-----------------//
// MMAP operations //
//-----------------//

// mmap - general case
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        desired protection (Java int)
//        flags (Java int)
//        file descriptor (Java int)
//        offset (Java long)  [to cover 64 bit file systems]
// Returned: address of region (or -1 on failure) (Java ADDRESS)

extern "C" void *
sysMMap(char *start , size_t length ,
        int protection , int flags ,
        int fd , Offset offset)
{
   return mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
}

// Same as mmap, but with more debugging support.
// Returned: address of region if successful; errno (1 to 127) otherwise

extern "C" void *
sysMMapErrno(char *start , size_t length ,
        int protection , int flags ,
        int fd , Offset offset)
{
  void* res = mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
  if (res == (void *) -1){
#if RVM_FOR_32_ADDR
    fprintf(stderr, "mmap (%x, %u, %d, %d, %d, %ld) failed with %d: ",
       (Address) start, (unsigned) length, protection, flags, fd, offset, errno);
#else
    fprintf(stderr, "mmap (%llx, %u, %d, %d, -1, 0) failed with %d: ",
       (Address) start, (unsigned) length, protection, flags, errno);
#endif
    return (void *) errno;
  }else{
#ifdef DEBUG_SYS
    printf("mmap worked - region = [0x%x ... 0x%x]    size = %d\n", res, ((int)res) + length, length);
#endif
    return res;
  }
}

// mprotect
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        new protection (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMProtect(char *start, size_t length, int prot)
{
    return mprotect(start, length, prot);
}

// getpagesize
// Taken: (no arguments)
// Returned: page size in bytes (Java int)
extern "C" int
sysGetPageSize()
{
    return (int)(getpagesize());
}

//
// Sweep through memory to find which areas of memory are mappable.
// This is invoked from a command-line argument.
extern "C" void
findMappable()
{
    int granularity = 1 << 22; // every 4 megabytes
    int max = (1 << 30) / (granularity >> 2);
    int pageSize = getpagesize();
    for (int i=0; i<max; i++) {
        char *start = (char *) (i * granularity);
        int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
        int flag = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
        void *result = mmap (start, (size_t) pageSize, prot, flag, -1, 0);
        int fail = (result == (void *) -1);
#if RVM_FOR_32_ADDR
        printf("0x%x: ", (Address) start);
#else
        printf("0x%llx: ", (Address) start);
#endif
        if (fail) {
            printf("FAILED with errno %d: %s\n", errno, strerror(errno));
        } else {
            printf("SUCCESS\n");
            munmap(start, (size_t) pageSize);
        }
    }
}


//----------------//
// JNI operations //
//----------------//

// Load dynamic library.
// Taken:
// Returned: a handler for this library, null if none loaded
//
extern "C" void*
sysDlopen(char *libname)
{
    void * libHandler;
    do {
        libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
    }
    while( (libHandler == 0 /*null*/) && (errno == EINTR) );
    if (libHandler == 0) {
        fprintf(SysErrorFile,
                "%s: error loading library %s: %s\n", Me,
                libname, dlerror());
//      return 0;
    }

    return libHandler;
}

// Look up symbol in dynamic library.
// Taken:
// Returned:
//
extern "C" void*
sysDlsym(Address libHandler, char *symbolName)
{
    return dlsym((void *) libHandler, symbolName);
}

//---------------------//
// Network operations. //
//---------------------//

// #define DEBUG_NET

#ifdef RVM_FOR_AIX
// Work around header file differences: AIX 4.1 vs AIX 4.2 vs AIX 4.3
//
#define getsockname xxxgetsockname
#define accept      xxxaccept
#endif
#include <sys/socket.h>
#include <sys/select.h>
#include <netdb.h>
#include <netinet/tcp.h>
#ifdef RVM_FOR_AIX
#undef  getsockname
#undef  accept
extern "C" int getsockname(int socketfd, struct sockaddr *address, int *address_len);
extern "C" int accept(int socketfd, struct sockaddr *address, int *address_len);
#endif

// instrumentation of socket troubles
int maxSelectInterrupts = 0;
int maxAcceptInterrupts = 0;
int maxConnectInterrupts = 0;
int selectInterrupts = 0;
int acceptInterrupts = 0;
int connectInterrupts = 0;


// Create a socket, unassociated with any particular address + port.
// Taken:    kind of socket to create (0: datagram, 1: stream)
// Returned: socket descriptor (-1: error)
//
extern "C" int
sysNetSocketCreate(int isStream)
{
    int fd;

    fd = socket(AF_INET, isStream ? SOCK_STREAM : SOCK_DGRAM, 0);
    if (fd == -1) {
        fprintf(SysErrorFile, "%s: socket create failed: %s (errno=%d)\n",
                Me, strerror(errno), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: create socket %d\n", Me, fd);
#endif

    return fd;
}

// Obtain port number associated with a socket.
// Taken: socket descriptor
// Returned: port number (-1: error)
//
extern "C" int
sysNetSocketPort(int fd)
{
    sockaddr_in info;
#if defined RVM_FOR_AIX
    int len;
#else
    socklen_t len;
#endif

    len = sizeof info;
    if (getsockname(fd, (sockaddr *)&info, &len) == -1)
    {
        fprintf(SysErrorFile, "%s: getsockname on %d failed: %s (errno=%d)\n",
                Me, fd, strerror(errno), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: socket %d using port %d\n", Me, fd, MANGLE16(info.sin_port));
#endif

    return MANGLE16(info.sin_port);
}

// Obtain send buffer size associated with a socket.
// Taken: socket descriptor
// Returned: size (-1: error)
//
extern "C" int
sysNetSocketSndBuf(int fd)
{
    int val = 0;
    socklen_t len;

    len = sizeof(int);
    if (getsockopt(fd, SOL_SOCKET, SO_SNDBUF, &val, &len) == -1)
    {
        fprintf(SysErrorFile, "%s: getsockopt on %d failed: %s (errno=%d)\n",
                Me, fd, strerror(errno), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: socket %d sndbuf size %d\n", Me, fd, val);
#endif

    return val;
}

// Obtain local address associated with a socket.
// Taken: socket descriptor
// Returned: local address (-1: error)
//
extern "C" int
sysNetSocketLocalAddress(int fd)
{
    sockaddr_in info;
#if defined RVM_FOR_AIX
    int len;
#else
    socklen_t len;
#endif

    len = sizeof info;
    if (getsockname(fd, (sockaddr *)&info, &len) == -1)
    {
        fprintf(SysErrorFile, "%s: getsockname on %d failed: %s (errno=%d)\n",
                Me, fd, strerror( errno ), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: socket %d using address %d\n", Me, fd, MANGLE32(info.sin_addr.s_addr));
#endif

    return MANGLE32(info.sin_addr.s_addr);
}

// Obtain family associated with a socket.
// Taken: socket descriptor
// Returned: local address (-1: error)
//
extern "C" int
sysNetSocketFamily(int fd)
{
    sockaddr_in info;
#if defined RVM_FOR_AIX
    int len;
#else
    socklen_t len;
#endif

    len = sizeof info;
    if (getsockname(fd, (sockaddr *)&info, &len) == -1) {
        fprintf(SysErrorFile, "%s: getsockname on %d failed: %s (errno=%d)\n",
                Me, fd, strerror( errno ), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: socket %d using family %d\n", Me, fd, info.sin_family);
#endif

    return info.sin_family;
}

// Make a socket into a "listener" so we can later accept() connections on it.
// Taken:    socket descriptor
//           max number of pending connections to allow
// Returned: 0:success -1:error
//
extern "C" int
sysNetSocketListen(int fd, int backlog)
{
    if (listen(fd, backlog) == -1) {
        fprintf(SysErrorFile, "%s: socket listen on %d failed: %s (errno=%d)\n", Me, fd, strerror(errno), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: listen on socket %d (backlog %d)\n", Me, fd, backlog);
#endif

    return 0;
}


// Associate a local address and port with a socket.
// Taken:    socket descriptor
//           address protocol family (AF_INET, for example)
//           desired local address
//           desired local port
// Returned: 0=success, -1=failure
//
extern "C" int
sysNetSocketBind(int fd,
                 int family,
                 unsigned int localAddress,
                 unsigned int localPort)
{
    sockaddr_in address;

    memset(&address, 0, sizeof address);
    address.sin_family      = family;
    address.sin_addr.s_addr = MANGLE32(localAddress);
    address.sin_port        = MANGLE16(localPort);

    if (bind(fd, (sockaddr *)&address, sizeof address) == -1) {
        fprintf(SysErrorFile,
                "%s: socket bind on %d for port %d failed: %s (errno=%d)\n",
                Me, fd, localPort, strerror( errno ), errno);
        return -1;
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: bind %d to %d.%d.%d.%d:%d\n", Me, fd,
            (localAddress >> 24) & 0xff, (localAddress >> 16) & 0xff,
            (localAddress >> 8) & 0xff, (localAddress >> 0) & 0xff,
            localPort & 0x0000ffff);
#endif

    return 0;
}

// Associate a remote address and port with a socket.
// Taken:    socket descriptor
//           address protocol family (AF_INET, for example)
//           desired remote address
//           desired remote port
// Returned: 0: success
//          -1: operation interrupted by timer tick (caller should try again)
//          -2: operation would have blocked (caller should try again)
//          -3: network error
//          -4: network error - connection refused
//          -5: network error - host unreachable
//
extern "C" int
sysNetSocketConnect(int fd, int family, int remoteAddress, int remotePort)
{
    int interruptsThisTime = 0;
    for (;;) {
        sockaddr_in address;

        memset(&address, 0, sizeof address);
        address.sin_family      = family;
        address.sin_addr.s_addr = MANGLE32(remoteAddress);
        address.sin_port        = MANGLE16(remotePort);

        if (connect(fd, (sockaddr *)&address, sizeof address) == -1) {
            if (errno == EINTR) {
                fprintf(SysTraceFile,
                        "%s: connect on %d interrupted, retrying\n", Me, fd);
                connectInterrupts++;
                interruptsThisTime++;
                continue;
            } else if (errno == EINPROGRESS) {
#ifdef DEBUG_NET
                fprintf(SysTraceFile, "%s: connect on %d failed: %s \n",
                        Me, fd, strerror(errno ));
#endif
                return -2;
            } else if (errno == EISCONN) {
                // connection was "in progress" due to previous call.
                // This (retry) call has succeeded.
#ifdef DEBUG_NET
                fprintf(SysTraceFile, "%s: connect on %d: %s\n",
                        Me, fd, strerror( errno ));
#endif
                goto ok;
            } else if (errno == ECONNREFUSED) {
                fprintf(SysTraceFile, "%s: connect on %d failed: %s \n",
                        Me, fd, strerror( errno ));
                return -4;
            } else if (errno == EHOSTUNREACH) {
                fprintf(SysTraceFile, "%s: connect on %d failed: %s \n",
                        Me, fd, strerror( errno ));
                return -5;
            } else {
                fprintf(SysErrorFile,
                        "%s: socket connect on %d failed: %s (errno=%d)\n",
                        Me, fd, strerror(errno), errno);
                return -3;
            }
        }

    ok:
        if (interruptsThisTime > maxConnectInterrupts) {
            maxConnectInterrupts = interruptsThisTime;
            fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n",
                    interruptsThisTime);
        }

#ifdef DEBUG_NET
        fprintf(SysTraceFile, "%s: connect %d to %d.%d.%d.%d:%d\n",
                Me, fd, (remoteAddress >> 24) & 0xff, (remoteAddress >> 16) & 0xff,
                (remoteAddress >> 8) & 0xff, (remoteAddress >> 0) & 0xff,
                remotePort & 0x0000ffff);
#endif
        return 0;
    }
}

// Wait for connection to appear on a socket.
// Taken:    socket descriptor on which to wait
//           place to put information about remote side of connection that appeared
// Returned: >= 0: socket descriptor that was assigned to connection
//             -1: operation interrupted by timer tick (caller should try again)
//             -2: operation would have blocked (caller should try again)
//             -3: network error
//
extern "C" int
sysNetSocketAccept(int fd, void *connectionObject)
{
    int interruptsThisTime = 0;
    int connectionFd = -1;
    sockaddr_in info;
#if defined RVM_FOR_AIX
    int len;
#else
    socklen_t len;
#endif

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "accepting for socket %d, 0x%x\n", fd, connectionObject);
#endif

    len = sizeof info;
    for (;;) {
        connectionFd = accept(fd, (sockaddr *)&info, &len);

        if (connectionFd > 0) {
            break;
        } else if (connectionFd == -1) {

            if (errno == EINTR) {
                fprintf(SysTraceFile, "%s: accept on %d interrupted\n", Me, fd);
                interruptsThisTime++;
                acceptInterrupts++;
                continue;
            } else if (errno == EAGAIN) {
#ifdef DEBUG_NET
                fprintf(SysTraceFile,
                        "%s: accept on %d would have blocked: needs retry\n",
                        Me, fd);
#endif
                return -2;
            } else {
#ifdef DEBUG_NET
                fprintf(SysTraceFile,
                        "%s: socket accept on %d failed: %s (errno=%d)\n",
                        Me, fd, strerror( errno ), errno);
#endif
                return -3;
            }
        }
    }

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "accepted %d for socket %d, 0x%x\n",
            connectionFd, fd, connectionObject);
#endif

    int remoteFamily  = info.sin_family;
    int remoteAddress = MANGLE32(info.sin_addr.s_addr);
    int remotePort    = MANGLE16(info.sin_port);

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: %d accept %d from %d.%d.%d.%d:%d\n",
            Me, fd, connectionFd,
            (remoteAddress >> 24) & 0xff, (remoteAddress >> 16) & 0xff,
            (remoteAddress >> 8) & 0xff, (remoteAddress >> 0) & 0xff,
            remotePort & 0x0000ffff);
#endif

    void *addressObject = *(void **)((char *)connectionObject + java_net_SocketImpl_address_offset);
    int  *familyField   =  NULL; // TODO - Harmony - (int   *)((char *)addressObject    + java_net_InetAddress_family_offset);
    int  *addressField  =  NULL; // TODO - Harmony - (int   *)((char *)addressObject    + java_net_InetAddress_address_offset);
    int  *portField     =  (int   *)((char *)connectionObject + java_net_SocketImpl_port_offset);

    *familyField  = remoteFamily;
    *addressField = remoteAddress;
    *portField    = remotePort;

    if (interruptsThisTime > maxAcceptInterrupts) {
        maxAcceptInterrupts = interruptsThisTime;
        fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n",
                interruptsThisTime);
    }

    return connectionFd;
}

// Set "linger" option for a socket.
// With option enabled, when socket on this end is closed, this process blocks until
// unsent data has been received by other end or timeout expires.
// With option disabled, when socket is closed on this end, any unsent data is discarded.
//
// Taken:       socket descriptor
//              enable option?
//              timeout (if option enabled, 0 otherwise)
// Returned:    0: success, -1: error
//
//
extern "C" int
sysNetSocketLinger(int fd, int enable, int timeout)
{

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: linger socket=%d enable=%d timeout=%d\n",
            Me, fd, enable, timeout);
#endif

    linger info;
    info.l_onoff  = enable;
    info.l_linger = timeout;

    int rc = setsockopt(fd, SOL_SOCKET, SO_LINGER, &info, sizeof info);
    if (rc == -1) fprintf(SysErrorFile,
                          "%s: socket linger on %d failed: %s (errno=%d)\n",
                          Me, fd, strerror(errno), errno);
    return rc;
}

// Set "no delay" option for a socket.
// With option enabled, data written to socket is sent immediately.
// With option disabled, sending is delayed in order to coalesce packets.
//
// Taken:    socket descriptor
//           enable option?
// Returned: 0: success, -1: error
//
extern "C" int
sysNetSocketNoDelay(int fd, int enable)
{
    int value = enable;

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: nodelay socket=%d value=%d\n", Me, fd, value);
#endif

    int rc = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &value, sizeof value);
    if (rc == -1)
        fprintf(SysErrorFile, "%s: TCP_NODELAY on %d failed: %s (errno=%d)\n",
                Me, fd, strerror(errno), errno);

    return rc;
}

// Enable non-blocking i/o on this socket.
// This will cause future accept(), read(), and write() calls on the socket
// to return errno == EAGAIN if the operation would block, and connect() calls
// to return errno == EINPROGRESS
// Taken:    socket descriptor
//           enable option?
// Returned: 0: success, -1: error
//
extern "C" int
sysNetSocketNoBlock(int fd, int enable)
{
    int value = enable;

#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: noblock socket=%d value=%d\n", Me, fd, value);
#endif

    int rc = ioctl(fd, FIONBIO, &value);
    if (rc == -1) {
        fprintf(SysErrorFile, "%s: FIONBIO on %d failed: %s (errno=%d)\n",
                Me, fd, strerror(errno), errno);
        return -1;
    }

    return rc;
}

// Close a socket.
// Taken:    socket descriptor
// Returned: 0: success
//          -1: socket not currently open
//          -2: i/o error
//
extern "C" int
sysNetSocketClose(int fd)
{
#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: close socket=%d\n", Me, fd);
#endif

    // shutdown (disable sends and receives on) socket then close it

    int rc = shutdown(fd, 2);

    if (rc == 0) {
        // shutdown succeeded
        return sysClose(fd);
    }

    if (errno == ENOTCONN) {
        // socket wasn't connected so shutdown error is meaningless
        return sysClose(fd);
    }

    fprintf(SysErrorFile, "%s: socket shutdown on %d failed: %s (errno=%d)\n",
            Me, fd, strerror(errno), errno);

    sysClose(fd);
    return -2; // shutdown (and possibly close) error
}

// Perform a "half-close" on a socket.
//
// Taken:
//   fd - the socket file descriptor
//   how - which side of socket should be closed: 0 if input, 1 if output
// Returned:
//   0 if success, -1 on error
extern "C" int
sysNetSocketShutdown(int fd, int how)
{
#ifdef DEBUG_NET
    fprintf(SysTraceFile, "%s: shutdown socket %d for %s\n", Me,
            fd,
            (how==0)? "input": "output");
#endif

    return shutdown(fd, how);
}

// Add file descriptors in an array to a fd_set,
// keeping track of the highest-numbered file descriptor
// seen so far.
//
// Taken:
// fdSet - the fd_set to which file descriptors should be added
// fdArray - array containing file descriptors to be added
// count - number of file descriptors in fdArray
// maxFd - pointer to int containing highest-numbered file descriptor
//         seen so far
// exceptFdSet - set of file descriptors to watch for exceptions.
//         We add ALL file descriptors to this set, in order to
//         detect invalid ones.
//
// Returned: true if successful
//           false if an invalid file descriptor is encountered
static bool
addFileDescriptors(
    fd_set *fdSet,
    int *fdArray,
    int count,
    int *maxFd,
    fd_set *exceptFdSet = 0)
{
    //fprintf ( SysTraceFile, "%d descriptors in set\n", count );
    for (int i = 0; i < count; ++i) {
        int fd = fdArray[i] & ThreadIOConstants_FD_MASK;
#ifdef DEBUG_SYS
        fprintf ( SysTraceFile, "select on fd %d\n", fd );
#endif
        if (fd > FD_SETSIZE) {
            fprintf(SysErrorFile, "%s: select: fd(%d) exceeds system limit(%d)\n", Me,
                    fd, FD_SETSIZE);
            return false;
        }
        if (fd > *maxFd)
            *maxFd = fd;
        FD_SET(fd, fdSet);
        if (exceptFdSet != 0)
            FD_SET(fd, exceptFdSet);
    }

    return true;
}

// Mark file descriptors which have become ready.
//
// Taken:
// fdArray - array of file descriptors to mark
// count - number of file descriptors in the array
// ready - fd_set indicating which file descriptors are ready
static void
updateStatus(int *fdArray, int count, fd_set *ready)
{
    for (int i = 0; i < count; ++i) {
        int fd = fdArray[i] & ThreadIOConstants_FD_MASK;
        if (FD_ISSET(fd, ready))
            fdArray[i] = ThreadIOConstants_FD_READY;
    }
}

// Check given array of file descriptors to see if any of
// them are in the exception fd set, meaning that they became
// invalid for some reason.
//
// Taken:
// fdArray - the array of file descriptors to check
// count - number of file descriptors in the array
// exceptFdSet - the set of exception fds as returned by select()
//
// Returned: the number of file descriptors from the array
// which are marked as invalid.
static int
checkInvalid(int *fdArray, int count, fd_set *exceptFdSet)
{
    int numInvalid = 0;
    for (int i = 0; i < count; ++i) {
        int fd = fdArray[i] & ThreadIOConstants_FD_MASK;
        if (FD_ISSET(fd, exceptFdSet)) {
            //fprintf(SysErrorFile, "%s: fd %d in sysNetSelect() is invalid\n", Me, fd);
            fdArray[i] = ThreadIOConstants_FD_INVALID;
            ++numInvalid;
        }
    }

    return numInvalid;
}

// Test list of sockets to see if an i/o operation would proceed without blocking.
// Taken:       array of file descriptors to be checked
//              number of file descriptors for read, write, and exceptions
// Returned:    1: some sockets can proceed with i/o operation
//              0: no sockets can proceed with i/o operation
//             -1: error
// Side effect: readFds[i] is set to "ThreadIOConstants.FD_READY" iff i/o can proceed on that socket
extern "C" int
sysNetSelect(
    int *allFds,           // all fds being polled: read, write, and exception
    int rc,                     // number of read file descriptors
    int wc,                     // number of write file descriptors
    int ec)                     // number of exception file descriptors
{
    // JTD 8/2/01 moved for loop up here because select man page says it can
    // corrupt all its inputs, including the fdsets, when it returns an error
    int interruptsThisTime = 0;
    for (;;) {
        int maxfd   = -1;         // largest fd currently in use

        // build bitstrings representing fd's to be interrogated
        //
        fd_set readReady; FD_ZERO(&readReady);
        fd_set writeReady; FD_ZERO(&writeReady);
        fd_set exceptReady; FD_ZERO(&exceptReady);

        if (!addFileDescriptors(&readReady, allFds + ThreadIOQueue_READ_OFFSET, rc, &maxfd, &exceptReady)
            || !addFileDescriptors(&writeReady, allFds + ThreadIOQueue_WRITE_OFFSET, wc, &maxfd, &exceptReady)
            || !addFileDescriptors(&exceptReady, allFds + ThreadIOQueue_EXCEPT_OFFSET, ec, &maxfd))
            return -1;

        // Ensure that select() call below
        // calls the real C library version, not our hijacked version
#ifdef HAVE_SYSWRAP
        SelectFunc_t realSelect = getLibcSelect();
#else
        #define realSelect(n, read, write, except, timeout) \
           select(n, read, write, except, timeout)
#endif
        // interrogate
        //
        // timeval timeout; timeout.tv_sec = 0; timeout.tv_usec = SelectDelay * 1000;
        timeval timeout; timeout.tv_sec = 0; timeout.tv_usec = 0;
        int ret = realSelect(maxfd + 1, &readReady, &writeReady, &exceptReady, &timeout);
        int err = errno;

        if (ret == 0) {
            // none ready
            if (interruptsThisTime > maxSelectInterrupts) {
                maxSelectInterrupts = interruptsThisTime;
                fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n", interruptsThisTime);
            }

            return 0;
        }

        if (ret > 0)
        { // some ready
            updateStatus(allFds + ThreadIOQueue_READ_OFFSET, rc, &readReady);
            updateStatus(allFds + ThreadIOQueue_WRITE_OFFSET, wc, &writeReady);
            updateStatus(allFds + ThreadIOQueue_EXCEPT_OFFSET, ec, &exceptReady);

            if (interruptsThisTime > maxSelectInterrupts)
                maxSelectInterrupts = interruptsThisTime;

            return 1;
        }

        if (err == EINTR) { // interrupted by timer tick: retry
            return 0;
        } else if (err == EBADF) {
            // This can happen if somebody passes us an invalid file descriptor.
            // Check the read and write file descriptors against the exception
            // fd set, so we can find the culprit(s).
            int numInvalid = 0;
            numInvalid += checkInvalid(allFds + ThreadIOQueue_READ_OFFSET, rc, &exceptReady);
            numInvalid += checkInvalid(allFds + ThreadIOQueue_WRITE_OFFSET, wc, &exceptReady);
            if (numInvalid == 0) {
                // This is bad.
                fprintf(SysErrorFile,
                        "%s: select returned with EBADF, but no file descriptors found in exception set\n", Me);
                return -1;
            } else {
                return 1;
            }
        }

        // fprintf(SysErrorFile, "%s: socket select failed (err=%d (%s))\n", Me, err, strerror( err ));
        return -1;
    }

    return -1; // not reached (but xlC isn't smart enough to realize it)
}

// Poll given process ids to see if the processes they
// represent have finished.
//
// Taken:
// pidArray - array of process ids
// exitStatusArray - array in which to store the exit status code
//   of processes which have finished
// numPids - number of process ids being queried
extern "C" void
sysWaitPids(int pidArray[], int exitStatusArray[], int numPids)
{
    for (int i = 0; i < numPids; ++i) {
        int status;
        pid_t pid = (pid_t) pidArray[i];
        if (pid == waitpid(pid, &status, WNOHANG)) {
            // Process has finished
            int exitStatus;
            if (WIFSIGNALED(status))
                exitStatus = -1;
            else
                exitStatus = WEXITSTATUS(status);

            // Mark process as finished, and record its exit status
            pidArray[i] = ThreadProcessWaitQueue_PROCESS_FINISHED;
            exitStatusArray[i] = exitStatus;
        }
    }
}

extern "C" int
getArrayLength(void* ptr)
{
    return *(int*)(((char *)ptr) + ObjectModel_ARRAY_LENGTH_OFFSET);
}

// VMMath
extern "C" double
sysVMMathSin(double a) {
    return sin(a);
}

extern "C" double
sysVMMathCos(double a) {
    return cos(a);
}

extern "C" double
sysVMMathTan(double a) {
    return tan(a);
}

extern "C" double
sysVMMathAsin(double a) {
    return asin(a);
}

extern "C" double
sysVMMathAcos(double a) {
    return acos(a);
}

extern "C" double
sysVMMathAtan(double a) {
    return atan(a);
}

extern "C" double
sysVMMathAtan2(double a, double b) {
    return atan2(a, b);
}


extern "C" double
sysVMMathCosh(double a) {
    return cosh(a);
}

extern "C" double
sysVMMathSinh(double a) {
    return sinh(a);
}

extern "C" double
sysVMMathTanh(double a) {
    return tanh(a);
}

extern "C" double
sysVMMathExp(double a) {
    return exp(a);
}

extern "C" double
sysVMMathLog(double a) {
    return log(a);
}

extern "C" double
sysVMMathSqrt(double a) {
    return sqrt(a);
}

extern "C" double
sysVMMathPow(double a, double b) {
    return pow(a, b);
}

extern "C" double
sysVMMathIEEEremainder(double a, double b) {
    return remainder(a, b);
}

extern "C" double
sysVMMathCeil(double a) {
    return ceil(a);
}

extern "C" double
sysVMMathFloor(double a) {
    return floor(a);
}

extern "C" double
sysVMMathRint(double a) {
    return rint(a);
}

extern "C" double
sysVMMathCbrt(double a) {
    return cbrt(a);
}

extern "C" double
sysVMMathExpm1(double a) {
    return expm1(a);
}

extern "C" double
sysVMMathHypot(double a, double b) {
    return hypot(a, b);
}

extern "C" double
sysVMMathLog10(double a) {
    return log10(a);
}

extern "C" double
sysVMMathLog1p(double a) {
    return log1p(a);
}

#ifdef RVM_WITH_GCSPY
// GCspy

//NOTE It is the responsibility of the calling code to
//     check that server, driver etc are non-null.

extern "C" {
#include "gcspy_gc_stream.h"
#include "gcspy_main_server.h"
#include "gcspy_gc_driver.h"
#include "gcspy_color_db.h"
#include "gcspy_utils.h"
}

typedef void * (*pthread_start_routine_t)(void *);

static gcspy_main_server_t server;

// debugging
#define GCSPY_TRACE 0
static int stream_count = 0;
static int stream_len;

extern "C" gcspy_gc_stream_t *
gcspyDriverAddStream (gcspy_gc_driver_t *driver, int id) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverAddStream: driver=%x(%s), id=%d...",
          driver, driver->name, id);
#endif
  gcspy_gc_stream_t *stream = gcspy_driverAddStream(driver, id);
#if GCSPY_TRACE
  fprintf(SysTraceFile, "stream=%x\n", stream);
#endif
  return stream;
}

extern "C" void
gcspyDriverEndOutput (gcspy_gc_driver_t *driver) {
  int len;
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverEndOutput: driver=%x(%s), len=%d, written=%d\n",
                        driver, driver->name, stream_len, stream_count);
  stream_count = 0;
  /*??*/
  gcspy_buffered_output_t *output =
      gcspy_command_stream_get_output(driver->interpreter);
  len = gcspy_bufferedOutputGetLen(output);
  fprintf(SysTraceFile, "gcspyDriverEndOutput: interpreter has len=%d\n", len);
#endif
  gcspy_driverEndOutput(driver);
}

extern "C" void
gcspyDriverInit (gcspy_gc_driver_t *driver, int id, char *serverName, char *driverName,
                 char *title, char *blockInfo, int tileNum,
                 char *unused, int mainSpace) {

#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverInit: driver=%x, id=%d, serverName=%s, driverName=%s, title=%s, blockInfo=%s, %d tiles, used=%s, mainSpace=%d\n",
                   driver, id, serverName, driverName,
                   title, blockInfo, tileNum,
                   unused, mainSpace);
#endif
  gcspy_driverInit(driver, id, serverName, driverName,
                   title, blockInfo, tileNum,
                   unused, mainSpace);
}

extern "C" void
gcspyDriverInitOutput (gcspy_gc_driver_t *driver) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverInitOutput: driver=%x(s)\n",
          driver, driver->name);
#endif
  gcspy_driverInitOutput(driver);
}

extern "C" void
gcspyDriverResize (gcspy_gc_driver_t *driver, int size) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverResize: driver=%x(%s), size %d\n",
          driver, driver->name, size);
#endif
  gcspy_driverResize(driver, size);
}

extern "C" void
gcspyDriverSetTileName (gcspy_gc_driver_t *driver, int tile, char *format, long value) {
  char buffer[128];
  sprintf(buffer, format, value);
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyDriverSetTileName: driver=%x(%s), tile %d %s\n", driver, driver->name, tile, buffer);
#endif
  gcspy_driverSetTileName(driver, tile, buffer);
}

extern "C" void
gcspyDriverSetTileNameRange (gcspy_gc_driver_t *driver, int tile, Address start, Address end) {
  char name[256];
#ifndef RVM_FOR_32_ADDR
  snprintf(name, sizeof name, "   [%016llx-%016llx)", start, end);
#else
  snprintf(name, sizeof name, "   [%08x-%08x)", start, end);
#endif
  gcspyDriverSetTileName(driver, tile, name, 0);
}

extern "C" void
gcspyDriverSpaceInfo (gcspy_gc_driver_t *driver, char *spaceInfo) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverSpaceInfo: driver=%x(%s), spaceInfo = +%s+(%x)\n", driver, driver->name, spaceInfo, spaceInfo);
#endif
  gcspy_driverSpaceInfo(driver, spaceInfo);
}

extern "C" void
gcspyDriverStartComm (gcspy_gc_driver_t *driver) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverStartComm: driver=%x(%s)\n", driver, driver->name);
#endif
  gcspy_driverStartComm(driver);
}

extern "C" void
gcspyDriverStream (gcspy_gc_driver_t *driver, int id, int len) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverStream: driver=%x(%s), id=%d(%s), len=%d\n",
          driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
#endif
  gcspy_driverStream(driver, id, len);
}

extern "C" void
gcspyDriverStreamByteValue (gcspy_gc_driver_t *driver, int val) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyDriverStreamByteValue: driver=%x, val=%d\n", driver, val);
#endif
#if GCSPY_TRACE
  stream_count++;
#endif
  gcspy_driverStreamByteValue(driver, val);
}

extern "C" void
gcspyDriverStreamShortValue (gcspy_gc_driver_t *driver, short val) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyDriverStreamShortValue: driver=%x, val=%d\n", driver, val);
#endif
#if GCSPY_TRACE
  stream_count++;
#endif
  gcspy_driverStreamShortValue(driver, val);
}

extern "C" void
gcspyDriverStreamIntValue (gcspy_gc_driver_t *driver, int val) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyDriverStreamIntValue: driver=%x, val=%d\n", driver, val);
#endif
#if GCSPY_TRACE
  stream_count++;
#endif
  gcspy_driverStreamIntValue(driver, val);
}

extern "C" void
gcspyDriverSummary (gcspy_gc_driver_t *driver, int id, int len) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyDriverSummary: driver=%x(%s), id=%d(%s), len=%d\n",
          driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
#endif
  gcspy_driverSummary(driver, id, len);
}

extern "C" void
gcspyDriverSummaryValue (gcspy_gc_driver_t *driver, int val) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyDriverSummaryValue: driver=%x, val=%d\n", driver, val);
#endif
#if GCSPY_TRACE
  stream_count++;
#endif
  gcspy_driverSummaryValue(driver, val);
}

/* Note: passed driver but uses driver->interpreter */
extern "C" void
gcspyIntWriteControl (gcspy_gc_driver_t *driver, int id, int len) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyIntWriteControl: driver=%x(%s), interpreter=%x, id=%d, len=%d\n", driver, driver->name, driver->interpreter, id, len);
  stream_count = 0;
  stream_len = len;
#endif
  gcspy_intWriteControl(driver->interpreter, id, len);
}

extern "C" gcspy_gc_driver_t *
gcspyMainServerAddDriver (gcspy_main_server_t *server) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerAddDriver: server address = %x(%s), adding driver...", server, server->name);
#endif
  gcspy_gc_driver_t *driver = gcspy_mainServerAddDriver(server);
#if GCSPY_TRACE
  fprintf(SysTraceFile, "address = %d\n", driver);
#endif
  return driver;
}

extern "C" void
gcspyMainServerAddEvent (gcspy_main_server_t *server, int event, const char *name) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerAddEvent: server address = %x(%s), event=%d, name=%s\n", server, server->name, event, name);
#endif
  gcspy_mainServerAddEvent(server, event, name);
}

extern "C" gcspy_main_server_t *
gcspyMainServerInit (int port, int len, const char *name, int verbose) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerInit: server=%x, port=%d, len=%d, name=%s, verbose=%d\n", &server, port, len, name, verbose);
#endif
  gcspy_mainServerInit(&server, port, len, name, verbose);
  return &server;
}

extern "C" int
gcspyMainServerIsConnected (gcspy_main_server_t *server, int event) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerIsConnected: server=%x, event=%d...", &server, event);
#endif
  int res = gcspy_mainServerIsConnected(server, event);
#if GCSPY_TRACE
  if (res)
    fprintf(SysTraceFile, "connected\n");
  else
    fprintf(SysTraceFile, "not connected\n");
#endif
  return res;
}

typedef void gcspyMainServerOuterLoop_t(gcspy_main_server_t *);

extern "C" gcspyMainServerOuterLoop_t *
gcspyMainServerOuterLoop () {
  /* return gcspy_mainServerOuterLoop;*/
  return gcspy_mainServerMainLoop;
}

extern "C" void
gcspyMainServerSafepoint (gcspy_main_server_t *server, int event) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerSafepoint: server=%x, event=%d\n", &server, event);
#endif
  gcspy_mainServerSafepoint(server, event);
}

extern "C" void
gcspyMainServerSetGeneralInfo (gcspy_main_server_t *server, char *generalInfo) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerSetGeneralInfo: server=%x, info=%s\n", &server, generalInfo);
#endif
  gcspy_mainServerSetGeneralInfo(server, generalInfo);
}

extern "C" void
gcspyMainServerStartCompensationTimer (gcspy_main_server_t *server) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerStartCompensationTimer: server=%x\n", server);
#endif
  gcspy_mainServerStartCompensationTimer(server);
}

extern "C" void
gcspyMainServerStopCompensationTimer (gcspy_main_server_t *server) {
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyMainServerStopCompensationTimer: server=%x\n", server);
#endif
  gcspy_mainServerStopCompensationTimer(server);
}

extern "C" void
gcspyStartserver (gcspy_main_server_t *server, int wait, void *loop) {
//#ifndef __linux__
//  printf("I am not Linux!");
//     exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
//#endif __linux__
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyStartserver: starting thread, wait=%d\n", wait);
#endif
  pthread_t tid;
  int res = pthread_create(&tid, NULL,
                          (pthread_start_routine_t) loop,  server);
  if (res != 0) {
      fprintf(SysErrorFile,"Couldn't create thread.\n");
      exit(EXIT_STATUS_MISC_TROUBLE);
  }

  if(wait) {
#if GCSPY_TRACE
    fprintf(SysTraceFile, "gcspy_mainServerWaitForClient: server=%x\n", server);
#endif
    gcspy_mainServerWaitForClient(server);
  }
}

extern "C" void
gcspyStreamInit (gcspy_gc_stream_t *stream, int id, int dataType, char *streamName,
                 int minValue, int maxValue, int zeroValue, int defaultValue,
                 char *stringPre, char *stringPost, int presentation, int paintStyle,
                 int indexMaxStream, int red, int green, int blue) {
  gcspy_color_t colour;
  colour.red = (unsigned char) red;
  colour.green = (unsigned char) green;
  colour.blue = (unsigned char) blue;
#if GCSPY_TRACE
  fprintf(SysTraceFile, "gcspyStreamInit: stream=%x, id=%d, dataType=%d, streamName=\"%s\", min=%d, max=%d, zero=%d, default=%d, pre=\"%s\", post=\"%s\", presentation=%d, style=%d, maxIndex=%d, colour=%x<%d,%d,%d>\n",
                   stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue, defaultValue,
		   stringPre, stringPost, presentation, paintStyle,
		   indexMaxStream, &colour, colour.red, colour.green, colour.blue);
#endif
  gcspy_streamInit(stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue,defaultValue,
		   stringPre, stringPost, presentation, paintStyle,
		   indexMaxStream, &colour);
}

extern "C" void
gcspyFormatSize (char *buffer, int size) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "gcspyFormatSize: size=%d...", size);
#endif
  strcpy(buffer, gcspy_formatSize(size));
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "buffer=%s\n", buffer);
#endif
}

extern "C" int
gcspySprintf(char *str, const char *format, char *arg) {
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "sprintf: str=%x, format=%s, arg=%s\n", str, format, arg);
#endif
  int res = sprintf(str, format, arg);
#if (GCSPY_TRACE > 1)
  fprintf(SysTraceFile, "sprintf: result=%s (%x)\n", str, str);
#endif
  return res;
}


#endif



