/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import java.lang.reflect.*;

/**
 * Platform dependent utility functions called from VM_JNIFunctions
 * (cannot be placed in VM_JNIFunctions because methods 
 * there are specially compiled to be called from native).
 * 
 * @see VM_JNIFunctions
 * 
 * @author Dave Grove
 * @author Ton Ngo
 * @author Steve Smith 
 */
abstract class VM_JNIHelpers extends VM_JNIGenericHelpers {
  
  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  static Object invokeInitializer(Class cls, int methodID, VM_Address argAddress, 
                                         boolean isJvalue, boolean isDotDotStyle) 
    throws Exception  {

    // get the parameter list as Java class
    VM_Method mth = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = mth.getParameterTypes();
    Class[]   argClasses = new Class[argTypes.length];
    for (int i=0; i<argClasses.length; i++) {
      argClasses[i] = argTypes[i].resolve().getClassForType();
    }

    Constructor constMethod = cls.getConstructor(argClasses);
    if (constMethod==null)
      throw new Exception("Constructor not found");


    // Package the parameters for the constructor
    VM_Address varargAddress;
    if (isDotDotStyle) 
      // flag is false because this JNI function has 3 args before the var args
      varargAddress = getVarArgAddress(false);    
    else
      varargAddress = argAddress;

    Object argObjs[];
    if (isJvalue)
      argObjs = packageParameterFromJValue(mth, argAddress);
    else
      argObjs = packageParameterFromVarArg(mth, varargAddress);

    // construct the new object
    return constMethod.newInstance(argObjs);
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>Method
   * (static method invocation)
   * @param methodID the method ID
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithDotDotVarArg(int methodID, VM_TypeReference expectReturnType)
    throws Exception, 
           VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure
    VM_Address varargAddress = getVarArgAddress(false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, true);
  }

