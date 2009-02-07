/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni.ia32;

import java.lang.reflect.Constructor;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIFunctions;
import org.jikesrvm.jni.JNIGenericHelpers;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.unboxed.Address;

/**
 * Platform dependent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see JNIFunctions
 */
public abstract class JNIHelpers extends JNIGenericHelpers {

  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  public static Object invokeInitializer(Class<?> cls, int methodID, Address argAddress, boolean isJvalue,
                                         boolean isDotDotStyle) throws Exception {

    // get the parameter list as Java class
    MemberReference mr = MemberReference.getMemberRef(methodID);
    TypeReference tr = java.lang.JikesRVMSupport.getTypeForClass(cls).getTypeRef();
    RVMMethod mth = MemberReference.findOrCreate(tr, mr.getName(), mr.getDescriptor()).asMethodReference().resolve();

    Constructor<?> constMethod = java.lang.reflect.JikesRVMSupport.createConstructor(mth);
    if (!mth.isPublic()) {
      constMethod.setAccessible(true);
    }

    // Package the parameters for the constructor
    Address varargAddress;
    if (isDotDotStyle) {
      // flag is false because this JNI function has 3 args before the var args
      varargAddress = getVarArgAddress(false);
    } else {
      varargAddress = argAddress;
    }

    Object[] argObjs;
    if (isJvalue) {
      argObjs = packageParameterFromJValue(mth, argAddress);
    } else {
      argObjs = packageParameterFromVarArg(mth, varargAddress);
    }

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
  @NoInline
  @NoOptCompile
  // expect a certain stack frame structure
  public static Object invokeWithDotDotVarArg(int methodID, TypeReference expectReturnType) throws Exception {
    Address varargAddress = getVarArgAddress(false);
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
  @NoInline
  @NoOptCompile
  // expect a certain stack frame structure
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, TypeReference expectReturnType,
                                              boolean skip4Args) throws Exception {

    Address varargAddress = getVarArgAddress(skip4Args);
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, true);
  }

  /**
   * This method supports var args passed from C.
   *
   * In the Linux Intel C convention, the caller places the args immediately above the
   * saved return address, starting with the first arg. <br>
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by
   * subsequent calls. <br>
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention. <br>
   *
   * NOTE: This method contains internal stack pointer.
   * For now we assume that the stack will not be relocatable while native code is running
   * because native code can hold an address into the stack, so this code is OK,
   * but this is an issue to be resolved later. <br>
   *
   * NOTE:  this method assumes that it is immediately above the
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and
   * the C caller frame in the respective order.
   * Therefore, this method will not work if called from anywhere else.
   *
   * <pre>
   *  low address
   *
   *   |  fp  | <- JNIEnvironment.getVarArgAddress
   *   | mid  |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | <- JNIEnvironment.invokeWithDotDotVarArg frame
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
   * </pre>
   *
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  @NoInline
  private static Address getVarArgAddress(boolean skip4Args) {
    Address fp = Magic.getFramePointer();
    fp = fp.loadAddress();
    fp = fp.loadAddress();
    return (fp.plus(2 * WORDSIZE + (skip4Args ? 4 * WORDSIZE : 3 * WORDSIZE)));
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithVarArg(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, true);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithVarArg(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, true);
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID id of MemberReference
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithJValue(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, false);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance
   * @param methodID id of MemberReference
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithJValue(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, false);
  }

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance
   * @param methodID id of MemberReference
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to
   *                     Reflection.invoke().
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param isVarArg  This flag describes whether the array of parameters is in var arg format or
   *                  jvalue format
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  @NoInline
  @NoOptCompile
  // expect a certain stack frame structure
  static Object packageAndInvoke(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                 boolean skip4Args, boolean isVarArg) throws Exception {

    RVMMethod targetMethod = MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    TypeReference returnType = targetMethod.getReturnType();

    if (JNIFunctions.traceJNI) {
      VM.sysWrite("JNI CallXXXMethod:  (mid " +
                  methodID +
                  ") " +
                  targetMethod.getDeclaringClass().toString() +
                  "." +
                  targetMethod.getName().toString() +
                  "\n");
    }

    if (expectReturnType == null) {   // for reference return type
      if (!returnType.isReferenceType()) {
        throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);
      }
    } else {    // for primitive return type
      if (!returnType.definitelySame(expectReturnType)) {
        throw new Exception("Wrong return type for method: expect " + expectReturnType + " instead of " + returnType);
      }
    }

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    if (isVarArg) {
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
    } else {
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
    }

    // now invoke the method
    return Reflection.invoke(targetMethod, null, obj, argObjectArray, skip4Args);
  }

  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param targetMethod   The target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(RVMMethod targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef(addr.loadInt());
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = addr.loadInt();
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = addr.loadLong();
        addr = addr.plus(2*WORDSIZE);
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = addr.loadByte() != 0;
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = addr.loadByte();
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = addr.loadChar();
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = addr.loadShort();
        addr = addr.plus(WORDSIZE);
      } else if (argTypes[i].isFloatType()) {
        // NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
        // so we have to extract it as a double and convert it back to a float
        argObjectArray[i] = (float) addr.loadDouble();
        addr = addr.plus(2*WORDSIZE);
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = addr.loadDouble();
        addr = addr.plus(2*WORDSIZE);
      }
    }
    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param targetMethod   The target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(RVMMethod targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++, addr = addr.plus(2*WORDSIZE)) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef(addr.loadInt());
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = addr.loadInt();
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = addr.loadLong();
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = addr.loadByte() != 0;
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = addr.loadByte();
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = addr.loadChar();
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = addr.loadShort();
      } else if (argTypes[i].isFloatType()) {
        argObjectArray[i] = addr.loadFloat();
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = addr.loadDouble();
      }
    }
    return argObjectArray;
  }
}
