/*
 * (C) Copyright IBM Corp. 2001
 */
/*****************************************************************
 *  Implement the JNI functions that are called without the JNI Environment
 * These are called directly from C so the bottom stack frame is not a
 * Java frame.
 *
 *
 * Ton Ngo, Steve Smith 18/10/00
 */

#ifdef _AIX43
#include </usr/include/unistd.h>
extern "C" int sched_yield();
#endif

#include "jni.h"
#include "jniExternal.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#ifdef __linux__
#include <signal.h>
#endif

extern char *bootFilename;
extern unsigned smallHeapSize;
extern unsigned largeHeapSize;
extern unsigned nurserySize;
extern unsigned permanentHeapSize;
extern int lib_verbose;
extern char **JavaArgs;
extern int    JavaArgc;
extern FILE *SysTraceFile;
extern FILE *SysErrorFile;
extern int SysTraceFd;
int pthread_id;
extern char *me;
extern int VmToc;
extern int AttachThreadRequestedOffset;
extern pthread_t vm_pthreadid;

extern "C" int createJVM(int);
extern "C" jint JNI_GetDefaultJavaVMInitArgs(void *);
extern "C" jint JNI_CreateJavaVM(JavaVM **, JNIEnv **, void *); 
extern "C" jint JNI_GetCreatedJavaVMs(JavaVM **, jsize, jsize *);

/* These functions should be accessed through the JavaVM structure */
extern "C" jint DestroyJavaVM (JavaVM *);
extern "C" jint AttachCurrentThread (JavaVM *, JNIEnv **, void *);
extern "C" jint DetachCurrentThread (JavaVM *);
extern "C" jint GetEnv (JavaVM *, void **, jint);


void addPair(JavaVM_ *, int, JNIEnv *);
void removePair(JavaVM_ *, int);
void removeAll(JavaVM_ *);
JNIEnv * lookupPair(JavaVM_ *, int);


/* Make sure there is enough room for the normal command line argument passed to the VM
 * -arg[JNI_ARG_INDEX] is saved for the address of the external JNIEnv structure that the 
 *  shadow VM_Thread needs to link to;  this will be supplied in JNI_CreateJavaVM   
 * -arg[PID_ARG_INDEX] is saved for the pthread ID for the VM to associate a VM_Processor with
 * -the last arg[9] is a null marker 
 */
#define JNI_ARG_INDEX 5
#define PID_ARG_INDEX 6
char *defaultStartUpProgramName[8] = {"-X:vmClasses=/u/tango/rvmBuild/Jalapeno.classes",
				      "-X:processors=1",
				      "-classpath", ".",
				      "VM_JNIStartUp", 
				       NULL, NULL, 
				       NULL};

int trace = 0;
int functionTableInitialized = 0;
struct JNIInvokeInterface_ externalJNIFunctions;
pthread_t theMainPthread;
struct JavaVM_ *thevm;

/* a block of parameters to pass from C to Java to make a JNI service request */
struct requestParameters {
  int requestType;      /* value: ATTACHREQUEST, DETACHREQUEST, DESTROYREQUEST */
  JNIEnv ** env;        /* address where to store the JNIEnv to return */
  int pid;              /* the ID of the pthread making the request */
};

/* matching value is used in JNIServiceThread.java (make sure to update both) */
#define ATTACHREQUEST  0
#define DETACHREQUEST  1
#define DESTROYREQUEST 2 

/* pthread mutex for synchronizing access to the VM_Scheduler.attachThreadRequested flag */
pthread_mutex_t JNIserviceMutex;


/* two co-indexed tables to keep track of pthread/Java thread pairs 
 * that have been created through CreateJavaVM and AttachCurrentThread 
 */
int *pthreadIdTable;     /* the pthread ID from thread_self() */
int *jniEnvTable;        /* the JNIEnvironment of the matching Java thread, used to make JNI calls */
int MAX_THREADS = 8192;  /* the size of the two arrays, should be the same as VM_Scheduler.MAX_THREADS */


/*****************************************************************
 * Initialize the arg structure with the default values from 
 * the libvm.a library 
 *
 */
