/*
 * (C) Copyright IBM Corp 2001,2002,2004
 */
//$Id$

/**
 * O/S support services required by the java class libraries.
 * See also: VM_BootRecord.java
 *
 * @author Derek Lieber
 * @date 20 Apr 1998
 */

// Aix and Linux version.  PowerPC and IA32.

// Only called externally from Java programs.
extern "C" void sysExit(int) __attribute__((noreturn));

// AIX needs this to get errno right. JTD
#define _THREAD_SAFE_ERRNO

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//
#ifdef _AIX43
#include </usr/include/unistd.h>
extern "C" void profil(void *, uint, ulong, uint);
extern "C" int sched_yield(void);
#endif

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <utime.h>

#ifdef RVM_FOR_LINUX
#include <asm/cache.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <netinet/in.h>
#include <linux/net.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <asm/ioctls.h>

# ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
# include <sched.h>
# endif

/* OSX/Darwin */
#elif (defined __MACH__)
#include <sys/stat.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <httpd/os.h>
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
extern "C"     int sigaltstack(const struct sigaltstack *ss, struct sigaltstack *oss);
# if (defined HAS_DLCOMPAT)
# include <dlfcn.h>
# endif
#define MAP_ANONYMOUS MAP_ANON 
# if (!defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
# include <sched.h>
# endif


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

#include "sys.h"
#ifdef _AIX
extern "C" timer_t gettimerid(int timer_type, int notify_type);
extern "C" int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In rvm/src/tools/bootImageRunner.

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
#include <pthread.h>
#endif

#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
# include "syswrap.h"

#endif // RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS

/* #define DEBUG_SYS               */
#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
// #define VERBOSE_PTHREAD false
#define VERBOSE_PTHREAD lib_verbose
#endif

// static int TimerDelay  =  10; // timer tick interval, in milliseconds     (10 <= delay <= 999)
// static int SelectDelay =   2; // pause time for select(), in milliseconds (0  <= delay <= 999)

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
static void *sysVirtualProcessorStartup(void *args);
#endif

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


//---------------------------//
// Environmental operations. //
//---------------------------//

//!!TEMP - dummy function
//!!TODO: remove this when boot record has single "sysTOC" instead of multiple "sysXXXTOC" values
extern "C" void sys(void) __attribute__((noreturn));

extern "C" void
sys()
{
    fprintf(SysErrorFile, "%s: unexpected call to \"sys\"\n", Me);
    sysExit(EXIT_STATUS_UNEXPECTED_CALL_TO_SYS);
}

// Console write (java character).
//
extern "C" void
sysWriteChar(unsigned value)
{
    char c = (value > 127) ? '?' : (char)value;
    // use high level stdio to ensure buffering policy is observed
    fprintf(SysTraceFile, "%c", c);
}

// Console write (java integer).
//
extern "C" void
sysWrite(int value, int hexToo)
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
sysWriteLong(long long value, int hexToo)
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
sysWriteDouble(double value,  int postDecimalDigits)
{
    if (value != value) {
        fprintf(SysTraceFile, "NaN");
    } else {
        if (postDecimalDigits > 9) postDecimalDigits = 9;
        char tmp[5] = {'%', '.', '0'+postDecimalDigits, 'f', 0};
        fprintf(SysTraceFile, tmp, value);
    }
}

// Exit with a return code.
//
#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
pthread_mutex_t DeathLock = PTHREAD_MUTEX_INITIALIZER;
#endif

static bool systemExiting = false;

extern "C" void
sysExit(int value)
{
    if (value != 0)
        fprintf(SysErrorFile, "%s: exit %d\n", Me, value);

    fflush(SysErrorFile);
    fflush(SysTraceFile);
    fflush(stdout);

    systemExiting = true;

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    pthread_mutex_lock( &DeathLock );
#endif
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
        char *src = JavaArgs[argno];
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

//------------------------//
// Filesystem operations. //
//------------------------//

// List contents of a filesystem directory (with names delimited by nulls).
// Taken:    null terminated directory name
//           buffer in which to place results
//           buffer size
// Returned: number of bytes written to buffer (-1=i/o error)
// Note:     a full buffer indicates a partial list, in which
//           case caller should make a bigger buffer and retry
//
extern "C" int
sysList(char *dirName, char *buf, int limit)
{

#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: list %s 0x%08x %d\n", Me, dirName, buf, limit);
#endif

    char DELIMITER = '\0';
    int  cnt = 0;
    DIR *dir = opendir(dirName);
    for (dirent *dp = readdir(dir); dp; dp = readdir(dir)) {
        // POSIX says that d_name is NULL-terminated
        char *name = dp->d_name;
        int len = strlen( name );

#ifdef DEBUG_SYS
        fprintf(SysTraceFile, "%s: found %s\n", Me, name);
#endif


        if (len == 2 && name[0] == '.' && name[1] == '.') 
            continue; // skip ".."
        if (len == 1 && name[0] == '.'                  ) 
            continue;  // skip "."

        while (len--) {
            if (cnt == limit) 
                break;
            *buf++ = *name++;
            cnt +=1;
        }

        if (cnt == limit) 
            break;
        *buf++ = DELIMITER;
        cnt += 1;
        if (cnt == limit) 
            break;
    }
    closedir(dir);
    return cnt;
}

// Get file status.
// Taken:    null terminated filename
//           kind of info desired (see VM_FileSystem.STAT_XXX)
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
    case VM_FileSystem_STAT_EXISTS:        
        return 1;                              // exists
    case VM_FileSystem_STAT_IS_FILE:       
        return S_ISREG(info.st_mode) != 0; // is file
    case VM_FileSystem_STAT_IS_DIRECTORY:  
        return S_ISDIR(info.st_mode) != 0; // is directory
    case VM_FileSystem_STAT_IS_READABLE:   
        return (info.st_mode & S_IREAD) != 0; // is readable by owner
    case VM_FileSystem_STAT_IS_WRITABLE:
        return (info.st_mode & S_IWRITE) != 0; // is writable by owner
    case VM_FileSystem_STAT_LAST_MODIFIED: 
        return info.st_mtime;   // time of last modification
    case VM_FileSystem_STAT_LENGTH:        
        return info.st_size;    // length
    }
    return -1; // unrecognized request
}

// Check user's perms.
// Taken:    null terminated filename
//           kind of access perm to check for (see VM_FileSystem.ACCESS_W_OK)
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

// Set modification time (and access time) to given value
// (representing seconds after the epoch).
extern "C" int
sysUtime(const char *fileName, int modTimeSec)
{
    struct utimbuf buf;
    buf.actime = modTimeSec;
    buf.modtime = modTimeSec;
    return utime(fileName, &buf);
}

// Open file.
// Taken:    null terminated filename
//           access/creation mode (see VM_FileSystem.OPEN_XXX)
// Returned: file descriptor (-1: not found or couldn't create)
//
extern "C" int
sysOpen(char *name, int how)
{
    int fd;

    switch (how) {
    case VM_FileSystem_OPEN_READ:   
        fd = open(name, O_RDONLY                         ); // "read"
        break;
    case VM_FileSystem_OPEN_WRITE:  
        fd = open(name, O_RDWR | O_CREAT | O_TRUNC,  0666); // "write"
        break;
    case VM_FileSystem_OPEN_MODIFY: 
        fd = open(name, O_RDWR | O_CREAT,            0666); // "modify"
        break;
    case VM_FileSystem_OPEN_APPEND: 
        fd = open(name, O_RDWR | O_CREAT | O_APPEND, 0666); // "append"
        break;
    default: 
        return -1;
    }

#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: open %s %d, fd=%d\n", Me, name, how, fd);
#endif

    return fd;
}

