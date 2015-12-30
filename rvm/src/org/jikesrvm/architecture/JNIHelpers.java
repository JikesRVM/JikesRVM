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
package org.jikesrvm.architecture;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.unboxed.Address;

/**
 * Dispatches calls to JNIHelpers to the appropriate architecture-specific class.
 * <p>
 * This is necessary until the PowerPC JNI design is changed to match the design
 * for IA32 JNI (i.e. until PowerPC uses C code to do var args passing on both sides).
 * When PPC uses C var arg passing, the code can be refactored and moved from the JNI
 * helpers into a single, architecture-independent class.
 */
public final class JNIHelpers {

  public static Object invokeInitializer(Class<?> cls, int methodID, Address argAddress, boolean isJvalue,
                                         boolean isDotDotStyle) throws Exception {
    if (VM.BuildForIA32) {
      return org.jikesrvm.jni.ia32.JNIHelpers.invokeInitializer(cls, methodID, argAddress, isJvalue, isDotDotStyle);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNIHelpers.invokeInitializer(cls, methodID, argAddress, isJvalue, isDotDotStyle);
    }
  }

  // don't allow inlining because PPC JNI code expects a certain stack frame layout
  @NoInline
  @NoOptCompile
  public static Object invokeWithDotDotVarArg(int methodID, TypeReference expectReturnType) throws Exception {
    if (VM.BuildForIA32) {
      // All architectures other than PPC should invoke the C functions
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return org.jikesrvm.jni.ia32.JNIHelpers.invokeWithDotDotVarArg(methodID, expectReturnType);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNIHelpers.invokeWithDotDotVarArg(methodID, expectReturnType);
    }
  }

  // don't allow inlining because PPC JNI code expects a certain stack frame layout
  @NoInline
  @NoOptCompile
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, TypeReference expectReturnType,
                                              boolean skip4Args) throws Exception {
    if (VM.BuildForIA32) {
      // All architectures other than PPC should invoke the C functions
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return org.jikesrvm.jni.ia32.JNIHelpers.invokeWithDotDotVarArg(obj, methodID, expectReturnType, skip4Args);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNIHelpers.invokeWithDotDotVarArg(obj, methodID, expectReturnType, skip4Args);
    }
  }

  public static Object invokeWithVarArg(int methodID, Address argAddress, TypeReference expectReturnType)
      throws Exception {
    if (VM.BuildForIA32) {
      return org.jikesrvm.jni.ia32.JNIHelpers.invokeWithVarArg(methodID, argAddress, expectReturnType);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNIHelpers.invokeWithVarArg(methodID, argAddress, expectReturnType);
    }
  }

  public static Object invokeWithVarArg(Object obj, int methodID, Address argAddress, TypeReference expectReturnType,
                                        boolean skip4Args) throws Exception {
    if (VM.BuildForIA32) {
      return org.jikesrvm.jni.ia32.JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, expectReturnType, skip4Args);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, expectReturnType, skip4Args);
    }
  }

}
