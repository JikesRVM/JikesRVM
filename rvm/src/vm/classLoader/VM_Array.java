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
 * @see VM_Class
 * @see VM_Primitive
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_Array extends VM_Type
  implements VM_Constants, VM_ClassLoaderConstants  {

  //-----------//
  // interface //
  //-----------//
   
  //--------------------------------------------------------------------------------------------------//
  //                                       Section 1.                                                 //
  //                              The following are always available.
  //--------------------------------------------------------------------------------------------------//
   
  /**
   * Name - something like "[I" or "[Ljava.lang.String;"
   */
  public final String getName() { 
    return descriptor.toString().replace('/','.');
  }

  /** 
   * Stack space requirement. 
   */
  public final int getStackWords() throws VM_PragmaUninterruptible {
    return 1;
  }
      
  /** 
   * Element type.
   */
  public final VM_Type getElementType() throws VM_PragmaUninterruptible { 
    return elementType;
  }

  // Convenience method.
  public final VM_Type getInnermostElementType() throws VM_PragmaUninterruptible {
    return innermostElementType;
  }
      
  /**
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  public final int getLogElementSize() throws VM_PragmaUninterruptible {
    switch (getDescriptor().parseForArrayElementTypeCode()) 
      {
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
   * @return size
   */
  public final int getInstanceSize(int numelts) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_ObjectModel.computeArrayHeaderSize(this) + (numelts << getLogElementSize());
  }

   //--------------------------------------------------------------------------------------------------//
   //                                       Section 2.                                                 //
   //         The following are available after "resolve()" has been called.                           //
   //--------------------------------------------------------------------------------------------------//

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

  public final ClassLoader getClassLoader() {
      return elementType.getClassLoader();
  }

  public final void setClassLoader(ClassLoader cl) {
    if (!elementType.isPrimitiveType()) {
      elementType.setClassLoader(cl);
    }
  }

   //--------------------------------------------------------------------------------------------------//
   //                                       Section 3.                                                 //
   //         The following are available after "instantiate()" has been called.                       //
   //--------------------------------------------------------------------------------------------------//

   //--------------------------------------------------------------------------------------------------//
   //                                       Section 4.                                                 //
   //                                     Static methods.
   //--------------------------------------------------------------------------------------------------//

  /**
   * Load, resolve, instantiate, and initialize specified array.
   * @param arrayName - something like "[I" or "[Ljava.lang.String;"
   * @return array description
   */
  public static VM_Array forName(String arrayName) throws VM_ResolutionException {
    VM_Atom arrayDescriptor = VM_Atom.findOrCreateAsciiAtom(arrayName.replace('.','/'));
    
    ClassLoader cl = VM_SystemClassLoader.getVMClassLoader();
    VM_Array ary =
	VM_ClassLoader.findOrCreateType(arrayDescriptor, cl).asArray();

    ary.load();
    ary.resolve();
    ary.instantiate();
    ary.initialize();

    return ary;
  }

  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  public static VM_Array getPrimitiveArrayType(int atype) {
    switch (atype)
      {
      case  4: 
	return arrayOfBooleanType;
         
      case  5: 
	return arrayOfCharType;
         
      case  6: 
	return arrayOfFloatType;
         
      case  7: 
	return arrayOfDoubleType;
         
      case  8: 
	return arrayOfByteType;
         
      case  9: 
	return arrayOfShortType;
         
      case 10: 
	return arrayOfIntType;
         
      case 11: 
	return arrayOfLongType;
      }

    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

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
      try {
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
      } catch (VM_ResolutionException e) {
	failWithNoClassDefFoundError(e);
      }
    } else {
      failWithIndexOutOfBoundsException();
    }
  }

  private static void failWithIndexOutOfBoundsException() throws VM_PragmaNoInline {
    throw new ArrayIndexOutOfBoundsException();
  }

  private static void failWithNoClassDefFoundError(VM_ResolutionException e) throws VM_PragmaNoInline {
    throw new NoClassDefFoundError(e.getException().toString());
  }
   
  private static Object[] javaLangObjectTIB;
  public static VM_Array arrayOfBooleanType;
  public static VM_Array arrayOfByteType;
  public static VM_Array arrayOfShortType;
  public static VM_Array arrayOfIntType;
  public static VM_Array arrayOfLongType;
  public static VM_Array arrayOfFloatType;
  public static VM_Array arrayOfDoubleType;
  public static VM_Array arrayOfCharType;

  private VM_Type  elementType;
  private VM_Type  innermostElementType;
  private Object[] typeInformationBlock;
   
  // To guarantee uniqueness, only the VM_ClassLoader class may construct VM_Array instances.
  // All VM_Array creation should be performed by calling "VM_ClassLoader.findOrCreate" methods.
  //
  private VM_Array() { }

  VM_Array(VM_Atom descriptor, int dictionaryId, ClassLoader classloader) {
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Array: create " + descriptor + " with " + classloader + "\n");
    this.descriptor     = descriptor;
    this.dimension      = descriptor.parseForArrayDimensionality();
    this.depth          = 1;
    this.dictionaryId   = dictionaryId;
    this.tibSlot        = VM_Statics.allocateSlot(VM_Statics.TIB);
    this.elementType    = VM_ClassLoader.findOrCreateType(descriptor.parseForArrayElementDescriptor(), classloader);
    if (VM.VerifyAssertions && this.elementType.isWordType()) {
      VM.sysWriteln("\nDo not create arrays of VM_Address, VM_Word, VM_Offset, or other special primitive types.\n  Use an int array or long array for now and use casts.");
      VM._assert(false);
    }
    if (this.elementType.isArrayType()) {
      this.innermostElementType = this.elementType.asArray().getInnermostElementType();
    } else {
      this.innermostElementType = this.elementType;
    }

    // RCGC: Array is acyclic if its references are acyclic
    if (VM_Interface.RC_CYCLE_DETECTION)
      this.acyclic = elementType.isAcyclicReference(); 

    // install partial type information block (type-slot but no method-slots) for use in type checking.
    // later, during instantiate(), we'll replace it with full type information block (including method-slots).
    //
    Object[] tib = VM_Interface.newTIB(1);
    tib[0] = this;
    VM_Statics.setSlotContents(tibSlot, tib);
  }

  // Loading an array type also forces loading of element type.
  // 
  public final synchronized void load() throws VM_ResolutionException {
    if (isLoaded())
      return;
    elementType.load();

    state = CLASS_LOADED;
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded "+this.descriptor+"]\n");
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded superclasses of "+this.descriptor+"]\n");
  }

  // Resolution of element type also forces resolution of element type
  //
  public final synchronized void resolve() throws VM_ResolutionException {
    if (isResolved())
      return;

    elementType.resolve();
    
    // Using the type information block for java.lang.Object as a template,
    // build a type information block for this new array type by copying the
    // virtual method fields and substuting an appropriate type field.
    //
    if (javaLangObjectTIB == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Object;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
      javaLangObjectTIB = cls.getTypeInformationBlock();
    }
       
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

  // Build type information block and install it in jtoc.
  //
  public final synchronized void instantiate() {
    if (isInstantiated())
      return;
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);
  
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("VM_Array: instantiate " + descriptor + "\n");
    
    // Initialize TIB slots for virtual methods (copy from superclass == Object)
    for (int i = TIB_FIRST_VIRTUAL_METHOD_INDEX, n = javaLangObjectTIB.length; i < n; ++i)
      typeInformationBlock[i] = javaLangObjectTIB[i];
    
    state = CLASS_INITIALIZED; // arrays have no "initialize" phase
  }

  // No-op (arrays have no <clinit> method).
  //
  public final void initialize() { }


  static void init() {
    arrayOfBooleanType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[Z"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfCharType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[C"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfFloatType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[F"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfDoubleType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[D"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfByteType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfShortType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[S"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfIntType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[I"), VM_SystemClassLoader.getVMClassLoader()).asArray();
    arrayOfLongType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[J"), VM_SystemClassLoader.getVMClassLoader()).asArray();
  }

}
