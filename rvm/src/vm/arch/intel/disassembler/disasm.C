/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/** 
 * @author Ton Ngo
 */
#include <stdlib.h>
#include <string.h>
#include "disasm.h"

extern "C" PARLIST *Disassemble(char *pHexBuffer,
                     char *pMnemonicBuffer,
                     char *pOperandBuffer,
                     char *pDataBuffer,
                     int  *fInvalid,
                     int   WordSize)
  {
  static PARLIST disassembly;
  memset(&disassembly,0,sizeof(PARLIST));
  disassembly.hbuffer   = (UCHAR*) pHexBuffer;
  disassembly.mbuffer   = (UCHAR*) pMnemonicBuffer;
  disassembly.ibuffer   = (UCHAR*) pOperandBuffer;
  disassembly.iptr      = (UCHAR*) pDataBuffer;
  disassembly.instr_EIP = (ULONG)-1;  /* EIP value @ this instruction       */

  /***********************************************************************/
  /*                bit 2 (1) => MASM format decode                      */
  /*                      (0) => ASM/86 format decode                    */
  /*                                                                     */
  /*               NOTE: if the ASM/86 mnemonic table is                 */
  /*                     omitted, this bit is ignored.                   */
  /*                                                                     */
  /*                bit 1 (1) => ESC orders are decoded                  */
  /*                             287/387 orders                          */
  /*                      (0) => decoded as "ESC"                        */
  /*                                                                     */
  /*                bit 0 (1) => do 386 32-bit decode                    */
  /*                      (0) => do 16-bit decode                        */
  /***********************************************************************/
  if (WordSize != 4)
    {
    disassembly.flagbits = 6;           /* flag bits (16 bit) */
    }
  else
    {
    disassembly.flagbits = 7;           /* flag bits (32 bit) */
    } /* endif */

  p__DisAsm( &disassembly, 1 );

  pHexBuffer[2*disassembly.retleng] = '\0';

  if (disassembly.rettype == illegtype)
    {
    *fInvalid = 1;
    }
  else
    {
    *fInvalid = 0;
    }
  return &disassembly;
  }

