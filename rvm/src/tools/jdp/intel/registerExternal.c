/*
 * (C) Copyright IBM Corp. 2001
 */
/*$Id$ 
 * Intel Linux native implementation to access the registers of a process
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
/* #include <sys/ldr.h> */
#include <sys/errno.h>  

/* for register format */
#include <linux/user.h>

#define GPR_COUNT  8
#define FPR_COUNT  8
#define SPR_COUNT  3

/* Index for Intel registers (should be identical to registerConstants.java) 
   Note:  these indices are different from those defined in 
   /usr/include/asm/ptrace.h, which is for stack frame
*/

#define jvmEAX 0
#define jvmECX 1
#define jvmEDX 2
#define jvmEBX 3
#define jvmESP 4
#define jvmEBP 5
#define jvmESI 6
#define jvmEDI 7
#define jvmEIP 8


/* Intel FP register is 10 bytes */
#define INTEL_FPSIZE 10

/* RVM register convention, initialized in  */
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
  struct user_regs_struct gpr;
  int rc;

  rc = ptrace(PTRACE_GETREGS, debuggee_pid, 0, &gpr); 
  if (rc!=0) {
    printf("ERROR: readreg1 fails ptrace call for pid %d, errno = %d\n", 
	   debuggee_pid, errno);
    return 0;
  }

  /* Access the GPR structure (see /usr/include/asm/user.h)
     struct user_regs_struct {
     long ebx, ecx, edx, esi, edi, ebp, eax;
     unsigned short ds, __ds, es, __es;
     unsigned short fs, __fs, gs, __gs;
     long orig_eax, eip;
     unsigned short cs, __cs;
     long eflags, esp;
     unsigned short ss, __ss;
     };
  */
  switch (regnum) {
  case jvmEAX: return (int) (gpr.eax);
  case jvmECX: return (int) (gpr.ecx);
  case jvmEDX: return (int) (gpr.edx);
  case jvmEBX: return (int) (gpr.ebx);
  case jvmESP: return (int) (gpr.esp);
  case jvmEBP: return (int) (gpr.ebp);
  case jvmESI: return (int) (gpr.esi);
  case jvmEDI: return (int) (gpr.edi);
  case jvmEIP: return (int) (gpr.eip);
  default: printf("ERROR: unknown register number\n");
    return 0;
  }

}
/***************************************************************
 * Class:     Platform
 * Method:    fregtop1
 *            return the index of the top of the Intel FP stack
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Platform_fregtop1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int index, rc;
  struct user_i387_struct fpBuffer;
  
  rc = ptrace(PTRACE_GETFPREGS, debuggee_pid, 0, &fpBuffer); 
  if (rc!=0) {
    printf("ERROR: readfreg1 fails ptrace call for pid %d, errno = %d\n", 
	   debuggee_pid, errno);
    return 0;
  }
  index = (fpBuffer.swd & 0x00003800) >> 11;
  /* printf("  swd = 0x%X, index = %d\n", fpBuffer.swd, index); */

  return index;
}

/***************************************************************
 * Class:     Platform
 * Method:    fregtag1
 *            return the index of the top of the Intel FP stack
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Platform_fregtag1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  int tag, rc;
  struct user_i387_struct fpBuffer;
  
  rc = ptrace(PTRACE_GETFPREGS, debuggee_pid, 0, &fpBuffer); 
  if (rc!=0) {
    printf("ERROR: readfreg1 fails ptrace call for pid %d, errno = %d\n", 
	   debuggee_pid, errno);
    return 0;
  }
  tag = (fpBuffer.twd & 0x0000FFFF);
  /* printf("  twd = 0x%X \n",tag); */

  return tag;
}

/***************************************************************
 * Class:     Platform
 * Method:    readfreg1
 *            Intel Float Point reg is 10 bytes long, return the
 *            value as a Java byte array
 * Signature: (I)F
 */
