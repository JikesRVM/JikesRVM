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
package org.jikesrvm.jni.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;

import java.lang.reflect.Constructor;

import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIGenericHelpers;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.scheduler.RVMThread;
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
   * @return a new object created by the specified constructor
   * @throws Exception when the reflective invocation of the constructor fails
   */
  public static Object invokeInitializer(Class<?> cls, int methodID, Address argAddress, boolean isJvalue) throws Exception {
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
    Object[] argObjs;
    if (isJvalue) {
      argObjs = packageParametersFromJValuePtr(methodRef, argAddress);
    } else {
      argObjs = packageParameterFromVarArg(methodRef, argAddress);
    }

    // construct the new object
    return constMethod.newInstance(argObjs);
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value
   */
  public static Object invokeWithVarArg(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Object[] argObjectArray = packageParameterFromVarArg(mr, argAddress);
    return callMethod(null, mr, argObjectArray, expectReturnType, true);
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
    MethodReference mr = MemberReference.getMethodRef(methodID);
    Object[] argObjectArray = packageParameterFromVarArg(mr, argAddress);
    return callMethod(obj, mr, argObjectArray, expectReturnType, skip4Args);
  }

  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param targetMethod   The target {@link RVMMethod}
   * @param argAddress an address into the C space for the array of jvalue unions;
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(MethodReference targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++) {
      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
        // Alignment (round up to the nearest multiple of 8 bytes)
        addr = Address.fromIntZeroExtend((addr.toInt() + 7) & ~7);

        // NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
        // so we have to extract it as a double and convert it back to a float
        argObjectArray[i] = Reflection.wrapFloat((float) (Double.longBitsToDouble(addr.loadLong())));
        addr = addr.plus(BYTES_IN_DOUBLE);
      } else if (argTypes[i].isDoubleType()) {
        // Alignment (round up to the nearest multiple of 8 bytes)
        addr = Address.fromIntZeroExtend((addr.toInt() + 7) & ~7);

        argObjectArray[i] = Reflection.wrapDouble(Double.longBitsToDouble(addr.loadLong()));
        addr = addr.plus(BYTES_IN_DOUBLE);
      } else if (argTypes[i].isLongType()) {
        // Alignment (round up to the nearest multiple of 8 bytes)
        addr = Address.fromIntZeroExtend((addr.toInt() + 7) & ~7);

        argObjectArray[i] = Reflection.wrapLong(addr.loadLong());
        addr = addr.plus(BYTES_IN_LONG);
      } else if (argTypes[i].isBooleanType()) {
        argObjectArray[i] = Reflection.wrapBoolean((int) addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else if (argTypes[i].isByteType()) {
        argObjectArray[i] = Reflection.wrapByte((byte) addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else if (argTypes[i].isCharType()) {
        argObjectArray[i] = Reflection.wrapChar((char) addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else if (argTypes[i].isShortType()) {
        argObjectArray[i] = Reflection.wrapShort((short) addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef(addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = Reflection.wrapInt(addr.loadInt());
        addr = addr.plus(BYTES_IN_ADDRESS);
      } else {
        return null;
      }
    }
    return argObjectArray;
  }

}
