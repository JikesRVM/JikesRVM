/*
 * (C) Copyright IBM Corp. 2001
 */
/* AIX native implementation for creating and managing process
 * @author Ton Ngo   (1/9/98)
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

void print_wait_status(int ReturnedValue);
void print_exec_status(int status);
void lookup_AIX_signal(char *buf, int value);

/* Status seen when process stops during load or unload (for dynamic linking/unlinking) */
#define TRAP_LOAD  ((SIGTRAP << 8) | 0x7c)


/* register number for each system pointer, to be filled on init */
int jvmFP;
int jvmSP;
int jvmTI;
int jvmJTOC;
char **GPR_names; 
int    GPR_count;
char **FPR_names;
int    FPR_count;

/* shift amount for thread register:  to convert value to index */
int shiftTP;

/* These traps are to be ignored by jdp, they are checked in isIgnoredTrap */
int ignoredTraps[] = {SIGALRM};

/**********************************************************
 * Set the register mapping to be used in the native code
 * Class:     Platform
 * Method:    setRegisterMapping
 * Signature: (IIII[Ljava/lang/String;[Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_Platform_setRegisterMapping
  (JNIEnv *env, jobject obj, jint fp, jint sp, jint ti, jint jtoc,
   jobjectArray gprnames, jobjectArray fprnames, jint tpshift)
{
  jsize count; 
  jstring jname;
  const char *name;
  int i;

  jvmFP = fp;
  jvmSP = sp;
  jvmTI = ti;
  jvmJTOC = jtoc;
  shiftTP = tpshift;

  /* save the name mapping for the general purpose registers */
  count = (*env)->GetArrayLength(env, gprnames);
  GPR_count = count;
  GPR_names = (char **) malloc(count * sizeof(char *));
  /* printf("Mapping %d GPR names\n", count); */
  for (i=0; i<count; i++) {
    jname = (*env)->GetObjectArrayElement(env, gprnames, i);
    name = (*env)->GetStringUTFChars(env, jname, 0);
    GPR_names[i] = (char *) malloc (strlen(name));
    strcpy(GPR_names[i], name);
    (*env)->ReleaseStringUTFChars(env, jname, name);   
    /* printf("  entry %d: %s \n", i, GPR_names[i]); */
  }
  
  /* save the name mapping for the floating point registers */
  count = (*env)->GetArrayLength(env, fprnames);
  FPR_count = count;
  FPR_names = (char **) malloc(count * sizeof(char *));
  /* printf("Mapping %d FPR names\n", count); */
  for (i=0; i<count; i++) {
    jname = (*env)->GetObjectArrayElement(env, fprnames, i);
    name = (*env)->GetStringUTFChars(env, jname, 0);
    FPR_names[i] = (char *) malloc (strlen(name));
    strcpy(FPR_names[i], name);
    (*env)->ReleaseStringUTFChars(env, jname, name);   
    /* printf("  entry %d: %s \n", i, FPR_names[i]); */
  }
  
}

/**********************************************************
 * Return an array of system thread ID
 * Class:     Platform
 * Method:    getSystemThreadId
 * Signature: ()[I
 */
JNIEXPORT jintArray JNICALL Java_Platform_getSystemThreadId1
  (JNIEnv *env, jobject obj, jint debuggee_pid) 
{
  int *thread_id_array;
  int  threadcount, i;
  jintArray jths;
  jint *ths;
  jboolean isCopy;
    
  threadcount = getSystemThreadId(debuggee_pid, &thread_id_array);

  if (threadcount!=0) {
    /* Allocate integer array in Java and get a local copy */
    jths = (*env)->NewIntArray(env, threadcount);
    ths = (*env)->GetIntArrayElements(env, jths, &isCopy);
    
    /* For each thread, copy the thread ID into the java array */
    for (i=0; i<threadcount; i++) {
      ths[i] = thread_id_array[i];
    }
    
    /* release the array copy back to Java */
    (*env)->ReleaseIntArrayElements(env, jths, ths, 0);
    free(thread_id_array);   

    return jths;

  } else {
    return NULL;
  }

}

