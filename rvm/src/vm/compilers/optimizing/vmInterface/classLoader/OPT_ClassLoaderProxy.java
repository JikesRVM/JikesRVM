/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * @author Doug Lorch (retired)
 * @author Dave Grove
 * @author Julian Dolby
 **/

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

public final class OPT_ClassLoaderProxy 
implements VM_ClassLoaderConstants, VM_Constants, OPT_Constants
{
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
  public static VM_Type JavaLangObjectArrayType;
  public static VM_Type JavaIoSerializableType; 
  public static VM_Type JavaLangCloneableType; 
  public static VM_Type JavaLangStringType;    
  public static VM_Type JavaLangThrowableType; 
  public static VM_Array BooleanArrayType;
  public static VM_Array CharArrayType;
  public static VM_Array FloatArrayType;
  public static VM_Array DoubleArrayType;
  public static VM_Array ByteArrayType;
  public static VM_Array ShortArrayType;
  public static VM_Array IntArrayType;
  public static VM_Array LongArrayType;
  public static VM_Class JavaLangNullPointerExceptionType;
  public static VM_Class JavaLangArrayIndexOutOfBoundsExceptionType;
  public static VM_Class JavaLangArithmeticExceptionType;
  public static VM_Class JavaLangArrayStoreExceptionType;
  public static VM_Class JavaLangClassCastExceptionType;
  public static VM_Class JavaLangNegativeArraySizeExceptionType;
  public static VM_Class JavaLangIllegalMonitorStateExceptionType;
  public static VM_Class JavaLangErrorType;
  public static VM_Type NULL_TYPE;
  public static VM_Type VALIDATION_TYPE;
  public static VM_Type InstructionArrayType;
  public static VM_Class VM_Type_type;
  public static VM_Class VM_Array_type;
  public static VM_Class VM_Class_type;

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   * If there is no common superclass, than null is returned.
   */
  public static VM_Type findCommonSuperclass (VM_Type t1, VM_Type t2) {
    if (t1 == t2)
      return  t1;
    if (t1.isPrimitiveType() && t2.isPrimitiveType()) {
      VM_Type type = null;
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        if (t1.isWordType() || t2.isWordType())
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
    if (!((t1.isReferenceType() || t1.isWordType()) && 
          (t2.isReferenceType() || t2.isWordType())))
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
        VM._assert(t1 != t2);
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
  public static byte includesType (VM_Type parentType, VM_Type childType) {
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

  private static VM_Class uninterruptibleClass;
  private static VM_Class VM_BootRecordType;
  public static VM_Type VM_ProcessorType;
  public static VM_Type AddressType;
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


    NULL_TYPE = findOrCreateType("Lcom/ibm/JikesRVM/opt/OPT_ClassLoaderProxy$OPT_DUMMYNullPointerType;", vmcl);
    VALIDATION_TYPE = findOrCreateType("Lcom/ibm/JikesRVM/opt/OPT_ClassLoaderProxy$OPT_DUMMYValidationType;", vmcl);

    // VM Types
    VM_Type_type = findOrCreateType("Lcom/ibm/JikesRVM/VM_Type;", vmcl).asClass();
    VM_Array_type = findOrCreateType("Lcom/ibm/JikesRVM/VM_Array;", vmcl).asClass();
    VM_Class_type = findOrCreateType("Lcom/ibm/JikesRVM/VM_Class;", vmcl).asClass();
    uninterruptibleClass = findOrCreateType("Lcom/ibm/JikesRVM/VM_Uninterruptible;", vmcl).asClass();
    VM_BootRecordType     = findOrCreateType("Lcom/ibm/JikesRVM/VM_BootRecord;", vmcl).asClass();
    InstructionArrayType  = findOrCreateType(VM.INSTRUCTION_ARRAY_SIGNATURE, vmcl);
    VM_ProcessorType      = findOrCreateType("Lcom/ibm/JikesRVM/VM_Processor;", vmcl);
    MagicType             = findOrCreateType("Lcom/ibm/JikesRVM/VM_Magic;", vmcl);
    UninterruptibleType   = findOrCreateType("Lcom/ibm/JikesRVM/VM_Uninterruptible;", vmcl);
    DynamicBridgeType     = findOrCreateType("Lcom/ibm/JikesRVM/VM_DynamicBridge;", vmcl);
    SaveVolatileType      = findOrCreateType("Lcom/ibm/JikesRVM/VM_SaveVolatile;", vmcl);
    NativeBridgeType      = findOrCreateType("Lcom/ibm/JikesRVM/VM_NativeBridge;", vmcl);
    AddressType           = findOrCreateType("Lcom/ibm/JikesRVM/VM_Address;", vmcl);
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
  public static VM_Method findSpecialMethod(VM_Method sought) {
    return VM_Class.findSpecialMethod( sought );
  }
  /**
   * Get description of specified primitive array.
   * @param atype array type number (see "newarray" bytecode description in Java VM Specification)
   * @return array description
   */
  public static VM_Array getPrimitiveArrayType(int atype) {
    return VM_Array.getPrimitiveArrayType( atype );
  }

  /**
   *  Is dynamic linking code required to access one member when 
   * referenced from another?
   *
   * @param referent the member being referenced
   * @param referrer the method containing the reference
   */
  public static boolean needsDynamicLink(VM_Member referent, VM_Class referrer) {
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

  public static VM_Type getVMProcessorType() {
    return VM_ProcessorType;
  };

  public static VM_Class getBootRecordType() {
    return VM_BootRecordType;
  }
}
