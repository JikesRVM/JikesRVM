/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An implementation of {@link OPT_ClassLoaderProxy} for the RVM.
 * Use (install) by doing:  
 * <pre>
 * OPT_ClassLoaderProxy.proxy = new OPT_RVMClassLoaderProxy();
 * </pre>
 * 
 * @author Doug Lorch (retired)
 * @author Dave Grove
 **/
public final class OPT_RVMClassLoaderProxy extends OPT_ClassLoaderProxy {

  /**
   * Default constructor.
   */
  public OPT_RVMClassLoaderProxy () {
    // Initialize the static fields of OPT_ClassLoaderProxy
    BooleanArrayType = VM_Array.getPrimitiveArrayType(4);
    CharArrayType = VM_Array.getPrimitiveArrayType(5);
    FloatArrayType = VM_Array.getPrimitiveArrayType(6);
    DoubleArrayType = VM_Array.getPrimitiveArrayType(7);
    ByteArrayType = VM_Array.getPrimitiveArrayType(8);
    ShortArrayType = VM_Array.getPrimitiveArrayType(9);
    IntArrayType = VM_Array.getPrimitiveArrayType(10);
    LongArrayType = VM_Array.getPrimitiveArrayType(11);
    JavaLangNullPointerExceptionType = findOrCreateType("Ljava/lang/NullPointerException;").asClass();
    JavaLangArrayIndexOutOfBoundsExceptionType = findOrCreateType("Ljava/lang/ArrayIndexOutOfBoundsException;").asClass();
    JavaLangArithmeticExceptionType = findOrCreateType("Ljava/lang/ArithmeticException;").asClass();
    JavaLangArrayStoreExceptionType = findOrCreateType("Ljava/lang/ArrayStoreException;").asClass();
    JavaLangClassCastExceptionType = findOrCreateType("Ljava/lang/ClassCastException;").asClass();
    JavaLangNegativeArraySizeExceptionType = findOrCreateType("Ljava/lang/NegativeArraySizeException;").asClass();
    JavaLangIllegalMonitorStateExceptionType = findOrCreateType("Ljava/lang/IllegalMonitorStateException;").asClass();
    JavaLangErrorType = findOrCreateType("Ljava/lang/Error;").asClass();
    VM_Type_type = findOrCreateType("LVM_Type;").asClass();
    VM_Array_type = findOrCreateType("LVM_Array;").asClass();
    VM_Class_type = findOrCreateType("LVM_Class;").asClass();
    JavaLangObjectArrayType = findOrCreateType("[Ljava/lang/Object;");
    NULL_TYPE = findOrCreateType("LOPT_ClassLoaderProxy$OPT_DUMMYNullPointerType;");
    VALIDATION_TYPE = findOrCreateType("LOPT_ClassLoaderProxy$OPT_DUMMYValidationType;");
    uninterruptibleClass = findOrCreateType("LVM_Uninterruptible;").asClass();
    InstructionArrayType = findOrCreateType(VM.INSTRUCTION_ARRAY_SIGNATURE);
    VM_ProcessorType = findOrCreateType("LVM_Processor;");
  }

  // --------------------------------------------------------------------------
  // Creating/finding instances of classloader classes
  // --------------------------------------------------------------------------

