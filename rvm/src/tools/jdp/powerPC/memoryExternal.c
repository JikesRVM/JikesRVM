/*
 * (C) Copyright IBM Corp. 2001
 */
/* AIX native implementation to access memory of a process
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
#include <sys/debug.h>

typedef char TEXT_BUF[1000];
static struct tbtable *getTracebackTable(int debuggee_pid, int codep);
static int getTracebackName(int debuggee_pid, int codep, char *nameBuffer);


/************************************************************************
 * Class:     Platform
 * Method:    read
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Platform_readmem1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr)
{
  jint memdata;

  memdata = ptrace(PT_READ_I, debuggee_pid, (int *) addr, 0, 0);
  if (memdata==-1 && errno==EIO) {
    printf("CAUTION, address not accessible: 0x%08x\n", addr);
  }
  return memdata;
}

/************************************************************************
 * Class:     Platform
 * Method:    readblock
 * Signature: (II)[I
 */
JNIEXPORT jintArray JNICALL Java_Platform_readmemblock1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr, jint count)
{
  int memdata, i, error_occur;
  jintArray jmems;
  jint *mems;
  jboolean isCopy;

  /* allocate integer array in Java and get a local copy */
  jmems = (*env)->NewIntArray(env, count);
  mems = (*env)->GetIntArrayElements(env, jmems, &isCopy);

  /* read the memory data into the local array copy */
  error_occur = 0;
  for (i=0; i<count; i++) {
    mems[i] =  ptrace(PT_READ_I, debuggee_pid, (int *) (addr+i*4), 0, 0); 
    if (mems[i]==-1 && errno==EIO) 
      error_occur = (addr+i*4);
  }

  if (error_occur!=0)
    printf("CAUTION, block address not accessible: 0x%08x\n", addr);

  /* release the array copy back to Java */
  (*env)->ReleaseIntArrayElements(env, jmems, mems, 0);

  return jmems;  

}


/************************************************************************
 * Class:     Platform
 * Method:    write
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_Platform_writemem1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr, jint value)
{
  ptrace(PT_WRITE_I, debuggee_pid,  (int *) addr, value, 0);   
}

/************************************************************************
 * Class:     Platform
 * Method:    setbp
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_Platform_setbp1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr, jint BKPT_INSTRUCTION)
{
  ptrace(PT_WRITE_I, debuggee_pid, (int *) addr, BKPT_INSTRUCTION,0);
}


/************************************************************************
 * Class:     Platform
 * Method:    clearbp
 * Signature: (II)V
 * Maybe clear breakpoint should just be done in Java
 * if on all machine we just write back the old instruction
 */
JNIEXPORT void JNICALL Java_Platform_clearbp1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr, jint instr)
{


}


/************************************************************************
 * Class:     Platform
 * Method:    branchTarget1
 * Signature: (III)I
 * (code borrowed from Derek's cdb)
 * Return the target of a branch or conditional branch instruction.
 * Argument: the instruction
 *           the instruction address
 * Returned: the target (or -1 if instruction is not a branch instruction)
 * Note:     the "immediate <<" and "immediate >>" shift operations must be done
 *           as separate statements to ensure sign extension
 */
JNIEXPORT jint JNICALL Java_Platform_branchTarget1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint instruction, jint address)
{
  int word; 

  switch (instruction & 0xfc000000)  {
  case 18 << 26: {        /* unconditional branch absolute */
    int absolute = instruction & 2;
    int immediate = instruction << 6;
    immediate >>= 6;
    immediate &= ~3;
    return absolute ? immediate : (address + immediate);
  }

  case 16 << 26: {        /* conditional branch absolute */
    int absolute = instruction & 2;
    int immediate = instruction << 16;
    immediate >>= 16;
    immediate &= ~3;
    return absolute ? immediate : (address + immediate);
  }

  case 19 << 26: {
    switch (instruction & 0x000007fe) {
    case 16 << 1:  {      /* conditional branch through link register */
      word = ptrace(PT_READ_GPR, debuggee_pid, (int *) 131, 0, 0);
      return word & ~3;    
    }      
    
    case 528 << 1: {      /* conditional branch through count register */
      word = ptrace(PT_READ_GPR, debuggee_pid, (int *) 132, 0, 0);
      return word & ~3;   
    }
    }

  }
  }
 
  return -1;      /* no branch */

}

/************************************************************************
 *
 * Class:     Platform
 * Method:    getNativeProcedureName1
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Platform_getNativeProcedureName1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint instructionAddress)
{
  int rc;
  TEXT_BUF nameBuffer;
  jstring j_buf;

  /* copy the name from the end of the instruction block */
  rc = getTracebackName(debuggee_pid, instructionAddress, nameBuffer);  
  
  if (rc==0) {
    j_buf = (*env)->NewStringUTF(env, nameBuffer);      /* copy the string back to java space */
    return j_buf;
  } else {
    return NULL;
  }

}


/**
 * Get name of a function.
 * (code borrowed from Derek Lieber's CDB)
 * Taken:    address of any instruction in the function
 * Returned: name of function (0 means "unknown")
 */
int getTracebackName(int debuggee_pid, int codep, char *name)
{
  struct tbtable *table = getTracebackTable(debuggee_pid, codep);
  char    *p     = (char *)&table->tb_ext;
  
  if (table->tb.fixedparms || table->tb.floatparms)
    p += sizeof(int);
  
  if (table->tb.has_tboff)
    p += sizeof(int);
  
  if (table->tb.int_hndl)
    p += sizeof(int);
  
  if (table->tb.has_ctl) {
    int n = *(int *)p;
    p += sizeof(int);
    while (n--)
      p += sizeof(int);
  }

  if (table->tb.name_present)  {
    int len = *(short *)p;
    p += sizeof(short);
    memcpy(name, p, len);
    name[len] = 0;
    return 0;
  }
   
  return -1;
}


/**
 * Get a function's traceback table.
 * (code borrowed from Derek Lieber's CDB)
 * Taken:    address of any instruction in the function
 * Returned: the table
 * Notes:    Every compiled function has a "traceback" table at the end of its
 *           code. The table is marked by a word-aligned word of zeros in the code.
 *           The table has a `fixed' part and an `extended' part. The contents of the latter
 *           depend on flags in the former.
 */
static struct tbtable *getTracebackTable(int debuggee_pid, int codep)
{
  static union {
    struct tbtable table;
    int     data[256];
  } cache;
  static int cache_codep;
  
  if (codep != cache_codep)  { 
    /* find traceback table, searching on word boundaries */
    int index = sizeof(cache.data)/sizeof(cache.data[0]);
    int scanp = codep &= ~3;
    for (;;)  {
      if (index == sizeof(cache.data)/sizeof(cache.data[0]))  {
	/* getTextBlock(scanp, cache.data, sizeof(cache.data)); */
	ptrace(PT_READ_BLOCK, debuggee_pid, (int *)scanp,  sizeof(cache.data), cache.data);
	index = 0;
      }
      scanp += 4;
      if (cache.data[index++] == 0) 
	break;                                   /* here's the marker */
    }
    
    /* load it, along with possible `extended' sections (assumption: total tablesize <= 4k) */
    /* getTextBlock(scanp, &cache.data, sizeof(cache.data)); */
    ptrace(PT_READ_BLOCK, debuggee_pid, (int *)scanp, sizeof(cache.data), cache.data);
    cache_codep = codep;
  }
  
  return &cache.table;

}