// Delete file.
// Taken:    null terminated filename
// Returned:
//
extern "C" int
sysDelete(char *name)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: delete %s\n", Me, name);
#endif

    return remove(name);
}

// Rename file.
// Taken:    null terminated from and to filenames
// Returned:
//
extern "C" int
sysRename(char *fromName, char *toName)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: rename %s to %s\n", Me, fromName, toName);
#endif

    return rename(fromName, toName);
}

// Make directory.
// Taken:    null terminated filename
// Returned: status (-1=error)
//
extern "C" int
sysMkDir(char *name)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: mkdir %s\n", Me, name);
#endif

    return mkdir(name, 0777); // Give all user/group/other permissions.
    // mkdir will modify them according to the
    // file mode creation mask (umask (1)).
}

// How many bytes can be read from file/socket without blocking?
// Taken:    file/socket descriptor
// Returned: >=0: count, VM_ThreadIOConstants_FD_INVALID: bad file descriptor,
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
        return badFD ? VM_ThreadIOConstants_FD_INVALID : -1;
    }
// fprintf(SysTraceFile, "%s: available fd=%d count=%d\n", Me, fd, count);
    return count;
}

extern "C" int
sysIsValidFD(int fd)
{
    int rc;
    struct stat sb;

    rc = fstat(fd, &sb);
    if (rc == -1)
        return -1;
    else
        return 0;
}

extern "C" int
sysLength(int fd)
{
    int rc;
    struct stat sb;

    rc = fstat(fd, &sb);
    if (rc == -1)
        return -1;
    else
        return sb.st_size;
}

extern "C" int
sysSetLength(int fd, int len)
{
    int rc = ftruncate(fd, len);
    if (rc == -1)
        return -1;
    else
        return 0;
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

// Change i/o position on file.
// Taken:    file descriptor
//           number of bytes by which to adjust position
//           how to interpret adjustment (see VM_FileSystem.SEEK_XXX)
// Returned: new i/o position, as byte offset from start of file (-1: error)
//
extern "C" int
sysSeek(int fd, int offset, int whence)
{
    // fprintf(SysTraceFile, "%s: seek %d %d %d\n", Me, fd, offset, whence);
    switch (whence) {
    case VM_FileSystem_SEEK_SET: 
        return lseek(fd, offset, SEEK_SET);
    case VM_FileSystem_SEEK_CUR: 
        return lseek(fd, offset, SEEK_CUR);
    case VM_FileSystem_SEEK_END: 
        return lseek(fd, offset, SEEK_END);
    default:                     
        return -1;
    }
}

// Close file or socket.
// Taken:    file/socket descriptor
// Returned:  0: success
//           -1: file/socket not currently open
//           -2: i/o error
//
extern "C" int
sysClose(int fd)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: close %d\n", Me, fd);
#endif

    if ( -1 == fd ) return -1;

    int rc = close(fd);

    if (rc == 0)
        return 0; // success

    if (errno == EBADF)
        return -1; // not currently open

    fprintf(SysErrorFile, "%s: close on %d failed: %s (errno=%d)\n", Me,
            fd, strerror(errno), errno);
    return -2; // some other error
}


