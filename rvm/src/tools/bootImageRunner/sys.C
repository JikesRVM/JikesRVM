/*
 * (C) Copyright IBM Corp. 2001
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

#ifdef __linux__
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

#elif __CYGWIN__

/* AIX/PowerPC */
#else
#include <sys/cache.h>
#include <sys/ioctl.h>
#endif

#ifndef __CYGWIN__
#include <sys/shm.h>        /* disclaim() */
#endif
#include <strings.h>        /* bzero() */
#include <sys/mman.h>       /* mmap & munmap() */
#include <errno.h>
#include <dlfcn.h>

#ifdef _AIX
extern "C" timer_t gettimerid(int timer_type, int notify_type);
extern "C" int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include "InterfaceDeclarations.h"


/*#define DEBUG_SYS*/
/*#define VERBOSE_PTHREAD*/

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
   exit(1);
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
extern "C" void
sysExit(int value)
   {
// fprintf(SysTraceFile, "sys: exit %d\n", value);
   if (value != 0)
      fprintf(SysErrorFile, "vm: exit %d\n", value);
 
   fflush(SysErrorFile);
   fflush(SysTraceFile);
   fflush(stdout);

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
// fprintf(SysTraceFile, "sys: list %s 0x%08x %d\n", name, buf, limit);
   
   char DELIMITER = '\0';
   int  cnt = 0;
   DIR *dir = opendir(name);
   for (dirent *dp = readdir(dir); dp; dp = readdir(dir))
      {

	// POSIX says that d_name is NULL-terminated
      char *name = dp->d_name;
      int len = strlen( name );

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
   //fprintf(SysTraceFile, "sys: stat %s\n", name);

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

// Open file.
// Taken:    null terminated filename
//           access/creation mode (see VM_FileSystem.OPEN_XXX)
// Returned: file descriptor (-1: not found or couldn't create)
//
extern "C" int
sysOpen(char *name, int how)
   {
// fprintf(SysTraceFile, "sys: open %s %d\n", name, how);
   switch (how)
      {
      case VM_FileSystem_OPEN_READ:   return open(name, O_RDONLY                         ); // "read"
      case VM_FileSystem_OPEN_WRITE:  return open(name, O_RDWR | O_CREAT | O_TRUNC,  0666); // "write"
      case VM_FileSystem_OPEN_MODIFY: return open(name, O_RDWR | O_CREAT,            0666); // "modify"
      case VM_FileSystem_OPEN_APPEND: return open(name, O_RDWR | O_CREAT | O_APPEND, 0666); // "append"
      default: return -1;
      }
   }

// Delete file.
// Taken:    null terminated filename
// Returned: 
//
extern "C" int
sysDelete(char *name)
   {
// fprintf(SysTraceFile, "sys: delete %s\n", name);
	return remove(name);
   }

// Rename file.
// Taken:    null terminated from and to filenames
// Returned: 
//
extern "C" int
sysRename(char *fromName, char *toName)
   {
// fprintf(SysTraceFile, "sys: rename %s\n", name);
	return rename(fromName, toName);
   }

// Make directory.
// Taken:    null terminated filename
// Returned: status (-1=error) 
//
extern "C" int
sysMkDir(char *name)
   {
// fprintf(SysTraceFile, "sys: mkdir %s\n", name);
     return mkdir(name, 0777); // Give all user/group/other permissions.
                               // mkdir will modify them according to the
                               // file mode creation mask (umask (1)).
   }

// How many bytes can be read from file/socket without blocking?
// Taken:    file/socket descriptor
// Returned: >=0: count, -1: error
//
extern "C" int
sysBytesAvailable(int fd)
   {
#if __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else
   int count = 0;
   if (ioctl(fd, FIONREAD, &count) == -1)
      {
      fprintf(SysErrorFile, "vm: FIONREAD ioctl on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }
// fprintf(SysTraceFile, "sys: available fd=%d count=%d\n", fd, count);
   return count;
#endif
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
// Returned: data read (-2: error, -1: eof, >= 0: valid)
//
extern "C" int
sysReadByte(int fd)
   {
   unsigned char ch;
   int rc;

   #ifdef DEBUG_SYS
   fprintf(SysTraceFile, "sys: read (byte) %d\n", fd);
   #endif    

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
	return -2;
      }
   }

// Write one byte to file.
// Taken:    file descriptor
//           data to write
// Returned: -1: error
//
extern "C" int
sysWriteByte(int fd, int data)
   {
   char ch = data;

   #ifdef DEBUG_SYS
   fprintf(SysTraceFile, "sys: write %d\n", fd);
   #endif   

   return write(fd, &ch, 1);
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
   int rc = read(fd, buf, cnt);
   int err = errno;
   if (rc >= 0)
      return rc;
   if (err == EAGAIN)
      {
	  // fprintf(SysTraceFile, "sys: read on %d would have blocked: needs retry\n", fd);
      return -1;
      }
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
   int rc = write(fd, buf, cnt);
   int err = errno;
   if (rc >= 0)
      return rc;
   if (err == EAGAIN)
      {
	  // fprintf(SysTraceFile, "sys: write on %d would have blocked: needs retry\n", fd);
       return -1;
       }
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

 //--------------------------//
 // System timer operations. //
 //--------------------------//

 extern int VmBottom, VmMiddle, VmTop;
 #ifdef _AIX
 #include <mon.h>
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
 #if (defined __linux__)
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
       exit(1);
       }
 #elif __CYGWIN__
    fprintf(SysErrorFile, "vm: skipping call to incinterval\n");
 #else
    // fetch system timer
    //
    timer_t timerId = gettimerid(TIMERID_REAL, DELIVERY_SIGNALS);
    if (timerId == -1)
       {
       fprintf(SysErrorFile, "vm: gettimerid failed (errno=%d)\n", errno);
       exit(1);
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
       exit(1);
       }
 #endif
 // fprintf(SysTraceFile, "sys: timeslice is %dms\n", timerDelay);
    }

 extern "C"  void
 sysVirtualProcessorEnableTimeSlicing()
    {
    setTimeSlicer(TimerDelay);
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

    #ifdef __linux__
      #ifdef RVM_FOR_POWERPC
      numpc = get_nprocs_conf();
      #elif RVM_FOR_IA32
      numpc = get_nprocs_conf();
      #endif
    #elif __CYGWIN__
      fprintf(SysTraceFile, "\nuntested system call: sysNumProcessors()\n");
      numpc = 1; // bogus.
    #else
      numpc = _system_configuration.ncpus;
    #endif

    #ifdef DEBUG_SYS
    fprintf(SysTraceFile, "sysNumProcessors: returning %d\n", numpc );
    #endif
    return numpc;
    }

 #if (!defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
 #include <pthread.h>
 #endif
 static void *sysVirtualProcessorStartup(void *arg);
 #ifdef RVM_FOR_IA32
 extern "C" void bootThread(int ip, int jtoc, int pr, int sp); // assembler routine
 #else
 extern "C" void bootThread(int jtoc, int pr, int ti, int fp); // assembler routine
 #endif

 // Create a virtual processor (aka "unix kernel thread", "pthread").
 // Taken:    register values to use for pthread startup
 // Returned: virtual processor's o/s handle
 //
 extern "C" int
 sysVirtualProcessorCreate(int jtoc, int pr, int ti_or_ip, int fp)
    {
 #if (defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    fprintf(stderr, "sysVirtualProcessorCreate: Unsupported operation with single virtual processor\n");
    exit (-1);
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
       exit(1);
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
       exit(1);
       }

 #ifdef VERBOSE_PTHREAD
    fprintf(SysTraceFile, "sys: pthread_create 0x%08x\n", sysVirtualProcessorHandle);
 #endif
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

 #ifdef VERBOSE_PTHREAD
    fprintf(SysTraceFile, "sys: sysVirtualProcessorStartup: jtoc=0x%08x pr=0x%08x ti_or_ip=0x%08x fp=0x%08x\n", jtoc, pr, ti_or_ip, fp);
 #endif

    // branch to vm code
    //
    #if RVM_FOR_IA32
    {
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
    exit (-1);
 #else
    int numCpus;
    numCpus = sysconf(_SC_NPROCESSORS_ONLN);
 #ifdef VERBOSE_PTHREAD
    fprintf(SysTraceFile, "sys: %d cpu's\n", numCpus);
 #endif

 // Linux does not seem to have this
 #ifndef __linux__
    if (numCpus == -1)
       {
       fprintf(SysErrorFile, "vm: sysconf failed (errno=%d)\n", errno);
       exit(1);
       }

    cpuId = cpuId % numCpus;

    int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
    fprintf(SysTraceFile, "sys: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

    if (rc)
       {
       fprintf(SysErrorFile, "vm: bindprocessor failed (errno=%d)\n", errno);
       exit(1);
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
   exit(-1);
#else
   int thread;
   sigset_t input_set, output_set;
   int rc;

   thread = (int)pthread_self();
   
   #ifdef VERBOSE_PTHREAD
   fprintf(SysTraceFile, "sysPthreadSelf: thread %d\n", thread);
   #endif

   /*
    * block the CONT signal.  This makes the signal reach this
    * pthread only when then pthread does a sigwai().  Maria
    */
   sigemptyset(&input_set);
   sigaddset(&input_set, SIGCONT);
#ifdef __linux__
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
   exit(-1);
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
   exit(-1);
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
   exit(-1);
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
   exit(-1);
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
   exit(-1);
#else 
   sigset_t input_set, output_set;
   int      sig;

   *lockwordAddress = lockReleaseValue;

   sigemptyset(&input_set);
   sigaddset(&input_set, SIGCONT);
#ifdef __linux__
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

//------------------------//
// Arithmetic operations. //
//------------------------//

// long divide and remainder
//
extern "C" long long
sysLongDivide(long long a, long long b)
  {
  return a / b;
  }

extern "C" long long
sysLongRemainder(long long a, long long b)
  {
  return a % b;
  }

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
sysZero(void *dst, int cnt)
   {
#ifdef __CYGWIN__
   bzero((char*)dst, cnt);
#else
   bzero(dst, cnt);
#endif
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
#ifdef __CYGWIN__
   bzero((char*)dst, cnt);
#else
   bzero(dst, cnt);
#endif
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
      exit(1);
      }

   void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
   if (addr == (void *)-1)
      {
      fprintf(SysErrorFile, "vm: mmap failed (errno=%d)\n", errno);
      exit(1);
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
      exit(1);
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
sysSyncCache(int address, int size)
   {
   #ifdef DEBUG_SYS
   fprintf(SysTraceFile, "sys: sync 0x%08x %d\n", address, size);
   #endif

   #ifdef IBM_AIX
   _sync_cache_range((caddr_t)address, size);
   #else
   #ifdef __linux__
   #ifdef RVM_FOR_POWERPC
     {
       if (size < 0) {
	 fprintf(SysErrorFile, "vm: tried to sync a region of negative size!\n");
	 exit(1);
       }

     /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
     caddr_t start = (caddr_t)address;
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
   exit(1);
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
   return mmap(start, (size_t)(length), protection, flags, -1, 0);
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
#ifdef __linux__
   return -1; // unimplemented in Linux
#elif __CYGWIN__
   return -1; // unimplemented in Cygwin
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
	   libHandler = dlopen(libname, RTLD_NOW);
       }
       while( (libHandler == 0 /*null*/) && (errno == EINTR) );
       if (libHandler == 0) {
	 if (errno == ENOEXEC)
	   fprintf(SysErrorFile, "vm: error loading library, %s\n", dlerror());
	 else {
	   switch (errno) {
	     case EACCES:
	       fprintf(SysErrorFile, "vm: error loading library, cannot access because not an ordinary file, or permission denied\n"); 
	       return 0;
	     case EINVAL:
	       fprintf(SysErrorFile, "vm: error loading library, incorrect file header for the host machine\n"); 
	       return 0;
	     case ELOOP:
	       fprintf(SysErrorFile, "vm: error loading library, too many symbolic links in path name\n"); 
	       return 0;
	     case ENOEXEC:
	       fprintf(SysErrorFile, "vm: error loading library, problem in loading or resolving symbols, possibly invalid XCOFF header\n"); 
	       return 0;
	     case ENOMEM:
	       fprintf(SysErrorFile, "vm: error loading library, not enough memory\n"); 
	       return 0;
	     case ETXTBSY:
	       fprintf(SysErrorFile, "vm: error loading library, file currently open for writing by others\n"); 
	       return 0;
	     case ENAMETOOLONG:
	       fprintf(SysErrorFile, "vm: error loading library, path exceeded 1023 characters\n"); 
	       return 0;
	     case ENOENT:
	       fprintf(SysErrorFile, "vm: error loading library, bad library path\n"); 
	       return 0;
	     case ENOTDIR:
	       fprintf(SysErrorFile, "vm: error loading library, library path not a directory\n"); 
	       return 0;
	     case ESTALE:
	       fprintf(SysErrorFile, "vm: error loading library, file system unmounted\n"); 
	       return 0;
	   }

	 }
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

#ifdef IBM_AIX
// Work around header file differences: AIX 4.1 vs AIX 4.2 vs AIX 4.3
//
#define getsockname xxxgetsockname
#define accept      xxxaccept
#endif
#include <sys/socket.h>
#include <sys/select.h>
#include <netdb.h>
#include <netinet/tcp.h>
#ifdef IBM_AIX
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
      if (buf[i] == 0)
         return i;

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
#ifdef __linux__
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
#elif __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
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
#ifdef __CYGWIN__
extern "C" int
sysNetHostAddresses(char *hostname, char **buf, int limit) 
  {
     fprintf(SysTraceFile, "\nunimplemented system call: sysNetHostAddresses\n");
     exit(1);                  
  }
#endif

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

#ifdef __linux__
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

// fprintf(SysTraceFile, "sys: create socket %d\n", fd);
   return fd;
   }

// Obtain port number associated with a socket.
// Taken: socket descriptor
// Returned: port number (-1: error)
//
extern "C" int
sysNetSocketPort(int fd)
   {
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else   
   sockaddr_in info;
   #ifdef IBM_AIX
   int len;
   #endif
   #ifdef __linux__
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }
// fprintf(SysTraceFile, "sys: socket %d using port %d\n", fd, MANGLE16(info.sin_port));
   return MANGLE16(info.sin_port);
#endif
   }
   
// Obtain local address associated with a socket.
// Taken: socket descriptor
// Returned: local address (-1: error)
//
extern "C" int
sysNetSocketLocalAddress(int fd)
   {
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else   
   sockaddr_in info;
   #ifdef IBM_AIX
   int len;
   #endif
   #ifdef __linux__
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }
// fprintf(SysTraceFile, "sys: socket %d using address %d\n", fd, MANGLE32(info.sin_addr.s_addr));
   return MANGLE32(info.sin_addr.s_addr);
#endif
   }
   
// Obtain family associated with a socket.
// Taken: socket descriptor
// Returned: local address (-1: error)
//
extern "C" int
sysNetSocketFamily(int fd)
   {
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else   
   sockaddr_in info;
   #ifdef IBM_AIX
   int len;
   #endif
   #ifdef __linux__
   socklen_t len;
   #endif

   len = sizeof(info);
   if (getsockname(fd, (sockaddr *)&info, &len) == -1)
      {
      fprintf(SysErrorFile, "vm: getsockname on %d failed (errno=%d (%s))\n", fd, errno, strerror( errno ));
      return -1;
      }
// fprintf(SysTraceFile, "sys: socket %d using family %d\n", fd, info.sin_family);
   return info.sin_family;
#endif
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
// fprintf(SysTraceFile, "sys: listen on socket %d (backlog %d)\n", fd, backlog);
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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else
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
   
// fprintf(SysTraceFile, "sys: bind %d to %d.%d.%d.%d:%d\n", fd, (localAddress >> 24) & 0xff, (localAddress >> 16) & 0xff, (localAddress >> 8) & 0xff, (localAddress >> 0) & 0xff, localPort & 0x0000ffff);
   return 0;
#endif
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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else
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

	   else if (errno == EINPROGRESS || errno == EALREADY) {
	     // fprintf(SysTraceFile, "sys: connect on %d failed: %s \n", fd, strerror( errno ));	       
	       return -2;
	   }

	   else if (errno == EISCONN) {
	       // connection was "in progress" due to previous call.
	       // This (retry) call has succeeded.
	     // fprintf(SysTraceFile, "sys: connect on %d: %s\n", fd, strerror( errno ));
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
	       fprintf(SysErrorFile, "vm: socket connect on %d failed (errno=%d)\n", fd, errno);
	       return -3;
	   }
       }

   ok:
       if (interruptsThisTime > maxConnectInterrupts) {
	   maxConnectInterrupts = interruptsThisTime;
	   fprintf(SysErrorFile, "maxSelectInterrupts is now %d\n", interruptsThisTime);
       }

       return 0;
   }
#endif
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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
   return 0;
#else
   int interruptsThisTime = 0;
   int connectionFd = -1;
   sockaddr_in info;
   #ifdef IBM_AIX
   int len;
   #endif
   #ifdef __linux__
   socklen_t len;
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
	       // fprintf(SysTraceFile, "sys: accept on %d would have blocked: needs retry\n", fd);
	       return -2;
	   }

	   else {
	       // fprintf(SysErrorFile, "vm: socket accept on %d failed (errno=%d, %s)\n", fd, errno, strerror( errno ));
	       return -3;
	   }
       }
   }

   int remoteFamily  = info.sin_family;
   int remoteAddress = MANGLE32(info.sin_addr.s_addr);
   int remotePort    = MANGLE16(info.sin_port);

// fprintf(SysTraceFile, "sys: %d accept %d from %d.%d.%d.%d:%d\n", fd, connectionFd, (remoteAddress >> 24) & 0xff, (remoteAddress >> 16) & 0xff, (remoteAddress >> 8) & 0xff, (remoteAddress >> 0) & 0xff, remotePort & 0x0000ffff);
   
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
#endif
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

// fprintf(SysTraceFile, "sys: linger socket=%d enable=%d timeout=%d\n", fd, enable, timeout);

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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
#else
   int value = enable;


// fprintf(SysTraceFile, "sys: nodelay socket=%d value=%d\n", fd, value);
   int rc = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &value, sizeof(value));
   if (rc == -1) fprintf(SysErrorFile, "vm: TCP_NODELAY on %d failed (errno=%d)\n", fd, errno);
   return rc;
#endif
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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
#else
   int value = enable;

// fprintf(SysTraceFile, "sys: noblock socket=%d value=%d\n", fd, value);
   int rc = ioctl(fd, FIONBIO, &value);
   if (rc == -1)
      {
      fprintf(SysErrorFile, "vm: FIONBIO on %d failed (errno=%d)\n", fd, errno);
      return -1;
      }
   return 0;
#endif
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
#ifdef __CYGWIN__
   fprintf(stderr, "vm: Unsupported operation (cygwin networking)\n");
   exit(-1);
#else
// fprintf(SysTraceFile, "sys: close socket=%d\n", fd);

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
#endif
   }

