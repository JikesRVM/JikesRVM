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
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Word;

/**
 * A field of a java class.
 */
public final class RVMField extends RVMMember {

  /**
   * constant pool index of field's value (0 --> not a "static final constant")
   */
  private final int constantValueIndex;

  /**
   * The size of the field in bytes
   */
  private final byte size;

  /**
   * Does the field hold a reference value?
   */
  private final boolean reference;

  /**
   * Has the field been made traced?
   */
  private boolean madeTraced;

  /**
   * Create a field.
   *
   * @param declaringClass the TypeReference object of the class
   * that declared this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this field.
   * @param signature generic type of this field.
   * @param constantValueIndex constant pool index of constant value
   * @param annotations array of runtime visible annotations
   */
  private RVMField(TypeReference declaringClass, MemberReference memRef, short modifiers, Atom signature,
                   int constantValueIndex, RVMAnnotation[] annotations) {
    super(declaringClass, memRef, modifiers, signature, annotations);
    this.constantValueIndex = constantValueIndex;
    TypeReference typeRef = memRef.asFieldReference().getFieldContentsType();
    this.size = (byte)typeRef.getMemoryBytes();
    this.reference = typeRef.isReferenceType();
    this.madeTraced = false;
    if (VM.runningVM && isUntraced()) {
      VM.sysFail("Untraced field " + toString() + " created at runtime!");
    }
  }

