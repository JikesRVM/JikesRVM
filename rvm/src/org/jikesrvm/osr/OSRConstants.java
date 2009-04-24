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
package org.jikesrvm.osr;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.ClassLoaderConstants;

/**
 * OSRConstants defines constants used for on-stack-replacement mapping,
 * VM scope descriptor, and pseudo bytecodes.
 */
public interface OSRConstants extends SizeConstants, ClassLoaderConstants {

  ////////////////////////////////////////////
  // Part I  constants used for opt compilation with OSR points
  ///////////////////////////////////////////

  /* use the similar encoding as GC map.
   *
   * An entry (long) containts the following data:
   *    m : a machine code offset ( in bytes )
   *    o : an index into the OSR maps array
   *    b : the bytecode index of the instruction
   *    i : index into the inline encoding
   *
   * (HIGH)  iiii iiii iiii iiib bbbb bbbb bbbb bbbo
   * (LOW)   oooo oooo oooo ommm mmmm mmmm mmmm mmmm
   */
  long OFFSET_MASK = 0x000000000007ffffL;
  long OSRI_MASK = 0x00000001fff80000L;
  long BCI_MASK = 0x0001fffe00000000L;
  long IEI_MASK = 0xfffe000000000000L;
  int OFFSET_SHIFT = 0;
  int OSRI_SHIFT = 19;
  int BCI_SHIFT = 33;
  int IEI_SHIFT = 49;

  /*
   * signifies there is no map entry for this machine code offset
   */
  int NO_OSR_ENTRY = (int) (OSRI_MASK >>> OSRI_SHIFT);
  int INVALID_BCI = (int) (BCI_MASK >>> BCI_SHIFT);
  int INVALID_IEI = (int) (IEI_MASK >>> IEI_SHIFT);

  /* array of OSR maps.
   *
   *  1. Each map has one or more ints as following:
   *     REG_REF (WORD1 WORD2) (WORD1 WORD2)
   *
   *  2. The first in REG_REF is a bit map of registers that
   *     contain references ( the MSB is used for chaining ).
   *     Use 'getRegBitPosition' to find the position for
   *     a register.
   *
   *  3. The following words are tuple of two words:
   *     (WORD 1)  Nxxx xxxx xxkt ttnn nnnn nnnn nnnn nnvv
   *     (WORD 2)  int bits value
   *
   *     N : next tuple is valid.
   *     x : unused bits
   *     k : kind of this element ( LOCAL/STACK )
   *     t : type of this element ( see type code )
   *     n : the number this element ( e.g, L0, S1 ), which is 16-bit
   *         as required by JVM spec.
   *     v : the type of the next word
 */

  /* bit pattern for the "Next" bit in the OSR maps array
  */
  int NEXT_BIT = 0x80000000;
  /* kind of element */ int KIND_MASK = 0x00400000;
  int KIND_SHIFT = 22;
  /* type code */
  int TCODE_MASK = 0x00380000;
  int TCODE_SHIFT = 19;
  /* number */
  int NUM_MASK = 0x0007fff8;
  int NUM_SHIFT = 3;
  /* value type */
  int VTYPE_MASK = 0x00000007;
  int VTYPE_SHIFT = 0;

  ////////////////////////////////////////////
  //  Part II  constants used when extract VM scope descriptor
  ////////////////////////////////////////////
  /** Used to indicate the kind of element is a local variable */
  boolean LOCAL = false;
  /** Used to indicate the kind of element is from the operand stack */
  boolean STACK = true;

  /* the type code of the element, used in osr map encoding. */
  byte INT = 0;
  byte HIGH_64BIT = 1; //used to store the high bits of a 64-bit value
  byte LONG = 2;
  byte FLOAT = 3;
  byte DOUBLE = 4;
  byte RET_ADDR = 5;
  byte REF = 6;
  byte WORD = 7;

  /* value type */
  byte ICONST = 0;
  byte ACONST = 3;
  byte LCONST = 4;
  byte PHYREG = 1;
  byte SPILL = 2;

  /////////////////////////////////////////////////
  // Part III  Pseudo bytecodes
  ////////////////////////////////////////////////
  /* We define instruction as follows: JBC_impdep1,
   *   PSEUDO_instruction, values
   *
   * LoadConst takes encoded value and push on the top of stack.
   * Compiler should construct constant values, and use Magic to
   * convert INT to FLOAT, or LONG to DOUBLE.
   *
   * LoadRetAddrConst followed by offset from the PC of this instruction.
   *
   * InvokeStatic encoded with index into JTOC.
   *
   * All value are signed except LoadRetAddrConst
   *
   * LoadIntConst :             B, V0, V1, V2, V3
   * LoadLongConst:             B, H0, H1, H2, H3, L0, L1, L2, L3
   * LoadWordConst: on 32-bit:  B, V0, V1, V2, V3
   * LoadWordConst: on 64-bit:  B, H0, H1, H2, H3, L0, L1, L2, L3
   * LoadFloatConst:            B, V0, V1, V2, V3
   * LoadDoubleConst:           B, H0, H1, H2, H3, L0, L1, L2, L3
   * LoadRetAddrConst:          B, V0, V1, V2, V3
   *
   * All value are unsigned:
   *
   * InvokeStatic :             B, L0, L1, L2, L3
   *
   * The change of stack is pretty obvious.
   */

  int PSEUDO_LoadIntConst = 1;
  int PSEUDO_LoadLongConst = 2;
  int PSEUDO_LoadFloatConst = 3;
  int PSEUDO_LoadDoubleConst = 4;
  int PSEUDO_LoadRetAddrConst = 5;
  int PSEUDO_LoadWordConst = 6;

  int PSEUDO_InvokeStatic = 7;
  int PSEUDO_CheckCast = 8;

  /* followed by compiled method ID */
  int PSEUDO_InvokeCompiledMethod = 9;

  /* indicate local initialization ends, for baselike compiler */
  int PSEUDO_ParamInitEnd = 10;

  /* special method id for PSEUDO_InvokeStatic, target must be listed here */
  int GETREFAT = 0;  // ObjectHolder.getRefAt
  int CLEANREFS = 1;  // ObjectHolder.cleanRefAt

  byte ReturnAddressTypeCode = (byte) 'R';
  byte WordTypeCode = (byte) 'W';  //'A'
}