  /**
   * Return an instance of a VM_Type
   * @param des String descriptor of the type
   * @return the VM_Type corresponding to the descriptor
   */
  public VM_Type findOrCreateType (String str) {
    return VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom(str));
  }

  // --------------------------------------------------------------------------
  // Querry classloader data structures
  // --------------------------------------------------------------------------

  /**
   * Compile time type inclusion test for use by the opt compiler.
   * This routine will never load classes to answer a type inclusion question.
   * The child type is interpreted as representing a type and all of its 
   * subtypes (ie the type is not assumed to be precise). <p>
   * 
   * Return OPT_Constants.YES if the parent type is defintely a supertype
   *    of the child type. <p>
   * Return OPT_Constants.NO if the parent type is definitely not a 
   *    supertype of the child type.<p>
   * Return OPT_Constants.MAYBE if the question cannot be currently answered
   *    (for example if one/both of the classes is not resolved or one of the 
   *     types is an interface and there are "overlapping cones").<p>
   *
   * Understands the special 'null-type', which corresponds to a null constant.<p>
   *
   * @param parentType parent type
   * @param childType child type
   * @return OPT_Constants.YES, OPT_Constants.NO, or OPT_Constants.MAYBE
   */
  public byte includesType (VM_Type parentType, VM_Type childType) {
    // First handle some cases that we can answer without needing to 
    // look at the type hierarchy
    // NOTE: The ordering of these tests is critical!
    if (childType == NULL_TYPE) {
      return parentType.isReferenceType() ? YES : NO;
    }
    if (parentType == NULL_TYPE)
      return NO;
    if (parentType == childType)
      return YES;
    if (parentType.isPrimitiveType() || childType.isPrimitiveType())
      return NO;
    if (parentType == VM_Type.JavaLangObjectType)
      return YES;
    // Oh well, we're going to have to try to actually look 
    // at the type hierarchy.
    // IMPORTANT: We aren't allowed to cause dynamic class loading, 
    // so we have to roll some of this ourselves 
    // instead of simply calling VM_Runtime.instanceOf 
    // (which is allowed/required to load classes to answer the question).
    try {
      if (parentType.isArrayType()) {
        if (childType == VM_Type.JavaLangObjectType)
          return MAYBE;        // arrays are subtypes of Object.
        if (!childType.isArrayType())
          return NO;
        VM_Type parentET = parentType.asArray().getInnermostElementType();
        if (parentET == VM_Type.JavaLangObjectType) {
	  int LHSDimension = parentType.getDimensionality();
	  int RHSDimension = childType.getDimensionality();
	  if ((RHSDimension > LHSDimension) ||
	      (RHSDimension == LHSDimension && 
	       childType.asArray().getInnermostElementType().isClassType()))
            return YES; 
	  else 
            return NO;
        } else {
          // parentType is [^k of something other than Object
          // If dimensionalities are equal, then we can reduce 
          // to includesType(parentET, childET).
          // If the dimensionalities are not equal then the answer is NO
          if (parentType.getDimensionality() == childType.getDimensionality())
            return includesType(parentET, childType.asArray().getInnermostElementType()); 
          else 
            return NO;
        }
      } else {                    // parentType.isClassType()
        if (!childType.isClassType())
          return NO; // we know that parentType is not java.lang.Object (see above)
        if (parentType.isInitialized() && childType.isInitialized() ||
	    (VM.writingBootImage && 
	     parentType.asClass().isInBootImage() && 
	     childType.asClass().isInBootImage())) {
          if (parentType.asClass().isInterface()) {
	    if (VM_Runtime.isAssignableWith(parentType, childType)) {
	      return YES;
	    } else {
              // If childType is not a final class, it is 
              // possible that a subclass will implement parentType.
              return childType.asClass().isFinal() ? NO : MAYBE;
            }
          } else if (childType.asClass().isInterface()) {
            // parentType is a proper class, childType is an interface
            return MAYBE;
          } else {
            // parentType & childType are both proper classes.
	    if (VM_Runtime.isAssignableWith(parentType, childType)) {
	      return YES;
	    }
	    // If childType is a final class, then 
	    // !instanceOfClass(parentType, childType) lets us return NO.
	    // However, if childType is not final, then it might have 
	    // subclasses so we can't return NO out of hand.
	    // But, if the reverse instanceOf is also false, then we know 
	    // that parentType and childType are completely 
	    // unrelated and we can return NO.
	    if (childType.asClass().isFinal())
	      return NO; 
	    else {
	      if (VM_Runtime.isAssignableWith(childType, parentType))
		return MAYBE; 
	      else 
		return NO;
            }
          }
        } else {
          return MAYBE;
        }
      }
    } catch (Throwable e) {
      OPT_OptimizingCompilerException.UNREACHABLE();
      return MAYBE;            // placate jikes.
    }
  }

  // --------------------------------------------------------------------------
  // Constant pool access
  // --------------------------------------------------------------------------
  /**
   * Get the integer stored at a particular index of a class's constant
   * pool.
   */
  public OPT_IntConstantOperand getIntFromConstantPool (VM_Class klass, 
							int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    int val = VM_Statics.getSlotContentsAsInt(offset);
    return new OPT_IntConstantOperand(val);
  }

  /**
   * Get the double stored at a particular index of a class's constant
   * pool.
   */
  public OPT_DoubleConstantOperand getDoubleFromConstantPool (VM_Class klass, 
							      int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    long val_raw = VM_Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return new OPT_DoubleConstantOperand(val, offset);
  }

  /**
   * Get the float stored at a particular index of a class's constant
   * pool.
   */
  public OPT_FloatConstantOperand getFloatFromConstantPool (VM_Class klass, 
							    int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    int val_raw = VM_Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return new OPT_FloatConstantOperand(val, offset);
  }

  /**
   * Get the long stored at a particular index of a class's constant
   * pool.
   */
  public OPT_LongConstantOperand getLongFromConstantPool (VM_Class klass, 
							  int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    long val = VM_Statics.getSlotContentsAsLong(offset);
    return new OPT_LongConstantOperand(val, offset);
  }

  /**
   * Get the String stored at a particular index of a class's constant
   * pool.
   */
  public OPT_StringConstantOperand getStringFromConstantPool (VM_Class klass, 
							      int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    StringWrapper str = new RVMStringWrapper(offset);
    return new OPT_StringConstantOperand(str);
  }

  /**
   * A wrapper around String constants in the RVM
   */
  public static final class RVMStringWrapper extends StringWrapper {
    private int offset;         // offset in JTOC

    /**
     * @param     int off JTOC offset of the STring constant
     */
    RVMStringWrapper (int off) {
      offset = off;
    }

    public boolean equals (Object str) {
      if (!(str instanceof RVMStringWrapper))
        return false;
      return offset == ((RVMStringWrapper)str).offset;
    }

    public String toString () {
      return "string constant @" + offset;
    }

    /**
     * @return the JTOC offset of this String constant
     */
    public int offset () {
      return offset;
    }
  }
}
