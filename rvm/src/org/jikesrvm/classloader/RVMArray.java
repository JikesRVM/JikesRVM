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

import static org.jikesrvm.mm.mminterface.Barriers.*;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Description of a java "array" type. <p>
 *
 * This description is not read from a ".class" file, but rather
 * is manufactured by the vm as execution proceeds.
 *
 * @see RVMType
 * @see RVMClass
 * @see Primitive
 * @see UnboxedType
 */
@NonMoving
public final class RVMArray extends RVMType implements Constants, ClassLoaderConstants {

  /*
   * We hold on to a number of commonly used arrays for easy access.
   */
  public static final RVMArray BooleanArray;
  public static final RVMArray ByteArray;
  public static final RVMArray CharArray;
  public static final RVMArray ShortArray;
  public static final RVMArray IntArray;
  public static final RVMArray LongArray;
  public static final RVMArray FloatArray;
  public static final RVMArray DoubleArray;
  public static final RVMArray JavaLangObjectArray;

  static {
    BooleanArray = (RVMArray) TypeReference.BooleanArray.resolve();
    CharArray = (RVMArray) TypeReference.CharArray.resolve();
    FloatArray = (RVMArray) TypeReference.FloatArray.resolve();
    DoubleArray = (RVMArray) TypeReference.DoubleArray.resolve();
    ByteArray = (RVMArray) TypeReference.ByteArray.resolve();
    ShortArray = (RVMArray) TypeReference.ShortArray.resolve();
    IntArray = (RVMArray) TypeReference.IntArray.resolve();
    LongArray = (RVMArray) TypeReference.LongArray.resolve();
    JavaLangObjectArray = (RVMArray) TypeReference.JavaLangObjectArray.resolve();
  }

  /**
   * The RVMType object for elements of this array type.
   */
  private final RVMType elementType;

  /**
   * The log of the element size for this array type.
   */
  private final int logElementSize;

  /**
   * The RVMType object for the innermost element of this array type.
   */
  private final RVMType innermostElementType;

  /**
   * The dimension of the innermost element of this array type.
   */
  @Entrypoint
  @SuppressWarnings({"unused"})
  private final int innermostElementTypeDimension;

  /**
   * The desired alignment for instances of this type.
   * Cached rather than computed because this is a frequently
   * asked question
   */
  private final int alignment;

  /**
   * Reference Count GC: is this type acyclic?
   */
  private final boolean acyclic;

  /**
   * The TIB for this type, created when the array is resolved.
   */
  private TIB typeInformationBlock;

  /**
   * current class-loading stage (loaded, resolved or initialized)
   */
  private byte state;

  /**
   * Is this array type in the bootimage?
   */
  private boolean inBootImage;

  /**
   * Name - something like "[I" or "[Ljava.lang.String;"
   */
  @Override
  @Pure
  public String toString() {
    return getDescriptor().toString().replace('/', '.');
  }

  /**
   * @return java Expression stack space requirement.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getStackWords() {
    return 1;
  }

  /**
   * Space required in memory in bytes.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getMemoryBytes() {
    return BYTES_IN_ADDRESS;
  }

  /**
   * @return element type.
   */
  @Uninterruptible
  public RVMType getElementType() {
    return elementType;
  }

  /**
   * @return innermost element type
   */
  @Uninterruptible
  public RVMType getInnermostElementType() {
    return innermostElementType;
  }

  /**
   * @return alignment for instances of this array type
   */
  @Uninterruptible
  public int getAlignment() {
    return alignment;
  }

  /**
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  @Uninterruptible
  public int getLogElementSize() {
    return logElementSize;
  }

  /**
   * Calculate the size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  private int computeLogElementSize() {
    if (elementType.getTypeRef().equals(TypeReference.Code)) {
      return ArchitectureSpecific.ArchConstants.LG_INSTRUCTION_WIDTH;
    }
    switch (getDescriptor().parseForArrayElementTypeCode()) {
      case ClassTypeCode:
        return LOG_BYTES_IN_ADDRESS;
      case ArrayTypeCode:
        return LOG_BYTES_IN_ADDRESS;
      case BooleanTypeCode:
        return LOG_BYTES_IN_BOOLEAN;
      case ByteTypeCode:
        return 0;
      case ShortTypeCode:
        return LOG_BYTES_IN_SHORT;
      case IntTypeCode:
        return LOG_BYTES_IN_INT;
      case LongTypeCode:
        return LOG_BYTES_IN_LONG;
      case FloatTypeCode:
        return LOG_BYTES_IN_FLOAT;
      case DoubleTypeCode:
        return LOG_BYTES_IN_DOUBLE;
      case CharTypeCode:
        return LOG_BYTES_IN_CHAR;
    }
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return -1;
  }

  /**
   * Total size, in bytes, of an instance of this array type (including object header).
   * @param numelts number of array elements in the instance
   * @return size in bytes
   */
  @Inline
  @Pure
  @Uninterruptible
  public int getInstanceSize(int numelts) {
    return ObjectModel.computeArrayHeaderSize(this) + (numelts << getLogElementSize());
  }

