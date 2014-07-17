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

/**
 * OSRConstants defines constants used for on-stack-replacement mapping,
 * VM scope descriptor, and pseudo bytecodes.
 */
public final class OSRConstants {

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
  public static final long OFFSET_MASK = 0x000000000007ffffL;
  public static final long OSRI_MASK = 0x00000001fff80000L;
  public static final long BCI_MASK = 0x0001fffe00000000L;
  public static final long IEI_MASK = 0xfffe000000000000L;
  public static final int OFFSET_SHIFT = 0;
  public static final int OSRI_SHIFT = 19;
  public static final int BCI_SHIFT = 33;
  public static final int IEI_SHIFT = 49;

  /*
   * signifies there is no map entry for this machine code offset
   */
  public static final int NO_OSR_ENTRY = (int) (OSRI_MASK >>> OSRI_SHIFT);
  public static final int INVALID_BCI = (int) (BCI_MASK >>> BCI_SHIFT);
  public static final int INVALID_IEI = (int) (IEI_MASK >>> IEI_SHIFT);

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
  public static final int NEXT_BIT = 0x80000000;
  /* kind of element */
  public static final int KIND_MASK = 0x00400000;
  public static final int KIND_SHIFT = 22;
  /* type code */
  public static final int TCODE_MASK = 0x00380000;
  public static final int TCODE_SHIFT = 19;
  /* number */
  public static final int NUM_MASK = 0x0007fff8;
  public static final int NUM_SHIFT = 3;
  /* value type */
  public static final int VTYPE_MASK = 0x00000007;
  public static final int VTYPE_SHIFT = 0;

  ////////////////////////////////////////////
  //  Part II  constants used when extract VM scope descriptor
  ////////////////////////////////////////////
  /** Used to indicate the kind of element is a local variable */
  public static final boolean LOCAL = false;
  /** Used to indicate the kind of element is from the operand stack */
  public static final boolean STACK = true;

  /* the type code of the element, used in osr map encoding. */
  public static final byte INT = 0;
  public static final byte HIGH_64BIT = 1; //used to store the high bits of a 64-bit value
  public static final byte LONG = 2;
  public static final byte FLOAT = 3;
  public static final byte DOUBLE = 4;
  public static final byte RET_ADDR = 5;
  public static final byte REF = 6;
  public static final byte WORD = 7;

  /* value type */
  public static final byte ICONST = 0;
  public static final byte ACONST = 3;
  public static final byte LCONST = 4;
  public static final byte PHYREG = 1;
  public static final byte SPILL = 2;

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

  public static final int PSEUDO_LoadIntConst = 1;
  public static final int PSEUDO_LoadLongConst = 2;
  public static final int PSEUDO_LoadFloatConst = 3;
  public static final int PSEUDO_LoadDoubleConst = 4;
  public static final int PSEUDO_LoadRetAddrConst = 5;
  public static final int PSEUDO_LoadWordConst = 6;

  public static final int PSEUDO_InvokeStatic = 7;
  public static final int PSEUDO_CheckCast = 8;

  /* followed by compiled method ID */
  public static final int PSEUDO_InvokeCompiledMethod = 9;

  /* indicate local initialization ends, for baselike compiler */
  public static final int PSEUDO_ParamInitEnd = 10;

  /* special method id for PSEUDO_InvokeStatic, target must be listed here */
  public static final int GETREFAT = 0;  // ObjectHolder.getRefAt
  public static final int CLEANREFS = 1;  // ObjectHolder.cleanRefAt

  public static final byte ReturnAddressTypeCode = (byte) 'R';
  public static final byte WordTypeCode = (byte) 'W';  //'A'

  private OSRConstants() {
    // prevent instantiation
  }

}
