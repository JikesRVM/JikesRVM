/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An implementation of {@link OPT_ClassLoaderProxy} for the RVM.
 *
 * @author Doug Lorch (retired)
 * @author Dave Grove
 * @author Julian Dolby
 **/
public final class OPT_ClassLoaderProxy 
    extends OPT_ClassLoaderProxyBase 
    implements VM_ClassLoaderConstants, VM_Constants
{

  private static VM_Class uninterruptibleClass;
  private static VM_Class VM_BootRecordType;
  static VM_Type VM_ProcessorType;
  static VM_Type AddressType;
  private static VM_Type MagicType;             
  private static VM_Type UninterruptibleType;   
  private static VM_Type DynamicBridgeType;     
  private static VM_Type NativeBridgeType;     
  private static VM_Type SaveVolatileType;      

  /**
   * Initialize proxy state to Jikes RVM values
   */
  static {
      ClassLoader vmcl = VM_SystemClassLoader.getVMClassLoader();

      // primitive types
      VoidType = findOrCreateType("void", "V");
      BooleanType = findOrCreateType("boolean", "Z");
      ByteType = findOrCreateType("byte", "B");
      ShortType = findOrCreateType("short", "S");
      IntType = findOrCreateType("int", "I");
      LongType = findOrCreateType("long", "J");
      FloatType = findOrCreateType("float", "F");
      DoubleType = findOrCreateType("double", "D");
      CharType = findOrCreateType("char", "C");

      // primitive arrays
      BooleanArrayType = VM_Array.getPrimitiveArrayType(4);
      CharArrayType = VM_Array.getPrimitiveArrayType(5);
      FloatArrayType = VM_Array.getPrimitiveArrayType(6);
      DoubleArrayType = VM_Array.getPrimitiveArrayType(7);
      ByteArrayType = VM_Array.getPrimitiveArrayType(8);
      ShortArrayType = VM_Array.getPrimitiveArrayType(9);
      IntArrayType = VM_Array.getPrimitiveArrayType(10);
      LongArrayType = VM_Array.getPrimitiveArrayType(11);

      // commonly used object types
      JavaLangObjectType    = findOrCreateType("Ljava/lang/Object;", vmcl);
      JavaLangClassType     = findOrCreateType("Ljava/lang/Class;", vmcl);
      JavaLangThrowableType = findOrCreateType("Ljava/lang/Throwable;", vmcl);
      JavaLangStringType    = findOrCreateType("Ljava/lang/String;", vmcl);
      JavaLangCloneableType = findOrCreateType("Ljava/lang/Cloneable;", vmcl);
      JavaIoSerializableType = findOrCreateType("Ljava/io/Serializable;", vmcl);
      JavaLangObjectArrayType = findOrCreateType("[Ljava/lang/Object;", vmcl);

      // exception types
      JavaLangNullPointerExceptionType = findOrCreateType("Ljava/lang/NullPointerException;", vmcl).asClass();
      JavaLangArrayIndexOutOfBoundsExceptionType = findOrCreateType("Ljava/lang/ArrayIndexOutOfBoundsException;", vmcl).asClass();
      JavaLangArithmeticExceptionType = findOrCreateType("Ljava/lang/ArithmeticException;", vmcl).asClass();
      JavaLangArrayStoreExceptionType = findOrCreateType("Ljava/lang/ArrayStoreException;", vmcl).asClass();
      JavaLangClassCastExceptionType = findOrCreateType("Ljava/lang/ClassCastException;", vmcl).asClass();
      JavaLangNegativeArraySizeExceptionType = findOrCreateType("Ljava/lang/NegativeArraySizeException;", vmcl).asClass();
      JavaLangIllegalMonitorStateExceptionType = findOrCreateType("Ljava/lang/IllegalMonitorStateException;", vmcl).asClass();
      JavaLangErrorType = findOrCreateType("Ljava/lang/Error;", vmcl).asClass();


      NULL_TYPE = findOrCreateType("LOPT_ClassLoaderProxy$OPT_DUMMYNullPointerType;", vmcl);
      VALIDATION_TYPE = findOrCreateType("LOPT_ClassLoaderProxy$OPT_DUMMYValidationType;", vmcl);

      // VM Types
      VM_Type_type = findOrCreateType("LVM_Type;", vmcl).asClass();
      VM_Array_type = findOrCreateType("LVM_Array;", vmcl).asClass();
      VM_Class_type = findOrCreateType("LVM_Class;", vmcl).asClass();
      uninterruptibleClass = findOrCreateType("LVM_Uninterruptible;", vmcl).asClass();
      VM_BootRecordType     = findOrCreateType("LVM_BootRecord;", vmcl).asClass();
      InstructionArrayType  = findOrCreateType(VM.INSTRUCTION_ARRAY_SIGNATURE, vmcl);
      VM_ProcessorType      = findOrCreateType("LVM_Processor;", vmcl);
      MagicType             = findOrCreateType("LVM_Magic;", vmcl);
      UninterruptibleType   = findOrCreateType("LVM_Uninterruptible;", vmcl);
      DynamicBridgeType     = findOrCreateType("LVM_DynamicBridge;", vmcl);
      SaveVolatileType      = findOrCreateType("LVM_SaveVolatile;", vmcl);
      NativeBridgeType      = findOrCreateType("LVM_NativeBridge;", vmcl);
      AddressType           = findOrCreateType("LVM_Address;", vmcl);
  }

  /**
   * Return an instance of a VM_Type
   * @param des VM_Atom descriptor of the type
   * @return the VM_Type corresponding to the descriptor
   */
  public static VM_Type findOrCreateType (VM_Atom des, ClassLoader cl) {
      return VM_ClassLoader.findOrCreateType(des, cl);
  }

  public static VM_Type findOrCreateSystemType (VM_Atom des) {
      return findOrCreateType(des,VM_SystemClassLoader.getVMClassLoader());
  }

  /**
   * Return an instance of a VM_Type
   * @param des String descriptor of the type
   * @return the VM_Type corresponding to the descriptor
   */
  public static VM_Type findOrCreateType (String str, ClassLoader cl) {
    return findOrCreateType(VM_Atom.findOrCreateAsciiAtom(str), cl);
  }

  public static VM_Type findOrCreateSystemType (String str) {
    return findOrCreateType(str, VM_SystemClassLoader.getVMClassLoader());
  }

  /**
   * Return an instance of a VM_Type
   * @param des String descriptor of the type
   * @return the VM_Type corresponding to the descriptor
   */
  public static VM_Type findOrCreateType (String des, String symbol) {
    return 
	VM_ClassLoader.findOrCreatePrimitiveType(
	  VM_Atom.findOrCreateAsciiAtom(des),
	  VM_Atom.findOrCreateAsciiAtom(symbol));
  }

  /**
   * Return an instance of a VM_Type
   * @param des String descriptor of the type
   * @return the VM_Type corresponding to the descriptor
   */
  public static VM_Type findOrCreatePrimitiveType (VM_Atom des, VM_Atom symbol) {
      return VM_ClassLoader.findOrCreatePrimitiveType(des, symbol);
  }

  // --------------------------------------------------------------------------
  // Querry classloader data structures
  // --------------------------------------------------------------------------

  public static VM_Method findOrCreateMethod (VM_Atom klass, 
					      VM_Atom name, 
					      VM_Atom des,
					      ClassLoader cl) {
      return VM_ClassLoader.findOrCreateMethod( klass, name, des, cl );
  }

   // Find specified method using "invokespecial" lookup semantics.
  // Taken:    method sought
  // Returned: method found (null --> not found)
  // There are three kinds of "special" method invocation:
  //   - an instance initializer method, eg. <init>
  //   - a non-static but private and/or final method in the current class
  //   - a non-static method for which the non-overridden (superclass) 
  //   version is desired
  //
  static VM_Method findSpecialMethod(VM_Method sought) {
      return VM_Class.findSpecialMethod( sought );
  }
  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  static VM_Array getPrimitiveArrayType(int atype) {
      return VM_Array.getPrimitiveArrayType( atype );
  }

  /**
   *  Is dynamic linking code required to access one member when 
   * referenced from another?
   *
   * @param referent the member being referenced
   * @param referrer the method containing the reference
   */
  public static boolean needsDynamicLink(VM_Member referent, VM_Class referrer)
  {
      return VM_ClassLoader.needsDynamicLink(referent, referrer);
  }

  /**
   * Find the method of the given class that matches the given descriptor.
   */
  public static VM_Method lookupMethod(VM_Class cls, VM_Method meth) {
    VM_Method newmeth = null;
    if (cls.isResolved() && !cls.isInterface()) {
      for (; (newmeth == null) && (cls != null); cls = cls.getSuperClass()) {
        newmeth = 
	  (VM_Method)cls.findDeclaredMethod(meth.getName(), 
					    meth.getDescriptor());
      }
    }
    return newmeth;
  }

  /**
   * Does class vmCls implement interface vmInterf?
   */
  public static boolean classImplementsInterface (VM_Type vmCls, VM_Type vmInterf) {
    VM_Class[] interfaces = ((VM_Class)vmCls).getDeclaredInterfaces();
    VM_Class vmInterfClass = (VM_Class)vmInterf;
    for (int i = 0, n = interfaces.length; i < n; ++i)
      if (interfaces[i] == vmInterfClass)
        return  true;
    return  false;
  }

  // --------------------------------------------------------------------------
  // Constant pool access
  // --------------------------------------------------------------------------
  /**
   * Get the integer stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_IntConstantOperand getIntFromConstantPool (VM_Class klass, 
							int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    int val = VM_Statics.getSlotContentsAsInt(offset);
    return  new OPT_IntConstantOperand(val);
  }

  /**
   * Get the double stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_DoubleConstantOperand getDoubleFromConstantPool (VM_Class klass, 
							      int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    long val_raw = VM_Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return  new OPT_DoubleConstantOperand(val, offset);
  }

  /**
   * Get the float stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_FloatConstantOperand getFloatFromConstantPool (VM_Class klass, 
							    int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    int val_raw = VM_Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return  new OPT_FloatConstantOperand(val, offset);
  }

  /**
   * Get the long stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_LongConstantOperand getLongFromConstantPool (VM_Class klass, 
							  int index) {
    int offset = klass.getLiteralOffset(index) >> 2;
    long val = VM_Statics.getSlotContentsAsLong(offset);
    return  new OPT_LongConstantOperand(val, offset);
  }

  /**
   * Get the String stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_StringConstantOperand 
      getStringFromConstantPool (VM_Class klass, int index) 
  {
    int slot = klass.getLiteralOffset(index) >> 2;
    String val;
    if (VM.runningVM) {
      val = (String)VM_Statics.getSlotContentsAsObject(slot);
    } else {
      // Sigh. What we really want to do is acquire the 
      // String object from the class constant pool.
      // But, we aren't set up to do that.  The following
      // isn't strictly correct, but is closer than the completely bogus
      // thing we were doing before. 
      // TODO: Fix this to do the right thing. 
      //       This will be wrong if someone is comparing string constants
      //       using ==, != since we're very unlikely to get the aliasing right.
      //       Then again, if you are using ==, != with strings and one of them
      //       isn't <null>, perhaps you deserve what you get.
      // This is defect 2838.
      val = ("BootImageStringConstant "+slot).intern();
    }
    return new OPT_StringConstantOperand(val, slot);
  }

  // --------------------------------------------------------------------------
  // Jikes RVM specific stuff
  // --------------------------------------------------------------------------

  static VM_Type getVMProcessorType() {
      return VM_ProcessorType;
  };

  static VM_Class getBootRecordType() {
      return VM_BootRecordType;
  }
}