/* Local procedure to get the thread ID */
int getSystemThreadId(int debuggee_pid, int **thread_id_array) {
  int receivecount, requestcount, totalcount, thread_id, i;
  struct thrdsinfo *threadbuffer;
  struct ptsprs th_sreg;
  int *id_array, id_array_size;

  thread_id = 0;
  totalcount = 0;
  requestcount = 10;
  receivecount = requestcount;
  threadbuffer = (struct thrdsinfo *) malloc(sizeof(struct thrdsinfo) * requestcount);
  id_array = (int *)  malloc(sizeof(int) * requestcount);
  id_array_size = requestcount;

  /* loop to read all the thread ID belonging to this process */
  while (receivecount == requestcount) {
    receivecount = getthrds(debuggee_pid, threadbuffer, 
			    sizeof(struct thrdsinfo), 
			    &thread_id, requestcount);			    
    /*    printf("for %d, getthrds finds %d thread, next thread index %d\n ", 
	  debuggee_pid, receivecount, thread_id); */
    if (receivecount==-1) {
      switch (errno) {
      case EINVAL: printf("rc=EINVAL\n"); break;
      case ESRCH: printf("rc=ESRCH\n"); break;
      case EFAULT: printf("rc=EFAULT\n"); break;
      }
    } else {
      /* check to make sure we have room */
      if (totalcount+receivecount > id_array_size) {
	id_array_size += requestcount;
	id_array = (int *) realloc(id_array, sizeof(int) * id_array_size);
      }	
      for (i=0; i<receivecount; i++) {
	int thisone = threadbuffer[i].ti_flag & 0x00000040;
	id_array[totalcount++] = threadbuffer[i].ti_tid;
      }
    }
  }

  free(threadbuffer);
  *thread_id_array = id_array;
  return totalcount;

}


/**********************************************************
 * Return a string for a dump of the system thread
 * Class:     Platform
 * Method:    listSystemThreads
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Platform_listSystemThreads1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int receivecount, requestcount, threadcount;
  int thread_id, result_size;
  struct thrdsinfo *threadbuffer;
  struct ptsprs th_sreg;
  char buf[160], *result;     /* thread string buffer to copy back to Java */  
  jstring j_result;
  
  thread_id = 0;
  threadcount = 0;
  requestcount = 10;
  receivecount = requestcount;
  threadbuffer = (struct thrdsinfo *) malloc(sizeof(struct thrdsinfo) * requestcount);
  result_size = requestcount;
  result = (char *) malloc(1024 * result_size);

  /* Loop to query system threads */
  while (receivecount == requestcount) {
    receivecount = getthrds(debuggee_pid, threadbuffer, 
			    sizeof(struct thrdsinfo), 
			    &thread_id, requestcount);			    
    /* printf("for %d, getthrds finds %d thread, next thread index %d\n ", 
	   debuggee_pid, receivecount, thread_id); */
    if (receivecount!=-1) {
      int i,j;
      int regbuf[32];

      /* print thread info into buffer*/
      for (i=0; i<receivecount; i++) {
	threadcount++;
	/* Check to make sure we have enough room in the string buffer*/
	if (threadcount>result_size) {
	  result_size += requestcount;
	  result = (char *) realloc(result, 1024*result_size);
	}

	sprintf(buf, "System thread id = %d\n", threadbuffer[i].ti_tid);
	strcat(result, buf);
	switch (threadbuffer[i].ti_state) {
	case TSNONE:  sprintf(buf, "  state = slot is available\n"); break;
	case TSIDL:   sprintf(buf, "  state = being created\n"); break;
	case TSRUN:   sprintf(buf, "  state = runnable\n"); break;
	case TSSLEEP: sprintf(buf, "  state = awaiting an event\n"); break;
	case TSSWAP:  sprintf(buf, "  state = swapped \n"); break;
	case TSSTOP:  sprintf(buf, "  state = stopped\n"); break;
	case TSZOMB:  sprintf(buf, "  state = being deleted\n"); break;
	default:      sprintf(buf, "  state = unknown \n"); break;
	}
	strcat(result, buf);

	sprintf(buf, "  flag = %08x \n", threadbuffer[i].ti_flag);
	strcat(result, buf);

	/* get the register set */
	ptrace(PTT_READ_GPRS, threadbuffer[i].ti_tid, regbuf, 0, 0); 	
	for (j=0; j<8; j++) {
	  sprintf(buf, "  R%d = %08x  R%d = %08x  R%d = %08x  R%d = %08x\n", 
		  j, regbuf[j], (j+8), regbuf[j+8],
		  (j+16), regbuf[j+16], (j+24), regbuf[j+24]);
	  strcat(result, buf);
	}
	ptrace(PTT_READ_SPRS, threadbuffer[i].ti_tid, (int *) &th_sreg, 0, 0);
	sprintf(buf, "  IAR = %08x  MSR = %08x  CR  = %08x\n", 
		th_sreg.pt_iar, th_sreg.pt_msr, th_sreg.pt_cr);         
	strcat(result, buf);
	sprintf(buf, "  LR  = %08x  CTR = %08x  XER = %08x\n\n", 
		th_sreg.pt_lr, th_sreg.pt_ctr, th_sreg.pt_xer);         
	strcat(result, buf);

      }

    } else {
      switch (errno) {
      case EINVAL: sprintf(buf, "rc=EINVAL\n"); break;
      case ESRCH:  sprintf(buf, "rc=ESRCH\n"); break;
      case EFAULT: sprintf(buf, "rc=EFAULT\n"); break;
      }
    }
  }

  j_result = (*env)->NewStringUTF(env, result);  /* copy the string back to java space */
  free(threadbuffer);
  free(result);

  return j_result;

}

