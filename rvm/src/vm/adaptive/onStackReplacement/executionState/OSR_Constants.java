/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
import com.ibm.JikesRVM.VM_SizeConstants;
/**
 * OSR_Constants defines constants used for on-stack-replacement mapping,
 * JVM scope descriptor, and pseudo bytecodes.
 *
 * @author Feng Qian
 * @date 19 Dec 2002
 */
public interface OSR_Constants extends VM_SizeConstants {

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
  public static final long OFFSET_MASK  = 0x000000000007ffffL;
  public static final long OSRI_MASK    = 0x00000001fff80000L;
  public static final long BCI_MASK     = 0x0001fffe00000000L;
  public static final long IEI_MASK     = 0xfffe000000000000L;
  public static final int  OFFSET_SHIFT = 0;
  public static final int  OSRI_SHIFT   = 19;
  public static final int  BCI_SHIFT    = 33;
  public static final int  IEI_SHIFT    = 49;

  /*
   * signifies there is no map entry for this machine code offset
   */
  public static final int NO_OSR_ENTRY  = (int)(OSRI_MASK >>> OSRI_SHIFT);
  public static final int INVALID_BCI   = (int)(BCI_MASK >>> BCI_SHIFT);
  public static final int INVALID_IEI   = (int)(IEI_MASK >>> IEI_SHIFT);

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
   * 
   */
  
  /* bit pattern for the "Next" bit in the OSR maps array
   */
  public static final int NEXT_BIT   = 0x80000000;
  /* kind of element */
  public static final int KIND_MASK  = 0x00400000;
  public static final int KIND_SHIFT = 22;
  /* type code */
  public static final int TCODE_MASK = 0x00380000;
  public static final int TCODE_SHIFT= 19;
  /* number */
  public static final int NUM_MASK   = 0x0007fff8;
  public static final int NUM_SHIFT  = 3;
  /* value type */
  public static final int VTYPE_MASK = 0x00000007;
  public static final int VTYPE_SHIFT= 0;



  ////////////////////////////////////////////
  //  Part II  constants used when extract JVM scope descriptor
  ////////////////////////////////////////////
  /* the kind of element */
  public static final int LOCAL      = 0;
  public static final int STACK      = 1;

  /* the type code of the element, used in osr map encoding. */
  public static final int INT        = 0;
  public static final int HIGH_64BIT = 1; //used to store the high bits of a 64-bit value
  public static final int LONG       = 2;
  public static final int FLOAT      = 3;
  public static final int DOUBLE     = 4;
  public static final int RET_ADDR   = 5;
  public static final int REF        = 6;
  public static final int WORD       = 7;

  /* value type */
  public static final int ICONST     = 0;
  public static final int ACONST     = 3;
  public static final int LCONST     = 4;
  public static final int PHYREG     = 1;
  public static final int SPILL      = 2;


  /////////////////////////////////////////////////
  // Part III  Pseudo bytecodes
  ////////////////////////////////////////////////
  /* We define instruction as follows: JBC_impdep1,
   *   PSEUDO_instruction, values
   *
   * LoadConst takes encoded value and push on the top of stack.
   * Compiler should construct constant values, and use VM_Magic to
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
  public static final int GETREFAT   = 0;  // OSR_ObjectHolder.getRefAt
  public static final int CLEANREFS  = 1;  // OSR_ObjectHolder.cleanRefAt

  public static final byte ClassTypeCode   = (byte)'L';
  public static final byte ArrayTypeCode   = (byte)'[';
  public static final byte VoidTypeCode    = (byte)'V';
  public static final byte BooleanTypeCode = (byte)'Z';
  public static final byte ByteTypeCode    = (byte)'B';
  public static final byte ShortTypeCode   = (byte)'S';
  public static final byte IntTypeCode     = (byte)'I';
  public static final byte LongTypeCode    = (byte)'J';
  public static final byte FloatTypeCode   = (byte)'F';
  public static final byte DoubleTypeCode  = (byte)'D';
  public static final byte CharTypeCode    = (byte)'C';
  public static final byte ReturnAddressTypeCode = (byte)'R';
  public static final byte WordTypeCode    = (byte)'W';  //'A'
}
