/*
 * (C) Copyright IBM Corp. 2001
 */
/*$Id$
 * AIX native implementation to access the registers of a process
 * under debugging control, based on ptrace()
 * @author Ton Ngo
 */

#include <jni.h>
#include "Platform.h"
#include <stdio.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/reg.h>
#include <sys/ptrace.h>
#include <sys/ldr.h>
#include <sys/errno.h>  

#define GPR_COUNT  32
#define FPR_COUNT  32
#define SPR_COUNT   3     /* there are 9 or more, but we don't care for all*/

extern int jvmFP;
extern int jvmSP;
extern int jvmTI;
extern int shiftTP;

/***************************************************************
 * Class:     Platform
 * Method:    readreg1
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Platform_readreg1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint regnum)
{
  int regdata = ptrace(PT_READ_GPR, debuggee_pid, (int *) regnum, 0, 0); 
  return regdata;
}

/***************************************************************
 * Class:     Platform
 * Method:    readfreg1
 * Signature: (I)F
 */
JNIEXPORT jfloat JNICALL Java_Platform_readfreg1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint regnum)
{
  double fregdata;

  if ((regnum>=FPR0) && (regnum<=FPR31)){
    ptrace(PT_READ_FPR, debuggee_pid, (int *) (&fregdata), regnum, 0); 
    return fregdata;
  } else 
    return -1.0;

}


/***************************************************************
 * Take a register number and a value
 * Class:     Platform
 * Method:    writereg1
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_Platform_writereg1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint regnum, jint value)

{
  ptrace(PT_WRITE_GPR, debuggee_pid, (int *) regnum, value, 0); 
}


/***************************************************************
 * Take a register number and a value
 * Class:     Platform
 * Method:    writefreg1
 * Signature: (IF)V
 */
JNIEXPORT void JNICALL Java_Platform_writefreg1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint regnum, jfloat value)

{
  if ((regnum>=FPR0) && (regnum<=FPR31)) {
/** ptrace(PT_WRITE_FPR, debuggee_pid, (int *) value, regnum, 0); **/
    jdouble dvalue = value;
    ptrace(PT_WRITE_FPR, debuggee_pid, (int *) &dvalue, regnum, 0);
  }
}

/************************************************************************
 * Class:     Platform
 * Method:    currentIP1
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Platform_currentIP1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  jint regdata = ptrace(PT_READ_GPR, debuggee_pid, (int *) IAR, 0, 0);
  return regdata;
}
 
/************************************************************************
 * Return the link address for this stack frame, which is stored at 1 word above the FP
 * At the top stack frame, the link address is 0
 * Class:     Platform
 * Method:    currentFP1
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Platform_currentFP1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  jint regdata = ptrace(PT_READ_GPR, debuggee_pid, (int *) jvmFP, 0, 0);
  return regdata;

}
 
/************************************************************************
 * Return the link address for this stack frame, which is stored at 1 word above the FP
 * At the top stack frame, the link address is 0
 * Class:     Platform
 * Method:    currentSP1
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Platform_currentSP1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  jint regdata = ptrace(PT_READ_GPR, debuggee_pid, (int *) jvmSP, 0, 0);
  return regdata;

}


/***************************************************************
 * Given a VM thread ID and an array of system thread ID,
 * find the system thread executing the VM thread and 
 * return the GPR registers
 * If none, return null
 * Class:     Platform
 * Method:    getSystemThreadGPR1
 * Signature: (II[I)[I
 */
JNIEXPORT jintArray JNICALL Java_Platform_getSystemThreadGPR1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint threadID, jintArray systemThreadId_array)
{
  int i, systemThreadId;
  int regbuf[GPR_COUNT];
  jintArray jregs;
  jint *regs;
  jboolean isCopy;

  if (threadID!=0) {
    systemThreadId = getSystemThreadForVMThreadID(env, obj, threadID, 
    						systemThreadId_array);
    
    if (systemThreadId!=-1) {
      ptrace(PTT_READ_GPRS, systemThreadId, regbuf, 0, 0); 
      /* Allocate integer array in Java and get a local copy */
      jregs = (*env)->NewIntArray(env, GPR_COUNT);
      regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);
    
      for (i=0; i<GPR_COUNT; i++) {
    	regs[i] = regbuf[i];
      }
    
      /* release the array copy back to Java */
      (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
      return jregs;
    } else {
      return NULL;
    }
  }

  /* if threadpointer is 0, the RVM data structure is not 
   * set up yet, so go straight to the current register */
  else {

    /* Allocate integer array in Java and get a local copy */
    jregs = (*env)->NewIntArray(env, GPR_COUNT);
    regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);

    for (i=0; i<GPR_COUNT; i++) {
      regs[i] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0);
    }

    /* release the array copy back to Java */
    (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
    return jregs;
  }

}

