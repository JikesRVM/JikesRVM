/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.classloader;

import com.ibm.jikesrvm.*;
import org.vmmagic.pragma.*;
import java.io.DataInputStream;
import java.io.IOException;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;

/**
 * A field of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @modified Ian Rogers
 */
public final class VM_Field extends VM_Member {

  /**
   * constant pool index of field's value (0 --> not a "static final constant")
   */
  private final int constantValueIndex; 
  
  /**
   * Create a field.
   *
   * @param declaringClass the VM_TypeReference object of the class
   * that declared this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this field.
   * @param signature generic type of this field.
   * @param constantValueIndex constant pool index of constant value
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   */
  private VM_Field(VM_TypeReference declaringClass,
                   VM_MemberReference memRef,
                   int modifiers,
                   VM_Atom signature,
                   int constantValueIndex,
                   VM_Annotation runtimeVisibleAnnotations[],
                   VM_Annotation runtimeInvisibleAnnotations[])
  {
    super(declaringClass, memRef, modifiers, signature,
          runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    this.constantValueIndex = constantValueIndex;
    memRef.asFieldReference().setResolvedMember(this);
  }

  /**
   * Read and create a field. NB only {@link VM_Class} is allowed to
   * create an instance of a VM_Field.
   * 
   * @param declaringClass the VM_TypeReference object of the class
   * that declared this field
   * @param constantPool the constant pool of the class loading this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the field's attributed from
   */
  static VM_Field readField(VM_TypeReference declaringClass,
                            int constantPool[], VM_MemberReference memRef,
                            int modifiers, DataInputStream input) throws IOException {
    // Read the attributes, processing the "non-boring" ones
    int cvi = 0;
    VM_Atom tmp_signature = null;
    VM_Annotation tmp_runtimeVisibleAnnotations[] = null;
    VM_Annotation tmp_runtimeInvisibleAnnotations[] = null;
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName   = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      int     attLength = input.readInt();
      if (attName == VM_ClassLoader.constantValueAttributeName) {
        cvi = input.readUnsignedShort();
      } else if (attName == VM_ClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == VM_ClassLoader.signatureAttributeName) {
        tmp_signature = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == VM_ClassLoader.runtimeVisibleAnnotationsAttributeName) {
        tmp_runtimeVisibleAnnotations = VM_AnnotatedElement.readAnnotations(constantPool, input, 2,
                                                                            declaringClass.getClassLoader());
      } else if (VM_AnnotatedElement.retainRuntimeInvisibleAnnotations &&
                 (attName == VM_ClassLoader.runtimeInvisibleAnnotationsAttributeName)) {
        tmp_runtimeInvisibleAnnotations = VM_AnnotatedElement.readAnnotations(constantPool, input, 2,
                                                                              declaringClass.getClassLoader());
      } else {
        // all other attributes are boring...
        input.skipBytes(attLength);
      }
    }
    return new VM_Field(declaringClass, memRef, modifiers & APPLICABLE_TO_FIELDS, tmp_signature, cvi,
                        tmp_runtimeVisibleAnnotations, tmp_runtimeInvisibleAnnotations);
  }

  /**
   * Create a field for a synthetic annotation class
   */
  static VM_Field createAnnotationField(VM_TypeReference annotationClass,
                                        VM_MemberReference memRef) {
    return new VM_Field(annotationClass, memRef, ACC_PRIVATE|ACC_SYNTHETIC, null, 0, null, null);
  }

  /**
   * Get type of this field's value.
   */ 
  public final VM_TypeReference getType() throws UninterruptiblePragma {
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
  public final boolean isStatic() throws UninterruptiblePragma {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * May only be assigned once?
   */ 
  public final boolean isFinal() throws UninterruptiblePragma {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Value not to be cached in a register?
   */ 
  public final boolean isVolatile() throws UninterruptiblePragma {
    return (modifiers & ACC_VOLATILE) != 0;
  }

  /**
   * Value not to be written/read by persistent object manager?
   */ 
  public final boolean isTransient() throws UninterruptiblePragma {
    return (modifiers & ACC_TRANSIENT) != 0;
  }

  /**
   * Not present in source code file?
   */
  public boolean isSynthetic() {
    return (modifiers & ACC_SYNTHETIC) != 0;
  }

 /**
  * Enum constant
  */
  public boolean isEnumConstant() {
    return (modifiers & ACC_ENUM) != 0;
  }

  /**
   * Get index of constant pool entry containing this 
   * "static final constant" field's value.
   * @return constant pool index (0 --> field is not a "static final constant")
   */ 
  final int getConstantValueIndex() throws UninterruptiblePragma {
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
      if (type.isBooleanType())  return Boolean.valueOf(getBooleanValueUnchecked(obj));
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
      return VM_Statics.getSlotContentsAsObject(getOffset());
    } else {
      return VM_Magic.getObjectAtOffset(obj, getOffset());
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
      return VM_Statics.getSlotContentsAsInt(getOffset());
    } else {
      return VM_Magic.getIntAtOffset(obj, getOffset());
    }
  }

  private long get64Bits(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsLong(getOffset()); 
    } else {
      return VM_Magic.getLongAtOffset(obj, getOffset());
    }
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   */
  public final void setObjectValueUnchecked(Object obj, Object ref) {
    if (isStatic()) {
      if (MM_Interface.NEEDS_PUTSTATIC_WRITE_BARRIER)
        MM_Interface.putstaticWriteBarrier(getOffset(), ref);
      else
        VM_Statics.setSlotContents(getOffset(), ref);
    } else {
      if (MM_Interface.NEEDS_WRITE_BARRIER)
        MM_Interface.putfieldWriteBarrier(obj, getOffset(), ref, getId());
      else
        VM_Magic.setObjectAtOffset(obj, getOffset(), ref);
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
      VM_Statics.setSlotContents(getOffset(), value );
    } else {
      VM_Magic.setIntAtOffset(obj, getOffset(), value);
    }
  }

  private void put64(Object obj, long value) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), value);
    } else {
      VM_Magic.setLongAtOffset(obj, getOffset(), value);
    }
  }
}
