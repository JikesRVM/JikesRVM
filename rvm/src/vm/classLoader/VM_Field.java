/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

import java.io.DataInputStream;
import java.io.IOException;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * A field of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Field extends VM_Member {

  /**
   * constant pool index of field's value (0 --> not a "static final constant")
   */
  private final int constantValueIndex; 

  /**
   * NOTE: Only {@link VM_Class} is allowed to create an instance of a VM_Field.
   * 
   * @param declaringClass the VM_Class object of the class that declared this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the field's attributed from
   */
  VM_Field(VM_Class declaringClass, VM_MemberReference memRef,
           int modifiers, DataInputStream input) throws IOException {
    super(declaringClass, memRef, modifiers & APPLICABLE_TO_FIELDS);
    memRef.asFieldReference().setResolvedMember(this);
    
    // Read the attributes, processing the "non-boring" ones
    int cvi = 0; // kludge to allow us to make constantValueIndex final.
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName   = declaringClass.getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();
      if (attName == VM_ClassLoader.constantValueAttributeName) {
        cvi = input.readUnsignedShort();
      } else {
        // all other attributes are boring...
        input.skipBytes(attLength);
      }
    }
    
    constantValueIndex = cvi;
  }

  /**
   * Get type of this field's value.
   */ 
  public final VM_TypeReference getType() throws VM_PragmaUninterruptible {
    return memRef.asFieldReference().getFieldContentsType();
  }
  
  /**
   * How many stackslots do value of this type take?
   */
  public final int getNumberOfStackSlots() {
    return getType().getStackWords();  
  }
    
  /**
   * How many bytes of memory words do value of this type take?
   */
  public final int getSize() {
    return getType().getSize();  
  }
    
  /**
   * Shared among all instances of this class?
   */ 
  public final boolean isStatic() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * May only be assigned once?
   */ 
  public final boolean isFinal() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Value not to be cached in a register?
   */ 
  public final boolean isVolatile() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_VOLATILE) != 0;
  }

  /**
   * Value not to be written/read by persistent object manager?
   */ 
  public final boolean isTransient() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_TRANSIENT) != 0;
  }

  /**
   * Get index of constant pool entry containing this 
   * "static final constant" field's value.
   * @return constant pool index (0 --> field is not a "static final constant")
   */ 
  final int getConstantValueIndex() throws VM_PragmaUninterruptible {
    return constantValueIndex;
  }

  //-------------------------------------------------------------------//
  // Lowlevel support for various reflective operations                //
  // Because different clients have different error checking           //
  // requirements, these operations are completely unsafe and we       //
  // assume that the client has done the required error checking.      //
  //-------------------------------------------------------------------//

  /**
   * Read the contents of the field.
   * If the contents of this field is an object, return that object.
   * If the contents of this field is a primitive, get the value and wrap it in an object.
   */
  public final Object getObjectUnchecked(Object obj) {
    VM_TypeReference type = getType();
    if (type.isReferenceType()) {
      return getObjectValueUnchecked(obj);
    } else {
      if (type.isCharType())     return new Character(getCharValueUnchecked(obj));
      if (type.isDoubleType())   return new Double(getDoubleValueUnchecked(obj));
      if (type.isFloatType())    return new Float(getFloatValueUnchecked(obj));
      if (type.isLongType())     return new Long(getLongValueUnchecked(obj));
      if (type.isIntType())      return new Integer(getIntValueUnchecked(obj));
      if (type.isShortType())    return new Short(getShortValueUnchecked(obj));
      if (type.isByteType())     return new Byte(getByteValueUnchecked(obj));
      if (type.isBooleanType())  return new Boolean(getBooleanValueUnchecked(obj));
      return null;
    }
  }

  /**
    * Read one object ref from heap using RVM object model, GC safe.
    * @param obj the object whose field is to be read, 
    * or null if the field is static.
    * @return the reference described by this VM_Field from the given object.
    */
  public final Object getObjectValueUnchecked(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsObject(offset>>>LOG_BYTES_IN_INT);
    } else {
      return VM_Magic.getObjectAtOffset(obj, offset);
    }
  }

  public final boolean getBooleanValueUnchecked(Object obj) {
    int bits = get32Bits(obj);
    return (bits == 0) ? false : true;
  }

  public final byte getByteValueUnchecked(Object obj) {
    int bits = get32Bits(obj);
    return (byte)bits;
  }

  public final char getCharValueUnchecked(Object obj) {
    return (char)get32Bits(obj);
  }

  public final short getShortValueUnchecked(Object obj) {
    return (short)get32Bits(obj);
  }

  public final int getIntValueUnchecked(Object obj) {
    return get32Bits(obj);
  }

  public final long getLongValueUnchecked(Object obj) {
    return get64Bits(obj);
  }

  public final float getFloatValueUnchecked(Object obj) {
    return VM_Magic.intBitsAsFloat(get32Bits(obj));
  }

  public final double getDoubleValueUnchecked(Object obj) {
    return VM_Magic.longBitsAsDouble(get64Bits(obj));
  }

  private int get32Bits(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsInt(offset >>> LOG_BYTES_IN_INT);
    } else {
      return VM_Magic.getIntAtOffset(obj, offset);
    }
  }

  private long get64Bits(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsLong(offset >>> LOG_BYTES_IN_INT); 
    } else {
      return VM_Magic.getLongAtOffset(obj, offset);
    }
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   * @return void
   */
  public final void setObjectValueUnchecked(Object obj, Object ref) {
    if (isStatic()) {
      if (MM_Interface.NEEDS_PUTSTATIC_WRITE_BARRIER)
        MM_Interface.putstaticWriteBarrier(offset, ref);
      else
        VM_Statics.setSlotContents(offset>>>LOG_BYTES_IN_INT, ref);
    } else {
      if (MM_Interface.NEEDS_WRITE_BARRIER)
        MM_Interface.putfieldWriteBarrier(obj, offset, ref);
      else
        VM_Magic.setObjectAtOffset(obj, offset, ref);
    }
  }
  
  public final void setBooleanValueUnchecked(Object obj, boolean b) {
    put32(obj, b ? 1 : 0);
  }

  public final void setByteValueUnchecked(Object obj, byte b) {
    put32(obj, (int)b);
  }

  public final void setCharValueUnchecked(Object obj, char c) {
    put32(obj, (int)c);
  }

  public final void setShortValueUnchecked(Object obj, short i) {
    put32(obj, (int)i);
  }

  public final void setIntValueUnchecked(Object obj, int i) {
    put32(obj, i);
  }

  public final void setFloatValueUnchecked(Object obj, float f) {
    put32(obj, VM_Magic.floatAsIntBits(f));
  }

  public final void setLongValueUnchecked(Object obj, long l) {
    put64(obj, l);
  }

  public final void setDoubleValueUnchecked(Object obj, double d) {
    put64(obj, VM_Magic.doubleAsLongBits(d));
  }

  private void put32(Object obj, int value) {
    if (isStatic()) {
      VM_Statics.setSlotContents(offset >>> LOG_BYTES_IN_INT, value );
    } else {
      VM_Magic.setIntAtOffset(obj, offset, value);
    }
  }

  private void put64(Object obj, long value) {
    if (isStatic()) {
      VM_Statics.setSlotContents(offset >>> LOG_BYTES_IN_INT, value);
    } else {
      VM_Magic.setLongAtOffset(obj, offset, value);
    }
  }
}
