/*
 * (C) Copyright IBM Corp. 2001
 */
//VM_BytecodeStream.java
//$Id$
/**
 * VM_BytecodeStream.java
 *
 * This class provides a stream of bytecodes
 * 
 * @author Igor Pechtchanski
 * @see OPT_BC2IR
 * @see VM_ModifiableBytecodeStream
 */

public class VM_BytecodeStream implements VM_BytecodeConstants {
  private VM_Method method;
  private VM_Class declaringClass;
  private int bcLength;
  private int bcIndex;
  private byte[] bcodes;

  private int opcode;
  private boolean wide;

  /**
   * Constructor
   * @param m the method containing the bytecodes
   */
  public VM_BytecodeStream(VM_Method m) {
    method = m;
    declaringClass = m.getDeclaringClass();
    reload();
    bcIndex = 0;
  }

  /**
   * Reload information from the method data structure.
   */
  protected final void reload() {
    bcodes = method.getBytecodes();
    if (bcodes != null) bcLength = bcodes.length;
    else                bcLength = 0;
  }

  /**
   * Returns the method that this bytecode stream is from
   * @return method
   */
  public final VM_Method method() {
    return method;
  }

  /**
   * Returns the length of the bytecode stream
   * Returns 0 if the method doesn't have any bytecodes
   *         (i.e. is abstract or native)
   * @return bytecode stream length
   */
  public final int length() {
    return bcLength;
  }

  /**
   * Returns the current bytecode index
   * @return the current bytecode index
   */
  public final int index() {
    return bcIndex;
  }

  /**
   * Resets the stream to the beginning
   * @see reset(int)
   */
  public final void reset() {
    reset(0);
  }

  /**
   * Resets the stream to a given position
   * Use with caution
   * @param index the position to reset the stream to
   * @see reset()
   */
  public final void reset(int index) {
    bcIndex = index;
  }

  /**
   * Resets the stream to a given position
   * @deprecated reset(int)
   * @param index the position to reset the stream to
   * @see reset(int)
   */
  public final void setPosition(int index) {
    reset(index);
  }

  /**
   * Does the stream have more bytecodes in it?
   * @return whether there are more bytecodes
   */
  public final boolean hasMoreBytecodes() {
    return bcIndex < bcLength;
  }

  /**
   * Returns the opcode of the next instruction in the sequence
   * without advancing to it
   * @return the opcode of the next instruction
   * @see nextInstruction()
   */
  public final int peekNextOpcode() {
    if (VM.VerifyAssertions) VM.assert(bcIndex < bcLength);
    return getUnsignedByte(bcIndex);
  }

  /**
   * Sets up the next instruction in the sequence
   * @return the opcode of the next instruction
   * @see peekNextOpcode()
   */
  public final int nextInstruction() {
    if (VM.VerifyAssertions) VM.assert(bcIndex < bcLength);
    opcode = readUnsignedByte();
    wide = (opcode == JBC_wide);
    return opcode;
  }

  /**
   * Returns the opcode of the current instruction in the sequence
   * Note: if skipInstruction has been called, but nextInstruction has not,
   *       this method will return the opcode of the skipped instruction!
   * @return the opcode of the current instruction
   * @see nextInstruction()
   * @see isWide()
   */
  public final int getOpcode() {
    return opcode;
  }

  /**
   * Are we currently processing a wide instruction?
   * @return true if current instruction is wide
   * @see nextInstruction()
   * @see getOpcode()
   */
  public final boolean isWide() {
    return wide;
  }