/**********************************************************
 * Class:     Platform
 * Method:    getVMThreadInSystemThread
 * Signature: ()[I
 */
JNIEXPORT jintArray JNICALL Java_Platform_getVMThreadIDInSystemThread1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int i;
  int regbuf[32];
  int idx, *id_array;
  jintArray jths;
  jint *ths;
  jboolean isCopy;
  
  /* get the system thread id's */
  idx = getSystemThreadId(debuggee_pid, &id_array); 

  /* Allocate integer array in Java and get a local copy */
  jths = (*env)->NewIntArray(env, idx);
  ths = (*env)->GetIntArrayElements(env, jths, &isCopy);

  /* For each thread, read the registers to get the thread pointers in register 15 */
  for (i=0; i<idx; i++) {
    ptrace(PTT_READ_GPRS, id_array[i], regbuf, 0, 0); 
    ths[i] = registerToTPIndex(regbuf[15]);   /* convert the value to thread ID */
    /* printf("%d:%d  ", ths[i], id_array[i]); */
  }
  /* printf("\n"); fflush(stdout); */
  
  /* release the array copy back to Java */
  (*env)->ReleaseIntArrayElements(env, jths, ths, 0);

  free(id_array);

  return jths;

}

/**********************************************************
 * Continue only the thread in which the debugger is stopped
 * Class:     Platform
 * Method:    mcontinueThread(I)
 */
