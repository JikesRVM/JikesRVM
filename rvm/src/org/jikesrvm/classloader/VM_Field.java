/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import org.jikesrvm.VM;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Word;

/**
 * A field of a java class.
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
   * @param annotations array of runtime visible annotations
   */
  private VM_Field(VM_TypeReference declaringClass, VM_MemberReference memRef, short modifiers, VM_Atom signature,
                   int constantValueIndex, VM_Annotation[] annotations) {
    super(declaringClass, memRef, modifiers, signature, annotations);
    this.constantValueIndex = constantValueIndex;
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
  static VM_Field readField(VM_TypeReference declaringClass, int[] constantPool, VM_MemberReference memRef,
                            short modifiers, DataInputStream input) throws IOException {
    // Read the attributes, processing the "non-boring" ones
    int cvi = 0;
    VM_Atom signature = null;
    VM_Annotation[] annotations = null;
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      VM_Atom attName = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();
      if (attName == VM_ClassLoader.constantValueAttributeName) {
        cvi = input.readUnsignedShort();
      } else if (attName == VM_ClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == VM_ClassLoader.signatureAttributeName) {
        signature = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == VM_ClassLoader.runtimeVisibleAnnotationsAttributeName) {
        annotations = VM_AnnotatedElement.readAnnotations(constantPool, input, 2, declaringClass.getClassLoader());
      } else {
        // all other attributes are boring...
        input.skipBytes(attLength);
      }
    }
    return new VM_Field(declaringClass,
                        memRef,
                        (short) (modifiers & APPLICABLE_TO_FIELDS),
                        signature,
                        cvi,
                        annotations);
  }

  /**
   * Create a field for a synthetic annotation class
   */
  static VM_Field createAnnotationField(VM_TypeReference annotationClass, VM_MemberReference memRef) {
    return new VM_Field(annotationClass, memRef, (short) (ACC_PRIVATE | ACC_SYNTHETIC), null, 0, null);
  }

  /**
   * Get type of this field's value.
   */
  @Uninterruptible
  public VM_TypeReference getType() {
    return memRef.asFieldReference().getFieldContentsType();
  }

  /**
   * How many stackslots do value of this type take?
   */
  public int getNumberOfStackSlots() {
    return getType().getStackWords();
  }

  /**
   * How many bytes of memory words do value of this type take?
   */
  public int getSize() {
    return getType().getMemoryBytes();
  }

  /**
   * Shared among all instances of this class?
   */
  @Uninterruptible
  public boolean isStatic() {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * May only be assigned once?
   */
  @Uninterruptible
  public boolean isFinal() {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Value not to be cached in a register?
   */
  @Uninterruptible
  public boolean isVolatile() {
    return (modifiers & ACC_VOLATILE) != 0;
  }

  /**
   * Value not to be written/read by persistent object manager?
   */
  @Uninterruptible
  public boolean isTransient() {
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
   * Is the field RuntimeFinal? That is can the annotation value be used in
   * place a reading the field.
   * @return whether the method has a pure annotation
   */
  public final boolean isRuntimeFinal() {
   return hasRuntimeFinalAnnotation();
  }

  /**
   * Get the value from the runtime final field
   * @return whether the method has a pure annotation
   */
  public final boolean getRuntimeFinalValue() {
    org.vmmagic.pragma.RuntimeFinal ann;
    if (VM.runningVM) {
      ann = getAnnotation(org.vmmagic.pragma.RuntimeFinal.class);
    } else {
      try {
      ann = getDeclaringClass().getClassForType().getField(getName().toString())
      .getAnnotation(org.vmmagic.pragma.RuntimeFinal.class);
      } catch (NoSuchFieldException e) {
        throw new Error(e);
      }
    }
    return ann.value();
  }

  /**
   * Get index of constant pool entry containing this
   * "static final constant" field's value.
   * @return constant pool index (0 --> field is not a "static final constant")
   */
  @Uninterruptible
  int getConstantValueIndex() {
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
  public Object getObjectUnchecked(Object obj) {
    VM_TypeReference type = getType();
    if (type.isReferenceType()) {
      return getObjectValueUnchecked(obj);
    } else {
      if (type.isCharType()) return getCharValueUnchecked(obj);
      if (type.isDoubleType()) return getDoubleValueUnchecked(obj);
      if (type.isFloatType()) return getFloatValueUnchecked(obj);
      if (type.isLongType()) return getLongValueUnchecked(obj);
      if (type.isIntType()) return getIntValueUnchecked(obj);
      if (type.isShortType()) return getShortValueUnchecked(obj);
      if (type.isByteType()) return getByteValueUnchecked(obj);
      if (type.isBooleanType()) return getBooleanValueUnchecked(obj);
      return null;
    }
  }

  /**
   * Read one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be read,
   * or null if the field is static.
   * @return the reference described by this VM_Field from the given object.
   */
  public Object getObjectValueUnchecked(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsObject(getOffset());
    } else {
      return VM_Magic.getObjectAtOffset(obj, getOffset());
    }
  }

  public Word getWordValueUnchecked(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsAddress(getOffset()).toWord();
    } else {
      return VM_Magic.getWordAtOffset(obj, getOffset());
    }
  }

  public boolean getBooleanValueUnchecked(Object obj) {
    byte bits;
    if (isStatic()) {
      bits = (byte) VM_Statics.getSlotContentsAsInt(getOffset());
    } else {
      bits = VM_Magic.getUnsignedByteAtOffset(obj, getOffset());
    }
    return (bits != 0);
  }

  public byte getByteValueUnchecked(Object obj) {
    if (isStatic()) {
      return (byte) VM_Statics.getSlotContentsAsInt(getOffset());
    } else {
      return VM_Magic.getByteAtOffset(obj, getOffset());
    }
  }

  public char getCharValueUnchecked(Object obj) {
    if (isStatic()) {
      return (char) VM_Statics.getSlotContentsAsInt(getOffset());
    } else {
      return VM_Magic.getCharAtOffset(obj, getOffset());
    }
  }

  public short getShortValueUnchecked(Object obj) {
    if (isStatic()) {
      return (short) VM_Statics.getSlotContentsAsInt(getOffset());
    } else {
      return VM_Magic.getShortAtOffset(obj, getOffset());
    }
  }

  public int getIntValueUnchecked(Object obj) {
    return get32Bits(obj);
  }

  public long getLongValueUnchecked(Object obj) {
    return get64Bits(obj);
  }

  public float getFloatValueUnchecked(Object obj) {
    return VM_Magic.intBitsAsFloat(get32Bits(obj));
  }

  public double getDoubleValueUnchecked(Object obj) {
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
  
  private Word getWord(Object obj) {
    if (isStatic()) {
      return VM_Statics.getSlotContentsAsAddress(getOffset()).toWord();
    } else {
      return VM_Magic.getWordAtOffset(obj, getOffset());
    }
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   */
  public void setObjectValueUnchecked(Object obj, Object ref) {
    if (isStatic()) {
      if (MM_Constants.NEEDS_PUTSTATIC_WRITE_BARRIER) {
        MM_Interface.putstaticWriteBarrier(getOffset(), ref);
      } else {
        VM_Statics.setSlotContents(getOffset(), ref);
      }
    } else {
      if (MM_Constants.NEEDS_WRITE_BARRIER) {
        MM_Interface.putfieldWriteBarrier(obj, getOffset(), ref, getId());
      } else {
        VM_Magic.setObjectAtOffset(obj, getOffset(), ref);
      }
    }
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   */
  public void setWordValueUnchecked(Object obj, Word ref) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), ref);
    } else {
      VM_Magic.setWordAtOffset(obj, getOffset(), ref);
    }
  }

  public void setBooleanValueUnchecked(Object obj, boolean b) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), b ? 1 : 0);
    } else {
      VM_Magic.setByteAtOffset(obj, getOffset(), b ? (byte) 1 : (byte) 0);
    }
  }

  public void setByteValueUnchecked(Object obj, byte b) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), b);
    } else {
      VM_Magic.setByteAtOffset(obj, getOffset(), b);
    }
  }

  public void setCharValueUnchecked(Object obj, char c) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), c);
    } else {
      VM_Magic.setCharAtOffset(obj, getOffset(), c);
    }
  }

  public void setShortValueUnchecked(Object obj, short i) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), i);
    } else {
      VM_Magic.setCharAtOffset(obj, getOffset(), (char) i);
    }
  }

  public void setIntValueUnchecked(Object obj, int i) {
    put32(obj, i);
  }

  public void setFloatValueUnchecked(Object obj, float f) {
    put32(obj, VM_Magic.floatAsIntBits(f));
  }

  public void setLongValueUnchecked(Object obj, long l) {
    put64(obj, l);
  }

  public void setDoubleValueUnchecked(Object obj, double d) {
    put64(obj, VM_Magic.doubleAsLongBits(d));
  }

  private void put32(Object obj, int value) {
    if (isStatic()) {
      VM_Statics.setSlotContents(getOffset(), value);
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
