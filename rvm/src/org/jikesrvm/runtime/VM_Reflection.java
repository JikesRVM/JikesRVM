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
package org.jikesrvm.runtime;

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.VM_MachineReflection;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.scheduler.VM_Processor;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.WordArray;

/**
 * Arch-independent portion of reflective method invoker.
 */
public class VM_Reflection implements VM_Constants {

  /**
   * Call a method.
   * @param method method to be called
   * @param thisArg "this" argument (ignored if method is static)
   * @param otherArgs remaining arguments
   *
   * isNonvirtual flag is false if the method of the real class of this
   * object is to be invoked; true if a method of a superclass may be invoked
   * @return return value (wrapped if primitive)
   * See also: java/lang/reflect/Method.invoke()
   */
  public static Object invoke(RVMMethod method, Object thisArg, Object[] otherArgs) {
    return invoke(method, thisArg, otherArgs, false);
  }

  public static Object invoke(RVMMethod method, Object thisArg, Object[] otherArgs, boolean isNonvirtual) {

    // the class must be initialized before we can invoke a method
    //
    RVMClass klass = method.getDeclaringClass();
    if (!klass.isInitialized()) {
      RuntimeEntrypoints.initializeClassForDynamicLink(klass);
    }

    // remember return type
    // Determine primitive type-ness early to avoid call (possible yield)
    // later while refs are possibly being held in int arrays.
    //
    VM_TypeReference returnType = method.getReturnType();
    boolean returnIsPrimitive = returnType.isPrimitiveType();

    // decide how to pass parameters
    //
    int triple = VM_MachineReflection.countParameters(method);
    int gprs = triple & REFLECTION_GPRS_MASK;
    WordArray GPRs = WordArray.create(gprs);
    int fprs = (triple >> REFLECTION_GPRS_BITS) & 0x1F;
    double[] FPRs = new double[fprs];
    byte[] FPRmeta = new byte[fprs];

    int spillCount = triple >> (REFLECTION_GPRS_BITS + REFLECTION_FPRS_BITS);

    WordArray Spills = WordArray.create(spillCount);

    if (firstUse) {
      // force dynamic link sites in unwrappers to get resolved,
      // before disabling gc.
      // this is a bit silly, but I can't think of another way to do it [--DL]
      unwrapBoolean(wrapBoolean(0));
      unwrapByte(wrapByte((byte) 0));
      unwrapChar(wrapChar((char) 0));
      unwrapShort(wrapShort((short) 0));
      unwrapInt(wrapInt(0));
      unwrapLong(wrapLong((long) 0));
      unwrapFloat(wrapFloat((float) 0));
      unwrapDouble(wrapDouble((double) 0));
      firstUse = false;
    }

    // choose actual method to be called
    //
    RVMMethod targetMethod;
    if (method.isStatic() || method.isObjectInitializer() || isNonvirtual) {
      targetMethod = method;
    } else {
      int tibIndex = method.getOffset().toInt() >>> LOG_BYTES_IN_ADDRESS;
      targetMethod =
          VM_Magic.getObjectType(thisArg).asClass().getVirtualMethods()[tibIndex - TIB_FIRST_VIRTUAL_METHOD_INDEX];
    }

    // getCurrentCompiledMethod is synchronized but Unpreemptible.
    // Therefore there are no possible yieldpoints from the time
    // the compiledMethod is loaded in getCurrentCompiledMethod
    // to when we disable GC below.
    // We can't allow any yieldpoints between these points because of the way in which
    // we GC compiled code.  Once a method is marked as obsolete, if it is not
    // executing on the stack of some thread, then the process of collecting the
    // code and meta-data might be initiated.
    targetMethod.compile();
    VM_CompiledMethod cm = targetMethod.getCurrentCompiledMethod();
    while (cm == null) {
      targetMethod.compile();
      cm = targetMethod.getCurrentCompiledMethod();
    }

    VM_Processor.getCurrentProcessor().disableThreadSwitching("Packaging parameters for reflection");

    CodeArray code = cm.getEntryCodeArray();
    VM_MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, FPRs, FPRmeta, Spills);

    // critical: no threadswitch/GCpoints between here and the invoke of code!
    //           We may have references hidden in the GPRs and Spills arrays!!!
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    if (!returnIsPrimitive) {
      return VM_Magic.invokeMethodReturningObject(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isVoidType()) {
      VM_Magic.invokeMethodReturningVoid(code, GPRs, FPRs, FPRmeta, Spills);
      return null;
    }

    if (returnType.isBooleanType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return x == 1;
    }

    if (returnType.isByteType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (byte) x;
    }

    if (returnType.isShortType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (short) x;
    }

    if (returnType.isCharType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (char) x;
    }

    if (returnType.isIntType()) {
      return VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isLongType()) {
      return VM_Magic.invokeMethodReturningLong(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isFloatType()) {
      return VM_Magic.invokeMethodReturningFloat(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isDoubleType()) {
      return VM_Magic.invokeMethodReturningDouble(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  // Method parameter wrappers.
  //
  @NoInline
  public static Object wrapBoolean(int b) { return b == 1; }

  @NoInline
  public static Object wrapByte(byte b) { return b; }

  @NoInline
  public static Object wrapChar(char c) { return c; }

  @NoInline
  public static Object wrapShort(short s) { return s; }

  @NoInline
  public static Object wrapInt(int i) { return i; }

  @NoInline
  public static Object wrapLong(long l) { return l; }

  @NoInline
  public static Object wrapFloat(float f) { return f; }

  @NoInline
  public static Object wrapDouble(double d) { return d; }

  // Method parameter unwrappers.
  //
  @NoInline
  public static int unwrapBooleanAsInt(Object o) {
    if (unwrapBoolean(o)) {
      return 1;
    } else {
      return 0;
    }
  }

  @NoInline
  public static boolean unwrapBoolean(Object o) { return (Boolean) o; }

  @NoInline
  public static byte unwrapByte(Object o) { return (Byte) o; }

  @NoInline
  public static char unwrapChar(Object o) { return (Character) o; }

  @NoInline
  public static short unwrapShort(Object o) { return (Short) o; }

  @NoInline
  public static int unwrapInt(Object o) { return (Integer) o; }

  @NoInline
  public static long unwrapLong(Object o) { return (Long) o; }

  @NoInline
  public static float unwrapFloat(Object o) { return (Float) o; }

  @NoInline
  public static double unwrapDouble(Object o) { return (Double) o; }

  @NoInline
  public static Address unwrapObject(Object o) { return VM_Magic.objectAsAddress(o); }

  private static boolean firstUse = true;
}