// Determine whether or not given file descriptor is
// connected to a TTY.
//
// Taken: the file descriptor to query
// Returned: 1 if it's connected to a TTY, 0 if not
//
extern "C" int
sysIsTTY(int fd)
{
    return isatty(fd);
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

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR

static void *timeSlicerThreadMain(void *) __attribute__((noreturn));

static void *
timeSlicerThreadMain(void *arg)
{
    int ns = (int)arg;
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
#endif

/*
 * Actions to take on a timer tick
 */
extern "C" void processTimerTick(void) {
    /* 
     * Check to see if a gc is in progress.
     * If it is then simply return (ignore timer tick).
     */
    VM_Address VmToc = (VM_Address) getJTOC();
    int gcStatus = *(int *) ((char *) VmToc + com_ibm_JikesRVM_memoryManagers_JMTk_BasePlan_gcStatusOffset);
    if (gcStatus != 0) return;

    /*
     * Increment VM_Processor.epoch
     */
    int epoch = *(int *) ((char *) VmToc + VM_Processor_epoch_offset);
    *(int *) ((char *) VmToc + VM_Processor_epoch_offset) = epoch + 1;

    /*
     * Turn on thread-switch flag in each virtual processor.
     * Note that "jtoc" is not necessarily valid, because we might have
     * interrupted C-library code, so we use boot image 
     * jtoc address (== VmToc) instead. 
     */
    VM_Address *processors 
        = *(VM_Address **) ((char *) VmToc + getProcessorsOffset());
    unsigned cnt = getArrayLength(processors);
    unsigned longest_stuck_ticks = 0;
    for (unsigned i = VM_Scheduler_PRIMORDIAL_PROCESSOR_ID; i < cnt ; i++) {
        // During how many ticks has this VM_Processor ignored 
        // a thread switch request? 
        int val = (*(int *)((char *)processors[i] + 
                            VM_Processor_threadSwitchRequested_offset))--;
        if (longest_stuck_ticks < (unsigned) -val)
            longest_stuck_ticks = -val;
    }
    
#if !defined(RVM_WITH_GCSPY)
    /* 
     * After 500 timer intervals (often == 5 seconds), print a message
     * every 50 timer intervals (often == 1/2 second), so we don't
     * just appear to be hung. 
     */
    if (longest_stuck_ticks >= 500 && (longest_stuck_ticks % 50) == 0) {
      /* When performing tracing, delays will often last more than 5
       * seconds and can take much, much longer (on a fairly fast
       * machine I've seen delays above 1 minute).  This is due to a
       * GC possibly needing to include additional processing and
       * outputting a large amount of information which will
       * presumably be saved.  Unfortunately, these warnings will appear
       * in the midst of the trace and cause them to be very difficult to
       * parse.  As this is a normal condition during tracing, and causes
       * a lot of problems to tracing, we elide the warning.
       */
#if (!defined RVM_FOR_GCTRACE)
        fprintf(stderr, "%s: WARNING: Virtual processor has ignored"
                " timer interrupt for %d ms.\n", 
                Me, getTimeSlice_msec() * longest_stuck_ticks);
        fprintf(stderr, "This may indicate that a blocking system call"
                " has occured and the JVM is deadlocked\n");
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
#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    pthread_t timeSlicerThread; // timeSlicerThread is a write-only dummy
                                // variable.
    int nsTimerDelay = msTimerDelay * 1000 * 1000;
    int errorCode = pthread_create(&timeSlicerThread, NULL,
                                   timeSlicerThreadMain, (void*)nsTimerDelay);
    if (errorCode) {
        fprintf(SysErrorFile,
                "%s: Unable to create the Time Slicer thread: %s\n",
                Me, strerror(errorCode));
        sysExit(EXIT_STATUS_TIMER_TROUBLE);
    }
#elif (defined RVM_FOR_LINUX)  || (defined __MACH__) 
    /* && RVM_FOR_SINGLE_VIRTUAL_PROCESSOR */

    /* NOTE: This code is ONLY called if we have defined
     * RVM_FOR_SINGLE_VIRTUAL_PROCESSOR.  */

    // set it to issue a periodic SIGALRM (or 0 to disable timer)
    //

    struct itimerval timerInfo, oldtimer;

    timerInfo.it_value.tv_sec     = 0;
    timerInfo.it_value.tv_usec    = msTimerDelay * 1000;
    timerInfo.it_interval.tv_sec  = timerInfo.it_value.tv_sec;
    timerInfo.it_interval.tv_usec = timerInfo.it_value.tv_usec;

    if (setitimer(ITIMER_REAL, &timerInfo, &oldtimer))
    {
        fprintf(SysErrorFile, "%s: setitimer failed (errno=%d, %s): ", 
                Me, errno, strerror(errno));
        perror(NULL);
        sysExit(EXIT_STATUS_TIMER_TROUBLE);
    }
#else  /* RVM_FOR_SINGLE_VIRTUAL_PROCESSOR &&  ! defined RVM_FOR_LINUX && !
          defined __MACH__ */
    // fetch system timer
    //
    timer_t timerId = gettimerid(TIMERID_REAL, DELIVERY_SIGNALS);
    if (timerId == -1)
    {
        fprintf(SysErrorFile, "%s: gettimerid failed (errno=%d, %s): ", 
                Me, errno, strerror(errno));
        perror(NULL);
        sysExit(EXIT_STATUS_TIMER_TROUBLE);
    }

    // set it to issue a periodic SIGALRM (or 0 to disable timer)
    //
    struct itimerstruc_t timerInfo, oldtimer;
    timerInfo.it_value.tv_sec     = 0;
    timerInfo.it_value.tv_nsec    = msTimerDelay * 1000 * 1000;
    timerInfo.it_interval.tv_sec  = timerInfo.it_value.tv_sec;
    timerInfo.it_interval.tv_nsec = timerInfo.it_value.tv_nsec;
    if (incinterval(timerId, &timerInfo, &oldtimer))
    {
        fprintf(SysErrorFile, "%s: incinterval failed (errno=%d, %s): ", 
                Me, errno, strerror(errno));
        perror(NULL);
        sysExit(EXIT_STATUS_TIMER_TROUBLE);
    }
#endif
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


//
// returns the time of day in the buffer provided
//
/*
  extern "C" int
  sysGetTimeOfDay(char * buffer) {
  int rc;
  struct timeval tv;
  struct timezone tz;

  rc = gettimeofday(&tv, &tz);
  if (rc != 0) return rc;

  buffer[0] = (tv.tv_sec >> 24) & 0x000000ff;
  buffer[1] = (tv.tv_sec >> 16) & 0x000000ff;
  buffer[2] = (tv.tv_sec >> 8) & 0x000000ff;
  buffer[3] = tv.tv_sec & 0x000000ff;

  buffer[4] = (tv.tv_usec >> 24) & 0x000000ff;
  buffer[5] = (tv.tv_usec >> 16) & 0x000000ff;
  buffer[6] = (tv.tv_usec >> 8) & 0x000000ff;
  buffer[7] = tv.tv_usec & 0x000000ff;

  return rc;
  }
*/


extern "C" long long
sysGetTimeOfDay()
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
        returnValue = (long long) tv.tv_sec * 1000000;
        returnValue += tv.tv_usec;
    }

    return returnValue;
}


//-----------------------//
// Processor operations. //
//-----------------------//

#ifdef _AIX
#include <sys/systemcfg.h>
#endif

// How many physical cpu's are present?
// Taken:    nothing
// Returned: number of cpu's
//
extern "C" int
sysNumProcessors()
{
    int numpc = 1;  /* default */

#ifdef RVM_FOR_LINUX
    numpc = get_nprocs_conf();
#elif RVM_FOR_OSX
    int mib[2];
    size_t len;
    mib[0] = CTL_HW;
    mib[1] = HW_NCPU;
    len = sizeof(numpc);
    sysctl(mib, 2, &numpc, &len, NULL, 0);
#else
    numpc = _system_configuration.ncpus;
#endif

#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: sysNumProcessors: returning %d\n", Me, numpc );
#endif
    return numpc;
}



//-----------------------------------------------------------
//  Minimal subset of hardware performance monitor operations
//  for PowerPC that are needed for boot strapping Jikes RVM
//  because JNI is not yet enabled.
//
//  Called from VM_HardwarePerformanceMonitors.boot().
//-----------------------------------------------------------
#ifdef RVM_WITH_HPM
# ifdef RVM_FOR_LINUX
# include "papi.h"
# include "papiStdEventDefs.h"
# define PM_CAVEAT     0
# define PM_UNVERIFIED 0
# define PM_VERIFIED   0
#else
# include "pmapi.h"
# endif
#endif

#ifdef RVM_WITH_HPM
extern "C" int hpm_init(int);
extern "C" int hpm_set_event  (int, int, int, int);
extern "C" int hpm_set_event_X(int, int, int, int);
extern "C" int hpm_set_mode(int);

extern "C" int hpm_set_program_mythread();
extern "C" int hpm_start_mythread();
extern "C" int hpm_stop_mythread();
extern "C" int hpm_reset_mythread();
extern "C" long long hpm_get_counter_mythread(int);
extern "C" int hpm_get_number_of_counters();
extern "C" int hpm_get_number_of_events();
extern "C" int hpm_get_processor_name();
extern "C" int hpm_is_big_endian();
extern "C" int hpm_test();

extern "C" int hpm_set_program_mygroup();
extern "C" int hpm_start_mygroup();
extern "C" int hpm_stop_mygroup();
extern "C" int hpm_reset_mygroup();
extern "C" int hpm_print_mygroup();
extern "C" long long hpm_get_counter_mygroup(int);
#endif


/*
 * Initialize HPM services.
 * Must be called before any other HPM requests.
 * Only returns if successful.
 */
extern "C" int
sysHPMinit(void)
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMinit() called:\n", Me);
# endif
  int filter = PM_UNVERIFIED|PM_VERIFIED|PM_CAVEAT;
  rc = hpm_init(filter);
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMinit() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
  /* NOTREACHED */
#endif
}


/*
 * Set, in HPM World, events to be monitored.
 * Only returns if valid parameters.
 * PowerPC 604e only has 4 counters.
 */
extern "C" int
sysHPMsetEvent(int e1, int e2, int e3, int e4)
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMsetEvent(%d,%d,%d,%d) called\n",
          Me, e1,e2,e3,e4);
# endif
  rc = hpm_set_event(e1, e2, e3, e4);
  return rc;
#else
  fprintf(SysErrorFile, 
          "%s: sysHPMsetEvent(%d,%d,%d,%d) called: not compiled for HPM\n",
          Me, e1,e2,e3,e4);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Set events to be monitored.
 * Only returns if valid parameters.
 * The PowerPC 630 has 8 counters, this is how we access the additional 4.
 */
extern "C" int
sysHPMsetEventX(int e5, int e6, int e7, int e8)
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMsetEventX(%d,%d,%d,%d) called\n", Me,
          e5,e6,e7,e8);
# endif
  rc = hpm_set_event_X(e5, e6, e7, e8);
  return rc;
#else
  fprintf(SysErrorFile,
          "%s: sysHPMsetEventX(%d,%d,%d,%d) called: not compiled for HPM\n",
          Me, e5,e6,e7,e8);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Set mode(s) to be monitored.
 * Only returns if valid parameters.
 * Possible modes are:
 *   #define PM_USER            4       // turns user mode counting on
 *   #define PM_KERNEL          8       // turns kernel mode counting on
 */
