/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

/**
 * O/S support services required by the java class libraries.
 * See also: VM_BootRecord.java
 *
 * @author Derek Lieber
 * @date 20 Apr 1998
 */

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//

// Aix and Linux version.  PowerPC and IA32.

extern "C" void sysExit(int);

// AIX needs this to get errno right. JTD
#define _THREAD_SAFE_ERRNO

#ifdef _AIX43
#include </usr/include/unistd.h>
extern "C" void profil(void*, uint, ulong, uint);
extern "C" int sched_yield();
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
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <netinet/in.h>
#include <linux/net.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <asm/ioctls.h>
#if (!defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
#include <sched.h>
#endif

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

#ifdef _AIX
extern "C" timer_t gettimerid(int timer_type, int notify_type);
extern "C" int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include "InterfaceDeclarations.h"

// Because of the time-slicer thread, we use the pthread library
// even when building for a single virtual processor.
#include <pthread.h>

#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
# include "syswrap.h"

// These are defined in libvm.C.
extern "C" void *getJTOC();
extern "C" int getProcessorsOffset();
#endif // RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS

/* #define DEBUG_SYS */
#define VERBOSE_PTHREAD 0

static int TimerDelay  =  10; // timer tick interval, in milliseconds     (10 <= delay <= 999)
static int SelectDelay =   2; // pause time for select(), in milliseconds (0  <= delay <= 999)