  /**
   * Does this class override java.lang.Object.finalize()?
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean hasFinalizer() {
    return false;
  }

  /**
   * Static fields of this array type.
   */
  @Override
  @Pure
  public RVMField[] getStaticFields() {
    return RVMType.JavaLangObjectType.getStaticFields();
  }

  /**
   * Non-static fields of this array type.
   */
  @Override
  @Pure
  public RVMField[] getInstanceFields() {
    return RVMType.JavaLangObjectType.getInstanceFields();
  }

  /**
   * Statically dispatched methods of this array type.
   */
  @Override
  @Pure
  public RVMMethod[] getStaticMethods() {
    return RVMType.JavaLangObjectType.getStaticMethods();
  }

  /**
   * Virtually dispatched methods of this array type.
   */
  @Override
  @Pure
  public RVMMethod[] getVirtualMethods() {
    return RVMType.JavaLangObjectType.getVirtualMethods();
  }

  /**
   * Runtime type information for this array type.
   */
  @Override
  @Pure
  @Uninterruptible
  public TIB getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return typeInformationBlock;
  }

  /**
   * get number of superclasses to Object
   * @return 1
   */
  @Override
  @Pure
  @Uninterruptible
  public int getTypeDepth() {
    return 1;
  }

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isAcyclicReference() {
    return acyclic;
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes
   */
  @Override
  @Pure
  @Uninterruptible
  public int getDimensionality() {
    return dimension;
  }

  /**
   * Resolution status.
   */
  @Override
  @Uninterruptible
  public boolean isResolved() {
    return state >= CLASS_RESOLVED;
  }

  /**
   * Instantiation status.
   */
  @Override
  @Uninterruptible
  public boolean isInstantiated() {
    return state >= CLASS_INSTANTIATED;
  }

  /**
   * Initialization status.
   */
  @Override
  @Uninterruptible
  public boolean isInitialized() {
    return state == CLASS_INITIALIZED;
  }

  /**
   * Only intended to be used by the BootImageWriter
   */
  @Override
  public void markAsBootImageClass() {
    inBootImage = true;
  }

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Override
  @Uninterruptible
  public boolean isInBootImage() {
    return inBootImage;
  }

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * Offset.max() if instances of this type do not have thin lock words.
   */
  @Override
  @Uninterruptible
  public Offset getThinLockOffset() {
    return ObjectModel.defaultThinLockOffset();
  }

  /**
   * Whether or not this is an instance of RVMClass?
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isClassType() {
    return false;
  }

  /**
   * Whether or not this is an instance of RVMArray?
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isArrayType() {
    return true;
  }

  /**
   * Whether or not this is a primitive type
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isPrimitiveType() {
    return false;
  }

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isReferenceType() {
    return true;
  }

  /**
   * @return whether or not this is an unboxed type
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isUnboxedType() {
    return false;
  }

  /**
   * Constructor
   * @param typeRef
   * @param elementType
   */
  RVMArray(TypeReference typeRef, RVMType elementType) {
    super(typeRef, typeRef.getDimensionality(), null);
    this.elementType = elementType;
    this.logElementSize = computeLogElementSize();
    depth = 1;

    if (elementType.isArrayType()) {
      innermostElementType = elementType.asArray().getInnermostElementType();
    } else {
      innermostElementType = elementType;
    }
    innermostElementTypeDimension = innermostElementType.dimension;
    if (VM.BuildForIA32 && typeRef == TypeReference.CodeArray) {
      this.alignment = 16;
    } else if (BYTES_IN_DOUBLE != BYTES_IN_ADDRESS) {
      // Desired alignment on 32bit architectures
      if (elementType.isDoubleType() || elementType.isLongType()) {
        this.alignment = BYTES_IN_DOUBLE;
      } else {
        this.alignment = BYTES_IN_ADDRESS;
      }
    } else {
      this.alignment = BYTES_IN_DOUBLE;
    }

    // RCGC: Array is acyclic if its references are acyclic
    acyclic = elementType.isAcyclicReference();

    state = CLASS_LOADED;

    if (VM.verboseClassLoading) VM.sysWrite("[Loaded " + this.getDescriptor() + "]\n");
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded superclasses of " + this.getDescriptor() + "]\n");
  }

  /**
   * Resolve an array.
   * Also forces the resolution of the element type.
   */
  @Override
  public synchronized void resolve() {
    if (isResolved()) return;

    if (VM.VerifyAssertions) VM._assert(state == CLASS_LOADED);

    elementType.resolve();

    // Using the type information block for java.lang.Object as a template,
    // build a type information block for this new array type by copying the
    // virtual method fields and substituting an appropriate type field.
    //
    TIB javaLangObjectTIB = RVMType.JavaLangObjectType.getTypeInformationBlock();
    TIB allocatedTib = MemoryManager.newTIB(javaLangObjectTIB.numVirtualMethods());
    superclassIds = DynamicTypeCheck.buildSuperclassIds(this);
    doesImplement = DynamicTypeCheck.buildDoesImplement(this);
    publishResolved(allocatedTib, superclassIds, doesImplement);

    MemoryManager.notifyClassResolved(this);
  }

  /**
   * Atomically initialize the important parts of the TIB and let the world know this type is
   * resolved.
   *
   * @param allocatedTib The TIB that has been allocated for this type
   * @param superclassIds The calculated superclass ids array
   * @param doesImplement The calculated does implement array
   */
  @Uninterruptible
  private void publishResolved(TIB allocatedTib, short[] superclassIds, int[] doesImplement) {
    Statics.setSlotContents(getTibOffset(), allocatedTib);
    allocatedTib.setType(this);
    allocatedTib.setSuperclassIds(superclassIds);
    allocatedTib.setDoesImplement(doesImplement);
    if (!(elementType.isPrimitiveType()||elementType.isUnboxedType())) {
      allocatedTib.setArrayElementTib(elementType.getTypeInformationBlock());
    }
    typeInformationBlock = allocatedTib;
    state = CLASS_RESOLVED;
  }

  @Override
  public void allBootImageTypesResolved() {
    // nothing to do
  }

  /**
   * Instantiate an array.
   * Main result is to copy the virtual methods from JavaLangObject's tib.
   */
  @Override
  public synchronized void instantiate() {
    if (isInstantiated()) return;

    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);
    if (VM.TraceClassLoading && VM.runningVM) {
      VM.sysWrite("RVMArray: instantiate " + this + "\n");
    }

    // Initialize TIB slots for virtual methods (copy from superclass == Object)
    RVMType objectType = RVMType.JavaLangObjectType;
    int retries=0;
    while(!objectType.isInstantiated()) {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {}
      retries++;
      if (retries > 10) {
        throw new Error("Failed waiting for java.lang.Object to be instantiated during instantiation of "+toString());
      }
    }
    if (VM.VerifyAssertions) VM._assert(objectType.isInstantiated());
    TIB javaLangObjectTIB = objectType.getTypeInformationBlock();

    for(int i=0; i < javaLangObjectTIB.numVirtualMethods(); i++) {
      typeInformationBlock.setVirtualMethod(i, javaLangObjectTIB.getVirtualMethod(i));
    }

    SpecializedMethodManager.notifyTypeInstantiated(this);

    state = CLASS_INITIALIZED; // arrays have no "initialize" phase
  }

  /**
   * Initialization is a no-op (arrays have no <clinit> method).
   */
  @Override
  public void initialize() { }

  //-------------------------------------------------------------------------------------------------//
  //                                   Misc static methods.                                          //
  //-------------------------------------------------------------------------------------------------//

  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  @Pure
  public static RVMArray getPrimitiveArrayType(int atype) {
    switch (atype) {
      case 4:
        return BooleanArray;
      case 5:
        return CharArray;
      case 6:
        return FloatArray;
      case 7:
        return DoubleArray;
      case 8:
        return ByteArray;
      case 9:
        return ShortArray;
      case 10:
        return IntArray;
      case 11:
        return LongArray;
    }
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  //--------------------------------------------------------------------------------------------------//
  //                                     Support for array copy                                       //
  //--------------------------------------------------------------------------------------------------//

  /**
   * Perform an array copy for arrays of bytes.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(byte[] src, int srcIdx, byte[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx >= (dstIdx + BYTES_IN_ADDRESS)) && BYTE_BULK_COPY_SUPPORTED) {
        if (NEEDS_BYTE_ASTORE_BARRIER || NEEDS_BYTE_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx);
          Barriers.byteBulkCopy(src, srcOffset, dst, dstOffset, len);
        } else {
          Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of bytes.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(byte[] src, int srcIdx, byte[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of booleans.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(boolean[] src, int srcIdx, boolean[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx >= (dstIdx + BYTES_IN_ADDRESS / BYTES_IN_BOOLEAN)) && BOOLEAN_BULK_COPY_SUPPORTED) {
        if (NEEDS_BOOLEAN_ASTORE_BARRIER || NEEDS_BOOLEAN_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_BOOLEAN);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_BOOLEAN);
          Barriers.booleanBulkCopy(src, srcOffset, dst, dstOffset, len);
        } else {
          Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of booleans.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(boolean[] src, int srcIdx, boolean[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of shorts.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(short[] src, int srcIdx, short[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx >= (dstIdx + BYTES_IN_ADDRESS / BYTES_IN_SHORT)) && SHORT_BULK_COPY_SUPPORTED) {
        if (NEEDS_SHORT_ASTORE_BARRIER || NEEDS_SHORT_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_SHORT);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_SHORT);
          Barriers.shortBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_SHORT);
        } else {
          Memory.arraycopy16Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of shorts.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(short[] src, int srcIdx, short[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of chars.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(char[] src, int srcIdx, char[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx >= (dstIdx + BYTES_IN_ADDRESS / BYTES_IN_CHAR)) && CHAR_BULK_COPY_SUPPORTED) {
        if (NEEDS_CHAR_ASTORE_BARRIER || NEEDS_CHAR_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_CHAR);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_CHAR);
          Barriers.charBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_CHAR);
        } else {
          Memory.arraycopy16Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of chars.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(char[] src, int srcIdx, char[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of ints.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(int[] src, int srcIdx, int[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx >= dstIdx) && INT_BULK_COPY_SUPPORTED) {
        if (NEEDS_INT_ASTORE_BARRIER || NEEDS_INT_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_INT);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_INT);
          Barriers.intBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_INT);
        } else {
          Memory.arraycopy32Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of ints.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(int[] src, int srcIdx, int[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of floats.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(float[] src, int srcIdx, float[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx > dstIdx) && FLOAT_BULK_COPY_SUPPORTED) {
        if (NEEDS_FLOAT_ASTORE_BARRIER || NEEDS_FLOAT_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_FLOAT);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_FLOAT);
          Barriers.floatBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_FLOAT);
        } else {
          Memory.arraycopy32Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of floats.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(float[] src, int srcIdx, float[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of longs.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(long[] src, int srcIdx, long[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx > dstIdx) && LONG_BULK_COPY_SUPPORTED) {
        if (NEEDS_LONG_ASTORE_BARRIER || NEEDS_LONG_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_LONG);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_LONG);
          Barriers.longBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_LONG);
        } else {
          Memory.arraycopy64Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of longs.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(long[] src, int srcIdx, long[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of doubles.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public static void arraycopy(double[] src, int srcIdx, double[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      if ((src != dst || srcIdx > dstIdx) && DOUBLE_BULK_COPY_SUPPORTED) {
        if (NEEDS_DOUBLE_ASTORE_BARRIER || NEEDS_DOUBLE_ALOAD_BARRIER) {
          Offset srcOffset = Offset.fromIntZeroExtend(srcIdx<<LOG_BYTES_IN_DOUBLE);
          Offset dstOffset = Offset.fromIntZeroExtend(dstIdx<<LOG_BYTES_IN_DOUBLE);
          Barriers.doubleBulkCopy(src, srcOffset, dst, dstOffset, len << LOG_BYTES_IN_DOUBLE);
        } else {
          Memory.arraycopy64Bit(src, srcIdx, dst, dstIdx, len);
        }
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform element-by-element arraycopy for array of doubles.  Used
   * when bulk copy is not possible.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  @NoInline // unlikely case, so reduce code space costs
  private static void arraycopyPiecemeal(double[] src, int srcIdx, double[] dst, int dstIdx, int len) {
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    } else {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    }
  }

  /**
   * Perform an array copy for arrays of objects.  This code must
   * ensure that write barriers are invoked as if the copy were
   * performed element-by-element.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  public static void arraycopy(Object[] src, int srcIdx, Object[] dst, int dstIdx, int len) {
    // Check offsets and lengths before doing anything
    if (srcIdx >= 0 &&
        dstIdx >= 0 &&
        len >= 0 &&
        (srcIdx + len) >= 0 &&
        (srcIdx + len) <= src.length &&
        (dstIdx + len) >= 0 &&
        (dstIdx + len) <= dst.length) {
      RVMType lhs = Magic.getObjectType(dst).asArray().getElementType();
      RVMType rhs = Magic.getObjectType(src).asArray().getElementType();

      if ((lhs == rhs) || (lhs == RVMType.JavaLangObjectType) || RuntimeEntrypoints.isAssignableWith(lhs, rhs)) {
        arraycopyNoCheckcast(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyPiecemeal(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  /**
   * Perform an array copy for arrays of objects where the possibility
   * of an ArrayStoreException being thrown <i>does not</i> exist.
   * This may be done using direct byte copies, <i>however</i>, write
   * barriers must be explicitly invoked (if required by the GC) since
   * the write barrier associated with an explicit array store
   * (aastore) will be bypassed.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting source index
   * @param len The number of array elements to be copied
   */
  private static void arraycopyNoCheckcast(Object[] src, int srcIdx, Object[] dst, int dstIdx, int len) {
    Offset srcOffset = Offset.fromIntZeroExtend(srcIdx << LOG_BYTES_IN_ADDRESS);
    Offset dstOffset = Offset.fromIntZeroExtend(dstIdx << LOG_BYTES_IN_ADDRESS);
    int bytes = len << LOG_BYTES_IN_ADDRESS;

    if (((src != dst) || (srcIdx > dstIdx)) && OBJECT_BULK_COPY_SUPPORTED) {
      if (NEEDS_OBJECT_ASTORE_BARRIER || NEEDS_OBJECT_ALOAD_BARRIER) {
        Barriers.objectBulkCopy(src, srcOffset, dst, dstOffset, bytes);
      } else {
        Memory.alignedWordCopy(Magic.objectAsAddress(dst).plus(dstOffset), Magic.objectAsAddress(src).plus(srcOffset), bytes);
      }
    } else {
      arraycopyPiecemealNoCheckcast(src, dst, len, srcOffset, dstOffset, bytes);
    }
  }

  /**
   * Perform element-by-element arraycopy for array of objects without
   * performing checkcast.  Used when bulk copy is not possible, but
   * checkcast is still not necessary.  If barriers are required they
   * must be explicitly invoked.
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  private static void arraycopyPiecemealNoCheckcast(Object[] src, Object[] dst, int len,
      Offset srcOffset, Offset dstOffset, int bytes) {

    // set up things according to the direction of the copy
    int increment;
    if (srcOffset.sGT(dstOffset)) { // direction of copy
      increment = BYTES_IN_ADDRESS;
    } else {
      srcOffset = srcOffset.plus(bytes - BYTES_IN_ADDRESS);
      dstOffset = dstOffset.plus(bytes - BYTES_IN_ADDRESS);
      increment = -BYTES_IN_ADDRESS;
    }

    // perform the copy
    while (len-- != 0) {
      Object value;
      if (NEEDS_OBJECT_ALOAD_BARRIER) {
        value = Barriers.objectArrayRead(src, srcOffset.toInt() >> LOG_BYTES_IN_ADDRESS);
      } else {
        value = Magic.getObjectAtOffset(src, srcOffset);
      }
      if (NEEDS_OBJECT_ASTORE_BARRIER) {
        Barriers.objectArrayWrite(dst, dstOffset.toInt() >> LOG_BYTES_IN_ADDRESS, value);
      } else {
        Magic.setObjectAtOffset(dst, dstOffset, value);
      }
      srcOffset = srcOffset.plus(increment);
      dstOffset = dstOffset.plus(increment);
    }
  }

  /**
   * Perform an array copy for arrays of objects where the possibility
   * of an ArrayStoreException being thrown exists.  This must be done
   * with element by element assignments in the correct order.
   * <i>Since write barriers are implicitly performed on explicit
   * array stores, there is no need to explicitly invoke a write
   * barrier in this code.</i>
   *
   * @param src The source array
   * @param srcIdx The starting source index
   * @param dst The destination array
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  private static void arraycopyPiecemeal(Object[] src, int srcIdx, Object[] dst, int dstIdx, int len) {
    if ((src != dst) || srcIdx >= dstIdx) {
      while (len-- != 0) {
        dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0) {
        dst[--dstIdx] = src[--srcIdx];
      }
    }
  }

  @NoInline
  private static void failWithIndexOutOfBoundsException() {
    throw new ArrayIndexOutOfBoundsException();
  }
}