extern "C" int
sysHPMsetMode(int mode)
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMsetMode(%d) called\n", Me,mode);
# endif
  rc = hpm_set_mode(mode);
  return rc;
#else
  fprintf(SysErrorFile, 
          "%s: sysHPMsetMode(%d) called: not compiled for HPM\n",
          Me, mode);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Set, in HPM world, what is to be monitored.
 * Constraint: sysHPMsetEvent must have been called.
 *   sysHPMsetEventX and sysHPMsetMode must have been called to take affect.
 * Only returns if valid parameters.
 * Must be called after sysHPMinit is called.
 * May be called multiple times only if sysHPMdeleteSettings is called in between.
 */
extern "C" int
sysHPMsetProgramMyThread()
{
#ifdef RVM_WITH_HPM
  int rc;
# if defined DEBUG_SYS && ! defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  fprintf(SysTraceFile, "%s: sysHPMsetProgramMyThread() called from pthread id %d\n", Me,pthread_self());
# endif
  rc = hpm_set_program_mythread();
  return rc;
#else
  fprintf(SysTraceFile, 
          "%s: sysHPMsetProgramMyThread() called: not compiled for HPM\n", 
          Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Set, in HPM world, what is to be monitored for groups.
 * Only returns if valid parameters.
 * Must be called after sysHPMinit is called.
 * May becalled multiple times only if sysHPMdeleteGroupSettings is called in between.
 */
extern "C" int
sysHPMsetProgramMyGroup()
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMsetProgramMyGroup() called\n", Me);
# endif
  rc = hpm_set_program_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMsetProgramMyGroup() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Start HPM counting.
 * Constraint: sysHPMstartProgramMythread must have been called.
 * Only returns if valid parameters.
 */
extern "C" int
sysHPMstartMyThread()
{
#ifdef RVM_WITH_HPM
# if defined DEBUG_SYS && ! defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  fprintf(SysTraceFile, "%s: sysHPMstartMyThread() called from pthread id %d\n", Me,pthread_self());
# endif
  int rc = hpm_start_mythread();
  return rc;
#else
  fprintf(SysTraceFile, 
          "%s: sysHPMstartMyThread() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Start group monitoring.
 * May be called only after sysHPMsetGroupSettings is called.
 * Only returns if successful.
 */
extern "C" int
sysHPMstartMyGroup()
{
#ifdef RVM_WITH_HPM
  int rc;
# if defined DEBUG_SYS && ! defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  fprintf(SysTraceFile, "%s: sysHPMstartMyGroup() called from pthread id %d\n", Me,pthread_self());
# endif
  rc = hpm_start_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMstartMyGroup() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Stop monitoring.
 * Should be called only after sysHPMstartMythread is called.
 * Only returns if successful.
 */
extern "C" int
sysHPMstopMyThread()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_stop_mythread();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMstopMyThread() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Stop group monitoring.
 * Should be called only after sysHPMstartGroupCounting is called.
 * Only returns if successful.
 */
extern "C" int
sysHPMstopMyGroup()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_stop_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMstopMyGroup() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Reset counters to zero.
 * Should be called only after sysHPMstop has been called.
 * Only returns if successful.
 */
extern "C" int
sysHPMresetMyThread()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_reset_mythread();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMresetMyThread() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Reset counters to zero.
 * Should be called only after sysHPMstop has been called.
 * Only returns if successful.
 */
extern "C" int
sysHPMresetMyGroup()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_reset_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMresetMyGroup() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Get value from a counter.
 * Only returns if successful.
 * May be called only after sysHPMstart is called.
 * parameters:
 *   counter input: specifies which counter to read.
 *   return value   output: is the value of the counter that is read.
 */
extern "C" long long
sysHPMgetCounterMyThread(int counter)
{
#ifdef RVM_WITH_HPM
  return hpm_get_counter_mythread(counter);
#else
  fprintf(SysErrorFile, "%s: sysHPMgetCounterMyThread(%d) called: not compiled for HPM\n", Me,counter);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Get value from a counter.
 * Only returns if successful.
 * May be called only after sysHPMstart is called.
 * parameters:
 *   counter input: specifies which counter to read.
 *   return value   output: is the value of the counter that is read.
 */
extern "C" long long
sysHPMgetCounterMyGroup(int counter)
{
#ifdef RVM_WITH_HPM
  return hpm_get_counter_mygroup(counter);
#else
  fprintf(SysErrorFile, "%s: sysHPMgetCounterMyGroup(%d) called: not compiled for HPM\n", Me,counter);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Get number of counters available.
 */
extern "C" int
sysHPMgetNumberOfCounters()
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMgetNumberOfCounters() called\n", Me);
# endif
  rc = hpm_get_number_of_counters();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMgetNumberOfCounters() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Get number of counters available.
 */
extern "C" int
sysHPMgetNumberOfEvents()
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMgetNumberOfEvents() called\n", Me);
# endif
  rc = hpm_get_number_of_events();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMgetNumberOfEvents() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}

/*
 * Get number of counters available.
 */
extern "C" int
sysHPMisBigEndian()
{
#ifdef RVM_WITH_HPM
  int rc;
# ifdef DEBUG_SYS
  fprintf(SysErrorFile, "%s: sysHPMisBigEndian() called\n", Me);
# endif
  rc = hpm_is_big_endian();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMisBigEndian() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Testing.
 * Only returns if successful.
 */
extern "C" int
sysHPMtest()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_test();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMtest() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}


/*
 * Print
 */
extern "C" int
sysHPMprintMyGroup()
{
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_print_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "%s: sysHPMprintMyGroup() called: not compiled for HPM\n", Me);
  exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}

// Create a virtual processor (aka "unix kernel thread", "pthread").
// Taken:    register values to use for pthread startup
// Returned: virtual processor's o/s handle
//
extern "C" VM_Address
sysVirtualProcessorCreate(VM_Address UNUSED_SVP jtoc, VM_Address UNUSED_SVP pr, VM_Address UNUSED_SVP ip, VM_Address UNUSED_SVP fp)
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysVirtualProcessorCreate: Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    VM_Address    *sysVirtualProcessorArguments;
    pthread_attr_t sysVirtualProcessorAttributes;
    pthread_t      sysVirtualProcessorHandle;
    int            rc;

    // create arguments
    //
    sysVirtualProcessorArguments = new VM_Address[4];
    sysVirtualProcessorArguments[0] = jtoc;
    sysVirtualProcessorArguments[1] = pr;
    sysVirtualProcessorArguments[2] = ip;
    sysVirtualProcessorArguments[3] = fp;

    // create attributes
    //
    if ((rc = pthread_attr_init(&sysVirtualProcessorAttributes)))
    {
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
        fprintf(SysTraceFile, "%s: pthread_create 0x%08x\n", Me, (VM_Address) sysVirtualProcessorHandle);

    return (VM_Address)sysVirtualProcessorHandle;
#endif
}

#ifndef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
static void *
sysVirtualProcessorStartup(void *args)
{
    VM_Address jtoc     = ((VM_Address *)args)[0];
    VM_Address pr       = ((VM_Address *)args)[1];
    VM_Address ip       = ((VM_Address *)args)[2];
    VM_Address fp       = ((VM_Address *)args)[3];

    if (VERBOSE_PTHREAD)
#ifdef RVM_FOR_64_ADDR
        fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: jtoc=0x%016llx pr=0x%016llx ip=0x%016llx fp=0x%016llx\n", Me, jtoc, pr, ip, fp);
#else
        fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: jtoc=0x%08x pr=0x%08x ip=0x%08x fp=0x%08x\n", Me, jtoc, pr, ip, fp);
#endif
    // branch to vm code
    //
#ifdef RVM_FOR_IA32
    {
        *(VM_Address *) (pr + VM_Processor_framePointer_offset) = fp;
        VM_Address sp = fp + VM_Constants_STACKFRAME_BODY_OFFSET;
        bootThread(ip, jtoc, pr, sp);
    }
#else
    bootThread(jtoc, pr, ip, fp);
#endif

    // not reached
    //
    fprintf(SysTraceFile, "%s: sysVirtualProcessorStartup: failed\n", Me);
    return 0;
}
#endif

// Bind execution of current virtual processor to specified physical cpu.
// Taken:    physical cpu id (0, 1, 2, ...)
// Returned: nothing
//
extern "C" void
sysVirtualProcessorBind(int POSSIBLY_UNUSED cpuId)
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysVirtualProcessorBind: Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    int numCpus = sysconf(_SC_NPROCESSORS_ONLN);
    if (VERBOSE_PTHREAD)
      fprintf(SysTraceFile, "%s: %d cpu's\n", Me, numCpus);

    // bindprocessor() seems to only be on AIX
#if defined RVM_FOR_AIX
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
#endif // ! defined RVM_FOR_LINUX or defined RVM_FOR_OSX
#endif // !defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
}

#if !defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
pthread_cond_t VirtualProcessorStartup = PTHREAD_COND_INITIALIZER;
pthread_cond_t MultithreadingStartup = PTHREAD_COND_INITIALIZER;

pthread_mutex_t VirtualProcessorStartupLock = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t MultithreadingStartupLock = PTHREAD_MUTEX_INITIALIZER;

int VirtualProcessorsLeftToStart;
int VirtualProcessorsLeftToWait;

#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
// Thread-specific data key in which to stash the id of
// the pthread's VM_Processor.  This allows the system call library
// to find the VM_Processor object at runtime.
pthread_key_t VmProcessorKey;
pthread_key_t IsVmProcessorKey;

// Create keys for thread-specific data.
extern "C" void
sysCreateThreadSpecificDataKeys(void)
{
    int rc1, rc2;

    // Create a key for thread-specific data so we can associate
    // the id of the VM_Processor object with the pthread it
    // is running on.
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

    // Let the syscall wrapper library know what the key is,
    // along with the JTOC address and offset of VM_Scheduler.processors.
    // This will enable it to find the VM_Processor object later on.
#ifdef DEBUG_SYS
    fprintf(stderr, "%s: vm processor key=%u\n", Me, VmProcessorKey);
#endif

    // creation of other keys can go here...
}
#endif // defined(RVM_WITH_INTERCEPT_BLOCKING_SYSTEM_CALLS)

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
#endif

// Routines to support sleep/wakeup of idle threads:
// CRA, Maria
// 09/14/00
//
/*
  I have filed defect report # 3925 about this function, with the following
  description of the defect: --Steve Augart:

  sysPthreadSelf(), in sys.C, logically, should just return the thread ID of
  the current thread. It does not. It also does some initialization related to
  per-thread signal handling for that thread. (Block SIGCONT, set up a special
  signal handling stack for the thread.)

  We have been getting away with this because we in fact only call
  sysPthreadSelf() once, at thread startup time. However, we probably should
  either break out the initialization code separately or rename sysPthreadSelf
  to something more accurate, like
  sysPthreadSetupSignalHandlingAndReturnPthreadSelf(). (I like the idea of
  breaking out the logically-unrelated initialization code.)

*/
extern "C" int
sysPthreadSelf()
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysPthreadSelf: FATAL Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    int thread;
    int rc;

    thread = (int)pthread_self();

    if (VERBOSE_PTHREAD)
        fprintf(SysTraceFile, "%s: sysPthreadSelf: thread %d\n", Me, thread);


#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_OSX)
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
        fprintf (SysErrorFile, "sigaltstack failed (errno=%d): ", errno);
        perror(NULL);
        return 1;
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

#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_OSX)
    rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
#else
    rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
#endif

    return thread;
#endif
}

//
extern "C" int
sysPthreadSignal(int UNUSED_SVP pthread)
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysPthreadSignal: FATAL Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    pthread_t thread;
    thread = (pthread_t)pthread;

    pthread_kill(thread, SIGCONT);
#endif
    return 0;
}

//
extern "C" int
sysPthreadJoin(int UNUSED_SVP pthread)
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysPthreadJoin: FATAL Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    pthread_t thread;
    thread = (pthread_t)pthread;
    // fprintf(SysTraceFile, "%s: pthread %d joins %d\n", Me, pthread_self(), thread);
    pthread_join(thread, NULL);
#endif
    return 0;
}

//
extern "C" void
sysPthreadExit()
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysPthreadExit: Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    // fprintf(SysTraceFile, "%s: pthread %d exits\n", Me, pthread_self());
    pthread_exit(NULL);
#endif
}

