/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * The opt compiler's (or at least the front end of the opt compiler)
 * interface to the host "virtual machine" classloader data structures.
 *  
 * @author Doug Lorch (retired)
 * @author Dave Grove
 */
public abstract class OPT_ClassLoaderProxyBase implements OPT_Constants {
  /*
   * Cache references to frequently used VM_Class and VM_Array objects 
   * in a common places
   * NB: OPT_ClassLoaderProxy.java caches references to many frequently used types; 
   * those cached here are
   * simply those used by the opt compiler that aren't currently 
   * cached in OPT_ClassLoaderProxy.
   * Before adding anything here, see what's already available on OPT_ClassLoaderProxy.
   * These static fields are initialized by the subclass constructor.
   */
  public static VM_Type VoidType;
  public static VM_Type BooleanType;
  public static VM_Type ByteType;
  public static VM_Type ShortType;
  public static VM_Type IntType;
  public static VM_Type LongType;
  public static VM_Type FloatType;
  public static VM_Type DoubleType;
  public static VM_Type CharType;
  public static VM_Type JavaLangObjectType;    
  public static VM_Type JavaLangClassType;    
  static VM_Type JavaLangObjectArrayType;
  static VM_Type JavaIoSerializableType; 
  static VM_Type JavaLangCloneableType; 
  static VM_Type JavaLangStringType;    
  static VM_Type JavaLangThrowableType; 
  static VM_Array BooleanArrayType;
  static VM_Array CharArrayType;
  static VM_Array FloatArrayType;
  static VM_Array DoubleArrayType;
  static VM_Array ByteArrayType;
  static VM_Array ShortArrayType;
  static VM_Array IntArrayType;
  static VM_Array LongArrayType;
  static VM_Class JavaLangNullPointerExceptionType;
  static VM_Class JavaLangArrayIndexOutOfBoundsExceptionType;
  static VM_Class JavaLangArithmeticExceptionType;
  static VM_Class JavaLangArrayStoreExceptionType;
  static VM_Class JavaLangClassCastExceptionType;
  static VM_Class JavaLangNegativeArraySizeExceptionType;
  static VM_Class JavaLangIllegalMonitorStateExceptionType;
  static VM_Class JavaLangErrorType;
  static VM_Type NULL_TYPE;
  static VM_Type VALIDATION_TYPE;
  static VM_Type InstructionArrayType;
  static VM_Class VM_Type_type;
  static VM_Class VM_Array_type;
  static VM_Class VM_Class_type;

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   * If there is no common superclass, than null is returned.
   */
  static public VM_Type findCommonSuperclass (VM_Type t1, VM_Type t2) {
    if (t1 == t2)
      return  t1;
    if (t1.isPrimitiveType() && t2.isPrimitiveType()) {
      VM_Type type = null;
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        if (t1.isAddressType() || t2.isAddressType())
          return OPT_ClassLoaderProxy.AddressType;
        if (t1.isIntType() || t2.isIntType())
	    return  OPT_ClassLoaderProxy.IntType;
        if (t1.isCharType() || t2.isCharType())
          return  OPT_ClassLoaderProxy.CharType;
        if (t1.isShortType() || t2.isShortType())
          return  OPT_ClassLoaderProxy.ShortType;
        if (t1.isByteType() || t2.isByteType())
          return  OPT_ClassLoaderProxy.ByteType;
      }
      return  null;
    }
    if (!((t1.isReferenceType() || t1.isAddressType()) && 
          (t2.isReferenceType() || t2.isAddressType())))
	return null;
    // can these next two cases happen?
    if (t1 == NULL_TYPE)
      return  t2;
    if (t2 == NULL_TYPE)
      return  t1;
    if (OPT_IRGenOptions.DBG_TYPE)
      VM.sysWrite("finding common supertype of " + t1 + " and " + t2);
    // Strip off all array junk.
    int arrayDimensions = 0;
    while (t1.isArrayType() && t2.isArrayType()) {
      ++arrayDimensions;
      t1 = ((VM_Array)t1).getElementType();
      t2 = ((VM_Array)t2).getElementType();
    }
    // at this point, they are not both array types.
    // if one is a primitive, then we want an object array of one less
    // dimensionality
    if (t1.isPrimitiveType() || t1.isPrimitiveType()) {
      if (VM.VerifyAssertions)
        VM.assert(t1 != t2);
      VM_Type type = JavaLangObjectType;
      --arrayDimensions;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("one is a primitive array, so supertype is " + type);
      return  type;
    }
    // neither is a primitive, and they are not both array types.
    if (!t1.isClassType() || !t2.isClassType()) {
      // one is a class type, while the other isn't.
      VM_Type type = OPT_ClassLoaderProxy.JavaLangObjectType;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("differing dimensionalities for arrays, so supertype is "
            + type);
      return  type;
    }
    // they both must be class types.
    // technique: push heritage of each type on a separate stack,
    // then find the highest point in the stack where they differ.
    VM_Class c1 = (VM_Class)t1;
    VM_Class c2 = (VM_Class)t2;
    OPT_Stack s1 = new OPT_Stack();
    if (c1.isLoaded() && c2.isLoaded()) {
      // The ancestor hierarchy is available, so do this exactly
      do {
        s1.push(c1);
        c1 = c1.getSuperClass();
      } while (c1 != null);
      OPT_Stack s2 = new OPT_Stack();
      do {
        s2.push(c2);
        c2 = c2.getSuperClass();
      } while (c2 != null);
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("stack 1: " + s1);
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("stack 2: " + s2);
      VM_Type best = OPT_ClassLoaderProxy.JavaLangObjectType;
      while (!s1.empty() && !s2.empty()) {
        VM_Class temp = (VM_Class)s1.pop();
        if (temp == s2.pop())
          best = temp; 
        else 
          break;
      }
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("common supertype of the two classes is " + best);
      while (arrayDimensions-- > 0)
        best = best.getArrayTypeForElementType();
      return  best;
    } else {
      if (OPT_IRGenOptions.DBG_TYPE && !c1.isLoaded())
        VM.sysWrite(c1 + " is not loaded, using Object as common supertype");
      if (OPT_IRGenOptions.DBG_TYPE && !c2.isLoaded())
        VM.sysWrite(c2 + " is not loaded, using Object as common supertype");
      VM_Type common = OPT_ClassLoaderProxy.JavaLangObjectType;
      while (arrayDimensions-- > 0)
        common = common.getArrayTypeForElementType();
      return  common;
    }
  }

 /**
   * Return OPT_Constants.YES if the parent type is defintely a supertype
   *    of the child type.
   * <p> Return OPT_Constants.NO if the parent type is definitely not 
   * a supertype of the child type.
   * <p> Return OPT_Constants.MAYBE if the question cannot be currently answered
   *    (for example if one/both of the classes is not resolved)
   *
   * <p> Takes into account the special 'null-type', which corresponds to a null
   * constant.
   *
   * @param parentType parent type
   * @param childType child type
   * @return OPT_Constants.YES, OPT_Constants.NO, or OPT_Constants.MAYBE
   */
  static public byte includesType (VM_Type parentType, VM_Type childType) {
    // First handle some cases that we can answer without needing to 
    // look at the type hierarchy
    // NOTE: The ordering of these tests is critical!
    if (childType == NULL_TYPE) {
      if (parentType.isReferenceType())
        return  YES; 
      else 
        return  NO;
    }
    if (parentType == NULL_TYPE)
      return  NO;
    if (parentType == childType)
      return  YES;
    if (parentType.isPrimitiveType() || childType.isPrimitiveType())
      return  NO;
    if (parentType == OPT_ClassLoaderProxy.JavaLangObjectType)
      return  YES;
    // Oh well, we're going to have to try to actually look 
    // at the type hierarchy.
    // IMPORTANT: We aren't allowed to cause dynamic class loading, 
    // so we have to roll some of this ourselves 
    // instead of simply calling VM_Runtime.instanceOf 
    // (which is allowed/required to load classes to answer the question).
    try {
      if (parentType.isArrayType()) {
        if (childType == OPT_ClassLoaderProxy.JavaLangObjectType)
          return  MAYBE;        // arrays are subtypes of Object.
        if (!childType.isArrayType())
          return  NO;
        VM_Type parentET = parentType.asArray().getInnermostElementType();
        if (parentET == OPT_ClassLoaderProxy.JavaLangObjectType) {
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
          // to isAssignableWith(parentET, childET).
          // If the dimensionalities are not equal then the answer is NO
          if (parentType.getDimensionality() == childType.getDimensionality())
            return includesType(parentET, 
				childType.asArray().getInnermostElementType()); 
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
	      return  NO; 
	    else {
	      if (VM_Runtime.isAssignableWith(childType, parentType))
		return  MAYBE; 
	      else 
		return  NO;
            }
          }
        } else {
          return  MAYBE;
        }
      }
    } catch (Throwable e) {
      OPT_OptimizingCompilerException.UNREACHABLE();
      return  MAYBE;            // placate jikes.
    }
  }

  /**
   * Abstraction of a StringConstant (used in StringConstantOperand)
   */
  public static abstract class StringWrapperBase {

    public abstract boolean equals (Object str);

    public abstract String toString ();

    public abstract int offset ();
  }

  /** Bogus "null type" to use to stand for the null constant */
  interface OPT_DUMMYNullPointerType {}

  /**  Bogus "validation type" to use for validation operands. */
  interface OPT_DUMMYValidationType {}
}