JNIEXPORT void JNICALL Java_Platform_mcontinueThread1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint waitstatus)
{
  int threadcount, i, rc, threadsig, vmthread_id;
  int *thread_id_array;   /* to retrieve the list of all system thread id */
  struct ptthreads thread_id_buf;   /* for the list of system thread to continue */
  int regbuf[32];         /* to read the thread register set */

  threadcount = getSystemThreadId(debuggee_pid, &thread_id_array);

  /* use the value in register jvmTI to find the kernel thread we stopped in */
  vmthread_id = ptrace(PT_READ_GPR, debuggee_pid, (int *) jvmTI, 0, 0);

  threadsig = 0;
  if (waitstatus!=0) {
    if (WIFSTOPPED(waitstatus)) 
      threadsig = WSTOPSIG(waitstatus);
    else if (WIFSIGNALED(waitstatus)) 
      threadsig = WTERMSIG(waitstatus);
    else
      threadsig = 0;
  }  

  for (i=0; i<threadcount; i++) {

    /* For each thread, read the registers to get the thread pointers 
     *  in register jvmTI */
    ptrace(PTT_READ_GPRS, thread_id_array[i], regbuf, 0, 0);     

    if (regbuf[jvmTI]==vmthread_id) {
      /* continue this thread only */
      /* thread_id_buf.th[0] = thread_id_array[i]; */
      thread_id_buf.th[0] = 0;
      thread_id_buf.th[1] = 0;                      /* null terminated */
      /* printf("_____continuing only thread %d\n", thread_id_array[i]); fflush(stdout); */
      rc = ptrace(PTT_CONTINUE, thread_id_array[i], (int *) 1, threadsig, 
		  (int *)(&thread_id_buf)); 
      /* printf("threadsig = %d, rc = %d\n", threadsig, rc); fflush(stdout); */
      if (rc==-1) {
	printf("ptrace PTT_CONTINUE rc = %d, ", errno);
	switch (errno) {
	case EINVAL: printf("wrong thread ID in identifier\n"); break;
	case EIO: printf("invalid signal number\n"); break;
	case ESRCH: printf("wrong thread ID in buffer\n"); break;
	default: printf("unknown error\n");
	}
	fflush(stdout);
      }
      return;
    }

  } /* for loop */


}


/**********************************************************
 * Continue all threads in the process
 * Class:     Platform
 * Method:    mcontinue(I)
 */
JNIEXPORT void JNICALL Java_Platform_mcontinue1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint waitstatus)
{
  int rc, threadsig;

  threadsig = 0;
  if (waitstatus!=0) {
    if (WIFSTOPPED(waitstatus)) 
      threadsig = WSTOPSIG(waitstatus);
    else if (WIFSIGNALED(waitstatus)) 
      threadsig = WTERMSIG(waitstatus);
    else
      threadsig = 0;
  }

  /* printf("all thread continue with sig %d\n", threadsig); fflush(stdout); */

  rc = ptrace(PT_CONTINUE, debuggee_pid, (int *) 1, threadsig, 0);
}


/**********************************************************
 * Class:     Platform
 * Method:    mwait()I
 */
JNIEXPORT jint JNICALL Java_Platform_mwait
  (JNIEnv *env, jobject obj)
{
  int status;
  pid_t wakeup_pid;

  /* printf("(native) waiting for debuggee to stop\n");
   * fflush(stdout);
   */

  wakeup_pid = wait(&status);
  /* if (wakeup_pid==-1) {
    printf("Wait RC = %d, ", errno);
    switch (errno) {
    case ECHILD: printf("no such child process\n"); break;
    case EINTR: printf("already terminated\n"); break;
    case EFAULT: printf("cannot write out status\n"); break;
    default: printf("unknown error\n");
    }
  } */

  /* print_wait_status(status); */

  return status;

}

