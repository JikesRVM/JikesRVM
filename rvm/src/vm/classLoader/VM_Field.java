/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

import java.io.DataInputStream;
import java.io.IOException;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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
   * @param memRef the cannonical memberReference for this member.
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
  public final VM_Type getType() throws VM_PragmaUninterruptible {
    return memRef.asFieldReference().getType();
  }

  /**
   * Get size of this field's value, in bytes.
   */ 
  public final int getSize() throws VM_PragmaUninterruptible {
    return getType().getStackWords() << 2;
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
  // Support for various reflective operations                         //
  //-------------------------------------------------------------------//

  /**
   * Read the contents of the field.
   * If the contents of this field is an object, return that object.
   * If the contents of this field is a primitive, get the value and wrap it in an object.
   */
  public final Object getObject(Object obj) {
    VM_Type type = getType();
    if (type.isReferenceType()) {
      return getObjectValue(obj);
    } else {
      if (type.isCharType())     return new Character(getCharValue(obj));
      if (type.isDoubleType())   return new Double(getDoubleValue(obj));
      if (type.isFloatType())    return new Float(getFloatValue(obj));
      if (type.isLongType())     return new Long(getLongValue(obj));
      if (type.isIntType())      return new Integer(getIntValue(obj));
      if (type.isShortType())    return new Short(getShortValue(obj));
      if (type.isByteType())     return new Byte(getByteValue(obj));
      if (type.isBooleanType())  return new Boolean(getBooleanValue(obj));
      return null;
    }
  }

  /**
    * Read one object ref from heap using RVM object model, GC safe.
    * @param obj the object whose field is to be read, 
    * or null if the field is static.
    * @return the reference described by this VM_Field from the given object.
    */
  public final Object getObjectValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isReferenceType()) throw new IllegalArgumentException("field type mismatch");
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsObject(offset>>>2);
    } else {
      if (obj == null)
	throw new NullPointerException();
      if (!getDeclaringClass().getClassForType().isInstance(obj))
	throw new IllegalArgumentException();
      return VM_Magic.getObjectAtOffset(obj, offset);
    }
  }

  public final boolean getBooleanValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isBooleanType()) throw new IllegalArgumentException("field type mismatch");
    int bits = get32Bits(obj);
    return (bits == 0) ? false : true;
  }

  public final byte getByteValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isByteType()) throw new IllegalArgumentException("field type mismatch");
    int bits = get32Bits(obj);
    return (byte)bits;
  }

  public final char getCharValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isCharType()) throw new IllegalArgumentException("field type mismatch");
    return (char)get32Bits(obj);
  }

  public final short getShortValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isShortType()) throw new IllegalArgumentException("field type mismatch");
    return (short)get32Bits(obj);
  }

  public final int getIntValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isIntType()) throw new IllegalArgumentException("field type mismatch");
    return get32Bits(obj);
  }

  public final long getLongValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isLongType()) throw new IllegalArgumentException("field type mismatch");
    return get64Bits(obj);
  }

  public final float getFloatValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isFloatType()) throw new IllegalArgumentException("field type mismatch");
    return Float.intBitsToFloat(get32Bits(obj));
  }

  public final double getDoubleValue(Object obj) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isDoubleType()) throw new IllegalArgumentException("field type mismatch");
    return Double.longBitsToDouble(get64Bits(obj));
  }

  private int get32Bits(Object obj) {
    if (VM.VerifyAssertions) VM._assert(getSize()==4);
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsInt(offset >>> 2);  // divide by 4 to get words from bytes
    } else {
      if (obj==null)
	throw new NullPointerException();
      return VM_Magic.getIntAtOffset(obj, offset);
    }
  }

  private long get64Bits(Object obj) {
    if (VM.VerifyAssertions) VM._assert(getSize()==8);
    if (isStatic()) {
      long result = VM_Statics.getSlotContentsAsLong(offset >>> 2); // divide by 4 to get words from bytes
      return result;
    } else {
      if (obj==null)
	throw new NullPointerException();
      long result = VM_Magic.getLongAtOffset(obj, offset);
      return result;
    }
  }


  public void set(Object obj, Object value) throws IllegalArgumentException, IllegalAccessException {
    VM._assert(false, "FINISH ME\n");
    // !!TODO: get and modify form Field
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   * @return void
   */
  public final void setObjectValue(Object obj, Object ref) throws IllegalArgumentException {
    if (ref != null ) {
      VM_Type actualType = VM_Magic.getObjectType(ref);
      boolean ok = false;
      try {
	VM_Type type = getType();
	ok = ((type == actualType) ||
	      (type == VM_Type.JavaLangObjectType) ||
	      VM_Runtime.isAssignableWith(type, actualType));
      } catch (VM_ResolutionException e) {}
      if (!ok) throw new IllegalArgumentException();
    }

    if (isStatic()) {
      if (VM_Interface.NEEDS_WRITE_BARRIER) {
	VM_Interface.putstaticWriteBarrier(offset, ref);
      }
      VM_Statics.setSlotContents(offset>>>2, ref);
    } else {
      if (obj == null)
	throw new NullPointerException();
      if (!getDeclaringClass().getClassForType().isInstance(obj))
	throw new IllegalArgumentException();
      if (VM_Interface.NEEDS_WRITE_BARRIER) {
	VM_Interface.putfieldWriteBarrier(obj, offset, ref);
      }
      VM_Magic.setObjectAtOffset(obj, offset, ref);
    }
  }
  
  public final void setBooleanValue(Object obj, boolean b) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isBooleanType()) 
      if (b==true)
	put32(obj, 1 );
      else
	put32(obj, 0 );
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setByteValue(Object obj, byte b) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isLongType())
      setLongValue(obj, (long) b);
    else if (type.isIntType()) 
      setIntValue(obj, (int) b);
    else if (type.isShortType())
      setShortValue(obj, (short) b);
    else if (type.isCharType()) 
      setCharValue(obj, (char) b);
    else if (type.isByteType()) 
      put32(obj, (int)b);
    else if (type.isDoubleType())
      setDoubleValue(obj, (double)b);
    else if (type.isFloatType()) 
      setFloatValue(obj, (float)b);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setCharValue(Object obj, char c) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isLongType())
      setLongValue(obj, (long) c);
    else if (type.isIntType()) 
      setIntValue(obj, (int) c);
    else if (type.isShortType())
      setShortValue(obj, (short) c);
    else if (type.isCharType()) 
      put32(obj, (int)c);
    else if (type.isDoubleType())
      setDoubleValue(obj, (double)c);
    else if (type.isFloatType()) 
      setFloatValue(obj, (float)c);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setShortValue(Object obj, short i) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isLongType())
      setLongValue(obj, (long) i);
    else if (type.isIntType()) 
      setIntValue(obj, (int) i);
    else if (type.isShortType())
      put32(obj, (int)i);
    else if (type.isDoubleType())
      setDoubleValue(obj, (double)i);
    else if (type.isFloatType()) 
      setFloatValue(obj, (float)i);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setIntValue(Object obj, int i) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isLongType())
      setLongValue(obj, (long) i);
    else if (type.isIntType()) 
      put32(obj, i);
    else if (type.isDoubleType())
      setDoubleValue(obj, (double)i);
    else if (type.isFloatType()) 
      setFloatValue(obj, (float)i);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setFloatValue(Object obj, float f) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isDoubleType())
      setDoubleValue(obj, (double)f);
    else if (type.isFloatType()) 
      put32(obj, Float.floatToIntBits(f));
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setLongValue(Object obj, long l) throws IllegalArgumentException {
    VM_Type type = getType();
    if (type.isLongType()) 
      put64(obj, l);
    else if (type.isDoubleType())
      setDoubleValue(obj, (double)l);
    else if (type.isFloatType()) 
      setFloatValue(obj, (float)l);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setDoubleValue(Object obj, double d) throws IllegalArgumentException {
    VM_Type type = getType();
    if (!type.isDoubleType()) throw new IllegalArgumentException("field type mismatch");
    put64(obj, Double.doubleToLongBits(d));
  }

  private void put32(Object obj, int value) {
    if (isStatic()) {
      VM_Statics.setSlotContents( offset >>> 2, value );
    } else {
      if (obj == null) throw new NullPointerException();
      VM_Magic.setIntAtOffset(obj, offset, value);
    }
  }

  private void put64(Object obj, long value) {
    if (isStatic()) {
      VM_Statics.setSlotContents( offset >>> 2, value );
    } else {
      if (obj == null) throw new NullPointerException();
      VM_Magic.setLongAtOffset(obj, offset, value);
    }
  }
}
