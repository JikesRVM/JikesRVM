/*
 * (C) Copyright IBM Corp. 2001
 */
/*$Id$
 * Intel native implementation to access memory of a process
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
#include <sys/errno.h>  

/* offset into the debug registers in the kernel user area */
#define OFFSET_DEBUG_REG 252

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
  jclass eclass;

  memdata = ptrace(PTRACE_PEEKDATA, debuggee_pid, (int *) addr, 0);
  if (memdata==-1 && errno==EIO) {
    printf("CAUTION, address not accessible: 0x%08x\n", addr);
    eclass = (*env) -> FindClass(env, "java/lang/Exception");
    if (eclass!=NULL)
      (*env) -> ThrowNew(env, eclass, "Memory read, address not accessible");
  } else {
    return memdata;
  }
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
    mems[i] =  ptrace(PTRACE_PEEKDATA, debuggee_pid, (addr+i*4), 0); 
    if (mems[i]==-1 && errno!=0) 
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
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_Platform_writemem1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr, jint value)
{
  ptrace(PTRACE_POKEDATA, debuggee_pid,  addr, &value);   
}

/************************************************************************
 * Class:     Platform
 * Method:    setbp
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_Platform_setbp1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr)
{
  int offset, rc, bpIndex;
  long int user_data, user_control;

  /* printf("setbp1: 0x%8X \n", addr); */

  /* search for an available debug register */
  offset = OFFSET_DEBUG_REG + 28;
  user_control = ptrace(PTRACE_PEEKUSER, debuggee_pid, offset, 0);
  bpIndex = user_control & 0x000000FF;
  if (bpIndex == 0x000000FF) {
    printf("setbp1: Lintel only supports up to 4 breakpoints, specify index in the range of 0-3\n");
    return;
  } else {
    if ((bpIndex & 0x00000003) == 0)
      bpIndex = 0;
    else if ((bpIndex & 0x0000000C) == 0)
      bpIndex = 1;
    else if ((bpIndex & 0x00000030) == 0)
      bpIndex = 2;
    else if ((bpIndex & 0x000000C0) == 0)
      bpIndex = 3;    
    else {
      printf("setbp1: Lintel only supports up to 4 breakpoints, specify index in the range of 0-3\n");
      return;
    }
      
  }
  
  /* printf("setbp1: debug reg %d available \n", bpIndex); */

  /* set the debug register */
  offset = OFFSET_DEBUG_REG + bpIndex*4;
  rc = ptrace(PTRACE_POKEUSER, debuggee_pid, offset, addr);
  if (rc==-1) 
    printf ("setbp1: ERROR setting breakpoint at 0x%08lX, using debug reg offset %d\n", 
	    addr, offset);
  
  /* Set the control bits in DR7 for the 4 debug registers
   *  0000 0000 0000 0000 xx 0 001 10 0000 0011 
   *                                  ^^^^ ^^^^
   *                                  3322 1100
   */ 
  offset = OFFSET_DEBUG_REG + 28;
  user_control = user_control | 0x00000600 | (0x00000003 << (bpIndex*2));   
  rc = ptrace(PTRACE_POKEUSER, debuggee_pid, offset, user_control);
  if (rc==-1) 
    printf ("setbp1: ERROR setting control bit for debug register %d\n, 0x%08lX", 
	    bpIndex, user_control);


  /* Java_Platform_printbp1(env, obj, debuggee_pid); */
}






/************************************************************************
 * For Intel, check which of 4 debug registers has the address, clear it and
 * disable the control bits
 * Class:     Platform
 * Method:    clearbp
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_Platform_clearbp1
  (JNIEnv *env, jobject obj, jint debuggee_pid, jint addr)
{
  int offset, rc, bpIndex;
  long int user_data, user_control;

  /* verify address exists in the debug registers */
  offset = OFFSET_DEBUG_REG;
  for (bpIndex = 0; bpIndex < 4; bpIndex++) {
    user_data = ptrace(PTRACE_PEEKUSER, debuggee_pid, offset+(bpIndex*4), 0);
    if (user_data == addr) {
      rc = ptrace(PTRACE_POKEUSER, debuggee_pid, offset+(bpIndex*4), 0);
      if (rc==-1) { 
	printf ("clearbp1: ERROR clearing breakpoint at 0x%08lX\n", addr);
	return;
      }
      break;
    }
  }

  /* not found */
  if (bpIndex==4)
    return; 
  
  /* Clear the control bits in DR7 for the 4 debug registers
   *  0000 0000 0000 0000 xx 0 001 10 0000 0011 
   *                                  ^^^^ ^^^^
   *                                  3322 1100
   */  
  offset = OFFSET_DEBUG_REG + 28;
  user_control = ptrace(PTRACE_PEEKUSER, debuggee_pid, offset, 0);
  user_control = user_control & ~(0x00000003 << (bpIndex*2));
  rc = ptrace(PTRACE_POKEUSER, debuggee_pid, offset, user_control);
  if (rc==-1) 
    printf ("clearbp1: ERROR clearing control bit for debug register %d, 0x%08lX \n", 
	    bpIndex, user_control);

}

/************************************************************************
 * Class:     Platform
 * Method:    printbp1
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_Platform_printbp1
  (JNIEnv *env, jobject obj, jint debuggee_pid)  {
  int offset, rc;
  long int user_data;

  /* verify breakpoints and control bits */
  for (offset = OFFSET_DEBUG_REG; offset<=OFFSET_DEBUG_REG+28; offset+=4) {
    user_data = ptrace(PTRACE_PEEKUSER, debuggee_pid, offset, 0);
    printf("printbp1: debug reg at offset %d = 0x%08lX \n", offset, user_data);
  }
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

#ifdef asdf
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
#endif 

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

#ifdef asdf
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
#endif   
  printf("ERROR:  getTracebackName not implemented yet\n");
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
#ifdef asdf
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
#endif

