/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*********************************************************
 * Interface to the native disassembler for Intel:
 *     disasm.C
 *     ihnpdsm.C
 *
 * @author Ton Ngo  2/13/2001
 */


#include "IntelDisassembler.h"
#include "ihnpdsm.h"
/* #include "disasm.h" */
#define INSTRUCTION_BUFFER_SIZE 2048

int debugflag = 0;

/* to hold the disassembled instructions before passing back to Java */
char instrBuffer[INSTRUCTION_BUFFER_SIZE];  

/* to copy the register values for computing branch target */
jint regbuf[9];
jbyte instrbuf[256];
int branchAddr;
jboolean indirect;

/*---------------------------------------------------------------------------*/
/* Prototype for call to disassembler                                        */
/* Disassemble from a buffer, one instruction at a                           */
/* Wrapper around primary ihnpdsm.cpp functionality.                         */
/*---------------------------------------------------------------------------*/
PARLIST *Disassemble( 
  char *pHexBuffer,                /* output: hex dump of instruction bytes  */
  char *pMnemonicBuffer,           /* output: instruction mnemonic string    */
  char *pOperandBuffer,            /* output: operands string                */
  char *pDataBuffer,               /* input:  buffer of bytes to disassemble */
  int  *fInvalid,                  /* output: disassembly successful: 1 or 0 */
  int   WordSize);                 /* input:  Segment word size: 2 or 4      */


/*
 * Class:     IntelDisassembler
 * Method:    disasm
 * Signature: ([BII)Ljava/lang/String;
 */
/* JNIEXPORT void JNICALL Java_IntelDisassembler_disasm */
JNIEXPORT jstring JNICALL Java_IntelDisassembler_disasm
(JNIEnv *env, jclass cls, jbyteArray instr, jint count, jint address) {

  int Illegal = 0;
  char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256], AddrBuffer[256];
  int InstructionLength;
  size_t Index = 0;
  int WordSize = 4;   /* option is 2 or 4 */
  PARLIST *p;
  int i;
  int size, InstrCount, StopCount, spaceLeft;
  jbyte *buf;
  jstring returnMnemonic;

  size = (*env) -> GetArrayLength(env, instr);
  buf = (jbyte *) malloc(sizeof(jbyte) * size);
  (*env) -> GetByteArrayRegion(env, instr, 0, size, buf);

  /*
    printf(" size of jbyte = %d \n", sizeof(jbyte));
    printf(" get byte array of %d: \n", size);
    for (i = 0; i<size; i++) {
    printf(" 0x%02x", (unsigned char) buf[i]);
    }
    printf("\n");
  */
  

  /* DisassembleBuffer((char *) buf, size, 4);  */

  /*********************************************/
  /* do we disassemble all or only up to count */
  InstrCount = 0;
  if (count==0)
    StopCount = size;  /* set to size so that we disassemble all in the buffer */
  else 
    StopCount = count;

  /* printf("Disassemble %d instructions from address 0x%08X\n", StopCount, address); */

  spaceLeft = INSTRUCTION_BUFFER_SIZE;    /* reset the buffer */
  instrBuffer[0] = 0x00;

  for (i=0; i<256; i++)
      HexBuffer[0] = 0;

  while (!Illegal && Index < size && InstrCount < StopCount)   {
    p = Disassemble(HexBuffer,
                    MnemonicBuffer,
                    OperandBuffer,
                    (char *) (&buf[Index]),
                    &Illegal,
                    WordSize);
    InstructionLength = p->retleng;
    
    /* 
    printf("TANGO: InstrCount=%d, Index=%d, InstructionLength=%d, StopCount=%d\n", 
           InstrCount, Index, InstructionLength, StopCount);
    */

    if (!Illegal) {
      /* printf("%s %s ;       0x%s\n", MnemonicBuffer, OperandBuffer, HexBuffer); */
      /* accumulate the result in the buffer, making sure not to overflow */

      /*
      printf("disasm: offset=%x, imm=%x, type=%d, reg=%x", p->retoffset, p->retimmed, p->rettype, p->retreg); 
      printf("mod=%x, reg/op=%x, r/m=%x\n", p->retmod, p->retregop, p->retrm);
      */  

      sprintf(AddrBuffer, "0x%08X  ", (address+Index));
      strncat(instrBuffer, AddrBuffer, spaceLeft);
      spaceLeft -= strlen(AddrBuffer);

      strncat(instrBuffer, MnemonicBuffer, spaceLeft);  
      strncat(instrBuffer, " ", 1);  
      spaceLeft -= strlen(MnemonicBuffer)+1;

      strncat(instrBuffer, OperandBuffer, spaceLeft);  
      if (count >1) strncat(instrBuffer, "\n",1);
      spaceLeft -= strlen(MnemonicBuffer)+1;

      Index += InstructionLength;
      InstrCount++;
    } else {
      printf("Illegal instruction at index %d\n", Index);
    }
    
  }

  
  /* returnMnemonic = (*env) -> NewStringUTF(env, MnemonicBuffer); */
  returnMnemonic = (*env) -> NewStringUTF(env, instrBuffer);
  free(buf);

  return returnMnemonic;

}

/*
 * Class:     IntelDisassembler
 * Method:    instructionLength
 * Signature: ([BI)[I
 */
