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
 * @author Derek Lieber
 */
public final class VM_Field extends VM_Member implements VM_ClassLoaderConstants {
  //------------------------------------------------------------------//
  //                       Section 0.                                 //
  //       The following are always available.                        //
  //------------------------------------------------------------------//

  /**
   * Get type of this field's value.
   */ 
  public final VM_Type getType() throws VM_PragmaUninterruptible {
    return type;
  }

  /**
   * Get size of this field's value, in bytes.
   */ 
  public final int getSize() throws VM_PragmaUninterruptible {
    return type.getStackWords() << 2;
  }

  //------------------------------------------------------------------//
  //                       Section 1.                                 //
  //  The following are available after the declaring class has been  //
  //  "loaded".                                                       //
  //------------------------------------------------------------------//

  //
  // Attributes.
  //

  /**
   * Shared among all instances of this class?
   */ 
  public final boolean isStatic() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * May only be assigned once?
   */ 
  public final boolean isFinal() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Value not to be cached in a register?
   */ 
  public final boolean isVolatile() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_VOLATILE) != 0;
  }

  /**
   * Value not to be written/read by persistent object manager?
   */ 
  final boolean isTransient() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isLoaded());
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return (modifiers & ACC_TRANSIENT) != 0;
  }

  /**
   * Get index of constant pool entry containing this 
   * "static final constant" field's value.
   * @return constant pool index (0 --> field is not a "static final constant")
   */ 
  final int getConstantValueIndex() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return constantValueIndex;
  }

  public final int getModifiers() {
      return modifiers & 
	  (ACC_PUBLIC | 
	   ACC_PRIVATE |
	   ACC_PROTECTED |
	   ACC_STATIC | 
	   ACC_FINAL |
	   ACC_VOLATILE |
	   ACC_TRANSIENT);
  }

  //--------------------------------------------------------------------//
  //                         Section 2.                                 //
  // The following are available after the declaring class has been     //
  // "resolved".                                                        //
  //----------------------------------------------- --------------------//

  /**
   * The actual field that this object represents
   */
  public final VM_Field resolve() {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (!isLoaded()) return VM_ClassLoader.repairField(this);
    return this;
  }
 
  /**
   * Get offset of this field's value, in bytes.
   * 
   * <p> For static field, offset is with respect to
   * virtual machine's "table of contents" (jtoc).
   * 
   * <p> For non-static field, offset is with respect to
   * object pointer.
   */ 
  public final int getOffset() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isResolved());
    if (VM.VerifyAssertions) VM._assert(isLoaded());
    return offset;
  }


  //-------------------------------------------------------------------//
  // The following are available for an object instance that owns      //
  // this field.                                                       //
  // Obtain the value of an object described by this VM_Field          //
  //-------------------------------------------------------------------//


  /**
    * If this field is an object, return that object
    * If this field is a primitive, get the value and wrap it in an object
    *
    */
  public final Object getObject(Object obj) {
    if (type.isReferenceType()) {
      return getObjectValue(obj);
    } else {
      // TODO: wrap the primitive value as object
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
    if (VM.VerifyAssertions) VM._assert(isLoaded());
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
    if (!type.isBooleanType()) throw new IllegalArgumentException("field type mismatch");
    int bits = get32Bits(obj);
    if (bits == 0)
      return false;
    else
      return true;
  }

  public final byte getByteValue(Object obj) throws IllegalArgumentException {
    if (!type.isByteType()) throw new IllegalArgumentException("field type mismatch");
    int bits = get32Bits(obj);
    return (byte)bits;
  }

  public final char getCharValue(Object obj) throws IllegalArgumentException {
    if (!type.isCharType()) throw new IllegalArgumentException("field type mismatch");
    return (char)get32Bits(obj);
  }

  public final short getShortValue(Object obj) throws IllegalArgumentException {
    if (!type.isShortType()) throw new IllegalArgumentException("field type mismatch");
    return (short)get32Bits(obj);
  }

  public final int getIntValue(Object obj) throws IllegalArgumentException {
    if (!type.isIntType()) throw new IllegalArgumentException("field type mismatch");
    return get32Bits(obj);
  }

  public final long getLongValue(Object obj) throws IllegalArgumentException {
    if (!type.isLongType()) throw new IllegalArgumentException("field type mismatch");
    return get64Bits(obj);
  }

  public final float getFloatValue(Object obj) throws IllegalArgumentException {
    if (!type.isFloatType()) throw new IllegalArgumentException("field type mismatch");
    return Float.intBitsToFloat(get32Bits(obj));
  }

  public final double getDoubleValue(Object obj) throws IllegalArgumentException {
    if (!type.isDoubleType()) throw new IllegalArgumentException("field type mismatch");
    return Double.longBitsToDouble(get64Bits(obj));
  }

  private int get32Bits(Object obj) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());

    // then a static object, get from jtoc
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsInt(offset >>> 2);  // divide by 4 to get words from bytes
    } else {
      if (VM.VerifyAssertions)
	VM._assert(getSize()==4); // assume instance fields are 1 or 2 words
      if (obj==null)
	throw new NullPointerException();
      return VM_Magic.getIntAtOffset(obj, offset);
    }
  }

  private long get64Bits(Object obj) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());

    if (isStatic()) {
      long result = VM_Statics.getSlotContentsAsLong(offset >>> 2);
      return result;
    } else {
      if (VM.VerifyAssertions)
	VM._assert(getSize()==8); // assume instance fields are 1 or 2 words
      if (obj==null)
	throw new NullPointerException();
      long result = VM_Magic.getLongAtOffset(obj, offset);
      return result;
    }
  }


  //--------------------------------------------------------------------------------------------------//
  //               The assign a value to this field of an object.                                     //
  //--------------------------------------------------------------------------------------------------//

  public void set(Object obj, Object value) throws IllegalArgumentException, IllegalAccessException
  {
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
    if (VM.VerifyAssertions) VM._assert(isLoaded());

    if ( ref != null ) {
      VM_Type actualType = VM_Magic.getObjectType(ref);
      boolean ok = false;
      try {
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
    if (type.isBooleanType()) 
      if (b==true)
	put32(obj, 1 );
      else
	put32(obj, 0 );
    else
      throw new IllegalArgumentException("field type mismatch");
  }


  public final void setByteValue(Object obj, byte b) throws IllegalArgumentException {
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
    if (type.isDoubleType())
      setDoubleValue(obj, (double)f);
    else if (type.isFloatType()) 
      put32(obj, Float.floatToIntBits(f));
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  public final void setLongValue(Object obj, long l) throws IllegalArgumentException {
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
    if (!type.isDoubleType()) throw new IllegalArgumentException("field type mismatch");
    put64(obj, Double.doubleToLongBits(d));
  }

  private void put32(Object obj, int value) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());

    if (isStatic()) {
      VM_Statics.setSlotContents( offset >>> 2, value );
    } else {
      if (obj == null) throw new NullPointerException();
      VM_Magic.setIntAtOffset(obj, offset, value);
    }
  }

  private void put64(Object obj, long value) {
    if (VM.VerifyAssertions) VM._assert(isLoaded());

    if (isStatic()) {
      VM_Statics.setSlotContents( offset >>> 2, value );
    } else {
      if (obj == null) throw new NullPointerException();
      VM_Magic.setLongAtOffset(obj, offset, value);
    }
  }


  //----------------//
  // Implementation //
  //----------------//

  private VM_Type type;               // field's type
  private int     constantValueIndex; // constant pool index of field's value (0 --> not a "static final constant")
  public  int     offset;             // field's jtoc/obj offset, in bytes

  // To guarantee uniqueness, only the VM_ClassLoader class may construct VM_Field instances.
  // All VM_Field creation should be performed by calling "VM_ClassLoader.findOrCreate" methods.
  //
  private VM_Field() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  VM_Field(VM_Class declaringClass, VM_Atom name, 
	   VM_Atom descriptor, int dictionaryId) {
    super(declaringClass, name, descriptor, dictionaryId);
    type = VM_ClassLoader.findOrCreateType(getDescriptor(), declaringClass.getClassLoader());
    offset = VM_Member.UNINITIALIZED_OFFSET;
  }

  final void load(DataInputStream input, int modifiers) throws IOException {
    this.modifiers = modifiers;
    readAttributes(input);
    this.modifiers |= ACC_LOADED;
  }

  private void readAttributes(DataInputStream input) throws IOException {
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName   = declaringClass.getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Field attributes
      if (attName == VM_ClassLoader.constantValueAttributeName) {
	constantValueIndex = input.readUnsignedShort();
	continue;
      }

      if (attName == VM_ClassLoader.deprecatedAttributeName) { // boring
	input.skipBytes(attLength);
	continue;
      }

      if (attName == VM_ClassLoader.syntheticAttributeName) { // boring
	input.skipBytes(attLength);
	continue;
      }
      
      input.skipBytes(attLength);
    }
  }
}

