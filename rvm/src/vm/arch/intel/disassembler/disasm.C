/*
 * (C) Copyright IBM Corp. 2001, 2005
 */
//$Id$

/** 
 * @author Ton Ngo
 */
#include <stdlib.h>
#include <string.h>
#include "disasm.h"

extern "C" PARLIST *Disassemble(char *pHexBuffer,
                                size_t HexBuffer_sz,
                                char *pMnemonicBuffer,
                                size_t MnemonicBuffer_sz,
                                char *pOperandBuffer,
                                size_t OperandBuffer_sz,
                                char *pDataBuffer, // INPUT
                                int  *fInvalid,
                                PARLIST *disassemblyp)
{
  memset(disassemblyp,0,sizeof(PARLIST));
  disassemblyp->hbuffer   = (UCHAR*) pHexBuffer;
  disassemblyp->hbuffer_sz= HexBuffer_sz;
  disassemblyp->mbuffer   = (UCHAR*) pMnemonicBuffer;
  disassemblyp->mbuffer_sz= MnemonicBuffer_sz;
  disassemblyp->ibuffer   = (UCHAR*) pOperandBuffer;
  disassemblyp->ibuffer_sz= OperandBuffer_sz;
  disassemblyp->iptr      = (UCHAR*) pDataBuffer;
  disassemblyp->instr_EIP = (ULONG)-1;  /* EIP value @ this instruction       */

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
  disassemblyp->flagbits = 7;   /* flag bits (32 bit) -- We do not use
                                 * 16-bit code in Jikes RVM. */

  p__DisAsm( disassemblyp, 1 );

  pHexBuffer[2* disassemblyp->retleng] = '\0';

  if (disassemblyp->rettype == illegtype)
    {
    *fInvalid = 1;
    }
  else
    {
    *fInvalid = 0;
    }
  return disassemblyp;
  }