/**********************************************************
 * Terminate the process
 * Class:     Platform
 * Method:    mkill
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_Platform_mkill1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int rc = ptrace(PT_KILL,debuggee_pid, 0, 0, 0);
}

/**********************************************************
 * Detach the process
 * Class:     Platform
 * Method:    mdetach
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_Platform_mdetach1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int rc = ptrace(PT_DETACH,debuggee_pid, 0, 0, 0);
  if (rc==-1) {
    printf("Cannot detach process %d", debuggee_pid); 
    switch (errno) {
    case EIO: printf(", invalid signal sent\n"); break;
    default:  printf(", unknown error\n"); 
    }
  }     
}

/**********************************************************
 * Fork the OS process, enable its debugging mode
 * and return the process ID
 * Class:     Platform
 * Method:    createDebugProcess
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_Platform_createDebugProcess1
  (JNIEnv *env, jobject obj, jstring progname, jobjectArray args)
{
  int rc, debuggee_pid, i, numargs;
  jstring j_argstring;
  const char *argstring;
  char **newargs;

  /* convert Java string to C string */
  const char *buf = (*env)->GetStringUTFChars(env, progname, 0);
  numargs = (*env)->GetArrayLength(env,args);
  /* newargs = (char **) malloc(numargs * sizeof(char *)); */
  newargs = (char **) malloc((numargs+1) * sizeof(char *));      /* add NULL last entry */
  newargs[numargs] = NULL;
  for (i=0; i < numargs; i++) {
    j_argstring = (*env)->GetObjectArrayElement(env, args, i);
    argstring = (*env)->GetStringUTFChars(env, j_argstring, 0);
    newargs[i] = (char *) malloc (strlen(argstring) + 1); /* one more byte for null */
    strcpy(newargs[i], argstring);
    (*env)->ReleaseStringUTFChars(env, j_argstring, argstring);
  }
  

  printf("Starting debuggee process %s : ", buf);
  for (i=0; i < numargs; i++)   
    printf("%s ", newargs[i]);
  printf("\n"); 
  fflush(stdout);

  if((debuggee_pid = fork()) == 0)  { 
    /* debuggee process: enable control through ptrace */
    ptrace(PT_TRACE_ME,0,0,0,0); 

    /* exec the user program */
    printf("Debuggee process started.\n"); fflush(stdout);

    /* rc = execlp(buf, buf, 0); */
    rc = execv(buf, newargs); 

    /* if exec is successful, we will not return here from the debuggee */
    printf("ERROR:  cannot execute program %s, exec rc = %d\n", buf, rc);
    print_exec_status(errno);
    exit (-1);
  }

  /* tell Java we're finished with the string */
  (*env)->ReleaseStringUTFChars(env, progname, buf);
  return debuggee_pid;
}


/**********************************************************
 * Attach to a currently running process
 * Class:     Platform
 * Method:    attachDebugProcess
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Platform_attachDebugProcess1
  (JNIEnv *env, jobject obj, jint processID)
{
  int rc;

  printf("Attempting to attach to process %d\n", processID);
  rc = ptrace(PT_ATTACH, processID, 0, 0, 0);

  if (rc==-1) {
    switch (errno) {
    case ESRCH: 
      printf("Process ID invalid\n"); 
      break;
    case EPERM: 
      printf("Cannot get permission to attach to process %d\n", processID);
      break;
    case EINVAL:
      printf("Process ID %d belongs to the debugger, please use the one for the JVM\n", 
	     processID);
      break;
    default:
      printf("Fail to attach, unknown problem\n"); 
    }
    return -1;
  }

  printf("Attached.\n");
  return processID;

}

/**********************************************************
 * Return TRUE if the status word indicates a trap to be ignored
 * Class:     Platform
 * Method:    isIgnoredTrap
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_Platform_isIgnoredTrap
  (JNIEnv *env, jobject obj, jint status)
{
  int sig, i;
  int numIgnoredTraps = sizeof(ignoredTraps) / 4;
  sig = WSTOPSIG(status);    /* extract the stopping signal */
  
  for (i=0; i<numIgnoredTraps; i++) {
    if (sig == ignoredTraps[i]) 
      return JNI_TRUE;
  }
  return JNI_FALSE;


}

/**********************************************************
 * Return TRUE if the status word indicates a Breakpoint Trap
 * Class:     Platform
 * Method:    isBreakpointTrap
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_Platform_isBreakpointTrap
  (JNIEnv *env, jobject obj, jint status)
{
  int sig;
  sig = WSTOPSIG(status);    /* extract the stopping signal */
  if (sig == SIGTRAP) 
    return JNI_TRUE;
  else
    return JNI_FALSE;
}

/**********************************************************
 * Return TRUE if the status word indicates a Trap for a library loaded
 * Class:     Platform
 * Method:    isLibraryLoadedTrap
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_Platform_isLibraryLoadedTrap
  (JNIEnv *env, jobject obj, jint status)
{
  if (status == TRAP_LOAD)
    return JNI_TRUE;
  else
    return JNI_FALSE;
}

/**********************************************************
 * Return TRUE if the status word indicates a Kill signal
 * Class:     Platform
 * Method:    isKilled
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_Platform_isKilled
  (JNIEnv *env, jobject obj, jint status)
{
  int sig;
  sig = WTERMSIG(status);    /* extract the terminating signal */
  if (sig == SIGKILL) 
    return JNI_TRUE;
  else
    return JNI_FALSE;
}

