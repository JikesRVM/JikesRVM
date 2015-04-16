/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
#ifndef _disasm_hpp
#define _disasm_hpp

#include "ihnpdsm.h"

/*---------------------------------------------------------------------------*/
/* Disassemble one instruction                                               */
/* Wrapper around primary ihnpdsm.cpp functionality.                         */
/*---------------------------------------------------------------------------*/
extern "C" PARLIST *Disassemble(
  char *pHexBuffer,             /* output: hex dump of instruction bytes  */
  size_t HexBuffer_sz,
  char *pMnemonicBuffer,        /* output: instruction mnemonic string    */
  size_t MnemonicBuffer_sz,
  char *pOperandBuffer,         /* output: operands string                */
  size_t OperandBuffer_sz,
  char *pDataBuffer,            /* input:  buffer of bytes to disassemble */
  int  *fInvalid,               /* output: disassembly successful: 1 or 0 */
  PARLIST *disassemblyp    /* output: Where the other data will be returned */
    );

#endif