JNIEXPORT jbyteArray JNICALL Java_Platform_readfreg1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint regnum)
{
  int i, j, rc;
  jboolean isCopy;
  jbyteArray jFreg;
  jbyte *Freg, *Fspace;
  struct user_i387_struct fpBuffer;
  
  if (regnum <0 || regnum>=8) {
    printf("ERROR:  FPR number should be 0-7, got %d\n", regnum);
    return NULL;
  }

  rc = ptrace(PTRACE_GETFPREGS, debuggee_pid, 0, &fpBuffer); 
  if (rc!=0) {
    printf("ERROR: readfreg1 fails ptrace call for pid %d, errno = %d\n", 
	   debuggee_pid, errno);
    return 0;
  }

  /* Access the FPR's (see /usr/include/asm/user.h)
     Return the 10-byte floating point value as a byte array
     struct user_i387_struct {
     long    cwd;
     long    swd;
     long    twd;
     long    fip;
     long    fcs;
     long    fdp;
     long    fds;
     long    st_space[20];   8*10 bytes for each FP-reg = 80 bytes
     };

  printf("  cwd = 0x%X\n", fpBuffer.cwd);
  printf("  swd = 0x%X\n", fpBuffer.swd);
  printf("  twd = 0x%X\n", fpBuffer.twd);
  printf("  fip = 0x%X\n", fpBuffer.fip);
  printf("  fcs = 0x%X\n", fpBuffer.fcs);
  printf("  fdp = 0x%X\n", fpBuffer.fdp);
  printf("  fds = 0x%X\n", fpBuffer.fds);
  printf("  fpstack = ");
  for (i=0; i<20; i++) 
    printf(" %X", fpBuffer.st_space[i]);
  printf("\n");
  */

  jFreg = (*env)->NewByteArray(env, INTEL_FPSIZE);
  Freg = (*env)->GetByteArrayElements(env, jFreg, &isCopy);

  Fspace = &(fpBuffer.st_space);
  /* printf(" address = 0x%8X, status word = 0x%8X \n", Fspace, fpBuffer.swd); */
  for (i=0; i<INTEL_FPSIZE; i++) {
   Freg[INTEL_FPSIZE- 1 - i] = Fspace[regnum*INTEL_FPSIZE + i];
  }
  
  (*env)->ReleaseByteArrayElements(env, jFreg, Freg, 0);
  return jFreg;

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
  printf("ERROR:  registerExternal.writereg1 not implemented yet\n");
  /* ptrace(PT_WRITE_GPR, debuggee_pid, (int *) regnum, value, 0);  */
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
  printf("ERROR:  registerExternal.writefreg1 not implemented yet\n");
  /* 
  if ((regnum>=FPR0) && (regnum<=FPR31)) {
    jdouble dvalue = value;
    ptrace(PT_WRITE_FPR, debuggee_pid, (int *) &dvalue, regnum, 0);
  }
  */
}

/************************************************************************
 * Class:     Platform
 * Method:    currentIP1
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Platform_currentIP1
  (JNIEnv *env, jobject obj, jint debuggee_pid)
{
  jint regdata = Java_Platform_readreg1(env, obj, debuggee_pid, jvmEIP);
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
  jint regdata = Java_Platform_readreg1(env, obj, debuggee_pid, jvmFP);
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
  jint regdata = Java_Platform_readreg1(env, obj, debuggee_pid, jvmSP);
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
  int i, rc, systemThreadId;
  struct user_regs_struct gpr;
  jintArray jregs;
  jint *regs;
  jboolean isCopy;

  rc = ptrace(PTRACE_GETREGS, debuggee_pid, 0, &gpr);
  if (rc!=0) {
    printf("ERROR: getSystemThreadGPR1 fails ptrace call for pid %d, errno = %d\n", debuggee_pid, errno); 
    return NULL;
  }
  /* Allocate integer array in Java and get a local copy */
  jregs = (*env)->NewIntArray(env, GPR_COUNT+SPR_COUNT);
  regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);
  
  regs[jvmEAX] =  (int) (gpr.eax);
  regs[jvmECX] =  (int) (gpr.ecx);
  regs[jvmEDX] =  (int) (gpr.edx);
  regs[jvmEBX] =  (int) (gpr.ebx);
  regs[jvmESP] =  (int) (gpr.esp);
  regs[jvmEBP] =  (int) (gpr.ebp);
  regs[jvmESI] =  (int) (gpr.esi);
  regs[jvmEDI] =  (int) (gpr.edi);
  regs[jvmEIP] =  (int) (gpr.eip);

  /* release the array copy back to Java */
  (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
  return jregs;
  

#ifdef asdf
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
#endif

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

  printf("ERROR:  getSystemThreadFPR1 not implemented yet\n");
  return NULL;

#ifdef asdf
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
#endif

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
  int i, rc, systemThreadId;
  struct user_regs_struct regbuffer;
  jintArray jregs;
  jint *regs;
  jboolean isCopy;

  printf("getSystemThreadSPR for thread %d\n", threadID);

  if (threadID!=0) {
    printf("ERROR: getSystemThreadForVMThreadID not implemented yet\n");
#ifdef asdf
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
#endif    
  }

  /* if threadpointer is 0, the RVM data structure is not 
   * set up yet, so go straight to the current register */
  else {

    /* Allocate integer array in Java and get a local copy */
    jregs = (*env)->NewIntArray(env, SPR_COUNT);
    regs = (*env)->GetIntArrayElements(env, jregs, &isCopy);

    /* read the registers */
    rc = ptrace(PTRACE_GETREGS, debuggee_pid, 0, &regbuffer);
    /* error */
    if (rc!=0) {
      printf("ERROR:  cannot read the special purpose registers \n");
      return NULL;
    }
    regs[0] = regbuffer.eip;
    /* regs[1] = 
       regs[2] =  */

    /* release the array copy back to Java */
    (*env)->ReleaseIntArrayElements(env, jregs, regs, 0);
    return jregs;
  }

}

#ifdef asdf

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

#endif

/* convert the value in the thread register into the thread index */
int registerToTPIndex(int valueTP)
{
  return valueTP >> shiftTP;
}