/**********************************************************
 * Return TRUE if the status word indicates the process has exited
 * Class:     Platform
 * Method:    isExit
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_Platform_isExit
  (JNIEnv *env, jobject obj, jint status)
{
  if (WIFEXITED(status)) 
    return JNI_TRUE;
  else
    return JNI_FALSE;
}

/**********************************************************
 * Return a string describing the type of exception encountered
 * Class:     Platform
 * Method:    statusMessage
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Platform_statusMessage
  (JNIEnv *env, jobject obj, jint ReturnedValue)
{
  int mysig;
  char buf[160];
  jstring j_buf;

  /* extract the signal from the wait status word */
  if (WIFSTOPPED(ReturnedValue)) {
    mysig = WSTOPSIG(ReturnedValue);
    lookup_AIX_signal(buf, mysig);   /* fill the buffer with the signal description */
  } 
  else if (WIFEXITED(ReturnedValue)) {
    sprintf(buf, "exited with rc %d\n",  WEXITSTATUS(ReturnedValue));
  }
  else if (WIFSIGNALED(ReturnedValue)) {
    mysig = WTERMSIG(ReturnedValue);
    lookup_AIX_signal(buf, mysig);   /* fill the buffer with the signal description */
  }

  j_buf = (*env)->NewStringUTF(env, buf);  /* copy the string back to java space */
  return j_buf;

}

/* these messages should be the same as those emitted by psignal,
 * except that they go into the char buffer instead of stderr
 */
void lookup_AIX_signal(char *buf, int value)
{
  switch (value) {
  case SIGHUP    : sprintf(buf,"Hangup"); break;
  case SIGINT    : sprintf(buf,"Interrupt"); break;
  case SIGQUIT   : sprintf(buf,"Quit"); break;
  case SIGILL    : sprintf(buf,"Illegal instruction"); break;
  case SIGTRAP   : sprintf(buf,"Trace/BPT trap"); break;
  case SIGABRT   : sprintf(buf,"IOT/Abort trap"); break;
  case SIGEMT    : sprintf(buf,"EMT trap"); break;
  case SIGFPE    : sprintf(buf,"Floating point exception"); break;
  case SIGKILL   : sprintf(buf,"Killed"); break;
  case SIGBUS    : sprintf(buf,"Bus error"); break;
  case SIGSEGV   : sprintf(buf,"Segmentation fault"); break;
  case SIGSYS    : sprintf(buf,"Bad system call"); break;
  case SIGPIPE   : sprintf(buf,"Broken pipe"); break;
  case SIGALRM   : sprintf(buf,"Alarm clock"); break;
  case SIGTERM   : sprintf(buf,"Terminated"); break;
  case SIGURG    : sprintf(buf,"Urgent I/O condition"); break;
  case SIGSTOP   : sprintf(buf,"Stopped (signal)"); break;
  case SIGTSTP   : sprintf(buf,"Stopped"); break;
  case SIGCONT   : sprintf(buf,"Continued"); break;
  case SIGCHLD   : sprintf(buf,"Child exited"); break;
  case SIGTTIN   : sprintf(buf,"Stopped (tty input)"); break;
  case SIGTTOU   : sprintf(buf,"Stopped (tty output)"); break;
  case SIGIO     : sprintf(buf,"I/O possible/complete"); break;
  case SIGXCPU   : sprintf(buf,"Cputime limit exceeded"); break;
  case SIGXFSZ   : sprintf(buf,"Filesize limit exceeded"); break;
  case SIGMSG    : sprintf(buf,"Input device data"); break;
  case SIGWINCH  : sprintf(buf,"Window size changes"); break;
  case SIGPWR    : sprintf(buf,"Power-failure"); break;
  case SIGUSR1   : sprintf(buf,"User defined signal 1"); break;
  case SIGUSR2   : sprintf(buf,"User defined signal 2"); break;
  case SIGPROF   : sprintf(buf,"Profiling timer expired"); break;
  case SIGDANGER : sprintf(buf,"Paging space low"); break;
  case SIGVTALRM : sprintf(buf,"Virtual timer expired"); break;
  case SIGMIGRATE: sprintf(buf,"migrate process       "); break;
  case SIGPRE    : sprintf(buf,"programming exception  "); break;
  case SIGVIRT   : sprintf(buf,"AIX virtual time alarm "); break;
  case SIGALRM1  : sprintf(buf,"m:n condition variables "); break;
  case SIGWAITING: sprintf(buf,"m:n scheduling "); break;
  case 59        : sprintf(buf,"Secure attention"); break;
  case SIGGRANT  : sprintf(buf,"Monitor mode granted"); break;
  case SIGRETRACT: sprintf(buf,"Monitor mode retracted"); break;
  case SIGSOUND  : sprintf(buf,"Sound completed"); break;
  case 63        : sprintf(buf,"Signal 63"); break;
  default        : sprintf(buf,"unknown signal"); break;
  }
}