/***************************************************************
 * Given a VM thread ID and an array of system thread ID,
 * find the system thread executing the VM thread and 
 * return the floating point registers
 * If none, return null
 * Class:     Platform
 * Method:    getSystemThreadFPR1
 * Signature: (II[I)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_Platform_getSystemThreadFPR1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint threadID, jintArray systemThreadId_array)
{
  int i, systemThreadId, regnum, rc;
  double fregbuf[FPR_COUNT], regdata;
  jdoubleArray jregs;
  jdouble *regs;
  jboolean isCopy;

  if (threadID!=0) {
    systemThreadId = getSystemThreadForVMThreadID(env, obj, threadID, 
					      systemThreadId_array);    
    if (systemThreadId!=-1) {
      rc = ptrace(PTT_READ_FPRS, systemThreadId, (int *)fregbuf, 0, 0); 
      /* Allocate integer array in Java and get a local copy */
      jregs = (*env)->NewDoubleArray(env, FPR_COUNT);
      regs = (*env)->GetDoubleArrayElements(env, jregs, &isCopy);
      
      for (i=0; i<FPR_COUNT; i++) {
    	regs[i] = fregbuf[i];
      }

      /* release the array copy back to Java */
      (*env)->ReleaseDoubleArrayElements(env, jregs, regs, 0);
      return jregs;
    } else {
      return NULL;
    }
  }

  /* if threadpointer is 0, the RVM data structure is not 
   * set up yet, so go straight to the current register */
  else {

    /* Allocate integer array in Java and get a local copy */
    jregs = (*env)->NewDoubleArray(env, FPR_COUNT);
    regs = (*env)->GetDoubleArrayElements(env, jregs, &isCopy);

    for (i=0; i<FPR_COUNT; i++) {      
      regnum = i+FPR0;
/**   rc = ptrace(PT_READ_FPR, debuggee_pid, (int *) regdata, regnum, 0); **/
      rc = ptrace(PT_READ_FPR, debuggee_pid, (int *) &regdata, regnum, 0);
      regs[i] = regdata;
    }

    /* release the array copy back to Java */
    (*env)->ReleaseDoubleArrayElements(env, jregs, regs, 0);
    return jregs;
  }

}

/***************************************************************
 * Given a VM thread ID and an array of system thread ID,
 * find the system thread executing the VM thread and 
 * return the special purpose registers
 * If none, return null
 * Class:     Platform
 * Method:    getSystemThreadSPR1
 * Signature: (II[I)[I
 */
JNIEXPORT jintArray JNICALL Java_Platform_getSystemThreadSPR1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint threadID, jintArray systemThreadId_array)
{
  int i, systemThreadId;
  struct ptsprs th_sreg;
  jintArray jregs;
  jint *regs;
  jboolean isCopy;

  if (threadID!=0) {
    int systemThreadId = getSystemThreadForVMThreadID(env, obj, threadID, 
						    systemThreadId_array);
    if (systemThreadId!=-1) {
      ptrace(PTT_READ_SPRS, systemThreadId, (int *) &th_sreg, 0, 0); 
    
       /* Allocate integer array in Java and get a local copy */
      jregs = (*env)->NewIntArray(env, SPR_COUNT);
      regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);
      regs[0] = th_sreg.pt_iar;         /* instruction address register */
      regs[1] = th_sreg.pt_lr;          /* link register                */
      regs[2] = th_sreg.pt_cr;          /* condition register           */
      /* regs[3] = th_sreg.pt_msr;         machine state register       */
      /* regs[4] = th_sreg.pt_ctr;         count register               */
      /* regs[5] = th_sreg.pt_xer;         fixed point exception        */
    
      /* release the array copy back to Java */
      (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
      return jregs;
    
    } else {
      return NULL;
    }
  }

  /* if threadpointer is 0, the RVM data structure is not 
   * set up yet, so go straight to the current register */
  else {

    /* Allocate integer array in Java and get a local copy */
    jregs = (*env)->NewIntArray(env, SPR_COUNT);
    regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);
    i = IAR; regs[0] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0);
    i = LR ; regs[1] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0);
    i = CR ; regs[2] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0); 
    /* i = MSR; regs[3] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0); */
    /* i = CTR; regs[4] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0); */
    /* i = XER; regs[5] = ptrace(PT_READ_GPR, debuggee_pid, (int *) i, 0, 0); */

    /* release the array copy back to Java */
    (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
    return jregs;
  }

}

/* Local procedure for looking up system thread:
 * Given:
 *   - a value expected in the thead register jvmTI
 *   - an array of system thread ID
 * Return:  the ID of the system thread that holds 
 *          the matching value in its jvmTI
 */
int getSystemThreadForVMThreadID
  (JNIEnv *env, jobject obj, jint threadID, jintArray systemThreadId_array) 
{
  int j;
  int regbuf[GPR_COUNT];
  jint *sth_array, sth_size;
  jboolean isCopy;

  /* check each system thread to see if it holds this thread pointer */
  sth_size = (*env)->GetArrayLength(env, systemThreadId_array);
  sth_array = (*env)->GetIntArrayElements(env, systemThreadId_array, &isCopy);
  for (j=0; j<sth_size; j++) {
    ptrace(PTT_READ_GPRS, sth_array[j], regbuf, 0, 0); 
    /* Does the thread pointer register match? */
    if (registerToTPIndex(regbuf[jvmTI])==threadID) {
      return sth_array[j];
    }
  }
  return -1;
}


/* convert the value in the thread register into the thread index */
int registerToTPIndex(int valueTP)
{
  return valueTP >> shiftTP;
}