jint JNI_GetDefaultJavaVMInitArgs(void *vm_args1) {

  JDK1_1InitArgs *vm_args = (JDK1_1InitArgs *) vm_args1;

  /* Now find a reasonable value for large object heap */
  if (largeHeapSize == 0) {
    largeHeapSize = smallHeapSize >> 2;
    if (largeHeapSize < (10 * 1024 * 1024))
      largeHeapSize = 10 * 1024 * 1024;
  }

  vm_args -> version           = JNI_VERSION_1_1;
  vm_args -> classpath         = ".:~/rvmBuild/Jalapeno.classes";  // This doesn't seem to get picked up
  vm_args -> verbose           = lib_verbose;
  vm_args -> smallHeapSize     = smallHeapSize;     
  vm_args -> largeHeapSize     = largeHeapSize;     
  vm_args -> nurserySize       = nurserySize;       
  vm_args -> permanentHeapSize = permanentHeapSize;
  vm_args -> sysLogFile        = 0;
  vm_args -> bootFilename      = bootFilename;      
  vm_args -> JavaArgs          = defaultStartUpProgramName;
  

  me = "JNI_CreateJavaVM";   // for error messages

  if (trace)
    printf("JNI_GetDefaultJavaVMInitArgs: initialized, largeHeapSize \n"); 
  return 0;
}


/*****************************************************************
 * Create a Jalapeno Virtual Machine
 * Taken:     a structure holding the options for creating the VM 
 * Returned:  fill in the vm structure 
 *            fill in the address for the new JNI environment structure
 *            a Java main thread that is attached to the caller via the JNIEnv 
 */
