/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  public final int getLogElementSize() throws VM_PragmaUninterruptible {
    switch (getDescriptor().parseForArrayElementTypeCode()) {
    case VM_Atom.ClassTypeCode:   return 2;
    case VM_Atom.ArrayTypeCode:   return 2;
    case VM_Atom.BooleanTypeCode: return 0;
    case VM_Atom.ByteTypeCode:    return 0;
    case VM_Atom.ShortTypeCode:   return 1;
    case VM_Atom.IntTypeCode:     return 2;
    case VM_Atom.LongTypeCode:    return 3;
    case VM_Atom.FloatTypeCode:   return 2;
    case VM_Atom.DoubleTypeCode:  return 3;
    case VM_Atom.CharTypeCode:    return 1;
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
   * @param typeRef the cannonical type reference for this type.
   * @param elementType the VM_Type object for the array's elements.
   */
  VM_Array(VM_TypeReference typeRef, VM_Type elementType) {
    super(typeRef);
    depth = 1;
    this.elementType = elementType;
    if (VM.VerifyAssertions && elementType.isWordType()) {
      VM.sysWriteln("\nDo not create arrays of VM_Address, VM_Word, VM_Offset, or other special primitive types.\n  Use an int array or long array for now and use casts.");
      VM._assert(false);
    }
    if (elementType.isArrayType()) {
      innermostElementType = elementType.asArray().getInnermostElementType();
    } else {
      innermostElementType = elementType;
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
    typeInformationBlock = VM_Interface.newTIB(javaLangObjectTIB.length);
    VM_Statics.setSlotContents(tibSlot, typeInformationBlock);
    // Initialize dynamic type checking data structures
    typeInformationBlock[0] = this;
    typeInformationBlock[TIB_SUPERCLASS_IDS_INDEX] = VM_DynamicTypeCheck.buildSuperclassIds(this);
    typeInformationBlock[TIB_DOES_IMPLEMENT_INDEX] = VM_DynamicTypeCheck.buildDoesImplement(this);
    if (!elementType.isPrimitiveType()) {
      typeInformationBlock[TIB_ARRAY_ELEMENT_TIB_INDEX] = elementType.getTypeInformationBlock();
    }
 
    state = CLASS_RESOLVED;
  }


  /**
   * Instantiate an array.
   * Main result is to copy the virtual methods from JavaLangObject's tib.
   */
  public final synchronized void instantiate() {
    if (isInstantiated()) return;
    
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Array: instantiate " + this + "\n");
    
    // Initialize TIB slots for virtual methods (copy from superclass == Object)
    Object[] javaLangObjectTIB = VM_Type.JavaLangObjectType.getTypeInformationBlock();
    for (int i = TIB_FIRST_VIRTUAL_METHOD_INDEX, n = javaLangObjectTIB.length; i < n; ++i) {
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


  static void init() throws ClassNotFoundException {
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

  // NOTE: arraycopy for byte[] and boolean[] are identical
  public static void arraycopy(byte[] src, int srcPos, byte[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos >= (dstPos+4)) {
	VM_Memory.arraycopy8Bit(src, srcPos, dst, dstPos, len);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for byte[] and boolean[] are identical
  public static void arraycopy(boolean[] src, int srcPos, boolean[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos >= (dstPos+4)) {
	VM_Memory.arraycopy8Bit(src, srcPos, dst, dstPos, len);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for short[] and char[] are identical
  public static void arraycopy(short[] src, int srcPos, short[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos >= (dstPos+2)) {
	VM_Memory.arraycopy(src, srcPos, dst, dstPos, len);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for short[] and char[] are identical
  public static void arraycopy(char[] src, int srcPos, char[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos >= (dstPos+2)) {
	VM_Memory.arraycopy(src, srcPos, dst, dstPos, len);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }  

   
  // NOTE: arraycopy for int[] and float[] are identical
  public static void arraycopy(int[] src, int srcPos, int[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos > dstPos) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<2),
				VM_Magic.objectAsAddress(src).add(srcPos<<2),
				len<<2);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for int[] and float[] are identical
  public static void arraycopy(float[] src, int srcPos, float[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos > dstPos) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<2),
				VM_Magic.objectAsAddress(src).add(srcPos<<2),
				len<<2);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for long[] and double[] are identical
  public static void arraycopy(long[] src, int srcPos, long[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos > dstPos) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<3),
				VM_Magic.objectAsAddress(src).add(srcPos<<3),
				len<<3);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  // NOTE: arraycopy for long[] and double[] are identical
  public static void arraycopy(double[] src, int srcPos, double[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if (src != dst || srcPos > dstPos) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<3),
				VM_Magic.objectAsAddress(src).add(srcPos<<3),
				len<<3);
      } else if (srcPos < dstPos) {
	srcPos += len;
	dstPos += len;
	while (len-- != 0)
	  dst[--dstPos] = src[--srcPos];
      } else {
	while (len-- != 0)
	  dst[dstPos++] = src[srcPos++];
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }
   
  public static void arraycopy(Object[] src, int srcPos, Object[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      int dstStart,dstEnd;
      VM_Type lhs =VM_Magic.getObjectType(dst).asArray().getElementType();
      VM_Type rhs =VM_Magic.getObjectType(src).asArray().getElementType();
      if ((lhs==rhs) || 
	  (lhs == VM_Type.JavaLangObjectType) || 
	  VM_Runtime.isAssignableWith(lhs, rhs)) {
	
	if (len == 0) return;

	if (VM_Interface.NEEDS_WRITE_BARRIER) {
	  dstStart = dstPos;           
	  dstEnd = dstPos + len - 1;
	  VM.disableGC();     // prevent GC until writebarrier updated
	}

	// handle as two cases, for efficiency and in case subarrays overlap
	if (src != dst || srcPos > dstPos) {
	  if (VM_Interface.NEEDS_RC_WRITE_BARRIER) {
	    VM_Address dstS = VM_Magic.objectAsAddress(dst).add(dstPos<<2);
	    VM_Address srcS = VM_Magic.objectAsAddress(src).add(srcPos<<2);
	    for (int i = 0; i < len<<2; i += 4)
	      VM_Interface.arrayCopyRefCountWriteBarrier(dstS.add(i), VM_Magic.getMemoryAddress(srcS.add(i)));
	  }
	  VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<2),
				  VM_Magic.objectAsAddress(src).add(srcPos<<2),
				  len<<2);
	} else if (srcPos < dstPos) {
	  srcPos = (srcPos + len) << 2;
	  dstPos = (dstPos + len) << 2;
	  while (len-- != 0) {
	    srcPos -= 4;
	    dstPos -= 4;
	    if (VM_Interface.NEEDS_RC_WRITE_BARRIER) {
	      VM_Interface.arrayCopyRefCountWriteBarrier(VM_Magic.objectAsAddress(dst).add(dstPos), VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(src).add(srcPos)));
	    }
	    VM_Magic.setObjectAtOffset(dst, dstPos, VM_Magic.getObjectAtOffset(src, srcPos));
	  }
	} else {
	  while (len-- != 0)
	    dst[dstPos++] = src[srcPos++];
	}
	if (VM_Interface.NEEDS_WRITE_BARRIER) {
	  // generate write buffer entries for modified target array entries
	  if (!VM_Interface.NEEDS_RC_WRITE_BARRIER) 
	    VM_Interface.arrayCopyWriteBarrier(dst, dstStart, dstEnd);
	  VM.enableGC();
	}
      } else { 
	// not sure if copy might cause ArrayStoreException, must handle with
	// element by element assignments, in the right order.
	// handle as two cases, in case subarrays overlap
	if (src != dst || srcPos > dstPos) {
	  while (len-- != 0)
	    dst[dstPos++] = src[srcPos++];
	} else {
	  VM_Array ary = VM_Magic.getObjectType(src).asArray();
	  int allocator = VM_Interface.pickAllocator(ary);
	  Object temp[] = 
	    (Object[])VM_Runtime.resolvedNewArray(len, 
						  ary.getInstanceSize(len), 
						  ary.getTypeInformationBlock(),
						  allocator);
	  int cnt = len;
	  int tempPos = 0;
	  while (cnt-- != 0)
	    temp[tempPos++] = src[srcPos++];
	  tempPos = 0;
	  while (len-- != 0)
	    dst[dstPos++] = temp[tempPos++];
	}
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  private static void failWithIndexOutOfBoundsException() throws VM_PragmaNoInline {
    throw new ArrayIndexOutOfBoundsException();
  }
}
