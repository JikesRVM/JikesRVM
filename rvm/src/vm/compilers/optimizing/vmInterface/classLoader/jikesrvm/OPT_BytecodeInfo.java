/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.io.PrintStream;

/**
 * OPT_BytecodeInfo
 *
 * This class is used to provide bytecode information to the IR
 * 
 * @author Dave Streeter
 * @author Julian Dolby
 *
 * @see OPT_BC2IR
 */
public class OPT_BytecodeInfo {
  /**
   * The method whose bytecode this object represents.
   */
  final private VM_Method method;
  /**
   * The declaring class of method whose bytecode this object represents.
   */
  final private VM_Class declaringClass;
  /**
   * The length of the bytecode this object represents.
   */
  final private int bcLength;
  /**
   * A cursor into the bytecode stream
   */
  private int bcIndex;
  /**
   * The bytecodes this object represents
   */
  final private byte[] bcodes;

  /**
   * Create an object which implements an interface to the bytecode for a
   * method.
   * @param   m the method whose bytecode this object represents
   */
  public OPT_BytecodeInfo (VM_Method m) {
    method = m;
    declaringClass = m.getDeclaringClass();
    bcodes = m.getBytecodes();
    if (bcodes != null)
      bcLength = bcodes.length; 
    else 
      bcLength = 0;
  }

  public final boolean queryAnnotation( int index, byte mask ) {
    return method.queryAnnotationForBytecode( index, mask );
  }

  public void print(PrintStream s) {
      s.println( method.toString() );
  }

 /**
   *  Return the length of the of the bytecode array. 
   */
  public final int getLength() {
      return bcLength;
  }

  /**
   * Return the current cursor, i.e. the bytecode position in the
   * bytecode stream
   *
   * @return the current bytecode offset
   */
  public final int currentInstruction() {
      return bcIndex;
  }

  public final void finishedInstruction() {
  }

  /**
   * Set the cursor to a particular index in the bytecode stream.
   * @param index the position in the bytecode array of bytes
   */
  public final void setInstruction (int index) {
    bcIndex = index;
  }

  /**
   * Fetch the next byte from the bytecode stream and advance the cursor.
   * @return the next byte in the bytecode stream
   */
  public final int getNextInstruction () {
    return  fetch1ByteUnsigned();
  }

  /**
   * Fetch the next byte from the bytecode stream but do <em> not </em> 
   * advance the cursor.
   * @return the next byte in the bytecode stream
   */
  public final int peekNextOpcode () {
    return  get1ByteUnsigned(bcIndex);
  }

  /**
   * Fetch the value of the next byte from the bytecode stream as a signed
   * integer. Advance the cursor.
   * @return the value of next byte in the bytecode stream, 
   * as a <em> signed </em> integer.
   */
  public final int getByteValue () {
    return  fetch1ByteSigned();
  }

  /**
   * Fetch the value of the next byte from the bytecode stream as a signed
   * short. Advance the cursor.
   * @return the value of next byte in the bytecode stream, 
   * as a <em> signed </em> short.
   */
  public final int getShortValue () {
    return  fetch2BytesSigned();
  }

  /**
   * Fetch the value of the next operand, a constant, from the bytecode
   * stream. 
   * @return the value of a literal constant from the bytecode stream,
   * encoding as a constant IR operand
   */
  public final OPT_Operand getConstantOperand (boolean wide) {
    int i = (!wide) ? fetch1ByteUnsigned() : fetch2BytesUnsigned();
    byte desc = declaringClass.getLiteralDescription(i);
    switch (desc) {
      case VM_Statics.INT_LITERAL:
        return  OPT_ClassLoaderProxy.getIntFromConstantPool(
                                        declaringClass, i);
      case VM_Statics.FLOAT_LITERAL:
        return  OPT_ClassLoaderProxy.getFloatFromConstantPool(
                                        declaringClass, i);
      case VM_Statics.STRING_LITERAL:
        return  OPT_ClassLoaderProxy.getStringFromConstantPool(
                                        declaringClass, i);
      case VM_Statics.LONG_LITERAL:
        return  OPT_ClassLoaderProxy.getLongFromConstantPool(
                                        declaringClass, i);
      case VM_Statics.DOUBLE_LITERAL:
        return  OPT_ClassLoaderProxy.getDoubleFromConstantPool(
                                        declaringClass, i);
      default:
        {
          VM._assert(VM.NOT_REACHED, "invalid literal type: 0x" + 
                    Integer.toHexString(desc));
          return  null;
        }
    }
  }