jint JNI_CreateJavaVM(JavaVM **pvm, JNIEnv **penv, void *vm_args1) {
#if (defined __linux__) && (!defined __linuxsmp__)
     fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
     exit(-1);
#else
  JNIEnv *returnEnv = 0;
  JDK1_1InitArgs *vm_args = (JDK1_1InitArgs *) vm_args1;
  sigset_t input_set, output_set;       /* set up SIGCOND for Jalapeno native thread */
  int rc, i;


  /* Has the VM already been created? */
  if (thevm!=NULL) {
    /* already created, so the pthread ID should match */
    returnEnv = lookupPair(thevm, pthread_self());
    if (returnEnv==NULL) {
      fprintf(SysTraceFile, "JNI_CreateJavaVM Error:  the VM was already created by another pthread \n");
      return -1;
    } else {
      if (trace)
	fprintf(SysTraceFile, "JNI_CreateJavaVM: already created, use existing handles \n");
      *pvm = thevm;
      *penv = returnEnv;
      return 0;
    }      
  }

			   
  /* copy the arguments for creating the VM */
  lib_verbose        = vm_args -> verbose;           
  smallHeapSize      = vm_args -> smallHeapSize;     
  largeHeapSize      = vm_args -> largeHeapSize;     
  nurserySize	     = vm_args -> nurserySize;       
  permanentHeapSize  = vm_args -> permanentHeapSize; 
  char* sysLogFile   = vm_args -> sysLogFile;        
  bootFilename       = vm_args -> bootFilename;      
  JavaArgs	     = vm_args -> JavaArgs;          

  pthread_id         = pthread_self();

  /* open the log file */
  if (sysLogFile) {
    FILE* ftmp = fopen(sysLogFile, "a");
    if (!ftmp) {
  	 fprintf(SysTraceFile, "JNI_CreateJavaVM: can't open SysTraceFile \"%s\"\n", sysLogFile);
    } else {
  	 fprintf(SysTraceFile, "JNI_CreateJavaVM: redirecting sysWrites to \"%s\"\n", sysLogFile);
  	 SysTraceFile = ftmp;
#ifdef __linux__
  	 SysTraceFd   = ftmp->_fileno;
#else
  	 SysTraceFd   = ftmp->_file;
#endif
    }	
  } else {
    SysTraceFile = stderr;
    SysTraceFd   = 2;
  }

  SysErrorFile = stderr;


  /* check the boot image file */
  if (!bootFilename) {
#ifdef RVM_BOOTIMAGE
    bootFilename = RVM_BOOTIMAGE;
#endif
  }
  
  if (!bootFilename) {
    printf("JNI_CreateJavaVM: ERROR, please specify name of boot image file in JDK1_1InitArgs.bootFilename\n");
    return -1;
  } else {
    if (trace)
      fprintf(SysTraceFile, "JNI_CreateJavaVM: Jalapeno boot image is %s\n", bootFilename);
  }

  /* Set up the special command line argument for Jalapeno to start */
  char *envAddressArg = (char *) malloc(32);
  sprintf(envAddressArg, "-jni %d", &returnEnv);
  JavaArgs[JNI_ARG_INDEX] = envAddressArg;

  char *pthreadIdArg = (char *) malloc(32);
  sprintf(pthreadIdArg, "-pid %d", pthread_id);
  JavaArgs[PID_ARG_INDEX] = pthreadIdArg;

  if (trace)
    fprintf(SysTraceFile, "JNI_CreateJavaVM: start up jniEnv = %s, pid = %s\n", 
	    JavaArgs[5], JavaArgs[6]);

  /* count the number of args */
  i=0;
  while (JavaArgs[i]!=NULL) 
    i++;
  JavaArgc = i;

  /* Fill in the structure JNIInvokeInterface_ 
   */
  if (functionTableInitialized == 0) {
    externalJNIFunctions.DestroyJavaVM = DestroyJavaVM;
    externalJNIFunctions.AttachCurrentThread = AttachCurrentThread;
    externalJNIFunctions.DetachCurrentThread = DetachCurrentThread;
    externalJNIFunctions.GetEnv = GetEnv;
    functionTableInitialized = 1;
  }

  /* create the pthread mutex for synchronizing AttachCurrentThread */
  pthread_mutex_init(&JNIserviceMutex, NULL);

  /* create and fill in a JavaVM structure to return to  the caller 
   */
  struct JavaVM_ *newvm = (struct JavaVM_ *) malloc (sizeof(struct JavaVM_));
  thevm = newvm;
  theMainPthread = pthread_self();
  newvm->functions = &externalJNIFunctions;
  newvm->pthreadIDTable = (int *) malloc (sizeof(int) * MAX_THREADS); 
  newvm->jniEnvTable    = (JNIEnv **) malloc (sizeof(JNIEnv **) * MAX_THREADS); 
  for (i=0; i<MAX_THREADS; i++) {
    newvm->pthreadIDTable[i] = -1;
    newvm->jniEnvTable[i] = NULL;
  }

  /* store into the user's given address the pointer to the new JavaVM structure */
  *pvm = newvm;

  /*
   * block the CONT signal:  this is necessary for the mechanism in Jalapeno
   * to put idle VM pthreads in pthread wait.   This makes the signal reach this
   * pthread only when then pthread does a sigwai().  
   */
  sigemptyset(&input_set);
  sigaddset(&input_set, SIGCONT);
#ifdef __linux__
   rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
#else
  rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
#endif
  if (rc!=0) {
    fprintf(SysTraceFile, "JNI_CreateJavaVM: fail to block SIGCONT for idle thread \n"); 
    return -1;
  }

  /* create the VM, specify 1 to start in a separate new thread */
  rc = createJVM(1);

  if (rc!=0) {
    fprintf(SysTraceFile, "JNI_CreateJavaVM: fail to create VM\n");
    return -1;    
  }

  /* wait for the VM to finish booting and for the shadow VM_Thread to come up */
  while (returnEnv==0) {
#if (_AIX43 || __linux__)
    sched_yield();
#else
    pthread_yield();
#endif    
  }

  if (trace)
    fprintf(SysTraceFile, "JNI_CreateJavaVM: got JNIEnv = %8X \n", returnEnv);

  /* fill in the first table lookup entry for this pthread */
  addPair(newvm, pthread_self(), returnEnv);
  
  *penv = returnEnv;
#endif
  return 0;

}



/*****************************************************************
 *  Get the VM handle if the Jalapeno VM has been created
 * parameter pvm the address of a buffer to store the the VM handle into
 * parameter num the size of this buffer
 * parameter numVM the address where to store the number of VM handles returned
 * return  only one VM is supported in a process, so at most one handle is returned  
 *
 */
jint JNI_GetCreatedJavaVMs(JavaVM **pvm, jsize bufferSize, jsize *numVM) {

  if (trace)
    fprintf(SysTraceFile, "C lib: JNI_GetCreatedJavaVMs \n"); 

  if (thevm!=NULL) {
    pvm[0] = thevm;
    *numVM = 1;
    return 0;
  } else {    
    if (trace)
      fprintf(SysTraceFile, "C lib: Java VM has not been created yet \n"); 
    return -1;
  }

}



