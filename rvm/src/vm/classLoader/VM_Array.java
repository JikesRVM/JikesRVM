/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

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
  public static VM_Array BooleanArray;
  public static VM_Array ByteArray;
  public static VM_Array CharArray;
  public static VM_Array ShortArray;
  public static VM_Array IntArray;
  public static VM_Array LongArray;
  public static VM_Array FloatArray;
  public static VM_Array DoubleArray;
  public static VM_Array JavaLangObjectArray;

  /**
   * The VM_Type object for elements of this array type.
   */
  private final VM_Type elementType;
  
  /**
   * The VM_Type object for the innermost element of this array type.
   */
  private final VM_Type innermostElementType;

  /**
   * The TIB for this type
   */
  private Object[] typeInformationBlock;

  /**
   * The desired alignment for instances of this type.
   * Cached rather than computed because this is a frequently
   * asked question
   */
  private final int alignment;
  
  /**
   * Name - something like "[I" or "[Ljava.lang.String;"
   */
  public final String toString() { 
    return getDescriptor().toString().replace('/','.');
  }

  /** 
   * @return java Expression stack space requirement. 
  */
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return 1;
  }
      
  /** 
   * @return element type.
   */
  public final VM_Type getElementType() throws VM_PragmaUninterruptible { 
    return elementType;
  }

  /**
   * @return innermost element type
   */
  public final VM_Type getInnermostElementType() throws VM_PragmaUninterruptible {
    return innermostElementType;
  }
      
  /**
   * @return alignment for instances of this array type
   */
  public final int getAlignment() throws VM_PragmaUninterruptible {
    return alignment;
  }

  /**
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  public final int getLogElementSize() throws VM_PragmaUninterruptible {
    if (this == VM_Type.CodeArrayType) {
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
  public final int getInstanceSize(int numelts) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_ObjectModel.computeArrayHeaderSize(this) + (numelts << getLogElementSize());
  }

  /**
   * Does this class override java.lang.Object.finalize()?
   */
  public final boolean hasFinalizer() throws VM_PragmaUninterruptible {
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
  public final Object[] getTypeInformationBlock() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return typeInformationBlock;
  }

  //-------------------------------------------------------------------------------------------------//
  //                        Load, Resolve, Instantiate, Initialize                                   //
  //-------------------------------------------------------------------------------------------------//

  /**
   * Create an instance of a VM_Array
   * @param typeRef the canonical type reference for this type.
   * @param elementType the VM_Type object for the array's elements.
   */
  VM_Array(VM_TypeReference typeRef, VM_Type elementType) {
    super(typeRef);
    depth = 1;
    this.elementType = elementType;
    if (elementType.isArrayType()) {
      innermostElementType = elementType.asArray().getInnermostElementType();
    } else {
      innermostElementType = elementType;
    }
    if (elementType.isDoubleType() || elementType.isLongType()) {
      this.alignment = BYTES_IN_DOUBLE;
    } else {
      this.alignment = BYTES_IN_ADDRESS;
    }
    
    acyclic = elementType.isAcyclicReference(); // RCGC: Array is acyclic if its references are acyclic

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
    VM_Statics.setSlotContents(tibSlot, typeInformationBlock);
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


  static void init() {
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS)) {
        VM_Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_BOOLEAN)) {
        VM_Memory.arraycopy8Bit(src, srcIdx, dst, dstIdx, len);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_SHORT)) {
        VM_Memory.arraycopy(src, srcIdx, dst, dstIdx, len);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx >= (dstIdx+BYTES_IN_ADDRESS/BYTES_IN_CHAR)) {
        VM_Memory.arraycopy(src, srcIdx, dst, dstIdx, len);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx >= dstIdx) {
        VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstIdx<< LOG_BYTES_IN_INT),
                                VM_Magic.objectAsAddress(src).add(srcIdx << LOG_BYTES_IN_INT),
                                len<< LOG_BYTES_IN_INT);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstIdx << LOG_BYTES_IN_FLOAT),
                                VM_Magic.objectAsAddress(src).add(srcIdx << LOG_BYTES_IN_FLOAT),
                                len << LOG_BYTES_IN_FLOAT);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstIdx<<LOG_BYTES_IN_LONG),
                                VM_Magic.objectAsAddress(src).add(srcIdx<<LOG_BYTES_IN_LONG),
                                len<<LOG_BYTES_IN_LONG);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcIdx > dstIdx) {
        VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstIdx<<LOG_BYTES_IN_DOUBLE),
                                VM_Magic.objectAsAddress(src).add(srcIdx<<LOG_BYTES_IN_DOUBLE),
                                len<<LOG_BYTES_IN_DOUBLE);
      } else if (srcIdx < dstIdx) {
        srcIdx += len;
        dstIdx += len;
        while (len-- != 0)
          dst[--dstIdx] = src[--srcIdx];
      } else {
        while (len-- != 0)
          dst[dstIdx++] = src[srcIdx++];
      }
    } else {
      failWithIndexOutOfBoundsException();
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
    int srcOffset = srcIdx << LOG_BYTES_IN_ADDRESS;
    int dstOffset = dstIdx << LOG_BYTES_IN_ADDRESS;
    int bytes = len << LOG_BYTES_IN_ADDRESS;
    
    if (!MM_Interface.NEEDS_WRITE_BARRIER 
        && ((src != dst) || loToHi)) {
      if (VM.VerifyAssertions) VM._assert(!MM_Interface.NEEDS_WRITE_BARRIER);
      VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstOffset),
                              VM_Magic.objectAsAddress(src).add(srcOffset),
                              bytes);
    } else {
      // set up things according to the direction of the copy
      int increment;
      if (loToHi)
        increment = BYTES_IN_ADDRESS;
      else {
        srcOffset += (bytes - BYTES_IN_ADDRESS);
        dstOffset += (bytes - BYTES_IN_ADDRESS);
        increment = -BYTES_IN_ADDRESS;
      } 

      // perform the copy
      while (len-- != 0) {
        Object value = VM_Magic.getObjectAtOffset(src, srcOffset);
        if (MM_Interface.NEEDS_WRITE_BARRIER)
          MM_Interface.arrayStoreWriteBarrier(dst, dstOffset>>LOG_BYTES_IN_ADDRESS, value);
        else
          VM_Magic.setObjectAtOffset(dst, dstOffset, value);
        srcOffset += increment;
        dstOffset += increment;
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
      Object temp[] = (Object[])VM_Runtime.resolvedNewArray(len, ary);
      int cnt = len;
      int tempIdx = 0;
      while (cnt-- != 0)
        temp[tempIdx++] = src[srcIdx++];
      tempIdx = 0;
      while (len-- != 0)
        dst[dstIdx++] = temp[tempIdx++];
    }
  }

  private static void failWithIndexOutOfBoundsException() throws VM_PragmaNoInline {
    throw new ArrayIndexOutOfBoundsException();
  }
}