  /**
   * Fetch the number of a local variable from the bytecode stream and
   * advance the cursor.
   * @return the number of a local variable
   */
  public final int getLocalNumber () {
    return  fetch1ByteUnsigned();
  }

  /**
   * Fetch the bytecode index of a branch target from the bytecode stream and
   * advance the cursor.
   * @return the bytecode index of a branch target
   */
  public final int getBranchTarget () {
    return  fetch2BytesSigned();
  }

  /**
   * Fetch the bytecode index of a wide (4-byte) branch target from 
   * the bytecode stream and advance the cursor.
   * @return the bytecode index of a branch target
   */
  public final int getWideBranchTarget () {
    return  fetch4BytesSigned();
  }

  /**
   *  Get the return address of the current instruction (which must be
   * a JSR or call for this to be meaningful).  Implementations may
   * (and this one does) require that this accessor only be called
   * after all portions of the JSR or call have been read, so a
   * byte-level cursor points to the next instruction.
   */
  public final int getReturnAddress() {
      return currentInstruction();
  }

  /**
   * Advance the cursor to the next bytecode index that is a multiple of
   * four.
   */
  private final void alignSwitch () {
    int align = bcIndex & 3;
    if (align != 0)
      bcIndex += 4 - align;                     // eat padding
  }

  /**
   * Get the default target for a switch statement from the the next four
   * bytes in the bytecode stream. Advance the cursor.
   * @return the default target for a switch statement
   */
  public final int getSwitchDefaultTarget () {
      alignSwitch();
      return  fetch4BytesSigned();
  }

  /**
   * Get the low value for a switch statement from the the next four
   * bytes in the bytecode stream. Advance the cursor.
   * @return the low value for a switch statement
   */
  public final int getSwitchLowValue () {
    return  fetch4BytesSigned();
  }

  /**
   * Get the high value for a switch statement from the the next four
   * bytes in the bytecode stream. Advance the cursor.
   * @return the high value for a switch statement
   */
  public final int getSwitchHighValue () {
    return  fetch4BytesSigned();
  }

  /**
   * Get tableswitch offset for a given constant from the next four
   * bytes in the bytecode stream. Advance the cursor.
   * @param value the constant value of the tableswitch operand
   * @param low the low tableswitch value
   * @param high the high tableswitch value
   * @return the offset to jump to for the constant value
   */
  public final int getTableSwitchOffsetForConstant (int value, int low, int high) {
    return  get4BytesSigned(bcIndex + ((value - low) << 2));
  }

  /**
   * Advance the cursor past a number of table switch targets in the
   * bytecode stream.
   * @param numTargets the number of tableswitch targets to skip over
   */
  public final void skipTableSwitchTargets (int numTargets) {
    bcIndex += (numTargets << 2);
  }

  /**
   * Get the ith switch target; ie; the next four bytes in the bytecode
   * stream as a signed integer.  Advance the cursor.
   * @param i unused
   * @return the next four bytes in the stream as a signed integer
   */
  public final int getSwitchTarget (int i) {
    return  fetch4BytesSigned();
  }

  /**
   * Get the ith switch value; ie; the next four bytes in the bytecode
   * stream as a signed integer.  Advance the cursor.
   * @param i unused
   * @return the next four bytes in the stream as a signed integer
   */
  public final int getSwitchValue (int i) {
    return  fetch4BytesSigned();
  }