//
// Yield execution of current virtual processor back to o/s.
// Taken:    nothing
// Returned: nothing
//
extern "C" void
sysVirtualProcessorYield()
{
#if (!defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    sched_yield();
#else
    fprintf(stderr, "%s: sysVirtualProcessorYield: Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#endif
}

//
// Taken -- address of an integer lockword
//       -- value to store in the lockword to 'release' the lock
// release the lockout word by storing the in it
// and wait for a signal.
extern "C" int
sysPthreadSigWait( int UNUSED_SVP * lockwordAddress, 
                   int UNUSED_SVP  lockReleaseValue )
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "%s: sysPthreadSigWait: Unsupported operation with single virtual processor\n", Me);
    sysExit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
#else
    sigset_t input_set, output_set;
    int      sig;

    *lockwordAddress = lockReleaseValue;

    sigemptyset(&input_set);
    sigaddset(&input_set, SIGCONT);
#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_OSX)
    pthread_sigmask(SIG_BLOCK, NULL, &output_set);
#else
    sigthreadmask(SIG_BLOCK, NULL, &output_set);
#endif
    sigwait(&input_set, &sig);

    // if status has been changed to BLOCKED_IN_SIGWAIT (because of GC)
    // sysYield until unblocked
    //
    while ( *lockwordAddress == 5 /*VM_Processor.BLOCKED_IN_SIGWAIT*/ )
        sysVirtualProcessorYield();

    return 0;
#endif
}

