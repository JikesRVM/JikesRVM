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
package org.jikesrvm.runtime;

import static org.jikesrvm.Configuration.BuildForSSE2Full;
import static org.jikesrvm.VM.NOT_REACHED;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_FIRST_VIRTUAL_METHOD_INDEX;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.WordArray;

/**
 * Arch-independent portion of reflective method invoker.
 */
public class Reflection {

  /** Perform reflection using bytecodes (true) or out-of-line machine code (false) */
  public static boolean bytecodeReflection = false;
  /**
   * Cache the reflective method invoker in JavaLangReflect? If this is true and
   * bytecodeReflection is false, then bytecode reflection will only be used for
   * java.lang.reflect objects.
   */
  public static boolean cacheInvokerInJavaLangReflect = true;
  /*
   * Reflection uses an integer return from a function which logically
   * returns a triple.  The values are packed in the integer return value
   * by the following masks.
   */
  public static final int REFLECTION_GPRS_BITS = 5;
  public static final int REFLECTION_GPRS_MASK = (1 << REFLECTION_GPRS_BITS) - 1;
  public static final int REFLECTION_FPRS_BITS = 5;
  public static final int REFLECTION_FPRS_MASK = (1 << REFLECTION_FPRS_BITS) - 1;

  /**
   * Does the reflective method scheme need to check the arguments are valid?
   * Bytecode reflection doesn't need arguments checking as they are checking as
   * they are unwrapped
   *
   * @param invoker the invoker to use for the reflective invokation
   * @return whether the reflective method scheme needs to to be check
   *  the arguments for validity
   */
  @Inline
  public static boolean needsCheckArgs(ReflectionBase invoker) {
    // Only need to check the arguments when the user may be packaging them and
    // not using the bytecode based invoker (that checks them when they are unpacked)
    return !bytecodeReflection && !cacheInvokerInJavaLangReflect;
  }
  /**
   * Call a method.
   * @param method method to be called
   * @param invoker the invoker to use for the invocation, may be {@code null} in
   *  certain cases
   * @param thisArg "this" argument (ignored if method is static)
   * @param otherArgs remaining arguments
   * @param isNonvirtual flag is {@code false} if the method of the real class of this
   * object is to be invoked; {@code true} if a method of a superclass may be invoked
   * @return return value (wrapped if primitive)
   * See also: java/lang/reflect/Method.invoke()
   */
  @Inline
  public static Object invoke(RVMMethod method, ReflectionBase invoker,
                              Object thisArg, Object[] otherArgs,
                              boolean isNonvirtual) {
    // NB bytecode reflection doesn't care about isNonvirtual
    if (!bytecodeReflection && !cacheInvokerInJavaLangReflect) {
      return outOfLineInvoke(method, thisArg, otherArgs, isNonvirtual);
    } else if (!bytecodeReflection && cacheInvokerInJavaLangReflect) {
      if (invoker != null) {
        return invoker.invoke(method, thisArg, otherArgs);
      } else {
        return outOfLineInvoke(method, thisArg, otherArgs, isNonvirtual);
      }
    } else if (bytecodeReflection && !cacheInvokerInJavaLangReflect) {
      if (VM.VerifyAssertions) VM._assert(invoker == null);
      return method.getInvoker().invoke(method, thisArg, otherArgs);
    } else {
      // Even if we always generate an invoker this test is still necessary for
      // invokers that should have been created in the boot image
      if (invoker != null) {
        return invoker.invoke(method, thisArg, otherArgs);
      } else {
        return method.getInvoker().invoke(method, thisArg, otherArgs);
      }
    }
  }

  private static final WordArray emptyWordArray = WordArray.create(0);
  private static final double[] emptyDoubleArray = new double[0];
  private static final byte[] emptyByteArray = new byte[0];

