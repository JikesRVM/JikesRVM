/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * @author Ton Ngo
 */
#ifndef _disasm_hpp
#define _disasm_hpp

#include "ihnpdsm.h"

/*---------------------------------------------------------------------------*/
/* Disassemble one instruction                                               */
/* Wrapper around primary ihnpdsm.cpp functionality.                         */
/*---------------------------------------------------------------------------*/
extern "C" PARLIST *Disassemble(
  char *pHexBuffer,                /* output: hex dump of instruction bytes  */
  char *pMnemonicBuffer,           /* output: instruction mnemonic string    */
  char *pOperandBuffer,            /* output: operands string                */
  char *pDataBuffer,               /* input:  buffer of bytes to disassemble */
  int  *fInvalid,                  /* output: disassembly successful: 1 or 0 */
  int   WordSize);                 /* input:  Segment word size: 2 or 4      */

#endif