#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
// Stash address of the VM_Processor object in the thread-specific
// data for the current pthread.  This allows us to get a handle
// on the VM_Processor (and its associated state) from arbitrary
// native code.
//
extern "C" int
sysStashVmProcessorInPthread(VM_Address vmProcessor)
{
#if defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    // We have only a single VM_Processor, so just pass its id
    // directly to the system call wrapper library, along with the
    // JTOC address and the offset of VM_Scheduler.processors.
    // fprintf(SysErrorFile, "%s: stashing vm_processor id = %d\n", Me, vmProcessor);
    //  initSyscallWrapperLibrary(getJTOC(), getProcessorsOffset(), vmProcessor);
#else
    //fprintf(SysErrorFile, "stashing vm processor = %d, self=%u\n", vmProcessor, pthread_self());
    int rc = pthread_setspecific(VmProcessorKey, (void*) vmProcessor);
    int rc2 = pthread_setspecific(IsVmProcessorKey, (void*) 1);
    if (rc != 0 || rc2 != 0) {
        fprintf(SysErrorFile, "%s: pthread_setspecific() failed (err=%d,%d)\n", Me, rc, rc2);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif // defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    return 0;
}
#endif // !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)

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

#ifdef RVM_FOR_POWERPC
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
#endif

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
    char *end;			// This prototype is kinda broken.  It really
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

//-------------------//
// Memory operations //
//-------------------//

// Memory to memory copy.
//
extern "C" void
sysCopy(void *dst, const void *src, int cnt)
{
    memcpy(dst, src, cnt);
}

// Memory fill.
//
extern "C" void
sysFill(void *dst, int pattern, int cnt)
{
    memset(dst, pattern, cnt);
}

// Allocate memory.
//
extern "C" void *
sysMalloc(int length)
{
    return malloc(length);
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
sysZero(void *dst, int cnt)
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
sysSyncCache(void POSSIBLY_UNUSED *address, size_t POSSIBLY_UNUSED  size)
{
#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: sync 0x%08x %d\n", Me, (unsigned)address, size);
#endif

#ifdef RVM_FOR_AIX
    _sync_cache_range((caddr_t) address, size);
#elif (defined RVM_FOR_LINUX || defined RVM_FOR_OSX) && defined RVM_FOR_POWERPC
    {
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
    }
#else  // only needed on PowerPC platforms; skip here
    ///fprintf(SysTraceFile, "\nskipping: sysSyncCache(void *address, size_t size)\n");
#endif
}

//-----------------//
// SHM* operations //
//-----------------//
extern "C" int
sysShmget(int key, int size, int flags)
{
    return shmget(key, size,flags);
}

extern "C" void *
sysShmat(int shmid, char * addr, int flags)
{
    return shmat(shmid, addr, flags);
}

extern "C" int
sysShmdt(char * addr)
{
    if (shmdt(addr) == 1)
        return errno;
    return 0;
}

extern "C" int
sysShmctl(int shmid, int command)
{
    return shmctl(shmid, command, NULL);
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
        int fd , long long offset)
{
   return mmap(start, (size_t)(length), protection, flags, fd, (off_t)(offset));
}

// Same as mmap, but with more debugging support.
// Returned: address of region if successful; errno (1 to 127) otherwise

extern "C" void *
sysMMapErrno(char *start , size_t length ,
	     int protection , int flags ,
	     int fd , long long offset)
{
  void* res = mmap(start, (size_t)(length), protection, flags, fd, (off_t)(offset));
  if (res == (void *) -1){
#if RVM_FOR_32_ADDR
    fprintf(stderr, "mmap (%x, %u, %d, %d, -1, 0) failed with %d: ",
	    (VM_Address) start, (unsigned) length, protection, flags, errno);
#else
    fprintf(stderr, "mmap (%llx, %u, %d, %d, -1, 0) failed with %d: ",
	    (VM_Address) start, (unsigned) length, protection, flags, errno);
#endif          
    return (void *) errno;
  }else{
#ifdef DEBUG_SYS
    printf("mmap worked - region = [0x%x ... 0x%x]    size = %d\n", res, ((int)res) + length, length);
#endif
    return res;
  }
}

// munmap
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMUnmap(char *start, size_t length)
{
    return munmap(start, length);
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

// msync
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        flags (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMSync(char *start, size_t length, int flags)
{
    return msync(start, length, flags);
}

// madvise
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        advice (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMAdvise(char POSSIBLY_UNUSED      *start,
           size_t POSSIBLY_UNUSED   length,
           int POSSIBLY_UNUSED      advice)
{
  return madvise(start, length, advice);
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
        printf("0x%x: ", (VM_Address) start);
#else   
        printf("0x%llx: ", (VM_Address) start);
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
#if (defined RVM_FOR_OSX) && (!defined HAS_DLCOMPAT)
   fprintf(SysTraceFile, "sys: dlopen not implemented yet\n");
   return 0;
#else
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
#endif
}

// Look up symbol in dynamic library.
// Taken:
// Returned:
//
extern "C" void*
sysDlsym(VM_Address libHandler, char *symbolName)
{
#if (defined RVM_FOR_OSX) && (!defined HAS_DLCOMPAT)
   fprintf(SysTraceFile, "sys: dlsym not implemented yet\n");
   return 0;
#else
    return dlsym((void *) libHandler, symbolName);
#endif
}

// Unload dynamic library.
// Taken:
// Returned:
//
extern "C" void
sysDlclose()
{
    fprintf(SysTraceFile, "%s: dlclose not implemented yet\n", Me);
}

// Tell OS to remove shared library.
// Taken:
// Returned:
//
extern "C" void
sysSlibclean()
{
    fprintf(SysTraceFile, "%s: slibclean not implemented yet\n", Me);
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

// Get network name of machine we're running on.
// Taken:    buffer in which to place results
//           buffer size
// Returned: number of bytes written to buffer
//           -1: name didn't fit, buffer too small
//
extern "C" int
sysNetLocalHostName(char *buf, int limit)
{
    int rc;

    rc = gethostname(buf, limit);
    if (rc != 0)
    {
        fprintf(SysErrorFile, "%s: gethostname failed (rc=%d)\n", Me, rc);
        return 0;
    }

    for (int i = 0; i < limit; ++i)
        if (buf[i] == 0) {
#ifdef DEBUG_NET
            fprintf(SysErrorFile, "got hostname %s\n", buf);
#endif
            return i;
        }

    return -1;
}

// Get network name of machine at specified internet address.
// Taken:    internet address
//           buffer in which to place results
//           buffer size
// Returned: number of bytes written to buffer
//           -1: name didn't fit, buffer too small
//           0: system call returned an error.
//
extern "C" int
sysNetRemoteHostName(int internetAddress, char *buf, int limit)
{
#if (defined RVM_FOR_LINUX || defined RVM_FOR_OSX)
    hostent * resultAddress;

    fprintf(SysTraceFile, "untested system call sysNetRemoteHostName()\n");
    resultAddress = gethostbyaddr((char *)&internetAddress,
                                  sizeof internetAddress,
                                  AF_INET);

    if ( ! resultAddress )
        return 0;

    char *name = resultAddress->h_name;
    for (int i = 0; i < limit; ++i) {
        if (name[i] == 0)
            return i;
        buf[i] = name[i];
    }
    return -1;
#else
    hostent      results; memset(&results, 0, sizeof results);
    hostent_data data;    memset(&data, 0, sizeof data);

    int rc = gethostbyaddr_r((char *)&internetAddress,
                             sizeof internetAddress, AF_INET,
                             &results, &data);
    if (rc != 0) {
        // fprintf(SysErrorFile, "%s: gethostbyaddr_r failed (errno=%d)\n", Me, h_errno);
        return 0;
    }

    char *name = results.h_name;
    for (int i = 0; i < limit; ++i) {
        if (name[i] == 0)
            return i;
        buf[i] = name[i];
    }
    return -1;
#endif
}

// Get list of internet addresses at which specified host can be contacted.
// Taken:    hostname
//           buffer in which to place results (assumption: 4-byte-addresses)
//           buffer size
// Returned: number of addresses written to buffer
//           -1: addresses didn't fit, buffer too small
//           -2: network error
//
#ifdef _AIX
extern "C" int
sysNetHostAddresses(char *hostname, uint32_t **buf, int limit)
{
    int i;
    hostent      results; memset(&results, 0, sizeof results);
    hostent_data data;    memset(&data, 0, sizeof data);

    int rc = gethostbyname_r(hostname, &results, &data);
    if (rc != 0) {
#ifdef __GLIBC__
        fprintf(SysErrorFile, "%s: gethostbyname_r failed: %s (h_errno=%d)\n",
                Me, hstrerror(h_errno), h_errno);
#else
        fprintf(SysErrorFile, "%s: gethostbyname_r failed (h_errno=%d)\n",
                Me, h_errno);
#endif
        return -2;
    }

    // verify 4-byte-address assumption
    //
    if (results.h_addrtype != AF_INET || results.h_length != 4 
        || sizeof (in_addr_t) != 4) {
        fprintf(SysErrorFile, "%s: gethostbyname_r failed (unexpected address type or length)\n", Me);
        return -2;
    }

    in_addr **addresses = (in_addr **)results.h_addr_list;
    for (i = 0; addresses[i] != 0; ++i) {
        if (i == limit)
            return -1;

        printf("host address %x\n", addresses[i]->s_addr);

        *buf[i] = addresses[i]->s_addr;
    }
    return i;
}
#endif

#if (defined RVM_FOR_LINUX || defined RVM_FOR_OSX)
extern "C" int
sysNetHostAddresses(char *hostname, uint32_t **buf, int limit)
{
    int i;

    hostent * result = gethostbyname(hostname);

    if ( !result || result->h_addrtype != AF_INET || result->h_length != 4 )
        return -2;

    uint32_t **address = (uint32_t ** )result->h_addr_list;
    for (i=0; address[i]; i++ ) {
        if (i == limit)
            return -1;
        *buf[i] = *(address[i]);
    }
    return i;
}
#endif

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
#if (defined RVM_FOR_AIX || defined RVM_FOR_OSX)
    int len;
#endif
#ifdef RVM_FOR_LINUX
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
#if defined RVM_FOR_OSX
    int len;
#endif
#if defined RVM_FOR_AIX || defined RVM_FOR_LINUX
    socklen_t len;
#endif

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
#if (defined RVM_FOR_AIX || defined RVM_FOR_OSX)
    int len;
#endif
#ifdef RVM_FOR_LINUX
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
#if (defined RVM_FOR_AIX || defined RVM_FOR_OSX)
    int len;
#endif
#ifdef RVM_FOR_LINUX
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
#if (defined RVM_FOR_AIX || defined RVM_FOR_OSX)
    int len;
#endif
#ifdef RVM_FOR_LINUX
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
    int  *familyField   =  (int   *)((char *)addressObject    + java_net_InetAddress_family_offset);
    int  *addressField  =  (int   *)((char *)addressObject    + java_net_InetAddress_address_offset);
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
        int fd = fdArray[i] & VM_ThreadIOConstants_FD_MASK;
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
        int fd = fdArray[i] & VM_ThreadIOConstants_FD_MASK;
        if (FD_ISSET(fd, ready))
            fdArray[i] = VM_ThreadIOConstants_FD_READY;
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
        int fd = fdArray[i] & VM_ThreadIOConstants_FD_MASK;
        if (FD_ISSET(fd, exceptFdSet)) {
            //fprintf(SysErrorFile, "%s: fd %d in sysNetSelect() is invalid\n", Me, fd);
            fdArray[i] = VM_ThreadIOConstants_FD_INVALID;
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
// Side effect: readFds[i] is set to "VM_ThreadIOConstants.FD_READY" iff i/o can proceed on that socket
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

        if (!addFileDescriptors(&readReady, allFds + VM_ThreadIOQueue_READ_OFFSET, rc, &maxfd, &exceptReady)
            || !addFileDescriptors(&writeReady, allFds + VM_ThreadIOQueue_WRITE_OFFSET, wc, &maxfd, &exceptReady)
            || !addFileDescriptors(&exceptReady, allFds + VM_ThreadIOQueue_EXCEPT_OFFSET, ec, &maxfd))
            return -1;

        // Ensure that select() call below
        // calls the real C library version, not our hijacked version
#if defined(RVM_WITH_INTERCEPT_BLOCKING_SYSTEM_CALLS)
        SelectFunc_t realSelect = getLibcSelect();
        if (realSelect == 0) {
            fprintf(SysErrorFile, "%s: could not get pointer to real select()\n", Me);
            sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
        }
#else
# define realSelect(n, read, write, except, timeout) \
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
            updateStatus(allFds + VM_ThreadIOQueue_READ_OFFSET, rc, &readReady);
            updateStatus(allFds + VM_ThreadIOQueue_WRITE_OFFSET, wc, &writeReady);
            updateStatus(allFds + VM_ThreadIOQueue_EXCEPT_OFFSET, ec, &exceptReady);

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
            numInvalid += checkInvalid(allFds + VM_ThreadIOQueue_READ_OFFSET, rc, &exceptReady);
            numInvalid += checkInvalid(allFds + VM_ThreadIOQueue_WRITE_OFFSET, wc, &exceptReady);
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
            pidArray[i] = VM_ThreadProcessWaitQueue_PROCESS_FINISHED;
            exitStatusArray[i] = exitStatus;
        }
    }
}

extern "C" int
getArrayLength(void* ptr)
{
    return *(int*)(((char *)ptr) + VM_ObjectModel_ARRAY_LENGTH_OFFSET);
}

#if (defined RVM_WITH_GCSPY)
// GCspy
// Richard Jones 12.09.02

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
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverAddStream: driver=%x, id=%d...", driver, id);
  gcspy_gc_stream_t *stream = gcspy_driverAddStream(driver, id);
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "stream=%x\n", stream);
  return stream;
}

extern "C" void
gcspyDriverEndOutput (gcspy_gc_driver_t *driver) {
  int len;
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverEndOutput: driver=%x, len=%d, written=%d\n", 
	                  driver, stream_len, stream_count);
    stream_count = 0;
    /*??*/
    gcspy_buffered_output_t *output =
      gcspy_command_stream_get_output(driver->interpreter);
    len = gcspy_bufferedOutputGetLen(output);
    fprintf(SysTraceFile, "gcspyDriverEndOutput: interpreter has len=%d\n", len);
  }
  gcspy_driverEndOutput(driver);
}