  private static Object outOfLineInvoke(RVMMethod method, Object thisArg, Object[] otherArgs, boolean isNonvirtual) {

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
    TypeReference returnType = method.getReturnType();
    boolean returnIsPrimitive = returnType.isPrimitiveType();

    // decide how to pass parameters
    //
    int triple = 0;
    if (VM.BuildForIA32) {
      triple = org.jikesrvm.ia32.MachineReflection.countParameters(method);
    } else if (VM.BuildForPowerPC) {
      triple = org.jikesrvm.ppc.MachineReflection.countParameters(method);
    } else if (VM.BuildForARM) {
      triple = org.jikesrvm.arm.MachineReflection.countParameters(method);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      triple = 0;
    }
    int gprs = triple & REFLECTION_GPRS_MASK;
    WordArray GPRs = (gprs > 0) ? WordArray.create(gprs) : emptyWordArray;
    int fprs = (triple >> REFLECTION_GPRS_BITS) & 0x1F;
    double[] FPRs = (fprs > 0) ? new double[fprs] : emptyDoubleArray;
    byte[] FPRmeta;
    if (BuildForSSE2Full) {
      FPRmeta = (fprs > 0) ? new byte[fprs] : emptyByteArray;
    } else {
      FPRmeta = null;
    }

    int spillCount = triple >> (REFLECTION_GPRS_BITS + REFLECTION_FPRS_BITS);

    WordArray Spills = (spillCount > 0) ? WordArray.create(spillCount) : emptyWordArray;

    if (firstUse) {
      // force dynamic link sites in unwrappers to get resolved,
      // before disabling gc.
      // this is a bit silly, but I can't think of another way to do it [--DL]
      unwrapBoolean(wrapBoolean(0));
      unwrapByte(wrapByte((byte) 0));
      unwrapChar(wrapChar((char) 0));
      unwrapShort(wrapShort((short) 0));
      unwrapInt(wrapInt(0));
      unwrapLong(wrapLong(0));
      unwrapFloat(wrapFloat(0));
      unwrapDouble(wrapDouble(0));
      firstUse = false;
    }

    // choose actual method to be called
    //
    RVMMethod targetMethod;
    if (isNonvirtual || method.isStatic() || method.isObjectInitializer()) {
      targetMethod = method;
    } else {
      RVMClass C = Magic.getObjectType(thisArg).asClass();
      if (!method.getDeclaringClass().isInterface()) {
        int tibIndex = method.getOffset().toInt() >>> LOG_BYTES_IN_ADDRESS;
        targetMethod = C.getVirtualMethods()[tibIndex - TIB_FIRST_VIRTUAL_METHOD_INDEX];
      } else {
        RVMClass I = method.getDeclaringClass();
        if (!RuntimeEntrypoints.isAssignableWith(I, C))
          throw new IncompatibleClassChangeError();
        targetMethod = C.findVirtualMethod(method.getName(), method.getDescriptor());
        if (targetMethod == null)
          throw new IncompatibleClassChangeError();
      }
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
    CompiledMethod cm = targetMethod.getCurrentCompiledMethod();
    while (cm == null) {
      targetMethod.compile();
      cm = targetMethod.getCurrentCompiledMethod();
    }

    RVMThread.getCurrentThread().disableYieldpoints();

    CodeArray code = cm.getEntryCodeArray();
    if (VM.BuildForIA32) {
      org.jikesrvm.ia32.MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, FPRs, FPRmeta, Spills);
    } else if (VM.BuildForPowerPC) {
      org.jikesrvm.ppc.MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, FPRs, FPRmeta, Spills);
    } else if (VM.BuildForARM) {
      org.jikesrvm.arm.MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, FPRs, FPRmeta, Spills);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }

    // critical: no yieldpoints/GCpoints between here and the invoke of code!
    //           We may have references hidden in the GPRs and Spills arrays!!!
    RVMThread.getCurrentThread().enableYieldpoints();

    if (!returnIsPrimitive) {
      return Magic.invokeMethodReturningObject(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isVoidType()) {
      Magic.invokeMethodReturningVoid(code, GPRs, FPRs, FPRmeta, Spills);
      return null;
    }

    if (returnType.isBooleanType()) {
      int x = Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return x == 1;
    }

    if (returnType.isByteType()) {
      int x = Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (byte) x;
    }

    if (returnType.isShortType()) {
      int x = Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (short) x;
    }

    if (returnType.isCharType()) {
      int x = Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
      return (char) x;
    }

    if (returnType.isIntType()) {
      return Magic.invokeMethodReturningInt(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isLongType()) {
      return Magic.invokeMethodReturningLong(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isFloatType()) {
      return Magic.invokeMethodReturningFloat(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (returnType.isDoubleType()) {
      return Magic.invokeMethodReturningDouble(code, GPRs, FPRs, FPRmeta, Spills);
    }

    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  // Method parameter wrappers.
  //
  @NoInline
  public static Object wrapBoolean(int b) {
    return b == 1;
  }

  @NoInline
  public static Object wrapByte(byte b) {
    return b;
  }

  @NoInline
  public static Object wrapChar(char c) {
    return c;
  }

  @NoInline
  public static Object wrapShort(short s) {
    return s;
  }

  @NoInline
  public static Object wrapInt(int i) {
    return i;
  }

  @NoInline
  public static Object wrapLong(long l) {
    return l;
  }

  @NoInline
  public static Object wrapFloat(float f) {
    return f;
  }

  @NoInline
  public static Object wrapDouble(double d) {
    return d;
  }

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
  public static boolean unwrapBoolean(Object o) {
    return (Boolean) o;
  }

  @NoInline
  public static byte unwrapByte(Object o) {
    return (Byte) o;
  }

  @NoInline
  public static char unwrapChar(Object o) {
    return (Character) o;
  }

  @NoInline
  public static short unwrapShort(Object o) {
    return (Short) o;
  }

  @NoInline
  public static int unwrapInt(Object o) {
    return (Integer) o;
  }

  @NoInline
  public static long unwrapLong(Object o) {
    return (Long) o;
  }

  @NoInline
  public static float unwrapFloat(Object o) {
    return (Float) o;
  }

  @NoInline
  public static double unwrapDouble(Object o) {
    return (Double) o;
  }

  @NoInline
  public static Address unwrapObject(Object o) {
    return Magic.objectAsAddress(o);
  }

  private static boolean firstUse = true;
}
