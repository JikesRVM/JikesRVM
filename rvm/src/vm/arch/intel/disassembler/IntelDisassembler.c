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
#define INSTRUCTION_BUFFER_SIZE 2048

int debugflag = 0;

/* to hold the disassembled instructions before passing back to Java */
char instrBuffer[INSTRUCTION_BUFFER_SIZE];  

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