extern "C" void
gcspyDriverInit (gcspy_gc_driver_t *driver, int id, char *serverName, char *driverName,
                 char *title, char *blockInfo, int tileNum,
                 char *unused, int mainSpace) {
               
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverInit: driver=%x, id=%d, server=%s, driver=%s, title=%s, blockInfo=%s, %d tiles, used=%s, mainSpace=%d\n", 
                   driver, id, serverName, driverName, 
                   title, blockInfo, tileNum,
		   unused, mainSpace);
  gcspy_driverInit(driver, id, serverName, driverName, 
                   title, blockInfo, tileNum,
		   unused, mainSpace);
}

extern "C" void
gcspyDriverInitOutput (gcspy_gc_driver_t *driver) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverInitOutput: driver=%x\n", driver);
  gcspy_driverInitOutput(driver);
}

extern "C" void
gcspyDriverResize (gcspy_gc_driver_t *driver, int size) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverResize: driver=%x, size %d\n", driver, size);
  gcspy_driverResize(driver, size);
}

extern "C" void
gcspyDriverSetTileName (gcspy_gc_driver_t *driver, int tile, VM_Address start, VM_Address end) {
  char name[256];
#ifdef RVM_FOR_64_ADDR
  sprintf(name, "   [%016llx-%016llx)", start, end);
#else
  sprintf(name, "   [%08x-%08x)", start, end); 
#endif
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverSetTileName: driver=%x, tile %d %s\n", driver, tile, name);
  gcspy_driverSetTileName(driver, tile, name);
}