extern FILE *SysErrorFile;    // sink for serious error messages
extern FILE *SysTraceFile;    // sink for trace messages...
extern int   SysTraceFd;      // ...produced by VM.sysWrite()

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
extern "C" void
sys()
   {
   fprintf(SysErrorFile, "vm: unexpected call to \"sys\"\n");
   sysExit(1);
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
sysWriteLong(int value1, unsigned int value2, int hexToo)
   {
   long long value = (((long long)value1)<<32)|value2;
   if (hexToo==0 /*false*/) 
      fprintf(SysTraceFile, "%lld", value);
   else if (hexToo==1 /*true - also print in hex*/)
      fprintf(SysTraceFile, "%lld (0x%08x%08x)", value, value1, value2);
   else    /* hexToo==2 for only in hex */
      fprintf(SysTraceFile, "0x%08x%08x", value1, value2);
   }

// Exit with a return code.
//
pthread_mutex_t DeathLock = PTHREAD_MUTEX_INITIALIZER;

static bool systemExiting = false;

extern "C" void
sysExit(int value)
   {

#ifdef RVM_FOR_64_ADDR
   fprintf(stderr, "\nWHEEE....I got back to C code with value = %d or 0x%lx\n", value, value);
#endif

// fprintf(SysTraceFile, "sys: exit %d\n", value);
   if (value != 0)
      fprintf(SysErrorFile, "vm: exit %d\n", value);
 
   fflush(SysErrorFile);
   fflush(SysTraceFile);
   fflush(stdout);

   systemExiting = true;

#if (!defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   pthread_mutex_lock( &DeathLock );
   exit(value);
#else
   exit(value);
#endif
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
extern char **	JavaArgs;
extern int	JavaArgc;
extern "C" int
sysArg(int argno, char *buf, int buflen)
   {
   if (argno == -1) 
      { // return arg count
	return JavaArgc;
	/***********
      for (int i = 0;;++i)
         if (JavaArgs[i] == 0)
            return i;
	**************/
      }
   else
      { // return i-th arg
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
   return 0;
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
sysList(char *name, char *buf, int limit)
   {

#ifdef DEBUG_SYS
     fprintf(SysTraceFile, "sys: list %s 0x%08x %d\n", name, buf, limit);
#endif
     
   char DELIMITER = '\0';
   int  cnt = 0;
   DIR *dir = opendir(name);
   for (dirent *dp = readdir(dir); dp; dp = readdir(dir))
      {

	// POSIX says that d_name is NULL-terminated
      char *name = dp->d_name;
      int len = strlen( name );

#ifdef DEBUG_SYS
      fprintf(SysTraceFile, "sys: found %s\n", name);
#endif   


      if (len == 2 && name[0] == '.' && name[1] == '.') continue; // skip ".."
      if (len == 1 && name[0] == '.'                  ) continue; // skip "."
      
      while (len--)
         {
         if (cnt == limit) break;
         *buf++ = *name++;
         cnt +=1;
         }

      if (cnt == limit) break;
      *buf++ = DELIMITER;
      cnt += 1;
      if (cnt == limit) break;
      }
   closedir(dir);
   return cnt;
   }

// Get file status.
// Taken:    null terminated filename
//           kind of info desired (see VM_FileSystem.STAT_XXX)
// Returned: status (-1=error)
//
extern "C" int
sysStat(char *name, int kind)
   {

#ifdef DEBUG_SYS
     fprintf(SysTraceFile, "sys: stat %s\n", name);
#endif

   struct stat info;

   if (stat(name, &info)) 
      return -1; // does not exist

   switch (kind)
      {
      case VM_FileSystem_STAT_EXISTS:        return 1;                              // exists
      case VM_FileSystem_STAT_IS_FILE:       return S_ISREG(info.st_mode)     != 0; // is file
      case VM_FileSystem_STAT_IS_DIRECTORY:  return S_ISDIR(info.st_mode)     != 0; // is directory
      case VM_FileSystem_STAT_IS_READABLE:   return (info.st_mode & S_IREAD)  != 0; // is readable by owner
      case VM_FileSystem_STAT_IS_WRITABLE:   return (info.st_mode & S_IWRITE) != 0; // is writable by owner
      case VM_FileSystem_STAT_LAST_MODIFIED: return  info.st_mtime;                 // time of last modification
      case VM_FileSystem_STAT_LENGTH:        return  info.st_size;                  // length
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
     fprintf(SysTraceFile, "sys: access %s\n", name);
#endif

   return access(name, kind);
   }

// Set modification time (and access time) to given value
// (representing seconds after the epoch).
extern "C" int sysUtime(const char *fileName, int modTimeSec)
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

   switch (how)
      {
      case VM_FileSystem_OPEN_READ:   fd = open(name, O_RDONLY                         ); // "read"
	 break;
      case VM_FileSystem_OPEN_WRITE:  fd = open(name, O_RDWR | O_CREAT | O_TRUNC,  0666); // "write"
	 break;
      case VM_FileSystem_OPEN_MODIFY: fd = open(name, O_RDWR | O_CREAT,            0666); // "modify"
	 break;
      case VM_FileSystem_OPEN_APPEND: fd = open(name, O_RDWR | O_CREAT | O_APPEND, 0666); // "append"
	 break;
      default: return -1;
      }

#ifdef DEBUG_SYS
      fprintf(SysTraceFile, "sys: open %s %d, fd=%d\n", name, how, fd);
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
     fprintf(SysTraceFile, "sys: delete %s\n", name);
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
     fprintf(SysTraceFile, "sys: rename %s to %s\n", fromName, toName);
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
     fprintf(SysTraceFile, "sys: mkdir %s\n", name);
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
      fprintf(SysErrorFile, "vm: FIONREAD ioctl on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return badFD ? VM_ThreadIOConstants_FD_INVALID : -1;
      }
// fprintf(SysTraceFile, "sys: available fd=%d count=%d\n", fd, count);
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


extern "C" int sysSyncFile(int fd) {
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
        /*fprintf(SysTraceFile, "sys: read (byte) ch is %d\n", (int) ch);*/
	return (int) ch;
      case  0: 
        /*fprintf(SysTraceFile, "sys: read (byte) rc is 0\n");*/
        return -1;
      default: 
        /*fprintf(SysTraceFile, "sys: read (byte) rc is %d\n", rc);*/
	if (errno == EAGAIN)
	  return -2;	// Read would have blocked
	else if (errno == EINTR)
	  goto again;	// Read was interrupted; try again
	else
	  return -3;	// Some other error
      }
   }

// Write one byte to file.
// Taken:    file descriptor
//           data to write
// Returned: -2 operation would block, -1: error, 0: success
//
extern "C" int
sysWriteByte(int fd, int data) {
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
    fprintf(SysErrorFile, "sys: writeByte, fd=%d, write returned error %d (%s)\n",
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
   //fprintf(SysTraceFile, "sys: read %d 0x%08x %d\n", fd, buf, cnt);
 again:
   int rc = read(fd, buf, cnt);
   if (rc >= 0)
      return rc;
   int err = errno;
   if (err == EAGAIN)
      {
	  // fprintf(SysTraceFile, "sys: read on %d would have blocked: needs retry\n", fd);
      return -1;
      }
   else if (err == EINTR)
      goto again; // interrupted by signal; try again
   fprintf(SysTraceFile, "sys: read error %d (%s) on %d\n", err, strerror(err), fd);
   return -2;
   }

// Write multiple bytes to file or socket.
// Taken:    file or socket descriptor
//           buffer to be written
//           number of bytes to write
// Returned: number of bytes written (-2: error, -1: socket would have blocked,
//	     -3 EPIPE error)
//
extern "C" int
sysWriteBytes(int fd, char *buf, int cnt)
   {
// fprintf(SysTraceFile, "sys: write %d 0x%08x %d\n", fd, buf, cnt);
 again:
   int rc = write(fd, buf, cnt);
   if (rc >= 0)
      return rc;
   int err = errno;
   if (err == EAGAIN)
      {
	  // fprintf(SysTraceFile, "sys: write on %d would have blocked: needs retry\n", fd);
       return -1;
       }
   if (err == EINTR)
      goto again; // interrupted by signal; try again
    if (err == EPIPE)
       {
       //fprintf(SysTraceFile, "sys: write on %d with nobody to read it\n", fd);
       return -3;
       }
    fprintf(SysTraceFile, "sys: write error %d (%s) on %d\n", err, strerror( err ), fd);
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
 // fprintf(SysTraceFile, "sys: seek %d %d %d\n", fd, offset, whence);
    switch (whence)
       {
       case VM_FileSystem_SEEK_SET: return lseek(fd, offset, SEEK_SET);
       case VM_FileSystem_SEEK_CUR: return lseek(fd, offset, SEEK_CUR);
       case VM_FileSystem_SEEK_END: return lseek(fd, offset, SEEK_END);
       default:                     return -1;
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
    fprintf(SysTraceFile, "sys: close %d\n", fd);
    #endif

    if ( -1 == fd ) return -1;

    int rc = close(fd);

    if (rc == 0)
       return 0; // success

    if (errno == EBADF)
       return -1; // not currently open

    fprintf(SysErrorFile, "vm: close on %d failed (errno=%d)\n", fd, errno);
    return -2; // some other error
    }


// Determine whether or not given file descriptor is
// connected to a TTY.
//
// Taken: the file descriptor to query
// Returned: 1 if it's connected to a TTY, 0 if not
//
extern "C" int sysIsTTY(int fd) {
  return isatty(fd);
}

// Set the close-on-exec flag for given file descriptor.
//
// Taken: the file descriptor
// Returned: 0 if sucessful, nonzero otherwise
//
extern "C" int sysSetFdCloseOnExec(int fd) {
  return fcntl(fd, F_SETFD, FD_CLOEXEC);
}

 //--------------------------//
 // System timer operations. //
 //--------------------------//

 extern int VmBottom, VmTop;
 #ifdef _AIX
 #include <mon.h>
 #endif

#if (! defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
extern "C" void processTimerTick();

void *timeSlicerThreadMain(void *arg) {
    int ns = (int)arg;
    #ifdef DEBUG_SYS
    fprintf(SysErrorFile, "time slice interval %dns\n", ns);
    #endif
    for (;;) {
	struct timespec howLong;
	struct timespec remaining;
	
	howLong.tv_sec = 0;
	howLong.tv_nsec = ns;
	int errorCode = nanosleep( &howLong, &remaining );
	
	if (systemExiting) pthread_exit(0);
	
	processTimerTick();
    }
    
    return NULL;
}
#endif

 // Start/stop interrupt generator for thread timeslicing.
 // The interrupt will be delivered to whatever virtual processor
 // happens to be running when the timer fires.
 //
 // Taken:    interrupt interval, in milliseconds (0: "disable timer")
 // Returned: nothing
 //
 static void
 setTimeSlicer(int timerDelay)
    {
#if (! defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
      pthread_t timeSlicerThread;
      int nsTimerDelay = timerDelay * 1000000;
      int errorCode = pthread_create(&timeSlicerThread, NULL, timeSlicerThreadMain, (void*)nsTimerDelay);
#else
 #if (defined RVM_FOR_LINUX)
    // set it to issue a periodic SIGALRM (or 0 to disable timer)
    //

    struct itimerval timerInfo, oldtimer;

    timerInfo.it_value.tv_sec     = 0;
    timerInfo.it_value.tv_usec    = timerDelay * 1000;
    timerInfo.it_interval.tv_sec  = timerInfo.it_value.tv_sec;
    timerInfo.it_interval.tv_usec = timerInfo.it_value.tv_usec;

    if (setitimer(ITIMER_REAL, &timerInfo, &oldtimer))
       {
       fprintf(SysErrorFile, "vm: incinterval failed (errno=%d)\n", errno);
       sysExit(1);
       }
 #else
    // fetch system timer
    //
    timer_t timerId = gettimerid(TIMERID_REAL, DELIVERY_SIGNALS);
    if (timerId == -1)
       {
       fprintf(SysErrorFile, "vm: gettimerid failed (errno=%d)\n", errno);
       sysExit(1);
       }

    // set it to issue a periodic SIGALRM (or 0 to disable timer)
    //
    struct itimerstruc_t timerInfo, oldtimer;
    timerInfo.it_value.tv_sec     = 0;
    timerInfo.it_value.tv_nsec    = timerDelay * 1000 * 1000;
    timerInfo.it_interval.tv_sec  = timerInfo.it_value.tv_sec;
    timerInfo.it_interval.tv_nsec = timerInfo.it_value.tv_nsec;
    if (incinterval(timerId, &timerInfo, &oldtimer))
       {
       fprintf(SysErrorFile, "vm: incinterval failed (errno=%d)\n", errno);
       sysExit(1);
       }
 #endif
 #endif
 // fprintf(SysTraceFile, "sys: timeslice is %dms\n", timerDelay);
    }

    extern "C" void sysVirtualProcessorEnableTimeSlicing(int timeSlice) {
      if (VERBOSE_PTHREAD)
	fprintf(stderr,"Using a time-slice of %d ms\n", timeSlice);
      if (timeSlice < 10 || timeSlice > 999) {
	fprintf(SysErrorFile, "vm: timeslice of %d is outside range 10..999\n", timeSlice);
	sysExit(1);
      }
      setTimeSlicer(timeSlice);
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
 sysGetTimeOfDay() {
	 int rc;
	 long long returnValue;
	 struct timeval tv;
	 struct timezone tz;

	 returnValue = 0;

	 rc = gettimeofday(&tv, &tz);
	 if (rc != 0) {
		 returnValue = rc;
	 }
	 else {
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
      #ifdef RVM_FOR_POWERPC
      numpc = get_nprocs_conf();
      #elif RVM_FOR_IA32
      numpc = get_nprocs_conf();
      #endif
    #else
      numpc = _system_configuration.ncpus;
    #endif

    #ifdef DEBUG_SYS
    fprintf(SysTraceFile, "sysNumProcessors: returning %d\n", numpc );
    #endif
    return numpc;
    }

 static void *sysVirtualProcessorStartup(void *arg);
 #ifdef RVM_FOR_IA32
 extern "C" void bootThread(int ip, int jtoc, int pr, int sp); // assembler routine
 #else
 extern "C" void bootThread(int jtoc, int pr, int ti, int fp); // assembler routine
 #endif

//-----------------------------------------------------------
//  Minimal subset of hardware performance monitor operations for PowerPC      
//  that are needed for boot strapping Jikes RVM because JNI is not yet enabled.
//  Called from VM_HardwarePerformanceMonitors.boot().
//-----------------------------------------------------------
#ifdef RVM_WITH_HPM
#include "pmapi.h"
#include <pthread.h>
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
sysHPMinit()
{
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMinit() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "sys: sysHPMinit() called:\n");
#endif
  int filter = PM_UNVERIFIED|PM_VERIFIED|PM_CAVEAT;
  rc = hpm_init(filter);
  return rc;
#else
  fprintf(SysErrorFile, "sys: sysHPMinit() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMsetEvent(%d,%d,%d,%d) called: no support for linux\n",
	  e1,e2,e3,e4);
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "sys: sysHPMsetEvent(%d,%d,%d,%d) called\n",e1,e2,e3,e4);
#endif
  rc = hpm_set_event(e1, e2, e3, e4);
  return rc;
#else
  fprintf(SysErrorFile, "sys: sysHPMsetEvent(%d,%d,%d,%d) called: not compiled for HPM\n",
	  e1,e2,e3,e4);
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMsetEventX(%d,%d,%d,%d) called: no support for linux\n",
	  e5,e6,e7,e8);
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "sys: sysHPMsetEventX(%d,%d,%d,%d) called\n",e5,e6,e7,e8);
#endif
  rc = hpm_set_event_X(e5, e6, e7, e8);
  return rc;
#else
  fprintf(SysErrorFile, 
	  "sys: sysHPMsetEventX(%d,%d,%d,%d) called: not compiled for HPM\n",
	  e5,e6,e7,e8);
  exit(1);
  return 0;
#endif
#endif
}
/*
 * Set mode(s) to be monitored.
 * Only returns if valid parameters.
 * Possible modes are:
 *   #define PM_USER		4	// turns user mode counting on
 *   #define PM_KERNEL		8	// turns kernel mode counting on
 */
extern "C" int
sysHPMsetMode(int mode)
{
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMsetMode(%d) called: no support for linux\n",
	  mode);
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "sys: sysHPMsetMode(%d) called\n",mode);
#endif
  rc = hpm_set_mode(mode);
  return rc;
#else
  fprintf(SysErrorFile, "sys: sysHPMsetMode(%d) called: not compiled for HPM\n",
	  mode);
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMsetProgramMyThread() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysTraceFile, "sys: sysHPMsetProgramMyThread() called from pthread id %d\n",pthread_self());
#endif
  rc = hpm_set_program_mythread();
  return rc;
#else
  fprintf(SysTraceFile, "sys: sysHPMsetProgramMyThread() called: not compiled for HPM\n");
  exit(1);
 return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMsetProgramMyGroup() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "jvm: sysHPMsetProgramMyGroup() called\n");
#endif
  rc = hpm_set_program_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMsetProgramMyGroup() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMstartMyThread() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysTraceFile, "sys: sysHPMstartMyThread() called from pthread id %d\n",pthread_self());