JNIEXPORT jbyteArray JNICALL Java_IntelDisassembler_instructionLength
(JNIEnv *env, jclass cls, jbyteArray instr) {
  int Illegal = 0;
  char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256], AddrBuffer[256];
  size_t Index = 0;
  int WordSize = 4;   /* option is 2 or 4 */
  PARLIST *p;
  int i, size, InstructionLength, InstrCount;
  jbyte *buf;
  jbyteArray jLengthArray;
  jbyte *lengthBuf;

  size = (*env) -> GetArrayLength(env, instr);
  buf = (jbyte *) malloc(sizeof(jbyte) * size);
  (*env) -> GetByteArrayRegion(env, instr, 0, size, buf);

  /* create the array in Java to return the instruction length */
  jLengthArray = (*env) -> NewByteArray(env, size);
  lengthBuf = (jbyte *) malloc(sizeof(jbyte) * size);
  (*env) -> GetByteArrayRegion(env, jLengthArray, 0, size, lengthBuf);
  
  for (i=0; i<size; i++)
    lengthBuf[i] = 0;

  InstrCount = 0;
  while (!Illegal && Index < size)   {
    p = Disassemble(HexBuffer,
                    MnemonicBuffer,
                    OperandBuffer,
                    (char *) (&buf[Index]),
                    &Illegal,
                    WordSize);
    InstructionLength = p->retleng;
    if (!Illegal) {
      /* printf("instructionLength: %d = %d\n", InstrCount, InstructionLength); */
      lengthBuf[InstrCount++] = InstructionLength;
      Index += InstructionLength;
    } else {
      break;
    }
  }

  /* copy the result back to the Java array */
  (*env) -> SetByteArrayRegion(env, jLengthArray, 0, size, lengthBuf);
  
  free(buf);
  free(lengthBuf);

  return jLengthArray;
}



/*
 * Given the instruction and the registers for a call, jump, or jump conditional
 * instruction, compute the branch target
 * The register array include reg[0:7] in the order indexed by the instruction,
 * plus the current IP at reg[8]
 *
 * Class:     IntelDisassembler
 * Method:    getBranchTarget
 * Signature: ([B[I)I
 */
JNIEXPORT jint JNICALL Java_IntelDisassembler_getBranchTarget
  (JNIEnv *env, jclass cls, jbyteArray instr, jintArray regsArray){

  int Illegal = 0;
  char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256];
  int WordSize = 4;   /* option is 2 or 4 */
  PARLIST *p;
  int i;

  jbyte *buf;
  int size;

  /* get instruction */
  size = (*env) -> GetArrayLength(env, instr);
  buf = (jbyte *) malloc(sizeof(jbyte) * size);
  (*env) -> GetByteArrayRegion(env, instr, 0, size, buf);

  /* get registers (use static space) */
  (*env) -> GetIntArrayRegion(env, regsArray, 0, 9, regbuf);

  /* Run the disassembler to decode the instruction and compute the 
   * branch target
   */
  p = Disassemble(HexBuffer,
                  MnemonicBuffer,
                  OperandBuffer,
                  buf,
                  &Illegal,
                  WordSize);

  /*
  printf("branchTarget: %s, ", MnemonicBuffer);
  printf("offset=%x, imm=%x, type=%d, ", p->retoffset, p->retimmed, p->rettype); 
  printf("mod=%x, reg/op=%x, r/m=%x\n", p->retmod, p->retregop, p->retrm);
  */

  // compute the address and whether it's indirect depending on the addressing mode
  // NOTE: the address may be indirect, but we don't want to perform a memory read 
  // in this code because the disassembler is also used in the runtime, so we
  // need to return 2 pieces of info separately, the address and the indirect flag
  // The flag is queried in the procedure isLastAddressIndirect()
  switch (p->rettype) {

  case jreltype    : // relative to IP of next instruction
  case creltype    :  
    branchAddr = p->retoffset + regbuf[IP] + p->retleng;
    indirect = JNI_FALSE;
    break;

  case jnearmemtype:  /* mod = 0 */
  case cnearmemtype:  
    branchAddr = regbuf[p->retrm];
    indirect = JNI_TRUE;
    break;

  case jnearregtype:  
  case cnearregtype:  /* mod = 1, 2, 3 */
    if (p->retmod==3) {
      branchAddr = regbuf[p->retrm];
      indirect = JNI_FALSE;
    } else {
      branchAddr = p->retoffset + regbuf[p->retrm];
      indirect = JNI_TRUE;
    }
    break;

  case jfartype    :  
  case cfartype    :  
    printf("getBranchTarget: Stepping into far pointer not supported\n");
    branchAddr = -1;
    indirect = JNI_FALSE;    
    break;

  case jfarimmtype :  
  case cfarimmtype :  
    branchAddr = p->retimmed;
    indirect = JNI_FALSE;
    break;

  case retneartype:
    branchAddr = -2;
    indirect = JNI_FALSE;    
    break;

  case retfartype:
    printf("getBranchTarget: returning from far pointer not supported\n");
    branchAddr = -1;
    indirect = JNI_FALSE;    
    break;

  default: 

  }



  free(buf);

  if (!Illegal) {
    if (isCallOrJump(p)) {
      /* printf("getBranchTarget: legal branch instruction %08X\n", branchAddr); */
      return branchAddr;
    }
  } 

  return -1;

}

JNIEXPORT jboolean JNICALL Java_IntelDisassembler_isLastAddressIndirect
  (JNIEnv *env, jclass cls){
  // only valid from last call to getBranchTarget
  return indirect;
}


/**
 * Return true for any instruction that may cause a change in the 
 * instruction stream:  branch, call, return
 */
int isCallOrJump(PARLIST *p) {
  if (p->rettype==jreltype ||    
      p->rettype==jnearmemtype ||
      p->rettype==jnearregtype  ||
      p->rettype==jfartype  ||    
      p->rettype==jfarimmtype ||  
      p->rettype==creltype   ||   
      p->rettype==cnearmemtype || 
      p->rettype==cnearregtype || 
      p->rettype==cfartype   ||   
      p->rettype==cfarimmtype ||
      p->rettype==retneartype ||
      p->rettype==retfartype)
    return 1;
  else
    return 0;

}
