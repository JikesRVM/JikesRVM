/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.jikesrvm.classloader;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * Description of a java "array" type. <p>
 * 
 * This description is not read from a ".class" file, but rather
 * is manufactured by the vm as execution proceeds. 
 * 
 * @see VM_Type
 * @see VM_Class
 * @see VM_Primitive
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Array extends VM_Type implements VM_Constants, 
                                                       VM_ClassLoaderConstants  {

  /*
   * We hold on to a number of commonly used arrays for easy access.
   */
  public static final VM_Array BooleanArray;
  public static final VM_Array ByteArray;
  public static final VM_Array CharArray;
  public static final VM_Array ShortArray;
  public static final VM_Array IntArray;
  public static final VM_Array LongArray;
  public static final VM_Array FloatArray;
  public static final VM_Array DoubleArray;
  public static final VM_Array JavaLangObjectArray;

  static {
    BooleanArray = (VM_Array)VM_TypeReference.BooleanArray.resolve();
    CharArray    = (VM_Array)VM_TypeReference.CharArray.resolve();
    FloatArray   = (VM_Array)VM_TypeReference.FloatArray.resolve();
    DoubleArray  = (VM_Array)VM_TypeReference.DoubleArray.resolve();
    ByteArray    = (VM_Array)VM_TypeReference.ByteArray.resolve();
    ShortArray   = (VM_Array)VM_TypeReference.ShortArray.resolve();
    IntArray     = (VM_Array)VM_TypeReference.IntArray.resolve();
    LongArray    = (VM_Array)VM_TypeReference.LongArray.resolve();
    JavaLangObjectArray = (VM_Array)VM_TypeReference.JavaLangObjectArray.resolve();
  }

  /**
   * The VM_Type object for elements of this array type.
   */
  private final VM_Type elementType;
  
  /**
   * The log of the element size for this array type.
   */
  private final int logElementSize;
  
  /**
   * The VM_Type object for the innermost element of this array type.
   */
  private final VM_Type innermostElementType;

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
  private Object[] typeInformationBlock;

  /**
   * current class-loading stage (loaded, resolved or initialized)
   */
  private int state;        

  /**
   * Is this array type in the bootimage?
   */
  private boolean inBootImage;

  /**
   * At what offset is the thin lock word to be found in instances of
   * objects of this type?  A value of -1 indicates that the instances of
   * this type do not have inline thin locks.
   */
  private Offset thinLockOffset;

  /**
   * The memory manager's notion of this type created after the
   * resolving
   */
  private Object mmType;
 
  /**
   * Record the type information the memory manager holds about this
   * type
   * @param mmt the type to record
   */
  public void setMMType(Object mmt) {
    mmType = mmt;
  }

  /**
   * @return the type information the memory manager previously
   * recorded about this type
   */
  @Uninterruptible
  public Object getMMType() { 
    return mmType;
  }

  /**
   * Name - something like "[I" or "[Ljava.lang.String;"
   */
  public final String toString() { 
    return getDescriptor().toString().replace('/','.');
  }

  /** 
   * @return java Expression stack space requirement. 
   */
  @Uninterruptible
  public final int getStackWords() { 
    return 1;
  }

  /**
   * Space required in memory in bytes.
   */ 
  @Uninterruptible
  public int getMemoryBytes() { 
    return BYTES_IN_ADDRESS;
  }

  /** 
   * @return element type.
   */
  @Uninterruptible
  public final VM_Type getElementType() { 
    return elementType;
  }

  /**
   * @return innermost element type
   */
  @Uninterruptible
  public final VM_Type getInnermostElementType() { 
    return innermostElementType;
  }
      
  /**
   * @return alignment for instances of this array type
   */
  @Uninterruptible
  public final int getAlignment() { 
    return alignment;
  }

  /**
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  @Uninterruptible
  public final int getLogElementSize() { 
    return logElementSize;
  }

  /**
   * Calculate the size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  private int computeLogElementSize() {
    if (elementType.getTypeRef().equals(VM_TypeReference.Code)) {
      return LG_INSTRUCTION_WIDTH;
    }
    switch (getDescriptor().parseForArrayElementTypeCode()) {
    case VM_Atom.ClassTypeCode:   return LOG_BYTES_IN_ADDRESS;
    case VM_Atom.ArrayTypeCode:   return LOG_BYTES_IN_ADDRESS;
    case VM_Atom.BooleanTypeCode: return LOG_BYTES_IN_BOOLEAN;
    case VM_Atom.ByteTypeCode:    return 0;
    case VM_Atom.ShortTypeCode:   return LOG_BYTES_IN_SHORT;
    case VM_Atom.IntTypeCode:     return LOG_BYTES_IN_INT;
    case VM_Atom.LongTypeCode:    return LOG_BYTES_IN_LONG;
    case VM_Atom.FloatTypeCode:   return LOG_BYTES_IN_FLOAT;
    case VM_Atom.DoubleTypeCode:  return LOG_BYTES_IN_DOUBLE;
    case VM_Atom.CharTypeCode:    return LOG_BYTES_IN_CHAR;
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
  @Uninterruptible
  public final int getInstanceSize(int numelts) { 
    return VM_ObjectModel.computeArrayHeaderSize(this) + (numelts << getLogElementSize());
  }

  /**
   * Does this class override java.lang.Object.finalize()?
   */
  @Uninterruptible
  public final boolean hasFinalizer() { 
    return false;
  }

  /**
   * Static fields of this array type.
   */
  public final VM_Field[] getStaticFields() {
    return VM_Type.JavaLangObjectType.getStaticFields();
  }
 
  /**
   * Non-static fields of this array type.
   */
  public final VM_Field[] getInstanceFields() {
    return VM_Type.JavaLangObjectType.getInstanceFields();
  }

  /**
   * Statically dispatched methods of this array type.
   */
  public final VM_Method[] getStaticMethods() {
    return VM_Type.JavaLangObjectType.getStaticMethods();
  }
 
  /**
   * Virtually dispatched methods of this array type.
   */
  public final VM_Method[] getVirtualMethods() {
    return VM_Type.JavaLangObjectType.getVirtualMethods();
  }

  /**
   * Runtime type information for this array type.
   */
  @Uninterruptible
  public final Object[] getTypeInformationBlock() { 
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return typeInformationBlock;
  }

  /**
   * Does this slot in the TIB hold a TIB entry?
   * @param slot the TIB slot
   * @return true if this the array element TIB
   */
  public boolean isTIBSlotTIB(int slot) {
    if (VM.VerifyAssertions) checkTIBSlotIsAccessible(slot);
    return slot == TIB_ARRAY_ELEMENT_TIB_INDEX;
  }

  /**
   * Does this slot in the TIB hold code?
   * @param slot the TIB slot
   * @return true if slot is one that holds a code array reference
   */
  public boolean isTIBSlotCode(int slot) {
    if (VM.VerifyAssertions) checkTIBSlotIsAccessible(slot);
    return slot >= TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * get number of superclasses to Object 
   * @return 1
   */ 
  @Uninterruptible
  public int getTypeDepth () { 
    return 1;
  }

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   * @return true
   */ 
  @Uninterruptible
  public boolean isAcyclicReference() { 
    return acyclic;
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes
   */ 
  @Uninterruptible
  public int getDimensionality() { 
    return dimension;
  }

  /**
   * Resolution status.
   */ 
  @Uninterruptible
  public boolean isResolved() { 
    return state >= CLASS_RESOLVED;
  }

  /**
   * Instantiation status.
   */ 
  @Uninterruptible
  public final boolean isInstantiated() { 
    return state >= CLASS_INSTANTIATED; 
  }

  /**
   * Initialization status.
   */ 
  @Uninterruptible
  public final boolean isInitialized() { 
    return state == CLASS_INITIALIZED; 
  } 

  /**
   * Only intended to be used by the BootImageWriter
   */
  public final void markAsBootImageClass() {
    inBootImage = true;
  }
  
  /**
   * Is this class part of the virtual machine's boot image?
   */ 
  @Uninterruptible
  public final boolean isInBootImage() { 
    return inBootImage;
  }

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * -1 if instances of this type do not have thin lock words.
   */
  @Uninterruptible
  public final Offset getThinLockOffset() { 
    return thinLockOffset; 
  }

  /**
   * Set the thin lock offset for instances of this type
   */
  public final void setThinLockOffset(Offset offset) {
    if (VM.VerifyAssertions) VM._assert (thinLockOffset.isMax());
    thinLockOffset = offset;
  }

  /**
   * Whether or not this is an instance of VM_Class?
   * @return false
   */
  @Uninterruptible
  public boolean isClassType() { 
    return false;
  }

  /**
   * Whether or not this is an instance of VM_Array?
   * @return true
   */
  @Uninterruptible
  public boolean isArrayType() { 
    return true;
  }

  /**
   * Whether or not this is a primitive type
   * @return false
   */
  @Uninterruptible
  public boolean isPrimitiveType() { 
    return false;
  }

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Uninterruptible
  public boolean isReferenceType() { 
    return true;
  }
   
  /**
   * Constructor
   * @param typeRef
   * @param elementType
   */
  VM_Array(VM_TypeReference typeRef, VM_Type elementType) {
    super(typeRef, typeRef.getDimensionality(),  null, null);
    this.elementType = elementType;
    this.logElementSize = computeLogElementSize();
    thinLockOffset = VM_ObjectModel.defaultThinLockOffset();
    depth = 1;

    if (elementType.isArrayType()) {
      innermostElementType = elementType.asArray().getInnermostElementType();
    } else {
      innermostElementType = elementType;
    }
    if (BYTES_IN_DOUBLE != BYTES_IN_ADDRESS) {
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

    if (VM.verboseClassLoading) VM.sysWrite("[Loaded "+this.getDescriptor()+"]\n");
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded superclasses of "+this.getDescriptor()+"]\n");
  }

  /**
   * Resolve an array.  
   * Also forces the resolution of the element type.
   */
  public final synchronized void resolve() {
    if (isResolved()) return;

    if (VM.VerifyAssertions) VM._assert(state == CLASS_LOADED);

    elementType.resolve();
    
    // Using the type information block for java.lang.Object as a template,
    // build a type information block for this new array type by copying the
    // virtual method fields and substuting an appropriate type field.
    //
    Object[] javaLangObjectTIB = VM_Type.JavaLangObjectType.getTypeInformationBlock();
    typeInformationBlock = MM_Interface.newTIB(javaLangObjectTIB.length);
    VM_Statics.setSlotContents(getTibOffset(), typeInformationBlock);
    // Initialize dynamic type checking data structures
    typeInformationBlock[TIB_TYPE_INDEX] = this;
    typeInformationBlock[TIB_SUPERCLASS_IDS_INDEX] = VM_DynamicTypeCheck.buildSuperclassIds(this);
    typeInformationBlock[TIB_DOES_IMPLEMENT_INDEX] = VM_DynamicTypeCheck.buildDoesImplement(this);
    if (!elementType.isPrimitiveType()) {
      typeInformationBlock[TIB_ARRAY_ELEMENT_TIB_INDEX] = elementType.getTypeInformationBlock();
    }
 
    state = CLASS_RESOLVED;

    MM_Interface.notifyClassResolved(this);
  }


  /**
   * Instantiate an array.
   * Main result is to copy the virtual methods from JavaLangObject's tib.
   */
  public final synchronized void instantiate() {
    if (isInstantiated()) return;
    
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);
    if (VM.TraceClassLoading && VM.runningVM) 
      VM.sysWrite("VM_Array: instantiate " + this + "\n");
    
    // Initialize TIB slots for virtual methods (copy from superclass == Object)
    VM_Type objectType = VM_Type.JavaLangObjectType;
    if (VM.VerifyAssertions) VM._assert(objectType.isInstantiated());
    Object[] javaLangObjectTIB = objectType.getTypeInformationBlock();
    for (int i = TIB_FIRST_VIRTUAL_METHOD_INDEX; i<javaLangObjectTIB.length; i++) {
      typeInformationBlock[i] = javaLangObjectTIB[i];
    }

    state = CLASS_INITIALIZED; // arrays have no "initialize" phase
  }


  /**
   * Initialization is a no-op (arrays have no <clinit> method).
   */
  public final void initialize() { }


  //-------------------------------------------------------------------------------------------------//
  //                                   Misc static methods.                                          //
  //-------------------------------------------------------------------------------------------------//

  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  public static VM_Array getPrimitiveArrayType(int atype) {
    switch (atype) {
    case  4: return BooleanArray;
    case  5: return CharArray;
    case  6: return FloatArray;
    case  7: return DoubleArray;
    case  8: return ByteArray;
    case  9: return ShortArray;
    case 10: return IntArray;
    case 11: return LongArray;
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
  public static void arraycopy(byte[] src, int srcIdx, byte[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS)) {
        VM_Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(byte[] src, int srcIdx, byte[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(boolean[] src, int srcIdx, boolean[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_BOOLEAN)) {
        VM_Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(boolean[] src, int srcIdx, boolean[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(short[] src, int srcIdx, short[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_SHORT)) {
        VM_Memory.arraycopy16Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(short[] src, int srcIdx, short[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(char[] src, int srcIdx, char[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_CHAR)) {
        VM_Memory.arraycopy16Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }  

  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(char[] src, int srcIdx, char[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(int[] src, int srcIdx, int[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx >= dstIdx) {
        VM_Memory.arraycopy32Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(int[] src, int srcIdx, int[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(float[] src, int srcIdx, float[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.arraycopy32Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(float[] src, int srcIdx, float[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(long[] src, int srcIdx, long[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.arraycopy64Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(long[] src, int srcIdx, long[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(double[] src, int srcIdx, double[] dst, int dstIdx, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.arraycopy64Bit(src, srcIdx, dst, dstIdx, len);
      } else {
        arraycopyOverlap(src, srcIdx, dst, dstIdx, len);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // Outlined unlikely case of potentially overlapping subarrays
  // Motivation is to reduce code space costs of inlined array copy.
  @NoInline
  private static void arraycopyOverlap(double[] src, int srcIdx, double[] dst, int dstIdx, int len) { 
    if (srcIdx < dstIdx) {
      srcIdx += len;
      dstIdx += len;
      while (len-- != 0)
        dst[--dstIdx] = src[--srcIdx];
    } else {
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
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
  public static void arraycopy(Object[] src, int srcIdx, Object[] dst, 
                               int dstIdx, int len) {
    // Check offsets and lengths before doing anything
    if (srcIdx >= 0 && dstIdx >= 0 && len >= 0 && 
        (srcIdx + len) >=0 && (srcIdx+len) <= src.length && 
        (dstIdx + len) >= 0 && (dstIdx+len) <= dst.length) {
      VM_Type lhs = VM_Magic.getObjectType(dst).asArray().getElementType();
      VM_Type rhs = VM_Magic.getObjectType(src).asArray().getElementType();
      if ((lhs == rhs) || (lhs == VM_Type.JavaLangObjectType)
          || VM_Runtime.isAssignableWith(lhs, rhs))
        fastArrayCopy(src, srcIdx, dst, dstIdx, len);
       else
        slowArrayCopy(src, srcIdx, dst, dstIdx, len);
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
   * @param dstIdx The starting destination index
   * @param len The number of array elements to be copied
   */
  private static void fastArrayCopy(Object[] src, int srcIdx, Object[] dst, 
                                    int dstIdx, int len) {

    boolean loToHi = (srcIdx > dstIdx);  // direction of copy
    Offset srcOffset = Offset.fromIntZeroExtend(srcIdx << LOG_BYTES_IN_ADDRESS);
    Offset dstOffset = Offset.fromIntZeroExtend(dstIdx << LOG_BYTES_IN_ADDRESS);
    int bytes = len << LOG_BYTES_IN_ADDRESS;
    
    if ((src != dst) || loToHi) {
      if (!MM_Constants.NEEDS_WRITE_BARRIER ||
          !MM_Interface.arrayCopyWriteBarrier(src, srcOffset, dst, dstOffset, 
                                              bytes)) {
        VM_Memory.alignedWordCopy(VM_Magic.objectAsAddress(dst).plus(dstOffset),
                                  VM_Magic.objectAsAddress(src).plus(srcOffset),
                                  bytes);
      }
    } else {
      // set up things according to the direction of the copy
      int increment;
      if (loToHi)
        increment = BYTES_IN_ADDRESS;
      else {
        srcOffset = srcOffset.plus(bytes - BYTES_IN_ADDRESS);
        dstOffset = dstOffset.plus(bytes - BYTES_IN_ADDRESS);
        increment = -BYTES_IN_ADDRESS;
      } 

      // perform the copy
      while (len-- != 0) {
        Object value = VM_Magic.getObjectAtOffset(src, srcOffset);
        if (MM_Constants.NEEDS_WRITE_BARRIER)
          MM_Interface.arrayStoreWriteBarrier(dst, dstOffset.toInt()>>LOG_BYTES_IN_ADDRESS, value);
        else
          VM_Magic.setObjectAtOffset(dst, dstOffset, value);
        srcOffset = srcOffset.plus(increment);
        dstOffset = dstOffset.plus(increment);
      }
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
  private static void slowArrayCopy(Object[] src, int srcIdx, Object[] dst, 
                                    int dstIdx, int len) {
    // must perform copy in correct order
    if ((src != dst) || srcIdx > dstIdx) {
      // non-overlapping case: straightforward
      while (len-- != 0)
        dst[dstIdx++] = src[srcIdx++];
    } else {
      // the arrays overlap: must use temp array
      VM_Array ary = VM_Magic.getObjectType(src).asArray();
      Object[] temp = (Object[])VM_Runtime.resolvedNewArray(len, ary);
      int cnt = len;
      int tempIdx = 0;
      while (cnt-- != 0)
        temp[tempIdx++] = src[srcIdx++];
      tempIdx = 0;
      while (len-- != 0)
        dst[dstIdx++] = temp[tempIdx++];
    }
  }

  @NoInline
  private static void failWithIndexOutOfBoundsException() { 
    throw new ArrayIndexOutOfBoundsException();
  }
}
