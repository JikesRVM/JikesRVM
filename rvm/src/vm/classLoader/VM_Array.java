/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
public class VM_Array extends VM_Type
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
  public final int getStackWords() {
    return 1;
  }
      
  /** 
   * Element type.
   */
  public final VM_Type getElementType() { 
    return elementType;
  }

  // Convenience method.
  final VM_Type getInnermostElementType() {
    return innermostElementType;
  }
      
  /**
   * Size, in bytes, of an array element, log base 2.
   * @return log base 2 of array element size
   */
  final int getLogElementSize() {
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
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return -1;
  }

  /**
   * Total size, in bytes, of an instance of this array type (including object header).
   * @return size
   */
  public final int getInstanceSize(int numelts) {
    return ARRAY_HEADER_SIZE + (numelts << getLogElementSize());
  }

   //--------------------------------------------------------------------------------------------------//
   //                                       Section 2.                                                 //
   //         The following are available after "resolve()" has been called.                           //
   //--------------------------------------------------------------------------------------------------//

  /**
   * Does this class override java.lang.Object.finalize()?
   */
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
  public final Object[] getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM.assert(isResolved());
    return typeInformationBlock;
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
    VM_Array ary = VM_ClassLoader.findOrCreateType(arrayDescriptor).asArray();
    ary.load();
    ary.resolve();
    ary.instantiate();
    ary.initialize();
    VM_Callbacks.notifyForName(ary);
    return ary;
  }

  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  static VM_Array getPrimitiveArrayType(int atype) {
    switch (atype)
      {
      case  4: 
	if (arrayOfBooleanType == null)
	  arrayOfBooleanType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[Z")).asArray();
	return arrayOfBooleanType;
         
      case  5: 
	if (arrayOfCharType == null)
	  arrayOfCharType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[C")).asArray();
	return arrayOfCharType;
         
      case  6: 
	if (arrayOfFloatType == null)
	  arrayOfFloatType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[F")).asArray();
	return arrayOfFloatType;
         
      case  7: 
	if (arrayOfDoubleType == null)
	  arrayOfDoubleType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[D")).asArray();
	return arrayOfDoubleType;
         
      case  8: 
	if (arrayOfByteType == null)
	  arrayOfByteType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B")).asArray();
	return arrayOfByteType;
         
      case  9: 
	if (arrayOfShortType == null)
	  arrayOfShortType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[S")).asArray();
	return arrayOfShortType;
         
      case 10: 
	if (arrayOfIntType == null)
	  arrayOfIntType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[I")).asArray();
	return arrayOfIntType;
         
      case 11: 
	if (arrayOfLongType == null)
	  arrayOfLongType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[J")).asArray();
	return arrayOfLongType;
      }

    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null;
  }

  // NOTE: arraycopy for byte[] and boolean[] are identical
  public static void arraycopy(byte[] src, int srcPos, byte[] dst, int dstPos, int len) {
    // Don't do any of the assignments if the offsets and lengths
    // are in error
    if (srcPos >= 0 && dstPos >= 0 && len >= 0 && 
	(srcPos+len) <= src.length && (dstPos+len) <= dst.length) {
      // handle as two cases, for efficiency and in case subarrays overlap
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos >= (dstPos+4))) {
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos >= (dstPos+4))) {
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos >= (dstPos+2))) {
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos >= (dstPos+2))) {
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos > dstPos)) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<2),
				VM_Magic.objectAsAddress(src) + (srcPos<<2),
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos > dstPos)) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<2),
				VM_Magic.objectAsAddress(src) + (srcPos<<2),
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos > dstPos)) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<3),
				VM_Magic.objectAsAddress(src) + (srcPos<<3),
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
      if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos > dstPos)) {
	VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<3),
				VM_Magic.objectAsAddress(src) + (srcPos<<3),
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

	  if (VM_Collector.NEEDS_WRITE_BARRIER) {
	    dstStart = dstPos;           
	    dstEnd = dstPos + len - 1;
	    VM.disableGC();     // prevent GC until writebarrier updated
	  }

	  // handle as two cases, for efficiency and in case subarrays overlap
	  if ((! VM.BuildForRealtimeGC) && (src != dst || srcPos > dstPos)) {
	    VM_Memory.aligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<2),
				    VM_Magic.objectAsAddress(src) + (srcPos<<2),
				    len<<2);
	    if (VM.BuildForConcurrentGC) { // dfb: must increment for copied pointers
	      int start = VM_Magic.objectAsAddress(dst) + (dstPos<<2);
	      int end = start + (len<<2);
	      VM_Processor p = VM_Processor.getCurrentProcessor();
	      for (int i = start; i < end; i = i + 4) {
		//-#if RVM_WITH_CONCURRENT_GC // because VM_RCBuffers only available with concurrent memory managers
		VM_RCBuffers.addIncrement(VM_Magic.getMemoryWord(i), p);
		//-#endif
	      }
	    }
	  } else if (srcPos < dstPos) {
	    srcPos = (srcPos + len) << 2;
	    dstPos = (dstPos + len) << 2;
	    while (len-- != 0) {
	      srcPos -= 4;
	      dstPos -= 4;
	      if (! VM.BuildForRealtimeGC)
		  VM_Magic.setObjectAtOffset(dst, dstPos, VM_Magic.getObjectAtOffset(src, srcPos));
	      else
		  dst[dstPos>>2] = src[srcPos>>2];

	      if (VM.BuildForConcurrentGC) {
		//-#if RVM_WITH_CONCURRENT_GC // because VM_RCBuffers only available with concurrent memory managers
		VM_RCBuffers.addIncrement(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(dst) + dstPos),
					  VM_Processor.getCurrentProcessor());
		//-#endif
	      }
	    }
	  } else {
	    while (len-- != 0)
	      dst[dstPos++] = src[srcPos++];
	  }
	  if (VM_Collector.NEEDS_WRITE_BARRIER) {
	    // generate write buffer entries for modified target array entries
	    VM_WriteBarrier.arrayCopyWriteBarrier(dst, dstStart, dstEnd);
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
	    Object temp[] = 
	      (Object[])(VM_Allocator.allocateArray(len, ary.getInstanceSize(len), 
						    ary.getTypeInformationBlock()));
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

  private static void failWithIndexOutOfBoundsException() {
    VM_Magic.pragmaNoInline();
    throw new ArrayIndexOutOfBoundsException();
  }

  private static void failWithNoClassDefFoundError(VM_ResolutionException e) {
    VM_Magic.pragmaNoInline();
    throw new NoClassDefFoundError(e.getException().toString());
  }
   
  //----------------//
  // implementation //
  //----------------//
   
  private static Object[] javaLangObjectTIB;
  private static VM_Array arrayOfBooleanType;
  private static VM_Array arrayOfByteType;
  private static VM_Array arrayOfShortType;
  private static VM_Array arrayOfIntType;
  private static VM_Array arrayOfLongType;
  private static VM_Array arrayOfFloatType;
  private static VM_Array arrayOfDoubleType;
  private static VM_Array arrayOfCharType;

  private VM_Type  elementType;
  private VM_Type  innermostElementType;
  private Object[] typeInformationBlock;
   
  // To guarantee uniqueness, only the VM_ClassLoader class may construct VM_Array instances.
  // All VM_Array creation should be performed by calling "VM_ClassLoader.findOrCreate" methods.
  //
  private VM_Array() { }

  VM_Array(VM_Atom descriptor, int dictionaryId) {
    if (VM.TraceClassLoading) VM.sysWrite("VM_Array: create " + descriptor + "\n");
    this.descriptor     = descriptor;
    this.dimension      = descriptor.parseForArrayDimensionality();
    this.depth          = 1;
    this.dictionaryId   = dictionaryId;
    this.tibSlot        = VM_Statics.allocateSlot(VM_Statics.TIB);
    this.elementType    = VM_ClassLoader.findOrCreateType(descriptor.parseForArrayElementDescriptor());
    if (this.elementType.isArrayType()) {
      this.innermostElementType = this.elementType.asArray().getInnermostElementType();
    } else {
      this.innermostElementType = this.elementType;
    }

    if (VM.BuildForConcurrentGC)
      this.acyclic = elementType.isAcyclicReference(); // Array is acyclic if its references are acyclic

    // install partial type information block (type-slot but no method-slots) for use in type checking.
    // later, during instantiate(), we'll replace it with full type information block (including method-slots).
    //
    Object[] tib = new Object[1];
    tib[0] = this;
    VM_Statics.setSlotContents(tibSlot, tib);
  }

  // Ensure that the elementType is loaded
  // JVM spec says anewarray forces loading of base class   
  // 
  // TODO: this should throw VM_ResolutionException
  public final void load() {
    if (isLoaded())
      return;

    if (!elementType.isLoaded()) {
      // JVM spec says anewarray forces instantiation of base class
      try {
	elementType.load(); 
      }	catch (VM_ResolutionException e) {
	System.err.println("VM_Array.load: cannot load element type::: " + elementType); // TODO: we should throw e
      }
    }
    state = CLASS_LOADED;
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded "+this.descriptor+"]\n");
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded superclasses of "+this.descriptor+"]\n");
  }

  // Ensure that the elementType is resolved
  // JVM spec says anewarray forces resolution of base class   
  //
  // TODO: this should throw VM_ResolutionException
  public final void resolve() {
    if (isResolved())
      return;
    if (VM.VerifyAssertions) VM.assert(state == CLASS_LOADED);

    if (elementType.isLoaded() && !elementType.isResolved()) {
      try {
	elementType.resolve(); 
      }	catch (VM_ResolutionException e) {
	System.err.println("VM_Array.resolve: cannot resolve element type::: " + elementType); // TODO: we should throw e
      }
    }
    
    // Using the type information block for java.lang.Object as a template,
    // build a type information block for this new array type by copying the
    // virtual method fields and substuting an appropriate type field.
    //
    if (javaLangObjectTIB == null) {
      VM_Class cls = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Object;")).asClass();
      javaLangObjectTIB = cls.getTypeInformationBlock();
    }
       
    typeInformationBlock = new Object[javaLangObjectTIB.length];
    VM_Statics.setSlotContents(tibSlot, typeInformationBlock);
    typeInformationBlock[0] = this;
    if (VM.BuildForFastDynamicTypeCheck) {
      typeInformationBlock[TIB_SUPERCLASS_IDS_INDEX] = VM_DynamicTypeCheck.buildSuperclassIds(this);
      typeInformationBlock[TIB_IMPLEMENTS_TRITS_INDEX] = VM_DynamicTypeCheck.buildImplementsTrits(this);
      if (!elementType.isPrimitiveType() && elementType.isResolved()) {
	typeInformationBlock[TIB_ARRAY_ELEMENT_TIB_INDEX] = elementType.getTypeInformationBlock();
      }
    }
 
    state = CLASS_RESOLVED;
  }

  // Build type information block and install it in jtoc.
  //
  public final void instantiate() {
    if (isInstantiated())
      return;
    if (VM.VerifyAssertions) VM.assert(state == CLASS_RESOLVED);
  
    if (VM.TraceClassLoading) VM.sysWrite("VM_Array: instantiate " + descriptor + "\n");
    
    // Initialize TIB slots for virtual methods (copy from superclass == Object)
    for (int i = TIB_FIRST_VIRTUAL_METHOD_INDEX, n = javaLangObjectTIB.length; i < n; ++i)
      typeInformationBlock[i] = javaLangObjectTIB[i];
    
    state = CLASS_INITIALIZED; // arrays have no "initialize" phase
  }

  // No-op (arrays have no <clinit> method).
  //
  public final void initialize() { }
  
}