  /**
   * Advance the cursor past a switch value in the bytecode stream
   * @param i unused
   */
  public final void skipSwitchValue (int i) {
    bcIndex += 4;
  }

  /**
   * Return the number of pairs in a lookupswitch statement, which is
   * encoded in the next four bytes of bytecode.
   * @return the next four bytes of the stream as a signed integer
   */
  public final int getLookupSwitchNumberOfPairs () {
    return  fetch4BytesSigned();
  }

  /**
   * Advance the cursor past a number of lookup switch pairs.
   * @param numPairs the number of pairs in the switch statement
   * @param numProcessed the number of pairs the cursor has already passed
   */
  public final void skipLookupSwitchPairs (int numPairs, int numProcessed) {
    bcIndex += (numPairs - numProcessed)*8;
  }

  /**
   * Fetch the next two bytes from the bytecode stream, translate them
   * into a field reference, and return the result. Advance the cursor.
   * @return the field reference corresponding to the next two bytes of
   * bytecode
   */
  public final VM_Field getFieldReference () {
      int constantPoolIndex = fetch2BytesUnsigned();
      return  declaringClass.getFieldRef(constantPoolIndex);
  }

  /**
   * Get the index into the constant pool for a field reference. Advance
   * the cursor.
   * @return the next two bytes of the stream as an unsigned integer
   */
  public final int getFieldReferenceIndex () {
    return  fetch2BytesUnsigned();
  }

  /**
   * Get the field reference corresponding to a given constant pool index
   * @param constantPoolIndex a constant pool index
   * @return the field corresponding to this constant pool index
   */
  public final VM_Field getFieldReference (int constantPoolIndex) {
      VM_Field f = declaringClass.getFieldRef(constantPoolIndex);
      return f;
  }

  /**
   * Fetch the next two bytes from the bytecode stream, translate them
   * into a method reference, and return the result. Advance the cursor.
   * @return the method reference corresponding to the next two bytes of
   * bytecode
   */
  public final VM_Method getMethodReference () {
      int constantPoolIndex = fetch2BytesUnsigned();
      return getMethodReference( constantPoolIndex );
  }

  /**
   * Get the index into the constant pool for a method reference. Advance
   * the cursor.
   * @return the next two bytes of the stream as an unsigned integer
   */
  public final int getMethodReferenceIndex () {
    return  fetch2BytesUnsigned();
  }

  /**
   * Get the method reference corresponding to a given constant pool index
   * @param constantPoolIndex a constant pool index
   * @return the method corresponding to this constant pool index
   */
  public final VM_Method getMethodReference (int constantPoolIndex) {
    VM_Method m = declaringClass.getMethodRef(constantPoolIndex);
    return m;
  }

  /**
   * Advance the cursor by two bytes.
   */
  public final void eatInvokeInterfaceGarbage () {
    bcIndex += 2;     
  }

  /**
   * Fetch the next two bytes from the bytecode stream, translate them
   * into a type reference, and return the result. Advance the cursor.
   * @return the type reference corresponding to the next two bytes of
   * bytecode
   */
  public final VM_Type getTypeReference () {
      int constantPoolIndex = fetch2BytesUnsigned();
      return  declaringClass.getTypeRef(constantPoolIndex);
  }

  /**
   * Get the index into the constant pool for a type reference. Advance
   * the cursor.
   * @return the next two bytes of the stream as an unsigned integer
   */
  public final int getTypeReferenceIndex () {
    return  fetch2BytesUnsigned();
  }

  /**
   * Get the type reference corresponding to a given constant pool index
   * @param constantPoolIndex a constant pool index
   * @return the type corresponding to this constant pool index
   */
  public final VM_Type getTypeReference (int constantPoolIndex) {
    return  declaringClass.getTypeRef(constantPoolIndex);
  }

