/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni.ia32;

import java.lang.reflect.Constructor;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;

import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;

import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIGenericHelpers;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.unboxed.Address;

/**
 * Platform dependent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see org.jikesrvm.jni.JNIFunctions
 */
public abstract class JNIHelpers extends JNIGenericHelpers {

  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param cls class whose constructor is to be invoked
   * @param methodID the method ID for a constructor
   * @param argAddress where to find the arguments for the constructor
   * @param isJvalue {@code true} if parameters are passed as a jvalue array
   * @param isDotDotStyle {@code true} if the method uses varargs
   * @return a new object created by the specified constructor
   * @throws Exception when the reflective invocation of the constructor fails
   */
  public static Object invokeInitializer(Class<?> cls, int methodID, Address argAddress, boolean isJvalue,
                                         boolean isDotDotStyle) throws Exception {
    // get the parameter list as Java class
    MemberReference mr = MemberReference.getMemberRef(methodID);
    TypeReference tr = java.lang.JikesRVMSupport.getTypeForClass(cls).getTypeRef();
    MethodReference methodRef = MemberReference.findOrCreate(tr, mr.getName(), mr.getDescriptor()).asMethodReference();
    RVMMethod mth = methodRef.resolve();

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
      argObjs = packageParametersFromJValuePtr(methodRef, argAddress);
    } else {
      argObjs = packageParameterFromVarArg(methodRef, varargAddress);
    }

    // construct the new object
    return constMethod.newInstance(argObjs);
  }

  /**
   * Common code shared by the JNI functions CallStatic&lt;type&gt;Method
   * (static method invocation)
   * @param methodID the method ID
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value
   * @throws Exception if the return type doesn't match the expected return type
   */
  @NoInline
  @NoOptCompile
  // expect a certain stack frame structure
  public static Object invokeWithDotDotVarArg(int methodID, TypeReference expectReturnType) throws Exception {
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Address varargAddress = getVarArgAddress(false);
    Object[] argObjectArray = packageParameterFromVarArg(mr, varargAddress);
    return callMethod(null, mr, argObjectArray, expectReturnType, true);
  }

  /**
   * Common code shared by the JNI functions Call&lt;type&gt;Method
   * (virtual method invocation)
   * @param obj the object instance
   * @param methodID the method ID
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args  true if the calling JNI Function takes 4 args before the vararg
   *                   false if the calling JNI Function takes 3 args before the vararg
   * @return an object that may be the return object or a wrapper for the primitive return value
   * @throws Exception if the return type doesn't match the expected return type
   */
  @NoInline
  @NoOptCompile
  // expect a certain stack frame structure
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, TypeReference expectReturnType,
                                              boolean skip4Args) throws Exception {
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Address varargAddress = getVarArgAddress(skip4Args);
    Object[] argObjectArray = packageParameterFromVarArg(mr, varargAddress);
    return callMethod(obj, mr, argObjectArray, expectReturnType, skip4Args);
  }

  /**
   * This method supports var args passed from C.<p>
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
   *   |  fp  | &lt;- JNIEnvironment.getVarArgAddress
   *   | mid  |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | &lt;- JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | ...  |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | &lt;- JNI method frame
   *   | mid  |
   *   | ...  |
   *   | arg 0|    args copied by JNI prolog (3 for static, nonvirtual,
   *   | arg 1|    or 4 for virtual)
   *   | arg 2|
   *   |      |
   *   |      |
   *   |------|
   *   | fp   | &lt;- Native C caller frame
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
   * Common code shared by the JNI functions CallStatic&lt;type&gt;MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value
   * @throws Exception if the return type doesn't match the expected return type
   */
  public static Object invokeWithVarArg(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Object[] argObjectArray = packageParameterFromVarArg(mr, argAddress);
    return callMethod(null, mr, argObjectArray, expectReturnType, true);
  }

  /**
   * Common code shared by the JNI functions Call&lt;type&gt;MethodV
   * @param obj the object instance
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value
   * @throws Exception if the return type doesn't match the expected return type
   */
  public static Object invokeWithVarArg(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Object[] argObjectArray = packageParameterFromVarArg(mr, argAddress);
    return callMethod(obj, mr, argObjectArray, expectReturnType, skip4Args);
  }

  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic&lt;type&gt;MethodV
   * @param targetMethod   The target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(MethodReference targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    Address vaListCopy = SysCall.sysCall.sysVaCopy(argAddress);
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    for (int i = 0; i < argCount; i++) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        argObjectArray[i] = env.getJNIRef(SysCall.sysCall.sysVaArgJobject(vaListCopy));
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJint(vaListCopy);
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJlong(vaListCopy);
      } else if (argTypes[i].isBooleanType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJboolean(vaListCopy);
      } else if (argTypes[i].isByteType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJbyte(vaListCopy);
      } else if (argTypes[i].isCharType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJchar(vaListCopy);
      } else if (argTypes[i].isShortType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJshort(vaListCopy);
      } else if (argTypes[i].isFloatType()) {
        argObjectArray[i] = SysCall.sysCall.sysVaArgJfloat(vaListCopy);
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = SysCall.sysCall.sysVaArgJdouble(vaListCopy);
      }
    }
    SysCall.sysCall.sysVaEnd(vaListCopy);
    return argObjectArray;
  }
}