/**********************************************************
 */
void print_wait_status(int ReturnedValue)
{
  int mysig;
  int Ip;

  if (WIFSTOPPED(ReturnedValue)) {
    mysig = WSTOPSIG(ReturnedValue);
    if (mysig == SIGTRAP) {    /* check further if it's the debugger's own breakpoint */
      /* Ip = ptrace(PT_READ_GPR, debuggee_pid, (int *) IAR, 0, 0);  
      if (Ip != bp.next_addr && Ip !=bp.btarg_I) */
      psignal(mysig,"Debuggee stopped by signal");
    } else {
      psignal(mysig,"Debuggee stopped by signal");
    }
  } else if (WIFEXITED(ReturnedValue))
    printf("Debuggee exited with rc %d\n",  WEXITSTATUS(ReturnedValue));
  else if (WIFSIGNALED(ReturnedValue))
    psignal(WTERMSIG(ReturnedValue),"Debuggee terminated by signal"); 
}

void print_exec_status(int status)
{
  switch (status) {
  case EACCES: 
    printf("The exec file is not an ordinary file or does not have execution permission, or the path is incorrect.\n"); 
    break;

  case ENOEXEC: printf("The magic number or the header in the exec file is not valid is damaged or is incorrect.\n");
    break;

  case ETXTBSY: printf("The exec file is a shared text file that is currently open for writing by some process.\n");
    break;

  case ENOMEM: printf("The process requires more memory than is allowed by the system-imposed maximum, the MAXMEM compiler option.\n");
    break;

  case E2BIG: printf("The number of bytes in the  argument list is greater than the system-imposed limit, the NCARGS parameter value in the sys/param.h file.\n");
    break;
  
  case EFAULT: printf("The Path, ArgumentV, or EnviromentPointer parameter points outside of the process address space.\n");
    break;

  case EPERM: printf("The SetUserID or SetGroupID mode bit is set on the process image file. The translation tables at the server or client do not allow translation of this user or group ID.\n");
    break;

  case EIO: printf("An input/output (I/O) error occurred during the operation.\n");
    break;

  case ELOOP: printf("Too many symbolic links in translating the Path.\n");
    break;

  case ENAMETOOLONG: printf("Path name exceeded 255 characters or an entire path name exceeded 1023 characters.\n");
    break;

  case ENOENT: printf("A component of the path prefix does not exist, or symbolic link for a nonexistent file, or path name is null.\n");
    break;

  case ENOTDIR: printf("A component of the path prefix is not a directory.\n");
    break;

  case ESTALE: printf("The virtual file system for root or current directory of the process has been unmounted.\n");
    break;
  
  case ETIMEDOUT: printf("The connection timed out.\n");
    break;

  default:  printf("Unknown reason.\n");
  }

}