#endif
  rc = hpm_start_mythread();
  return rc;
#else
  fprintf(SysTraceFile, "sys: sysHPMstartMyThread() called: not compiled for HPM\n");
  exit(1);
 return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMstartMyGroup() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysTraceFile, "sys: sysHPMstartMyGroup() called from pthread id %d\n",pthread_self());
#endif
  rc = hpm_start_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMstartMyGroup() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMstopMyThread() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  // fprintf(SysErrorFile, "jvm: sysHPMstopMyThread() called\n");
  rc = hpm_stop_mythread();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMstopMyThread() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMstopMyGroup() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_stop_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMstopMyGroup() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMresetMyThread() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_reset_mythread();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMresetMyThread() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMresetMyGroup() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_reset_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMresetMyGroup() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMgetCounterMyThread(%d) called: no support for linux\n",
	  counter);
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  return hpm_get_counter_mythread(counter);
#else
  fprintf(SysErrorFile, "jvm: sysHPMgetCounterMyThread(%d) called: not compiled for HPM\n",counter);
  exit(1);
  return 0;
#endif
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
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMgetCounterMyGroup(%d) called: no support for linux\n",
	  counter);
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  return hpm_get_counter_mygroup(counter);
#else
  fprintf(SysErrorFile, "jvm: sysHPMgetCounterMyGroup(%d) called: not compiled for HPM\n",counter);
  exit(1);
  return 0;