  /**
   * Skips the current instruction
   * @see skipInstruction(int)
   */
  public final void skipInstruction() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    int len = JBC_length[opcode] - 1;
    if (wide) len += len;
    if (len >= 0) bcIndex += len;
    else          skipSpecialInstruction(opcode);
  }

  /**
   * Skips the current instruction (without using the opcode field)
   * A slightly optimized version of skipInstruction()
   * @param opcode current opcode
   * @param wide whether current instruction follows wide
   * @see skipInstruction()
   */
  public final void skipInstruction(int opcode, boolean wide) {
    if (VM.VerifyAssertions) VM.assert(bcIndex < bcLength);
    int len = JBC_length[opcode] - 1;
    if (wide) len += len;
    if (len >= 0) bcIndex += len;
    else          skipSpecialInstruction(opcode);
  }

  /**
   * Returns a signed byte value
   * Used for bipush
   * @return signed byte value
   */
  public final int getByteValue() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_bipush);
    return readSignedByte();
  }

  /**
   * Returns a signed short value
   * Used for sipush
   * @return signed short value
   */
  public final int getShortValue() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_sipush);
    return readSignedShort();
  }

  /**
   * Returns the number of the local (as an unsigned byte)
   * Used for iload, lload, fload, dload, aload,
   *          istore, lstore, fstore, dstore, astore,
   *          iinc, ret
   * @return local number
   * @see getWideLocalNumber()
   */
  public final int getLocalNumber() {
    if (VM.VerifyAssertions)
      VM.assert((opcode >= JBC_iload && opcode <= JBC_aload) ||
                (opcode >= JBC_istore && opcode <= JBC_astore) ||
                opcode == JBC_iinc || opcode == JBC_ret);
    return readUnsignedByte();
  }

  /**
   * Returns the wide number of the local (as an unsigned short)
   * Used for iload, lload, fload, dload, aload,
   *          istore, lstore, fstore, dstore, astore,
   *          iinc prefixed by wide
   * @return wide local number
   * @see getLocalNumber()
   */
  public final int getWideLocalNumber() {
    if (VM.VerifyAssertions)
      VM.assert(wide &&
                ((opcode >= JBC_iload && opcode <= JBC_aload) ||
                 (opcode >= JBC_istore && opcode <= JBC_astore) ||
                 opcode == JBC_iinc));
    return readUnsignedShort();
  }

  /**
   * Returns an increment value (as a signed byte)
   * Used for iinc
   * @return increment
   * @see getWideIncrement()
   */
  public final int getIncrement() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_iinc);
    return readSignedByte();
  }

  /**
   * Returns an increment value (as a signed short)
   * Used for iinc prefixed by wide
   * @return wide increment
   * @see getIncrement()
   */
  public final int getWideIncrement() {
    if (VM.VerifyAssertions) VM.assert(wide && opcode == JBC_iinc);
    return readSignedShort();
  }

  /**
   * Returns the offset of the branch (as a signed short)
   * Used for if<cond>, ificmp<cond>, ifacmp<cond>, goto, jsr
   * @return branch offset
   * @see getWideBranchOffset()
   */
  public final int getBranchOffset() {
    if (VM.VerifyAssertions)
      VM.assert((opcode >= JBC_ifeq && opcode <= JBC_ifle) ||
                (opcode >= JBC_if_icmpeq && opcode <= JBC_if_icmple) ||
                opcode == JBC_if_acmpeq || opcode == JBC_if_acmpne ||
                opcode == JBC_ifnull || opcode == JBC_ifnonnull ||
                opcode == JBC_goto || opcode == JBC_jsr);
    return readSignedShort();
  }

  /**
   * Returns the wide offset of the branch (as a signed int)
   * Used for goto_w, jsr_w
   * @return wide branch offset
   * @see getBranchOffset()
   */
  public final int getWideBranchOffset() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_goto_w || opcode == JBC_jsr_w);
    return readSignedInt();
  }

  /**
   * Skips the padding of a switch instruction
   * Used for tableswitch, lookupswitch
   */
  public final void alignSwitch() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_tableswitch || opcode == JBC_lookupswitch);
    int align = bcIndex & 3;
    if (align != 0) bcIndex += 4-align; // eat padding
  }

  /**
   * Returns the default offset of the switch (as a signed int)
   * Used for tableswitch, lookupswitch
   * @return default switch offset
   */
  public final int getDefaultSwitchOffset() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_tableswitch || opcode == JBC_lookupswitch);
    return readSignedInt();
  }

  /**
   * Returns the lowest value of the tableswitch (as a signed int)
   * Used for tableswitch
   * @return lowest switch value
   * @see getHighSwitchValue()
   */
  public final int getLowSwitchValue() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_tableswitch);
    return readSignedInt();
  }

  /**
   * Returns the highest value of the tableswitch (as a signed int)
   * Used for tableswitch
   * @return highest switch value
   * @see getLowSwitchValue()
   */
  public final int getHighSwitchValue() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_tableswitch);
    return readSignedInt();
  }

  /**
   * Skips the offsets of a tableswitch instruction
   * Used for tableswitch
   * @param num the number of offsets to skip
   * @see getTableSwitchOffset(int)
   */
  public final void skipTableSwitchOffsets(int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_tableswitch);
    bcIndex += (num << 2);
  }

  /**
   * Returns the numbered offset of the tableswitch (as a signed int)
   * Used for tableswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * @param num the number of the offset to retrieve
   * @return switch offset
   */
  public final int getTableSwitchOffset(int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_tableswitch);
    return getSignedInt(bcIndex + (num << 2));
  }

  /**
   * Returns the offset for a given value of the tableswitch (as a signed int)
   * or 0 if the value is out of range.
   * Used for tableswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * @param value the value to retrieve offset for
   * @param low the lowest value of the tableswitch
   * @param high the highest value of the tableswitch
   * @return switch offset
   */
  public final int computeTableSwitchOffset(int value, int low, int high) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_tableswitch);
    if (value < low || value > high) return 0;
    return getSignedInt(bcIndex + ((value - low) << 2));
  }

  /**
   * Returns the number of match-offset pairs in the lookupswitch
   *         (as a signed int)
   * Used for lookupswitch
   * @return number of switch pairs
   */
  public final int getSwitchLength() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_lookupswitch);
    return readSignedInt();
  }

  /**
   * Skips the match-offset pairs of a lookupswitch instruction
   * Used for lookupswitch
   * @param num the number of match-offset pairs to skip
   * @see getLookupSwitchValue(int)
   * @see getLookupSwitchOffset(int)
   */
  public final void skipLookupSwitchPairs(int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_lookupswitch);
    bcIndex += (num << 3);
  }

  /**
   * Returns the numbered offset of the lookupswitch (as a signed int)
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the pair table
   * NOTE: Will NOT advance cursor
   * @param num the number of the offset to retrieve
   * @return switch offset
   * @see getLookupSwitchValue(int)
   */
  public final int getLookupSwitchOffset(int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_lookupswitch);
    return getSignedInt(bcIndex + (num << 3) + 4);
  }

  /**
   * Returns the numbered value of the lookupswitch (as a signed int)
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the pair table
   * NOTE: Will NOT advance cursor
   * @param num the number of the value to retrieve
   * @return switch value
   * @see getLookupSwitchOffset(int)
   */
  public final int getLookupSwitchValue(int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_lookupswitch);
    return getSignedInt(bcIndex + (num << 3));
  }

  /**
   * Returns the offset for a given value of the lookupswitch
   *         (as a signed int) or 0 if the value is not in the table.
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * WARNING: Uses LINEAR search.  Whoever has time on their hands can
   *          re-implement this as a binary search.
   * @param value the value to retrieve offset for
   * @param num the number of match-offset pairs in the lookupswitch
   * @return switch offset
   */
  public final int computeLookupSwitchOffset(int value, int num) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_lookupswitch);
    for (int i = 0; i < num; i++)
      if (getSignedInt(bcIndex + (i << 3)) == value)
        return getSignedInt(bcIndex + (i << 3) + 4);
    return 0;
  }

  /**
   * Returns the constant pool index of a field reference
   *         (as an unsigned short)
   * Used for getstatic, putstatic, getfield, putfield
   * @return field reference index
   * @see getFieldReference(int)
   * @see getFieldReference()
   */
  public final int getFieldReferenceIndex() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_getstatic || opcode == JBC_putstatic ||
                opcode == JBC_getfield || opcode == JBC_putfield);
    return readUnsignedShort();
  }

  /**
   * Returns the field reference at given constant pool index (as a VM_Field)
   * Used for getstatic, putstatic, getfield, putfield
   * @param index constant pool index of a field reference
   * @return field reference
   * @see getFieldReferenceIndex()
   * @see getFieldReference()
   */
  public final VM_Field getFieldReference(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_getstatic || opcode == JBC_putstatic ||
                opcode == JBC_getfield || opcode == JBC_putfield);
    return declaringClass.getFieldRef(index);
  }

  /**
   * Returns the field reference (as a VM_Field)
   * Used for getstatic, putstatic, getfield, putfield
   * @return field reference
   * @see getFieldReferenceIndex()
   * @see getFieldReference(int)
   */
  public final VM_Field getFieldReference() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_getstatic || opcode == JBC_putstatic ||
                opcode == JBC_getfield || opcode == JBC_putfield);
    int index = readUnsignedShort();
    return declaringClass.getFieldRef(index);
  }

  /**
   * Returns the constant pool index of a method reference
   *         (as an unsigned short)
   * Used for invokevirtual, invokespecial, invokestatic, invokeinterface
   * @return method reference index
   * @see getMethodReference(int)
   * @see getMethodReference()
   */
  final int getMethodReferenceIndex() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_invokevirtual || opcode == JBC_invokespecial ||
                opcode == JBC_invokestatic || opcode == JBC_invokeinterface);
    return readUnsignedShort();
  }

  /**
   * Returns the method reference at given constant pool index (as a VM_Method)
   * Used for invokevirtual, invokespecial, invokestatic, invokeinterface
   * @param index constant pool index of a method reference
   * @return method reference
   * @see getMethodReferenceIndex()
   * @see getMethodReference()
   */
  final VM_Method getMethodReference(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_invokevirtual || opcode == JBC_invokespecial ||
                opcode == JBC_invokestatic || opcode == JBC_invokeinterface);
    return declaringClass.getMethodRef(index);
  }

  /**
   * Returns the method reference (as a VM_Method)
   * Used for invokevirtual, invokespecial, invokestatic, invokeinterface
   * @return method reference
   * @see getMethodReferenceIndex()
   * @see getMethodReference(int)
   */
  final VM_Method getMethodReference() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_invokevirtual || opcode == JBC_invokespecial ||
                opcode == JBC_invokestatic || opcode == JBC_invokeinterface);
    int index = readUnsignedShort();
    return declaringClass.getMethodRef(index);
  }

  /**
   * Skips the extra stuff after an invokeinterface instruction
   * Used for invokeinterface
   */
  public final void alignInvokeInterface() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_invokeinterface);
    bcIndex += 2; // eat superfluous stuff
  }

  /**
   * Returns the constant pool index of a type reference
   *         (as an unsigned short)
   * Used for new, anewarray, checkcast, instanceof, multianewarray
   * @return type reference index
   * @see getTypeReference(int)
   * @see getTypeReference()
   */
  public final int getTypeReferenceIndex() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_new || opcode == JBC_anewarray ||
                opcode == JBC_checkcast || opcode == JBC_instanceof ||
                opcode == JBC_multianewarray);
    return readUnsignedShort();
  }

  /**
   * Returns the type reference at given constant pool index (as a VM_Type)
   * Used for new, anewarray, checkcast, instanceof, multianewarray
   * @param index constant pool index of a type reference
   * @return type reference
   * @see getTypeReferenceIndex()
   * @see getTypeReference()
   */
  public final VM_Type getTypeReference(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_new || opcode == JBC_anewarray ||
                opcode == JBC_checkcast || opcode == JBC_instanceof ||
                opcode == JBC_multianewarray);
    return declaringClass.getTypeRef(index);
  }

  /**
   * Returns the type reference (as a VM_Type)
   * Used for new, anewarray, checkcast, instanceof, multianewarray
   * @return type reference
   * @see getTypeReferenceIndex()
   * @see getTypeReference(int)
   */
  public final VM_Type getTypeReference() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_new || opcode == JBC_anewarray ||
                opcode == JBC_checkcast || opcode == JBC_instanceof ||
                opcode == JBC_multianewarray);
    int index = readUnsignedShort();
    return declaringClass.getTypeRef(index);
  }

  /**
   * Returns the element type (primitive) of the array (as an unsigned byte)
   * Used for newarray
   * @return array element type
   * @see getPrimitiveArrayType()
   * @see getPrimitiveArrayType(int)
   */
  public final int getArrayElementType() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_newarray);
    return readUnsignedByte();
  }

  /**
   * Returns the type of the array of given primitive type (as a VM_Type)
   * Used for newarray
   * @param etype element type
   * @return array type
   * @see getArrayElementType()
   * @see getPrimitiveArrayType()
   */
  public final VM_Type getPrimitiveArrayType(int etype) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_newarray);
    return VM_Array.getPrimitiveArrayType(etype);
  }

  /**
   * Returns the type of the primitive array (as a VM_Type)
   * Used for newarray
   * @return array type
   * @see getArrayElementType()
   * @see getPrimitiveArrayType(int)
   */
  public final VM_Type getPrimitiveArrayType() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_newarray);
    int etype = readUnsignedByte();
    return VM_Array.getPrimitiveArrayType(etype);
  }

  /**
   * Returns the type of the array of given object type (as a VM_Type)
   * Used for anewarray
   * @param klass element type
   * @return array type
   * @see getTypeReference()
   * @see getTypeReference(int)
   * @see getObjectArrayType()
   */
  public final VM_Type getObjectArrayType(VM_Type klass) {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_anewarray);
    return klass.getArrayTypeForElementType();
  }

  /**
   * Returns the type of the object array (as a VM_Type)
   * Used for anewarray
   * @return array type
   * @see getObjectArrayType(VM_Type)
   */
  public final VM_Type getObjectArrayType() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_anewarray);
    VM_Type klass = getTypeReference();
    return klass.getArrayTypeForElementType();
  }

  /**
   * Returns the dimension of the array (as an unsigned byte)
   * Used for multianewarray
   * @return array dimension
   */
  public final int getArrayDimension() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_multianewarray);
    return readUnsignedByte();
  }

  /**
   * Returns the opcode of the wide instruction
   * Used for wide
   * Can be one of iload, lload, fload, dload, aload,
   *               istore, lstore, fstore, dstore, astore, iinc
   * @return the opcode of the wide instruction
   */
  public final int getWideOpcode() {
    if (VM.VerifyAssertions) VM.assert(wide && opcode == JBC_wide);
    opcode = readUnsignedByte();
    if (VM.VerifyAssertions)
      VM.assert((opcode >= JBC_iload && opcode <= JBC_aload) ||
                (opcode >= JBC_istore && opcode <= JBC_astore) ||
                opcode == JBC_iinc);
    return opcode;
  }

  /**
   * Returns the constant pool index of a constant (as an unsigned byte)
   * Used for ldc
   * @return constant index
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final int getConstantIndex() {
    if (VM.VerifyAssertions) VM.assert(opcode == JBC_ldc);
    return readUnsignedByte();
  }

  /**
   * Returns the wide constant pool index of a constant (as an unsigned short)
   * Used for ldc_w, ldc2_w
   * @return wide constant index
   * @see getConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final int getWideConstantIndex() {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_ldc_w || opcode == JBC_ldc2_w);
    return readUnsignedShort();
  }

  /**
   * Returns the type of a constant at a given constant pool index (as a byte)
   * Used for ldc, ldc_w, ldc2_w
   * @return constant type
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final byte getConstantType(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_ldc || opcode == JBC_ldc_w ||
                opcode == JBC_ldc2_w);
    byte desc = declaringClass.getLiteralDescription(index);
    return desc;
  }

  /**
   * Returns the constant at a given constant pool index (as an int)
   * Used for ldc, ldc_w
   * @return int constant
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final int getIntConstant(int index) {
    if (VM.VerifyAssertions)
      VM.assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                declaringClass.getLiteralDescription(index) ==
                VM_Statics.INT_LITERAL);
    int offset = declaringClass.getLiteralOffset(index) >> 2;
    int val = VM_Statics.getSlotContentsAsInt(offset);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a long)
   * Used for ldc2_w
   * @return long constant
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final long getLongConstant(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_ldc2_w &&
                declaringClass.getLiteralDescription(index) ==
                VM_Statics.LONG_LITERAL);
    int offset = declaringClass.getLiteralOffset(index) >> 2;
    long val = VM_Statics.getSlotContentsAsLong(offset);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a float)
   * Used for ldc, ldc_w
   * @return float constant
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getDoubleConstant(int)
   * @see getStringConstant(int)
   */
  public final float getFloatConstant(int index) {
    if (VM.VerifyAssertions)
      VM.assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                declaringClass.getLiteralDescription(index) ==
                VM_Statics.FLOAT_LITERAL);
    int offset = declaringClass.getLiteralOffset(index) >> 2;
    int val_raw = VM_Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a double)
   * Used for ldc2_w
   * @return double constant
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getStringConstant(int)
   */
  public final double getDoubleConstant(int index) {
    if (VM.VerifyAssertions)
      VM.assert(opcode == JBC_ldc2_w &&
                declaringClass.getLiteralDescription(index) ==
                VM_Statics.DOUBLE_LITERAL);
    int offset = declaringClass.getLiteralOffset(index) >> 2;
    long val_raw = VM_Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a String)
   * Used for ldc, ldc_w
   * @return String constant
   * @see getConstantIndex()
   * @see getWideConstantIndex()
   * @see getConstantType(int)
   * @see getIntConstant(int)
   * @see getLongConstant(int)
   * @see getFloatConstant(int)
   * @see getDoubleConstant(int)
   */
  public final String getStringConstant(int index) {
    if (VM.VerifyAssertions)
      VM.assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                declaringClass.getLiteralDescription(index) ==
                VM_Statics.STRING_LITERAL);
    int offset = declaringClass.getLiteralOffset(index) >> 2;
    String val = (String) VM_Statics.getSlotContentsAsObject(offset);
    return val;
  }

  //// HELPER FUNCTIONS

  // Skip a tableswitch or a lookupswitch instruction
  private void skipSpecialInstruction(int opcode) {
    switch (opcode) {
      case JBC_tableswitch:
        {
          alignSwitch();
          getDefaultSwitchOffset();
          int l = getLowSwitchValue();
          int h = getHighSwitchValue();
          skipTableSwitchOffsets(h - l + 1);  // jump offsets
        }
        break;
      case JBC_lookupswitch:
        {
          alignSwitch();
          getDefaultSwitchOffset();
          int n = getSwitchLength();
          skipLookupSwitchPairs(n);           // match-offset pairs
        }
        break;
      case JBC_wide:
        {
          getWideOpcode();
          int len = JBC_length[opcode] - 1;
          bcIndex += len + len;
        }
        break;
      default:
        if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    }
  }

  //// READ BYTECODES

  private final byte readSignedByte() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    return bcodes[bcIndex++];
  }
  private final int readUnsignedByte() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    return (int)(bcodes[bcIndex++] & 0xFF);
  }
  private final int getUnsignedByte(int index) {
    if (VM.VerifyAssertions) VM.assert(index <= bcLength);
    return (int)(bcodes[index] & 0xFF);
  }

  private final int readSignedShort() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    int i = bcodes[bcIndex++] << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return (int)i;
  }
  // UNUSED!
  private final int getSignedShort(int index) {
    if (VM.VerifyAssertions) VM.assert(index <= bcLength);
    int i = bcodes[index++] << 8;
    i |= (bcodes[index] & 0xFF);
    return (int)i;
  }

  private final int readUnsignedShort() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    int i = (bcodes[bcIndex++] & 0xFF) << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return (int)i;
  }
  // UNUSED!
  private final int getUnsignedShort(int index) {
    if (VM.VerifyAssertions) VM.assert(index <= bcLength);
    int i = (bcodes[index++] & 0xFF) << 8;
    i |= (bcodes[index] & 0xFF);
    return (int)i;
  }  

  private final int readSignedInt() {
    if (VM.VerifyAssertions) VM.assert(bcIndex <= bcLength);
    int i = bcodes[bcIndex++] << 24;
    i |= (bcodes[bcIndex++] & 0xFF) << 16;
    i |= (bcodes[bcIndex++] & 0xFF) << 8;
    i |= (bcodes[bcIndex++] & 0xFF);
    return i;
  }
  private final int getSignedInt(int index) {
    if (VM.VerifyAssertions) VM.assert(index <= bcLength);
    int i = bcodes[index++] << 24;
    i |= (bcodes[index++] & 0xFF) << 16;
    i |= (bcodes[index++] & 0xFF) << 8;
    i |= (bcodes[index] & 0xFF);
    return i;
  }
}