// Test list of sockets to see if an i/o operation would proceed without blocking.
// Taken:       array of socket descriptors to be checked for reading
//              array size
// Returned:    1: some sockets can proceed with i/o operation
//              0: no sockets can proceed with i/o operation
//             -1: error
// Side effect: readFds[i] is set to "VM_ThreadIOQueue.FD_READY" iff i/o can proceed on that socket
//
extern "C" int
sysNetSelect(int *readFds, int *writeFds, int rc, int wc) {
  // JTD 8/2/01 moved for loop up here because select man page says it can
  // corrupt all its inputs, including the fdsets, when it returns an error
  int interruptsThisTime = 0;
  for (;;) {
    int limitfd = FD_SETSIZE; // largest possible fd (aix-imposed limit)
    int maxfd   = -1;         // largest fd currently in use
    int i;
    
    // build bitstrings representing fd's to be interrogated
    //
    fd_set readReady; FD_ZERO(&readReady);
    fd_set writeReady; FD_ZERO(&writeReady);
    for (i = 0; i < rc; ++i)
      {
	int fd = readFds[i];
#ifdef DEBUG_SYS
	fprintf ( SysTraceFile, "select on fd %d\n", fd );
#endif
	if (fd > maxfd)
	  maxfd = fd;
	if (fd > limitfd)
	  {
	    fprintf(SysErrorFile, "vm: select: fd(%d) exceeds system limit(%d)\n", fd, limitfd);
	    return -1;
	  }
	FD_SET(fd, &readReady);
      }
    for (i = 0; i < wc; ++i)
      {
	int fd = writeFds[i];
#ifdef DEBUG_SYS
	fprintf ( SysTraceFile, "select on fd %d\n", fd );
#endif
	if (fd > maxfd)
	  maxfd = fd;
	if (fd > limitfd)
	  {
	    fprintf(SysErrorFile, "vm: select: fd(%d) exceeds system limit(%d)\n", fd, limitfd);
	    return -1;
	  }
	FD_SET(fd, &writeReady);
      }
    
    // interrogate
    //
    // timeval timeout; timeout.tv_sec = 0; timeout.tv_usec = SelectDelay * 1000;
    timeval timeout; timeout.tv_sec = 0; timeout.tv_usec = 0;
    int ret = select(maxfd + 1, &readReady, &writeReady, 0, &timeout);
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
	for (i = 0; i < rc; ++i)
	  {
            int fd = readFds[i];
            if (FD_ISSET(fd, &readReady))
	      {
		// fprintf(SysTraceFile, "%dr ", fd);
		readFds[i] = VM_ThreadIOQueue_FD_READY;
	      }
	  }

	for (i = 0; i < wc; ++i)
	  {
            int fd = writeFds[i];
            if (FD_ISSET(fd, &writeReady))
	      {
		// fprintf(SysTraceFile, "%dr ", fd);
		writeFds[i] = VM_ThreadIOQueue_FD_READY;
	      }
	  }

	if (interruptsThisTime > maxSelectInterrupts)
	    maxSelectInterrupts = interruptsThisTime;

	return 1;
      }
    
    if (err == EINTR)
      { // interrupted by timer tick: retry
	  return 0;
      }
    
    // fprintf(SysErrorFile, "vm: socket select failed (err=%d (%s))\n", err, strerror( err ));
    return -1;
  }
  
  return -1; // not reached (but xlC isn't smart enough to realize it)
}


extern "C" int
sysSprintf(char buffer[], double d)
  {
    sprintf(buffer, "%G", d);
    return strlen(buffer);
  }