extern "C" void
gcspyDriverSpaceInfo (gcspy_gc_driver_t *driver, char *spaceInfo) {
  if (GCSPY_TRACE) 
    fprintf(SysTraceFile, "gcspyDriverSpaceInfo: driver=%x, spaceInfo = +%s+(%x)\n", driver, spaceInfo, spaceInfo);
  gcspy_driverSpaceInfo(driver, spaceInfo);
}

extern "C" void
gcspyDriverStartComm (gcspy_gc_driver_t *driver) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyDriverStartComm: driver=%x\n", driver);
  gcspy_driverStartComm(driver);
}

extern "C" void
gcspyDriverStream (gcspy_gc_driver_t *driver, int id, int len) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverStream: driver=%x, id=%d, len=%d\n", driver, id, len);
    stream_count = 0;
    stream_len = len;
  }
  gcspy_driverStream(driver, id, len);
}

extern "C" void
gcspyDriverStreamByteValue (gcspy_gc_driver_t *driver, int val) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverStreamByteValue: driver=%x, val=%d\n", driver, val);
    stream_count++;
  }
  gcspy_driverStreamByteValue(driver, val);
}

extern "C" void
gcspyDriverStreamShortValue (gcspy_gc_driver_t *driver, short val) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverStreamShortValue: driver=%x, val=%d\n", driver, val);
    stream_count++;
  }
  gcspy_driverStreamShortValue(driver, val);
}

extern "C" void
gcspyDriverStreamIntValue (gcspy_gc_driver_t *driver, int val) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverStreamIntValue: driver=%x, val=%d\n", driver, val);
    stream_count++;
  }
  gcspy_driverStreamIntValue(driver, val);
}

extern "C" void
gcspyDriverSummary (gcspy_gc_driver_t *driver, int id, int len) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverSummary: driver=%x, id=%d, len=%d\n", driver, id, len);
    stream_count = 0;
    stream_len = len;
  }
  gcspy_driverSummary(driver, id, len);
}

extern "C" void
gcspyDriverSummaryValue (gcspy_gc_driver_t *driver, int val) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyDriverSummaryValue: driver=%x, val=%d\n", driver, val);
    stream_count++;
  }
  gcspy_driverSummaryValue(driver, val);
}

/* Note: passed driver but uses driver->interpreter */
extern "C" void
gcspyIntWriteControl (gcspy_gc_driver_t *driver, int id, int len) {
  if (GCSPY_TRACE) {
    fprintf(SysTraceFile, "gcspyIntWriteControl: driver=%x, interpreter=%x, id=%d, len=%d\n", driver, driver->interpreter, id, len);
    stream_count = 0;
    stream_len = len;
  }
  gcspy_intWriteControl(driver->interpreter, id, len);
}

extern "C" gcspy_gc_driver_t *
gcspyMainServerAddDriver (gcspy_main_server_t *server) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerAddDriver (server address = %x): adding driver...", server);
  gcspy_gc_driver_t *driver = gcspy_mainServerAddDriver(server);
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "address = %d\n", driver);
  return driver;
}

extern "C" void
gcspyMainServerAddEvent (gcspy_main_server_t *server, int event, const char *name) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerAddEvent (server address = %x): event=%d, name=%s\n", server, event, name);
  gcspy_mainServerAddEvent(server, event, name);
}

extern "C" gcspy_main_server_t *
gcspyMainServerInit (int port, int len, const char *name, int verbose) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerInit: server=%x, port=%d, len=%d, name=%s, verbose=%d\n", &server, port, len, name, verbose);
  gcspy_mainServerInit(&server, port, len, name, verbose);
  return &server;
}

extern "C" int
gcspyMainServerIsConnected (gcspy_main_server_t *server, int event) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerIsConnected: server=%x, event=%d...", &server, event);
  int res = gcspy_mainServerIsConnected(server, event);
  if (GCSPY_TRACE)
    if (res)
      fprintf(SysTraceFile, "connected");
    else
      fprintf(SysTraceFile, "not connected");
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
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerSafepoint: server=%x, event=%d\n", &server, event);
  gcspy_mainServerSafepoint(server, event);
}

extern "C" void
gcspyMainServerSetGeneralInfo (gcspy_main_server_t *server, char *generalInfo) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerSetGeneralInfo: server=%x, info=%s\n", &server, generalInfo);
  gcspy_mainServerSetGeneralInfo(server, generalInfo);
}

extern "C" void
gcspyMainServerStartCompensationTimer (gcspy_main_server_t *server) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerStartCompensationTimer: server=%x\n", server);
  gcspy_mainServerStartCompensationTimer(server);
}

extern "C" void
gcspyMainServerStopCompensationTimer (gcspy_main_server_t *server) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyMainServerStopCompensationTimer: server=%x\n", server);
  gcspy_mainServerStopCompensationTimer(server);
}

extern "C" void
gcspyStartserver (gcspy_main_server_t *server, int wait, void *loop) {
//#ifndef __linux__
//  printf("I am not Linux!");
//  exit(1);
//#endif __linux__
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyStartserver: starting thread, wait=%d\n", wait);
  pthread_t tid;
  int res = pthread_create(&tid, NULL, 
		       (pthread_start_routine_t) loop,  server);
  if (res != 0) {
    printf("Couldn't create thread.\n");
    exit(1);
  }

  if(wait) {
    if (GCSPY_TRACE)
      fprintf(SysTraceFile, "gcspy_mainServerWaitForClient: server=%x\n", server);
    gcspy_mainServerWaitForClient(server);
  }
}

extern "C" void
gcspyStreamInit (gcspy_gc_stream_t *stream, int id, int dataType, char *streamName, 
                 int minValue, int maxValue, int zeroValue, int defaultValue,
                 char *stringPre, char *stringPost, int presentation, int paintStyle,
                 int maxStreamIndex, int red, int green, int blue) {
  gcspy_color_t colour;
  colour.red = (unsigned char) red;
  colour.green = (unsigned char) green;
  colour.blue = (unsigned char) blue;
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyStreamInit: stream=%x, id=%d, dataType=%d, name=\"%s\", min=%d, max=%d, zero=%d, default=%d, pre=\"%s\", post=\"%s\", presentation=%d, style=%d, maxIndex=%d, colour=%x<%d,%d,%d>\n", 
                   stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue, defaultValue,
		   stringPre, stringPost, presentation, paintStyle,
		   maxStreamIndex, &colour, colour.red, colour.green, colour.blue);
  gcspy_streamInit(stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue,defaultValue,
		   stringPre, stringPost, presentation, paintStyle,
		   maxStreamIndex, &colour);
}

extern "C" void
gcspyFormatSize (char *buffer, int size) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "gcspyFormatSize: size=%d...", size);
  strcpy(buffer, gcspy_formatSize(size));
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "buffer=%s\n", buffer);
}

extern "C" int
gcspySprintf(char *str, const char *format, char *arg) {
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "sprintf: str=%x, format=%s, arg=%s\n", str, format, arg);
  int res = sprintf(str, format, arg);
  if (GCSPY_TRACE)
    fprintf(SysTraceFile, "sprintf: result=%s (%x)\n", str, str);
  return res;
}

  
#endif