#endif
#endif
}
/*
 * Set mode(s) to be monitored.
 * Only returns if valid parameters.
 * Possible modes are:
 *   #define PM_USER		4	// turns user mode counting on
 *   #define PM_KERNEL		8	// turns kernel mode counting on
 */
extern "C" int
sysHPMgetNumberOfCounters()
{
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "sys: sysHPMgetNumberofCounters() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
#ifdef DEBUG_SYS
  fprintf(SysErrorFile, "sys: sysHPMgetNumberofCounters() called\n");
#endif
  rc = hpm_get_number_of_counters();
  return rc;
#else
  fprintf(SysErrorFile, "sys: sysHPMgetNumberofCounters() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
#endif
}

/*
 * Testing.
 * Only returns if successful.
 */
extern "C" int
sysHPMtest()
{
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMtest() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_test();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMtest() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
#endif
}
/*
 * Print
 */
extern "C" int
sysHPMprintMyGroup() 
{
#ifdef RVM_FOR_LINUX
  fprintf(stderr, "jvm: sysHPMprintMyGroup() called: no support for linux\n");
  exit(1);
  return 0;
#else
#ifdef RVM_WITH_HPM
  int rc;
  rc = hpm_print_mygroup();
  return rc;
#else
  fprintf(SysErrorFile, "jvm: sysHPMprintMyGroup() called: not compiled for HPM\n");
  exit(1);
  return 0;
#endif
#endif
}

 // Create a virtual processor (aka "unix kernel thread", "pthread").
 // Taken:    register values to use for pthread startup
 // Returned: virtual processor's o/s handle
 //
 extern "C" int
 sysVirtualProcessorCreate(int jtoc, int pr, int ti_or_ip, int fp)
    {
 #if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "sysVirtualProcessorCreate: Unsupported operation with single virtual processor\n");
    sysExit(-1);
    return (0);
 #else
    int           *sysVirtualProcessorArguments;
    pthread_attr_t sysVirtualProcessorAttributes;
    pthread_t      sysVirtualProcessorHandle;
    int            rc;

    // create arguments
    //
    sysVirtualProcessorArguments = new int[4];
    sysVirtualProcessorArguments[0] = jtoc;
    sysVirtualProcessorArguments[1] = pr;
    sysVirtualProcessorArguments[2] = ti_or_ip;
    sysVirtualProcessorArguments[3] = fp;

    // create attributes
    //
    if ((rc = pthread_attr_init(&sysVirtualProcessorAttributes)))
       {
       fprintf(SysErrorFile, "vm: pthread_attr_init failed (rc=%d)\n", rc);
       sysExit(1);
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
       fprintf(SysErrorFile, "vm: pthread_create failed (rc=%d)\n", rc);
       sysExit(1);
       }

 if (VERBOSE_PTHREAD)
    fprintf(SysTraceFile, "sys: pthread_create 0x%08x\n", sysVirtualProcessorHandle);

    return (int)sysVirtualProcessorHandle;
 #endif
    }

 static void *
 sysVirtualProcessorStartup(void *args)
    {
    int jtoc	= ((int *)args)[0];
    int pr	= ((int *)args)[1];
    int ti_or_ip	= ((int *)args)[2];
    int fp	= ((int *)args)[3];

 if (VERBOSE_PTHREAD)
    fprintf(SysTraceFile, "sys: sysVirtualProcessorStartup: jtoc=0x%08x pr=0x%08x ti_or_ip=0x%08x fp=0x%08x\n", jtoc, pr, ti_or_ip, fp);

    // branch to vm code
    //
    #if RVM_FOR_IA32
    {
    *(unsigned *) (pr + VM_Processor_framePointer_offset) = fp;
    int sp = fp + VM_Constants_STACKFRAME_BODY_OFFSET;
    bootThread(ti_or_ip, jtoc, pr, sp);
    }
    #else
    bootThread(jtoc, pr, ti_or_ip, fp);
    #endif

    // not reached
    //
    fprintf(SysTraceFile, "vm: sysVirtualProcessorStartup: failed\n");
    return 0;
    }

 // Bind execution of current virtual processor to specified physical cpu.
 // Taken:    physical cpu id (0, 1, 2, ...)
 // Returned: nothing
 //
 extern "C" void
 sysVirtualProcessorBind(int cpuId)
    {
 #if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    int rc = 0;
    fprintf(stderr, "sysVirtualProcessorBind: Unsupported operation with single virtual processor with single virtual processor\n");
    sysExit(-1);
 #else
    int numCpus;
    numCpus = sysconf(_SC_NPROCESSORS_ONLN);
 if (VERBOSE_PTHREAD)
    fprintf(SysTraceFile, "sys: %d cpu's\n", numCpus);

 // Linux does not seem to have this
 #ifndef RVM_FOR_LINUX
    if (numCpus == -1)
       {
       fprintf(SysErrorFile, "vm: sysconf failed (errno=%d)\n", errno);
       sysExit(1);
       }

    cpuId = cpuId % numCpus;

    int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
    fprintf(SysTraceFile, "sys: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

    if (rc)
       {
       fprintf(SysErrorFile, "vm: bindprocessor failed (errno=%d)\n", errno);
       sysExit(1);
       }
 #endif
 #endif
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
pthread_key_t VmProcessorIdKey;
pthread_key_t IsVmProcessorKey;

// Create keys for thread-specific data.
extern "C" void sysCreateThreadSpecificDataKeys(void) {
  int rc1, rc2;

  // Create a key for thread-specific data so we can associate
  // the id of the VM_Processor object with the pthread it
  // is running on.
  rc1 = pthread_key_create(&VmProcessorIdKey, 0);
  if (rc1 != 0) { 
    fprintf(SysErrorFile, "sys: pthread_key_create(&VMProcessorIdKey,0) failed (err=%d)\n", rc1);
    sysExit(1);
  }
  rc2 = pthread_key_create(&IsVmProcessorKey, 0);
  if (rc2 != 0) { 
    fprintf(SysErrorFile, "sys: pthread_key_create(&IsVMProcessorKey,0) failed (err=%d)\n", rc2);
    sysExit(1);
  }

  // Let the syscall wrapper library know what the key is,
  // along with the JTOC address and offset of VM_Scheduler.processors.
  // This will enable it to find the VM_Processor object later on.
#ifdef DEBUG_SYS
  fprintf(stderr, "sys: vm processor key=%u\n", VmProcessorIdKey);
#endif

  // creation of other keys can go here...
}
#endif // defined(RVM_WITH_INTERCEPT_BLOCKING_SYSTEM_CALLS)

extern "C" void sysInitializeStartupLocks(int howMany) {
  VirtualProcessorsLeftToStart = howMany;
  VirtualProcessorsLeftToWait = howMany;
}

extern "C" void sysWaitForVirtualProcessorInitialization() {
  pthread_mutex_lock( &VirtualProcessorStartupLock );
  if (--VirtualProcessorsLeftToStart == 0)
    pthread_cond_broadcast( &VirtualProcessorStartup );
  else
    pthread_cond_wait(&VirtualProcessorStartup, &VirtualProcessorStartupLock);
  pthread_mutex_unlock( &VirtualProcessorStartupLock );
}

extern "C" void sysWaitForMultithreadingStart() {
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
extern "C" int
sysPthreadSelf()
   {
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   fprintf(stderr, "sysPthreadSelf: WARNING Unsupported operation with single virtual processor\n");
   sysExit(-1);
   return -1; // will never execute
#else
   int thread;
   sigset_t input_set, output_set;
   int rc;

   thread = (int)pthread_self();
   
   if (VERBOSE_PTHREAD)
   fprintf(SysTraceFile, "sysPthreadSelf: thread %d\n", thread);

   /*
    * block the CONT signal.  This makes the signal reach this
    * pthread only when then pthread does a sigwai().  Maria
    */
   sigemptyset(&input_set);
   sigaddset(&input_set, SIGCONT);

#ifdef RVM_FOR_LINUX
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
     fprintf (SysErrorFile, "sigaltstack failed (errno=%d)\n", errno);
     return 1;
   }
#endif

#ifdef RVM_FOR_LINUX
   rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
#else
   rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
#endif

   return thread;
#endif
   }

//
extern "C" int
sysPthreadSignal(int pthread)
   {
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   fprintf(stderr, "sysPthreadSignal: Unsupported operation with single virtual processor\n");
   sysExit(-1);
#else
   pthread_t thread;
   thread = (pthread_t)pthread;

   pthread_kill(thread, SIGCONT);
#endif
   return 0;
   }

// 
extern "C" int 
sysPthreadJoin(int pthread)
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   fprintf(stderr, "sysPthreadJoin: Unsupported operation with single virtual processor\n");
   sysExit(-1);
#else
   pthread_t thread;
   thread = (pthread_t)pthread;
   // fprintf(SysTraceFile, "sys: pthread %d joins %d\n", pthread_self(), thread);
   pthread_join(thread, NULL);
#endif
   return 0;
}

// 
extern "C" void 
sysPthreadExit()
{
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   fprintf(stderr, "sysPthreadExit: Unsupported operation with single virtual processor\n");
   sysExit(-1);
#else
  // fprintf(SysTraceFile, "sys: pthread %d exits\n", pthread_self());
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
   fprintf(stderr, "sysVirtualProcessorYield: Unsupported operation with single virtual processor\n");
   sysExit(-1);
#endif
   }

//
// Taken -- address of an integer lockword
//       -- value to store in the lockword to 'release' the lock
// release the lockout word by storing the in it
// and wait for a signal.
extern "C" int
sysPthreadSigWait( int * lockwordAddress, int lockReleaseValue )
   {
#if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
   fprintf(stderr, "sysPthreadSigWait: Unsupported operation with single virtual processor\n");
   sysExit(-1);
   return -1; // will never execute
#else 
   sigset_t input_set, output_set;
   int      sig;

   *lockwordAddress = lockReleaseValue;

   sigemptyset(&input_set);
   sigaddset(&input_set, SIGCONT);
#ifdef RVM_FOR_LINUX
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
// Stash id of the VM_Processor object in the thread-specific
// data for the current pthread.  This allows us to get a handle
// on the VM_Processor (and its associated state) from arbitrary
// native code.
//
// Note that simply stashing the address of the VM_Processor is not
// sufficient, because the garbage collector might move it.
// (By knowing the id, we can look in the VM_Scheduler.processors
// array, which is accessible via the JTOC.)
extern "C" int sysStashVmProcessorIdInPthread(int vmProcessorId)
{
#if defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
  // We have only a single VM_Processor, so just pass its id
  // directly to the system call wrapper library, along with the
  // JTOC address and the offset of VM_Scheduler.processors.
  //fprintf(SysErrorFile, "sys: stashing vm_processor id = %d\n", vmProcessorId);
  //  initSyscallWrapperLibrary(getJTOC(), getProcessorsOffset(), vmProcessorId);
#else
  //fprintf(SysErrorFile, "stashing vm processor id = %d, self=%u\n",
  //  vmProcessorId, pthread_self());
  int rc = pthread_setspecific(VmProcessorIdKey, (void*) vmProcessorId);
  int rc2 = pthread_setspecific(IsVmProcessorKey, (void*) 1);
  if (rc != 0 || rc2 != 0) {
    fprintf(SysErrorFile, "sys: pthread_setspecific() failed (err=%d,%d)\n", rc, rc2);
    sysExit(1);
  }
#endif // defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
  return 0;
}
#endif // !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)

//------------------------//
// Arithmetic operations. //
//------------------------//

extern "C" long long sysLongDivide(long long a, long long b) {
  return a/b;
}

extern "C" long long sysLongRemainder(long long a, long long b) {
  return a % b;
}

extern "C" double sysLongToDouble(long long a) {
  return (double)a;
}

extern "C" float sysLongToFloat(long long a) {
  return (float)a;
}

double maxlong = 0.5 + (double)0x7fffffffffffffffLL;
double maxint  = 0.5 + (double)0x7fffffff;

extern "C" int sysFloatToInt(float a) {
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

extern "C" int sysDoubleToInt(double a) {
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

extern "C" long long sysFloatToLong(float a) {
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

extern "C" long long sysDoubleToLong(double a) {
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

#ifdef RVM_FOR_POWERPC
#include <math.h>
extern "C" double sysDoubleRemainder(double a, double b) {
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

//-------------------//
// Memory operations //
//-------------------//

// Memory to memory copy.
//
extern "C" void
sysCopy(void *dst, void *src, int cnt)
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
sysZero(void *dst, int cnt) {
   bzero(dst, cnt);
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
   bzero(dst, cnt);
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
      fprintf(SysErrorFile, "vm: munmap failed (errno=%d)\n", errno);
      sysExit(1);
      }

   void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
   if (addr == (void *)-1)
      {
      fprintf(SysErrorFile, "vm: mmap failed (errno=%d)\n", errno);
      sysExit(1);
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
      fprintf(SysErrorFile, "vm: disclaim failed (errno=%d)\n", errno);
      sysExit(1);
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
sysSyncCache(caddr_t address, int size)
   {
   #ifdef DEBUG_SYS
   fprintf(SysTraceFile, "sys: sync 0x%08x %d\n", (int)address, size);
   #endif

   #ifdef RVM_FOR_AIX
   _sync_cache_range(address, size);
   #else
   #ifdef RVM_FOR_LINUX
   #ifdef RVM_FOR_POWERPC
     {
       if (size < 0) {
	 fprintf(SysErrorFile, "vm: tried to sync a region of negative size!\n");
	 sysExit(1);
       }

     /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
     caddr_t start = address;
     caddr_t end = start + size;
     caddr_t addr;
 
     /* update storage */
     /* Note: if one knew the cache line size, one could write a better loop */
     for(addr=start; addr < end; ++addr)
       asm("dcbst 0,%0" : : "r" (addr) );
 
     /* wait for update to commit */
     asm("sync");
 
     /* invalidate icache */
     /* Note: if one knew the cache line size, one could write a better loop */
     for(addr=start; addr<end; ++addr)
       asm("icbi 0,%0" : : "r" (addr) );                                                     

     /* context synchronization */
     asm("isync");
     }
   #else
     ///fprintf(SysTraceFile, "\nskipping: sysSyncCache(int address, int size)\n");
   #endif
   #else
     ///fprintf(SysTraceFile, "\nskipping: sysSyncCache(int address, int size)\n");
   #endif
   #endif
   }

//-----------------//
// SHM* operations //
//-----------------//
extern "C" int sysShmget(int key, int size, int flags) 
{
    return shmget(key, size,flags);
}

extern "C" void * sysShmat(int shmid, char * addr, int flags) 
{
    return shmat(shmid, addr, flags);
}

extern "C" int sysShmdt(char * addr)
{
    if (shmdt(addr) == 1)
	return errno;
    return 0;
}

extern "C" int sysShmctl(int shmid, int command)
{
    return shmctl(shmid, command, NULL);
}


//-----------------//
// MMAP operations //
//-----------------//

// mmap - general case
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
//        desired protection (Java int)
//        flags (Java int)
//        file descriptor (Java int)
//        offset (Java long)  [to cover 64 bit file systems]
// Returned: address of region (or -1 on failure) (Java ADDRESS)
//
extern "C" void *
sysMMap(char *start, char *length, int protection, int flags, int fd, long long offset)
   {
   fprintf(SysErrorFile, "vm: sysMMap called, but it's unimplemented\n");
   sysExit(1);
   exit(1); // shut warnings up
   // return mmap(start, (size_t)(length), protection, flags, fd, (off_t)(offset));
   }

// mmap - non-file general case
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
//        desired protection (Java int)
//        flags (Java int)
// Returned: address of region (or -1 on failure) (Java ADDRESS)
//
extern "C" void *
sysMMapNonFile(char *start, char *length, int protection, int flags)
   {
       void *res = mmap(start, (size_t)(length), protection, flags, -1, 0);
       if (res == (void *) -1) {
	 printf("mmap (%x, %d, %d, %d, -1, 0) failed with %d\n", start, length, protection, flags, errno);
	 return (void *) errno;
       }
       #ifdef DEBUG_SYS
       printf("mmap worked - region = [0x%x ... 0x%x]    size = %d\n", res, ((int)res) + length, length);
       #endif
       return res;
   }

// mmap - demand zero fixed address case
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
// Returned: address of region (or -1 on failure) (Java ADDRESS)
//
extern "C" char *
sysMMapGeneralFile(char *start, char *length, int fd, int prot)
   {
   int flag = MAP_FILE | MAP_FIXED | MAP_SHARED;
   char *foo = (char *) mmap(start, (size_t)(length), prot, flag, fd, 0);

   return foo;
   }

// mmap - demand zero fixed address case
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
// Returned: address of region (or -1 on failure) (Java ADDRESS)
//
extern "C" char *
sysMMapDemandZeroFixed(char *start, char *length)
   {
   int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
   int flag = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
   return (char *) mmap(start, (size_t)(length), prot, flag, -1, 0);
   }

// mmap - demand zero any address case
// Taken: length of region (Java ADDRESS)
// Returned: address of region (or -1 on failure) (Java ADDRESS)
//
extern "C" char *
sysMMapDemandZeroAny(char *length)
   {
   int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
   int flag = MAP_ANONYMOUS | MAP_PRIVATE;
   return (char *) mmap(0, (size_t)(length), prot, flag, -1, 0);
   }

// munmap
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMUnmap(char *start, char *length)
   {
   return munmap(start, (size_t)(length));
   }

// mprotect
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
//        new protection (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMProtect(char *start, char *length, int prot)
   {
   return mprotect(start, (size_t)(length), prot);
   }

// msync
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
//        flags (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMSync(char *start, char *length, int flags)
   {
   return msync(start, (size_t)(length), flags);
   }

// madvise
// Taken: start address (Java ADDRESS)
//        length of region (Java ADDRESS)
//        advice (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
extern "C" int
sysMAdvise(char *start, char *length, int advice)
   {
#ifdef RVM_FOR_LINUX
   return -1; // unimplemented in Linux
#else
   return madvise(start, (size_t)(length), advice);
#endif
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
// Sweep through memory to find which areas of memory are mappable
//
extern "C" void findMappable() {
  int granularity = 1 << 22; // every 4 megabytes
  int max = (1 << 30) / (granularity >> 2);
  int pageSize = getpagesize();
  for (int i=0; i<max; i++) {
    char *start = (char *) (i * granularity);
   int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
   int flag = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
    void *result = mmap (start, (size_t) pageSize, prot, flag, -1, 0);
    int fail = (result == (void *) -1);
    printf("0x%x: ", start);
    if (fail)
      printf("FAILED with errno %d\n", errno);
    else {
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
extern "C" int
sysDlopen(char *libname)
   {
       void * libHandler;
       do {
	   libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
       }
       while( (libHandler == 0 /*null*/) && (errno == EINTR) );
       if (libHandler == 0) {
	 fprintf(SysErrorFile,
		 "vm: error loading library %s: %s\n", 
		 libname, dlerror());
	 return 0;
       }

       return (int)libHandler;
   }

// Look up symbol in dynamic library.
// Taken:
// Returned:
//
extern "C" int
sysDlsym(int libHandler, char *symbolName)
   {
   void *symbolAddress = dlsym((void *) libHandler, symbolName);
   return (int)symbolAddress;
   }

// Unload dynamic library.
// Taken:
// Returned:
//
extern "C" void
sysDlclose()
   {
   fprintf(SysTraceFile, "sys: dlclose not implemented yet\n");
   }

// Tell OS to remove shared library.
// Taken:
// Returned:
//
extern "C" void
sysSlibclean()
   {
   fprintf(SysTraceFile, "sys: slibclean not implemented yet\n");
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
      fprintf(SysErrorFile, "vm: gethostname failed (rc=%d)\n", rc);
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
#ifdef RVM_FOR_LINUX
   hostent * resultAddress;

   fprintf(SysTraceFile, "untested system call sysNetRemoteHostName()\n");
   resultAddress = gethostbyaddr((char *)&internetAddress,
				 sizeof(internetAddress),
				 AF_INET);
   
   if ( !resultAddress )
     return 0;

   char *name = resultAddress->h_name;
   for (int i = 0; i < limit; ++i)
      {
      if (name[i] == 0)
         return i;
      buf[i] = name[i];
      }
   return -1;
#else
   hostent      results; memset(&results, 0, sizeof(results));
   hostent_data data;    memset(&data, 0, sizeof(data));

   int rc = gethostbyaddr_r((char *)&internetAddress,
		             sizeof(internetAddress), AF_INET,
			     &results, &data);
   if (rc != 0)
      {
   // fprintf(SysErrorFile, "vm: gethostbyaddr_r failed (errno=%d)\n", h_errno);
      return 0;
      }
   
   char *name = results.h_name;
   for (int i = 0; i < limit; ++i)
      {
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
   hostent      results; memset(&results, 0, sizeof(results));
   hostent_data data;    memset(&data, 0, sizeof(data));
   
   int rc = gethostbyname_r(hostname, &results, &data);
   if (rc != 0)
      {
      fprintf(SysErrorFile, "vm: gethostbyname_r failed (errno=%d)\n", h_errno);
      return -2;
      }

   // verify 4-byte-address assumption
   //
   if (results.h_addrtype != AF_INET || results.h_length != 4 || sizeof(in_addr_t) != 4)
      {
      fprintf(SysErrorFile, "vm: gethostbyname_r failed (unexpected address type or length)\n");
      return -2;
      }

   in_addr **addresses = (in_addr **)results.h_addr_list;
   for (i = 0; addresses[i] != 0; ++i)
      {
      if (i == limit)
         return -1;

      printf("host address %x\n", addresses[i]->s_addr);

      *buf[i] = addresses[i]->s_addr;
      }
   return i;
   }
#endif

#ifdef RVM_FOR_LINUX
extern "C" int
sysNetHostAddresses(char *hostname, uint32_t **buf, int limit)
   {
   int i;

   hostent * result = gethostbyname(hostname);

   if ( !result || result->h_addrtype != AF_INET || result->h_length != 4 )
     return -2;

   uint32_t **address = (uint32_t ** )result->h_addr_list;
   for(i=0; address[i]; i++ )
     {
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
   if (fd == -1)
      {
      fprintf(SysErrorFile, "vm: socket create failed (errno=%d)\n", errno);
      return -1;
      }

#ifdef DEBUG_NET
   fprintf(SysTraceFile, "sys: create socket %d\n", fd);
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
   #ifdef RVM_FOR_AIX
   int len;
   #endif
   #ifdef RVM_FOR_LINUX
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }

#ifdef DEBUG_NET
   fprintf(SysTraceFile, "sys: socket %d using port %d\n", fd, MANGLE16(info.sin_port));
#endif

   return MANGLE16(info.sin_port);
   }
   
// Obtain local address associated with a socket.
// Taken: socket descriptor
// Returned: local address (-1: error)
//
extern "C" int
sysNetSocketLocalAddress(int fd)
   {
   sockaddr_in info;
   #ifdef RVM_FOR_AIX
   int len;
   #endif
   #ifdef RVM_FOR_LINUX
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }

#ifdef DEBUG_NET
   fprintf(SysTraceFile, "sys: socket %d using address %d\n", fd, MANGLE32(info.sin_addr.s_addr));
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
   #ifdef RVM_FOR_AIX
   int len;
   #endif
   #ifdef RVM_FOR_LINUX
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }

#ifdef DEBUG_NET
   fprintf(SysTraceFile, "sys: socket %d using family %d\n", fd, info.sin_family);
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
   if (listen(fd, backlog) == -1)
      {
      fprintf(SysErrorFile, "vm: socket listen on %d failed (errno=%d)\n", fd, errno);
      return -1;
      }

#ifdef DEBUG_NET
 fprintf(SysTraceFile, "sys: listen on socket %d (backlog %d)\n", fd, backlog);
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
sysNetSocketBind(int fd, int family, unsigned int localAddress, unsigned int localPort)
   {
   sockaddr_in address;

   memset(&address, 0, sizeof(address));
   address.sin_family      = family;
   address.sin_addr.s_addr = MANGLE32(localAddress);
   address.sin_port        = MANGLE16(localPort);

   if (bind(fd, (sockaddr *)&address, sizeof(address)) == -1)
      {
      fprintf(SysErrorFile, "vm: socket bind on %d for port %d failed (errno=%d, %s)\n", fd, localPort, errno, strerror( errno ));
      return -1;
      }

#ifdef DEBUG_NET   
   fprintf(SysTraceFile, "sys: bind %d to %d.%d.%d.%d:%d\n", fd, (localAddress >> 24) & 0xff, (localAddress >> 16) & 0xff, (localAddress >> 8) & 0xff, (localAddress >> 0) & 0xff, localPort & 0x0000ffff);
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
sysNetSocketConnect(int fd, int family, int remoteAddress, int remotePort) {
   int interruptsThisTime = 0;
   for (;;) {
       sockaddr_in address;

       memset(&address, 0, sizeof(address));
       address.sin_family      = family;
       address.sin_addr.s_addr = MANGLE32(remoteAddress);
       address.sin_port        = MANGLE16(remotePort);
       
       if (connect(fd, (sockaddr *)&address, sizeof(address)) == -1) {

	   if (errno == EINTR) {
	       fprintf(SysTraceFile, "sys: connect on %d interrupted, retrying\n");
	       connectInterrupts++;
	       interruptsThisTime++;
	       continue;
	   }

	   else if (errno == EINPROGRESS) {
#ifdef DEBUG_NET
	     fprintf(SysTraceFile, "sys: connect on %d failed: %s \n", fd, strerror( errno ));	       
#endif
	     return -2;
	   }

	   else if (errno == EISCONN) {
	       // connection was "in progress" due to previous call.
	       // This (retry) call has succeeded.
#ifdef DEBUG_NET
	     fprintf(SysTraceFile, "sys: connect on %d: %s\n", fd, strerror( errno ));
#endif
	     goto ok;
	   }

	   else if (errno == ECONNREFUSED) {
	       fprintf(SysTraceFile, "sys: connect on %d failed: %s \n", fd, strerror( errno ));
	       return -4;
	   }

	   else if (errno == EHOSTUNREACH) {
	       fprintf(SysTraceFile, "sys: connect on %d failed: %s \n", fd, strerror( errno ));
	       return -5;
	   }

	   else {
	       fprintf(SysErrorFile, "vm: socket connect on %d failed: %s (errno=%d)\n", fd, strerror(errno), errno);
	       return -3;
	   }
       }

   ok:
       if (interruptsThisTime > maxConnectInterrupts) {
	   maxConnectInterrupts = interruptsThisTime;
	   fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n", interruptsThisTime);
       }

#ifdef DEBUG_NET
       fprintf(SysTraceFile, "sys: connect %d to %d.%d.%d.%d:%d\n", fd, (remoteAddress >> 24) & 0xff, (remoteAddress >> 16) & 0xff, (remoteAddress >> 8) & 0xff, (remoteAddress >> 0) & 0xff, remotePort & 0x0000ffff);
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
sysNetSocketAccept(int fd, void *connectionObject) {
   int interruptsThisTime = 0;
   int connectionFd = -1;
   sockaddr_in info;
   #ifdef RVM_FOR_AIX
   int len;
   #endif
   #ifdef RVM_FOR_LINUX
   socklen_t len;
   #endif
   
   #ifdef DEBUG_NET
   fprintf(SysTraceFile, "accepting for socket %d, 0x%x\n", fd, connectionObject);
   #endif

   len = sizeof(info);
   for (;;) {
       connectionFd = accept(fd, (sockaddr *)&info, &len);
       
       if (connectionFd > 0) {
	   break;
       }

       else if (connectionFd == -1) {
	   
	   if (errno == EINTR) {
	       fprintf(SysTraceFile, "sys: accept on %d interrupted\n", fd);
	       interruptsThisTime++;
	       acceptInterrupts++;
	       continue;
	   }

	   else if (errno == EAGAIN) {
#ifdef DEBUG_NET
	     fprintf(SysTraceFile, "sys: accept on %d would have blocked: needs retry\n", fd);
#endif
	     return -2;
	   }

	   else {
#ifdef DEBUG_NET
	     fprintf(SysTraceFile, "vm: socket accept on %d failed (errno=%d, %s)\n", fd, errno, strerror( errno ));
#endif
	     return -3;
	   }
       }
   }

   #ifdef DEBUG_NET
   fprintf(SysTraceFile, "accepted %d for socket %d, 0x%x\n", connectionFd, fd, connectionObject);
   #endif

   int remoteFamily  = info.sin_family;
   int remoteAddress = MANGLE32(info.sin_addr.s_addr);
   int remotePort    = MANGLE16(info.sin_port);

#ifdef DEBUG_NET
   fprintf(SysTraceFile, "sys: %d accept %d from %d.%d.%d.%d:%d\n", fd, connectionFd, (remoteAddress >> 24) & 0xff, (remoteAddress >> 16) & 0xff, (remoteAddress >> 8) & 0xff, (remoteAddress >> 0) & 0xff, remotePort & 0x0000ffff);
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
       fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n", interruptsThisTime);
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
   fprintf(SysTraceFile, "sys: linger socket=%d enable=%d timeout=%d\n", fd, enable, timeout);
#endif

   linger info;
   info.l_onoff  = enable;
   info.l_linger = timeout;

   int rc = setsockopt(fd, SOL_SOCKET, SO_LINGER, &info, sizeof(info));
   if (rc == -1) fprintf(SysErrorFile, "vm: socket linger on %d failed (errno=%d)\n", fd, errno);
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
   fprintf(SysTraceFile, "sys: nodelay socket=%d value=%d\n", fd, value);
#endif

   int rc = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &value, sizeof(value));
   if (rc == -1)
     fprintf(SysErrorFile, "vm: TCP_NODELAY on %d failed (%s, errno=%d)\n", fd, strerror(errno), errno);

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
   fprintf(SysTraceFile, "sys: noblock socket=%d value=%d\n", fd, value);
#endif

   int rc = ioctl(fd, FIONBIO, &value);
   if (rc == -1)
      {
      fprintf(SysErrorFile, "vm: FIONBIO on %d failed (%s, errno=%d)\n", fd, strerror(errno), errno);
      return -1;
      }

   return 0;
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
   fprintf(SysTraceFile, "sys: close socket=%d\n", fd);
#endif

   // shutdown (disable sends and receives on) socket then close it

   int rc = shutdown(fd, 2);

   if (rc == 0)
      { // shutdown succeeded
      return sysClose(fd);
      }

   if (errno == ENOTCONN)
      { // socket wasn't connected so shutdown error is meaningless
      return sysClose(fd);
      }

   fprintf(SysErrorFile, "vm: socket shutdown on %d failed (errno=%d (%s))\n", fd, errno, strerror(errno));

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
extern "C" int sysNetSocketShutdown(int fd, int how)
{
#ifdef DEBUG_NET
  fprintf(SysTraceFile, "sys: shutdown socket %d for %s\n",
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
static bool addFileDescriptors(
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
      fprintf(SysErrorFile, "vm: select: fd(%d) exceeds system limit(%d)\n",
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
static void updateStatus(int *fdArray, int count, fd_set *ready)
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
static int checkInvalid(int *fdArray, int count, fd_set *exceptFdSet)
{
  int numInvalid = 0;
  for (int i = 0; i < count; ++i) {
    int fd = fdArray[i] & VM_ThreadIOConstants_FD_MASK;
    if (FD_ISSET(fd, exceptFdSet)) {
      //fprintf(SysErrorFile, "vm: fd %d in sysNetSelect() is invalid\n", fd);
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
  int *allFds,	// all fds being polled: read, write, and exception
  int rc,	// number of read file descriptors
  int wc,	// number of write file descriptors
  int ec)	// number of exception file descriptors
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
    SelectFunc realSelect = getLibcSelect();
    if (realSelect == 0) {
      fprintf(SysErrorFile, "sys: could not get pointer to real select()\n");
      sysExit(1);
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

    if (ret == 0)
      { // none ready
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
    
    if (err == EINTR)
      { // interrupted by timer tick: retry
	  return 0;
      }
    else if (err == EBADF) {
      // This can happen if somebody passes us an invalid file descriptor.
      // Check the read and write file descriptors against the exception
      // fd set, so we can find the culprit(s).
      int numInvalid = 0;
      numInvalid += checkInvalid(allFds + VM_ThreadIOQueue_READ_OFFSET, rc, &exceptReady);
      numInvalid += checkInvalid(allFds + VM_ThreadIOQueue_WRITE_OFFSET, wc, &exceptReady);
      if (numInvalid == 0) {
	// This is bad.
	fprintf(SysErrorFile,
		"vm: select returned with EBADF, but no file descriptors found in exception set\n");
	return -1;
      }
      else
	return 1;
    }
    
    // fprintf(SysErrorFile, "vm: socket select failed (err=%d (%s))\n", err, strerror( err ));
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
extern "C" void sysWaitPids(int pidArray[], int exitStatusArray[], int numPids)
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
sysSprintf(char buffer[], double d)
  {
    sprintf(buffer, "%G", d);
    return strlen(buffer);
  }

extern "C" int getArrayLength(void* ptr) {
  return *(int*)(((unsigned)ptr) + VM_ObjectModel_ARRAY_LENGTH_OFFSET);
}