  /**
   * Return the next byte of the stream as an unsigned integer
   * @return the next byte of the stream as an unsigned integer
   */
  public final int getWideOpcode () {
    return  fetch1ByteUnsigned();
  }

  /**
   * Return the next 2 bytes of the stream as an unsigned integer
   * @return the next 2 bytes of the stream as an unsigned integer
   */
  public final int getWideLocalNumber () {
    return  fetch2BytesUnsigned();
  }

  /**
   * Return the next byte of the stream as an unsigned integer
   * @return the next byte of the stream as an unsigned integer
   */
  public final int getArrayDimension () {
    return  fetch1ByteUnsigned();
  }

  /**
   * Return the next byte of the stream as a signed integer
   * @return the next byte of the stream as a signed integer
   */
  private final byte fetch1ByteSigned () {
    return  bcodes[bcIndex++];
  }

  /**
   * Return the next byte of the stream as an unsigned integer
   * @return the next byte of the stream as an unsigned integer
   */
  private final int fetch1ByteUnsigned () {
    return  (int)(bcodes[bcIndex++] & 0xFF);
  }

  /**
   * Return the next byte of the stream as an unsigned integer but do <em>
   * not </em> advance the cursor.
   * @return the next byte of the stream as an unsigned integer
   */
  final int get1ByteUnsigned (int index) {
    return  (int)(bcodes[index] & 0xFF);
  }

  /**
   * Return the next four bytes of the stream as a signed integer
   * @return the next four bytes of the stream as a signed integer
   */
  private final int get4BytesSigned (int index) {
    int i = bcodes[index++] << 24;
    i |= (bcodes[index++] & 0xFF) << 16;
    i |= (bcodes[index++] & 0xFF) << 8;
    i |= (bcodes[index] & 0xFF);
    return  i;
  }

  /**
   * Return the next two bytes of the stream as a signed integer
   * @return the next two bytes of the stream as a signed integer
   */
  private final int fetch2BytesSigned () {
    int i = bcodes[bcIndex++] << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return  (int)i;
  }

  /**
   * Return the next two bytes of the stream as a signed integer, but 
   * do <em> not </em> advance the cursor.
   * @return the next two bytes of the stream as a signed integer
   */
  public final int get2BytesSigned (int index) {
    int i = bcodes[index++] << 8;
    i |= (bcodes[index] & 0xFF);
    return  (int)i;
  }

  /**
   * Return the next two bytes of the stream as an unsigned integer
   * @return the next two bytes of the stream as an unsigned integer
   */
  private final int fetch2BytesUnsigned () {
    int i = (bcodes[bcIndex++] & 0xFF) << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return  (int)i;
  }

  /**
   * Return two bytes of the stream as an unsigned integer. Do not advance
   * the cursor.
   * @param index the bytecode index from which to fetch the two bytes
   * @return the selected two bytes of the stream as an unsigned integer
   */
  private final int get2BytesUnsigned (int index) {
    int i = (bcodes[index++] & 0xFF) << 8;
    i |= (bcodes[index] & 0xFF);
    return  (int)i;
  }

  /**
   * Return the next two bytes of the stream as an unsigned integer. Do
   * not advance the cursor.
   * @return the next two bytes of the stream as an unsigned integer
   */
  public final int get2BytesUnsigned () {
    int i = (bcodes[bcIndex] & 0xFF) << 8;
    i |= (bcodes[bcIndex + 1] & 0xFF);
    return  (int)i;
  }

  /**
   * Return the next four bytes of the stream as a signed integer. 
   * @return the next four bytes of the stream as a signed integer
   */
  private final int fetch4BytesSigned () {
    int i = bcodes[bcIndex++] << 24;
    i |= (bcodes[bcIndex++] & 0xFF) << 16;
    i |= (bcodes[bcIndex++] & 0xFF) << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return  i;
  }
}