  /**
   * Read and create a field. NB only {@link RVMClass} is allowed to
   * create an instance of a RVMField.
   *
   * @param declaringClass the TypeReference object of the class
   * that declared this field
   * @param constantPool the constant pool of the class loading this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the field's attributed from
   */
  static RVMField readField(TypeReference declaringClass, int[] constantPool, MemberReference memRef,
                            short modifiers, DataInputStream input) throws IOException {
    // Read the attributes, processing the "non-boring" ones
    int cvi = 0;
    Atom signature = null;
    RVMAnnotation[] annotations = null;
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      Atom attName = ClassFileReader.getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();
      if (attName == RVMClassLoader.constantValueAttributeName) {
        cvi = input.readUnsignedShort();
      } else if (attName == RVMClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == RVMClassLoader.signatureAttributeName) {
        signature = ClassFileReader.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.runtimeVisibleAnnotationsAttributeName) {
        annotations = AnnotatedElement.readAnnotations(constantPool, input, declaringClass.getClassLoader());
      } else {
        // all other attributes are boring...
        int skippedAmount = input.skipBytes(attLength);
        if (skippedAmount != attLength) {
          throw new IOException("Unexpected short skip");
        }
      }
    }
    return new RVMField(declaringClass,
                        memRef,
                        (short) (modifiers & APPLICABLE_TO_FIELDS),
                        signature,
                        cvi,
                        annotations);
  }

  /**
   * Create a field for a synthetic annotation class
   */
  static RVMField createAnnotationField(TypeReference annotationClass, MemberReference memRef) {
    return new RVMField(annotationClass, memRef, (short) (ACC_PRIVATE | ACC_SYNTHETIC), null, 0, null);
  }

  /**
   * Get type of this field's value.
   */
  @Uninterruptible
  public TypeReference getType() {
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
    return size;
  }

  /**
   * Does the field hold a reference?
   */
  public boolean isTraced() {
    return (reference && !isUntraced()) || madeTraced;
  }

  /**
   * Does the field hold a reference?
   */
  public boolean isReferenceType() {
    return reference;
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
  public boolean isRuntimeFinal() {
   return hasRuntimeFinalAnnotation();
  }

  /**
   * Is this field invisible to the memory management system.
   */
  public boolean isUntraced() {
    return hasUntracedAnnotation();
  }

  /**
   * Make this field a traced field by garbage collection. Affects all
   * subclasses of the class in which this field is defined.
   */
  public void makeTraced() {
    madeTraced = true;
    getDeclaringClass().makeFieldTraced(this);
  }

  /**
   * Get the value from the runtime final field
   * @return whether the method has a pure annotation
   */
  public boolean getRuntimeFinalValue() {
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
  // Low level support for various reflective operations               //
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
    if (isReferenceType()) {
      return getObjectValueUnchecked(obj);
    } else {
      TypeReference type = getType();
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
   * @return the reference described by this RVMField from the given object.
   */
  public Object getObjectValueUnchecked(Object obj) {
    if (isStatic()) {
      if (MemoryManagerConstants.NEEDS_GETSTATIC_READ_BARRIER && !isUntraced()) {
        return MemoryManager.getstaticReadBarrier(getOffset(), getId());
      } else {
        return Statics.getSlotContentsAsObject(getOffset());
      }
    } else {
      if (MemoryManagerConstants.NEEDS_READ_BARRIER && !isUntraced()) {
        return MemoryManager.getfieldReadBarrier(obj, getOffset(), getId());
      } else {
        return Magic.getObjectAtOffset(obj, getOffset());
      }
    }
  }

  public Word getWordValueUnchecked(Object obj) {
    if (isStatic()) {
      return Statics.getSlotContentsAsAddress(getOffset()).toWord();
    } else {
      return Magic.getWordAtOffset(obj, getOffset());
    }
  }

  public boolean getBooleanValueUnchecked(Object obj) {
    byte bits;
    if (isStatic()) {
      bits = (byte) Statics.getSlotContentsAsInt(getOffset());
    } else {
      bits = Magic.getUnsignedByteAtOffset(obj, getOffset());
    }
    return (bits != 0);
  }

  public byte getByteValueUnchecked(Object obj) {
    if (isStatic()) {
      return (byte) Statics.getSlotContentsAsInt(getOffset());
    } else {
      return Magic.getByteAtOffset(obj, getOffset());
    }
  }

  public char getCharValueUnchecked(Object obj) {
    if (isStatic()) {
      return (char) Statics.getSlotContentsAsInt(getOffset());
    } else {
      return Magic.getCharAtOffset(obj, getOffset());
    }
  }

  public short getShortValueUnchecked(Object obj) {
    if (isStatic()) {
      return (short) Statics.getSlotContentsAsInt(getOffset());
    } else {
      return Magic.getShortAtOffset(obj, getOffset());
    }
  }

  public int getIntValueUnchecked(Object obj) {
    return get32Bits(obj);
  }

  public long getLongValueUnchecked(Object obj) {
    return get64Bits(obj);
  }

  public float getFloatValueUnchecked(Object obj) {
    return Magic.intBitsAsFloat(get32Bits(obj));
  }

  public double getDoubleValueUnchecked(Object obj) {
    return Magic.longBitsAsDouble(get64Bits(obj));
  }

  private int get32Bits(Object obj) {
    if (isStatic()) {
      return Statics.getSlotContentsAsInt(getOffset());
    } else {
      return Magic.getIntAtOffset(obj, getOffset());
    }
  }

  private long get64Bits(Object obj) {
    if (isStatic()) {
      return Statics.getSlotContentsAsLong(getOffset());
    } else {
      return Magic.getLongAtOffset(obj, getOffset());
    }
  }

  /**
   * assign one object ref from heap using RVM object model, GC safe.
   * @param obj the object whose field is to be modified, or null if the field is static.
   * @param ref the object reference to be assigned.
   */
  public void setObjectValueUnchecked(Object obj, Object ref) {
    if (isStatic()) {
      if (MemoryManagerConstants.NEEDS_PUTSTATIC_WRITE_BARRIER && !isUntraced()) {
        MemoryManager.putstaticWriteBarrier(ref, getOffset(), getId());
      } else {
        Statics.setSlotContents(getOffset(), ref);
      }
    } else {
      if (MemoryManagerConstants.NEEDS_WRITE_BARRIER && !isUntraced()) {
        MemoryManager.putfieldWriteBarrier(obj, ref, getOffset(), getId());
      } else {
        Magic.setObjectAtOffset(obj, getOffset(), ref);
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
      Statics.setSlotContents(getOffset(), ref);
    } else {
      Magic.setWordAtOffset(obj, getOffset(), ref);
    }
  }

  public void setBooleanValueUnchecked(Object obj, boolean b) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), b ? 1 : 0);
    } else {
      Magic.setByteAtOffset(obj, getOffset(), b ? (byte) 1 : (byte) 0);
    }
  }

  public void setByteValueUnchecked(Object obj, byte b) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), b);
    } else {
      Magic.setByteAtOffset(obj, getOffset(), b);
    }
  }

  public void setCharValueUnchecked(Object obj, char c) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), c);
    } else {
      Magic.setCharAtOffset(obj, getOffset(), c);
    }
  }

  public void setShortValueUnchecked(Object obj, short i) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), i);
    } else {
      Magic.setCharAtOffset(obj, getOffset(), (char) i);
    }
  }

  public void setIntValueUnchecked(Object obj, int i) {
    put32(obj, i);
  }

  public void setFloatValueUnchecked(Object obj, float f) {
    put32(obj, Magic.floatAsIntBits(f));
  }

  public void setLongValueUnchecked(Object obj, long l) {
    put64(obj, l);
  }

  public void setDoubleValueUnchecked(Object obj, double d) {
    put64(obj, Magic.doubleAsLongBits(d));
  }

  private void put32(Object obj, int value) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), value);
    } else {
      Magic.setIntAtOffset(obj, getOffset(), value);
    }
  }

  private void put64(Object obj, long value) {
    if (isStatic()) {
      Statics.setSlotContents(getOffset(), value);
    } else {
      Magic.setLongAtOffset(obj, getOffset(), value);
    }
  }
}