/*****************************************************************
 *  Terminate the main Java thread and wait for the VM to exit from
 * all pthreads that it owns
 * NOTE:  this implementation incomplete in the sense that the caller 
 * is not expected to create another VM, but the functionality 
 * is similar to the JDK1.1's implementation.  The JDK1.2 implementation
 * only extends slightly by allowing any external pthread to call
 * DestroyJavaVM, not just the one that created the VM.
 *   When this call returns, all pthreads that belong to the VM have
 * exited.  Therefore, this call will block if some other external pthreads
 * are still attached to the VM.  The intention is to force the user 
 * program to explicitly detaches the Java threads and releases system 
 * resources as necessary.
 *   Parameter  the JavaVM handle
 *   Return     -1 in all cases to indicate that recreating the VM is not supported
 */
jint DestroyJavaVM(JavaVM *vm) {
#if (defined __linux__) && (!defined __linuxsmp__)
  fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
  exit(-1);
#else
  
  JNIEnv *penv;
  struct requestParameters detachParms;
  /* check if this pthread is the one that created the VM */
  penv = lookupPair(vm, pthread_self());
  if (penv==NULL) {
    if (trace)
      printf("DestroyJavaVM: pthread %d is not allowed to destroy the VM, only the main pthread that called JNI_CreateJavaVM\n", pthread_self());
    return -1;   /* not the main pthread */
  }  

  /* lock request flag so no more request will be service from this point */
  pthread_mutex_lock(&JNIserviceMutex);
  
  /* flush all entries from the pid/JNIEnv cache */
  removeAll(vm);


  /* tell the Java main thread to terminate */
  /* set up the parameter to pass to Java */
  detachParms.requestType = DESTROYREQUEST;
  detachParms.env = &(penv);
  detachParms.pid = pthread_self();

  /*
   *  Set the attachThreadedRequest flag in the boot image
   *
   */
  
  char **flag = (char **)((char *)VmToc + AttachThreadRequestedOffset);
  if (trace)
    fprintf(SysTraceFile, "C lib: JTOC 0x%8X, offset 0x%8X\n", VmToc, AttachThreadRequestedOffset); 

  if (*flag)    {
    fprintf(SysTraceFile, "C lib: ERROR, multiple DestroyJavaVM/DetachCurrentThread in progress\n");
    return -1;
  } else {
    if (trace) {
      fprintf(SysTraceFile, "C lib: DestroyJavaVM detach request 0x%8X posted at 0x%8X, waiting for a thread switch\n",
	      (&detachParms), flag);
      fprintf(SysTraceFile, "C lib: DestroyJavaVM parms, env = 0x%8X, pid = %d\n", detachParms.env, detachParms.pid);
    }

    *flag = (char *) (&detachParms);
  }

  /* Join with the Jalapeno main pthread:  block until it terminates
   */
  if (trace) {
    fprintf(SysTraceFile, "C lib: DestroyJavaVM waiting for the vm to exit \n");
  }
  pthread_join(vm_pthreadid, NULL);

  /* Jalapeno VM has been taken down, unlock and 
   * discard the pthread mutex for JNI service */
  pthread_mutex_unlock (&JNIserviceMutex);
  pthread_mutex_destroy(&JNIserviceMutex);


  /* Clean up remaining pointers */
  thevm = NULL;

  /* return an error code anyway since we don't support restarting the VM */
#endif
  return -1;

}


/*****************************************************************
 * Set up a Java thread in Jalapeno and associate it with the calling
 * pthread.  
 * Parameter:  vm
 * Parameter:  penv
 * Parameter:  args
 * Return 0 if successful, -1 if not
 * On return the JNI environment will be stored in penv
 */