  /**
   * Common code shared by the JNI functions Call<type>Method
   * (virtual method invocation)
   * @param obj the object instance 
   * @param methodID the method ID
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args  true if the calling JNI Function takes 4 args before the vararg
   *                   false if the calling JNI Function takes 3 args before the vararg
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithDotDotVarArg(Object obj, int methodID, 
                                              VM_TypeReference expectReturnType, 
                                              boolean skip4Args)
    throws Exception,
           VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure

    VM_Address varargAddress = getVarArgAddress(skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, true);
  }

  /**
   * This method supports var args passed from C
   *
   * In the Linux Intel C convention, the caller places the args immediately above the
   * saved return address, starting with the first arg
   *
   *
   *
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by 
   * subsequent calls.
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention.
   *
   * NOTE: This method contains internal stack pointer.
   * For now we assume that the stack will not be relocatable while native code is running
   * because native code can hold an address into the stack, so this code is OK,
   * but this is an issue to be resolved later
   *
   * NOTE:  this method assumes that it is immediately above the 
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and 
   * the C caller frame in the respective order.  
   * Therefore, this method will not work if called from anywhere else
   *
   *  low address
   *
   *   |  fp  | <- VM_JNIEnvironment.getVarArgAddress
   *   | mid  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- VM_JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | ...  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- JNI method frame
   *   | mid  |
   *   | ...  |
   *   | arg 0|    args copied by JNI prolog (3 for static, nonvirtual, 
   *   | arg 1|    or 4 for virtual)
   *   | arg 2|
   *   |      |
   *   |      |
   *   |------|
   *   | fp   | <- Native C caller frame
   *   |return|
   *   | arg 0|    
   *   | arg 1|    
   *   | arg 2|
   *   | arg 3|
   *   | arg 4|
   *   | arg 5|
   *   | arg 6|
   *   | arg 7|
   *   | arg 8|    
   *   | arg 9|
   *   |      |
   *   |      |
   *   |      |
   *
   *
   *   high address
   *
   *
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  private static VM_Address getVarArgAddress(boolean skip4Args) {
    VM_Address fp = VM_Magic.getFramePointer();
    fp = VM_Magic.getMemoryAddress(fp);
    fp = VM_Magic.getMemoryAddress(fp);
    return (fp.add(2*4 + (skip4Args ? 4*4 : 3*4)));
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithVarArg(int methodID, VM_Address argAddress, VM_TypeReference expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, true);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance 
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithVarArg(Object obj, int methodID, VM_Address argAddress, 
                                        VM_TypeReference expectReturnType, boolean skip4Args) 
    throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, true);
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID id of VM_MemberReference
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithJValue(int methodID, VM_Address argAddress, 
                                        VM_TypeReference expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, false);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance 
   * @param methodID id of VM_MemberReference
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object invokeWithJValue(Object obj, int methodID, VM_Address argAddress, 
                                        VM_TypeReference expectReturnType, boolean skip4Args) 
    throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, false);
  }

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance 
   * @param methodID id of VM_MemberReference
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to 
   *                     VM_Reflection.invoke().  
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object 
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param isVarArg  This flag describes whether the array of parameters is in var arg format or
   *                  jvalue format
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  static Object packageAndInvoke(Object obj, int methodID, VM_Address argAddress, 
                                        VM_TypeReference expectReturnType, boolean skip4Args, 
                                        boolean isVarArg) 
    throws Exception,
           VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure

    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference returnType = targetMethod.getReturnType();

    if (VM_JNIFunctions.traceJNI) {
      VM.sysWrite("JNI CallXXXMethod:  (mid " + methodID + ") " +
                  targetMethod.getDeclaringClass().toString() + 
                  "." + 
                  targetMethod.getName().toString() + "\n");
    }
    
    if (expectReturnType==null) {   // for reference return type 
      if (!returnType.isReferenceType())
        throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);      
    } else {    // for primitive return type
      if (!returnType.definitelySame(expectReturnType))
        throw new Exception("Wrong return type for method: expect " + expectReturnType + 
                            " instead of " + returnType);
    }  

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    if (isVarArg) {
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
    } else {
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
    }

    // now invoke the method
    return VM_Reflection.invoke(targetMethod, obj, argObjectArray, skip4Args);
  }

  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    VM_Address addr = argAddress;
    for (int i=0; i<argCount; i++) {
      int loword = VM_Magic.getMemoryInt(addr);
      addr = addr.add(4);

      // convert and wrap the argument according to the expected type
      if (argTypes[i].isFloatType()) {
        // NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
        // so we have to extract it as a double and convert it back to a float
        int hiword = VM_Magic.getMemoryInt(addr);
        addr = addr.add(4);                       
        long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
      } else if (argTypes[i].isDoubleType()) {
        int hiword = VM_Magic.getMemoryInt(addr);
        addr = addr.add(4);
        long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
      } else if (argTypes[i].isLongType()) { 
        int hiword = VM_Magic.getMemoryInt(addr);
        addr = addr.add(4);
        long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapLong(longValue);
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte               
        argObjectArray[i] = VM_Reflection.wrapBoolean(loword);
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = VM_Reflection.wrapByte((byte) loword);
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapChar((char) loword);
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapShort((short) loword);
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
        argObjectArray[i] =  env.getJNIRef(loword);   
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = VM_Reflection.wrapInt(loword);
      } else {
        return null;
      }
    }

    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    VM_Address addr = argAddress;
    for (int i=0; i<argCount; i++, addr = addr.add(8)) {
      int loword = VM_Magic.getMemoryInt(addr);
      int hiword;
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isFloatType()) {
        argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat(loword));
      } else if (argTypes[i].isDoubleType()) {
        hiword = VM_Magic.getMemoryInt(addr.add(4));
        long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
      } else if (argTypes[i].isLongType()) { 
        hiword = VM_Magic.getMemoryInt(addr.add(4));
        long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
        argObjectArray[i] = VM_Reflection.wrapLong(longValue);
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte       
        argObjectArray[i] = VM_Reflection.wrapBoolean(loword & 0x000000FF);
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = VM_Reflection.wrapByte((byte) (loword & 0x000000FF));
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapChar((char) (loword & 0x0000FFFF));
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = VM_Reflection.wrapShort((short) (loword & 0x0000FFFF));
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] =  env.getJNIRef(loword);   
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = VM_Reflection.wrapInt(loword);
      } else {
        return null;
      }
    }

    return argObjectArray;
  }

}
