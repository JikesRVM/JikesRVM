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
public abstract class OPT_ClassLoaderProxy implements OPT_Constants {
  /**
   * The classloader to be used.
   * Must be set during OPT_Compiler initialization.
   */
  public static OPT_ClassLoaderProxy proxy;
  /*
   * Cache references to frequently used VM_Class and VM_Array objects 
   * in a common places
   * NB: VM_Type.java caches references to many frequently used types; 
   * those cached here are
   * simply those used by the opt compiler that aren't currently 
   * cached in VM_Type.
   * Before adding anything here, see what's already available on VM_Type.
   * These static fields are initialized by the subclass constructor.
   */
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
  static VM_Class VM_Type_type;
  static VM_Class VM_Array_type;
  static VM_Class VM_Class_type;
  static VM_Type JavaLangObjectArrayType;
  static VM_Type NULL_TYPE;
  static VM_Type VALIDATION_TYPE;
  static VM_Class uninterruptibleClass;
  static VM_Type InstructionArrayType;
  static VM_Type VM_ProcessorType;

  // --------------------------------------------------------------------------
  // Creating/finding instances of classloader classes
  // --------------------------------------------------------------------------
  /**
   * Does a "new" of a subclass of VM_Array named by des.
   */
  public abstract VM_Array createArray (VM_Atom des);

  /**
   * Does a "new" of a subclass of VM_Class named by des.
   */
  public abstract VM_Class createClass (VM_Atom des);

  /**
   * Does a "new" of a subclass of VM_Primitive named by des.
   */
  public abstract VM_Primitive createPrimitive (VM_Atom des);

  /**
   * Find (creating if it doesn't already exist) the VM_Type
   * object specified by the String
   */
  public abstract VM_Type findOrCreateType (String str);

  // --------------------------------------------------------------------------
  // Querry classloader data structures
  // --------------------------------------------------------------------------
  /**
   * Determines whether "cls" implements "interf".
   */
  public abstract boolean classImplementsInterface (VM_Type cls, 
                                                    VM_Type interf);

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   * If there is no common superclass, than null is returned.
   */
  public VM_Type findCommonSuperclass (VM_Type t1, VM_Type t2) {
    if (t1 == t2)
      return  t1;
    if (t1.isPrimitiveType() && t2.isPrimitiveType()) {
      VM_Type type = null;
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        if (t1.isIntType() || t2.isIntType())
          return  VM_Type.IntType;
        if (t1.isCharType() || t2.isCharType())
          return  VM_Type.CharType;
        if (t1.isShortType() || t2.isShortType())
          return  VM_Type.ShortType;
        if (t1.isByteType() || t2.isByteType())
          return  VM_Type.IntType;
      }
      return  null;
    }
    if (!t1.isReferenceType() || !t2.isReferenceType())
      return  null;
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
    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      if (VM.VerifyAssertions) VM.assert(t1 != t2);
      VM_Type type = VM_Type.JavaLangObjectType;
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
      VM_Type type = VM_Type.JavaLangObjectType;
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
      VM_Type best = VM_Type.JavaLangObjectType;
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
      VM_Type common = VM_Type.JavaLangObjectType;
      while (arrayDimensions-- > 0)
        common = common.getArrayTypeForElementType();
      return  common;
    }
  }

  /**
   * Return OPT_Constants.YES if the parent type is defintely a supertype
   *    of the child type.
   * <p> Return OPT_Constants.NO if the parent type is definitely not a 
   *     supertype of the child type.
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
  public abstract byte isAssignableWith (VM_Type parentType, VM_Type childType);

  // --------------------------------------------------------------------------
  // Constant pool access
  // --------------------------------------------------------------------------
  /**
   * Create an int constant operand to represent the specified 
   * constant pool entry
   * @param klass The class whose constant pool is being accessed.
   * @param cpIx  The number of the constant pool entry 
   */
  public abstract OPT_IntConstantOperand getIntFromConstantPool 
                                        (VM_Class klass, int cpIx);

  /**
   * Create a double constant operand to represent the specified constant 
   * pool entry
   * @param klass The class whose constant pool is being accessed.
   * @param cpIx  The number of the constant pool entry (in the class).
   */
  public abstract OPT_DoubleConstantOperand getDoubleFromConstantPool 
                                        (VM_Class klass, int cpIx);

  /**
   * Create a float constant operand to represent the specified 
   * constant pool entry
   * Gets a double constantfrom the constant pool.
   * @param klass The class whose constant pool is being accessed.
   * @param cpIx  The number of the constant pool entry (in the class).
   */
  public abstract OPT_FloatConstantOperand getFloatFromConstantPool 
                                        (VM_Class klass, int cpIx);

  /**
   * Create a long constant operand to represent the specified 
   * constant pool entry
   * Gets a long from the constant pool
   * @param klass The class whose constant pool is being accessed.
   * @param cpIx  The number of the constant pool entry (in the class).
   */
  public abstract OPT_LongConstantOperand getLongFromConstantPool 
                                        (VM_Class klass, int cpIx);

  /**
   * Create a string constant operand to represent the specified constant 
   * pool entry
   * Gets a string from the constant pool
   * @param klass The class whose constant pool is being accessed.
   * @param cpIx  The number of the constant pool entry (in the class).
   */
  public abstract OPT_StringConstantOperand getStringFromConstantPool 
                                        (VM_Class klass, int cpIx);

  /**
   * Abstraction of a StringConstant (used in StringConstantOperand)
   */
  public static abstract class StringWrapper {

    public abstract boolean equals (Object str);

    public abstract String toString ();

    public abstract int offset ();
  }

  /** Bogus "null type" to use to stand for the null constant */
  interface OPT_DUMMYNullPointerType {}

  /**  Bogus "validation type" to use for validation operands. */
  interface OPT_DUMMYValidationType {}
}