/* jint AttachCurrentThread (JavaVM *vm, void ** penv, void * args) {  */
jint AttachCurrentThread (JavaVM *vm, JNIEnv ** penv, void * args) { 
#if (defined __linux__) && (!defined __linuxsmp__)
  fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
  exit(-1);
#else

  JNIEnv *returnEnv = 0;  
  struct requestParameters attachParms;
  
  if (trace)
    fprintf(SysTraceFile, "C lib: AttachCurrentThread started \n"); 

  returnEnv = lookupPair(vm, pthread_self());
  if (returnEnv != NULL) {
    if (trace)
      fprintf(SysTraceFile, "C lib: this pthread has already attached, use the existing handle for JNIEnv\n");
    *penv = returnEnv;
    return 0;
  }

  /* set up the parameter to pass to Java */
  /* zero out for synch later */
  /* *penv = 0;               */
  returnEnv = 0;
  attachParms.requestType = ATTACHREQUEST;
  attachParms.env         = &returnEnv;
  attachParms.pid         = pthread_self();
      

  /* Only one attach is serviced by Jalapeno at a time
   * so serialize all concurrent requests from the pthreads
   */
  pthread_mutex_lock(&JNIserviceMutex);


  /*
   *  Set the attachThreadedRequest flag in the boot image
   *
   */
  
  char **flag = (char **)((char *)VmToc + AttachThreadRequestedOffset);
  if (trace)
    fprintf(SysTraceFile, "C lib: JTOC 0x%8X, offset 0x%8X\n", VmToc, AttachThreadRequestedOffset); 

  if (*flag)    {
    fprintf(SysTraceFile, "C lib: ERROR, multiple AttachCurrentThread in progress\n");
    return -1;
  } else {
    if (trace)
      fprintf(SysTraceFile, "C lib: AttachCurrentThread request 0x%8X posted at 0x%8X, waiting for a thread switch\n",
	      (&attachParms), flag);
    *flag = (char *) (&attachParms);
  }

  /* wait for the Java thread to start, 
   * spin in pthread yield 
   */
  while (*flag != NULL) {
#if (_AIX43 || __linux__)
    sched_yield();
#else
    pthread_yield();
#endif
  }

  /* Java thread has started, release the lock so other pthread can attach */
  pthread_mutex_unlock(&JNIserviceMutex);

  if (trace)
    fprintf(SysTraceFile, "C lib: Java thread has started, waiting for JNIEnv\n");
  
  /* wait for the JNIEnv to be ready from the associated Java thread 
   * spin in pthread yield
   */
  while (returnEnv==0) {
#if (_AIX43 || __linux__)
    sched_yield();
#else
    pthread_yield();
#endif
  }

  if (trace)
    fprintf(SysTraceFile, "C lib: got JNIEnv 0x%8X, returning to thread\n", *penv);

  /* check if the VM has any error attaching */
  if (returnEnv==((void*) -1)) {
    return -1;
  } else {
    /* fill in the first table lookup entry for this pthread */
    addPair(vm, pthread_self(), returnEnv);
    *penv = returnEnv;
    return 0;
  }
#endif

}



/*****************************************************************
 * Detach the Java thread current attached to this pthread
 * The Java thread will execute its normal termination sequence
 *
 */
jint DetachCurrentThread(JavaVM *vm) { 
#if (defined __linux__) && (!defined __linuxsmp__)
  fprintf(stderr, "vm: Unsupported operation (no linux pthreads)\n");
  exit(-1);
#else
  JNIEnv *penv;
  struct requestParameters detachParms;

  /* printf("C lib: DetachCurrentThread\n"); */

  /* if pthread is the one that called JNICreateJavaVM, then it cannot 
   * detach, but must use DestroyJavaVM instead
   */
  if (theMainPthread == pthread_self()) {
    if (trace)
      printf("DetachCurrentThread: pthread %d is attached to the Java main thread, must use DestroyJavaVM instead\n", pthread_self());
    return -1;
  }

  /* get the JNIEnv associated with this pthread */
  penv = lookupPair(vm, pthread_self());
  if (penv==NULL) {
    if (trace)
      printf("DetachCurrentThread: pthread %d has not been attached to the VM\n", pthread_self());
    return -1;   /* thread not attached */
  }  

  /* printf("DetachCurrentThread: external JNIenv = 0x%8X \n", penv); */

  /* set up the parameter to pass to Java */
  detachParms.requestType = DETACHREQUEST;
  detachParms.env = &(penv);
  detachParms.pid = pthread_self();
  
  /* Only one attach is serviced by Jalapeno at a time
   * so serialize all concurrent requests from the pthreads
   */
  pthread_mutex_lock(&JNIserviceMutex);

  /*
   *  Set the attachThreadedRequest flag in the boot image
   *
   */
  
  char **flag = (char **)((char *)VmToc + AttachThreadRequestedOffset);
  if (trace)
    fprintf(SysTraceFile, "C lib: JTOC 0x%8X, offset 0x%8X\n", VmToc, AttachThreadRequestedOffset); 

  if (*flag)    {
    fprintf(SysTraceFile, "C lib: ERROR, multiple DetachCurrentThread in progress\n");
    return -1;
  } else {
    if (trace) {
      fprintf(SysTraceFile, "C lib: DetachCurrentThread request 0x%8X posted at 0x%8X, waiting for a thread switch\n",
	      (&detachParms), flag);
      fprintf(SysTraceFile, "C lib: DetachCurrentThread parms, env = 0x%8X, pid = %d\n", detachParms.env, detachParms.pid);
    }

    *flag = (char *) (&detachParms);
  }

  /* wait for the JNI service thread to respond, 
   * spin in pthread yield 
   */
  while (*flag != NULL) {
#if _AIX43
    sched_yield();
#else
    pthread_yield();
#endif
    /* if (trace)
       fprintf(SysTraceFile, "0x%8X  ", *flag); */
  }

  /* JNI thread has replied, release the lock so other pthread can attach/detach */
  pthread_mutex_unlock(&JNIserviceMutex);

  if (trace)
    fprintf(SysTraceFile, "C lib: Java thread being terminated \n");


  /* finally, remove the cache entry */
  removePair(vm, pthread_self());
#endif
  return 0;
}



/*****************************************************************
 *  Look up to see if the pthread has an existing JNIEnv from a previous
 * call to AttachCurrentThread.  
 * Parameter the JavaVM handle
 * Parameter address where to store the JNIEnv handle
 * Parameter a constant indicating the Java 1.1 or 1.2 version
 * Return  
 */
jint GetEnv(JavaVM *vm, void **penv, jint version) { 
  JNIEnv *returnEnv = 0;  

  if (trace)
    fprintf(SysTraceFile, "C lib: GetEnv \n"); 

  /* Java 1.2 is not supported yet */
  if (version == JNI_VERSION_1_2)
    return JNI_EVERSION;

  /* look up current list */
  returnEnv = lookupPair(vm, pthread_self());
  if (returnEnv == NULL) {
    if (trace)
      fprintf(SysTraceFile, "C lib: this pthread has not attached to the VM yet\n");
    return JNI_EDETACHED;
  } else {
    *penv = returnEnv;
    return JNI_OK;
  }

}



/*************************************************************
 *  Simple cache of PthreadID and JNIEnv pair so that repeated
 * calls to CreateJavaVM and AttachCurrentThread will return
 * the same handle created initially
 *
 */
void addPair(JavaVM_ *vm, int pid, JNIEnv *jniEnv) {
  
  int i;
  for (i=0; i<MAX_THREADS; i++) {
    if (vm->pthreadIDTable[i] == -1)
      break;
  }
  if (trace)
    printf("Adding entry %d for vm 0x%8X:  pid %d, jnienv 0x%8x\n", i, vm, pid, jniEnv);
  vm->pthreadIDTable[i] = pid;
  vm->jniEnvTable[i] = jniEnv;
  
}

void removePair(JavaVM_ *vm, int pid) {
  int i;

  for (i=0; i<MAX_THREADS; i++) {
    if (vm->pthreadIDTable[i]==-1)
      continue;    
    if (vm->pthreadIDTable[i] == pid) {
      if (trace)
	printf("Removing entry %d, pid %d, jnienv 0x%8x\n", i,
	       vm->pthreadIDTable[i],  vm->jniEnvTable[i]);
      vm->pthreadIDTable[i] = -1;
      vm->jniEnvTable[i] = NULL;
      return;
    }
  }
  
  if (trace)
    printf("No entry found for pid %d\n", pid);
  return;

}

void removeAll(JavaVM_ *vm) {
  int i;

  for (i=0; i<MAX_THREADS; i++) {
    vm->pthreadIDTable[i]=-1;
    vm->jniEnvTable[i] = NULL;
  }
  
  if (trace)
    printf("pid/JNIEnv cache flushed %d\n");
  return;
}


JNIEnv * lookupPair(JavaVM_ *vm, int pid) {
  int i;

  for (i=0; i<MAX_THREADS; i++) {
    if (vm->pthreadIDTable[i]==-1) {
      continue;
    }
    if (vm->pthreadIDTable[i] == pid) {
      if (trace)
	printf("Look up:  found for pid %d, jnienv 0x%8X\n", vm->pthreadIDTable[i], vm->jniEnvTable[i]); 
      return vm->jniEnvTable[i];
    }
  }
  if (trace)
    printf("Look up: found no entry for pid %d\n", pid);
  return NULL;

}

