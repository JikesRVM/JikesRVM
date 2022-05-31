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

import static org.jikesrvm.runtime.EntrypointHelper.getField;
import static org.jikesrvm.runtime.EntrypointHelper.getMethod;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;

/**
 * Fields and methods of the virtual machine that are needed by
 * compiler-generated machine code or C runtime code.
 */
public class Entrypoints {
  // The usual causes for getField/Method() to fail are:
  //  1. you misspelled the class name, member name, or member signature
  //  2. the class containing the specified member didn't get compiled
  //

  public static final NormalMethod bootMethod = EntrypointHelper.getMethod(org.jikesrvm.VM.class, "boot", "()V");

  public static final RVMMethod java_lang_reflect_Method_invokeMethod =
      getMethod(java.lang.reflect.Method.class, "invoke",
          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
  public static final RVMMethod java_lang_reflect_Constructor_newInstance =
      getMethod(java.lang.reflect.Constructor.class, "newInstance",
          "([Ljava/lang/Object;)Ljava/lang/Object;");

  // This is only necessary to work around OpenJDKs work around, see sun_reflect_Reflection
  // in the libraryInterface for details.
  public static final RVMMethod java_lang_reflect_Method_getCallerClass;
  // Necessary to wipe out cached fields (TODO OPENJDK/ICEDTEA consider doing this in the BootImageWriter)
  public static final RVMField usr_paths_Field;
  public static final RVMField sys_paths_Field;
  // Necessary to set application classloader for OpenJDK
  public static final RVMField scl_Field;
  public static final RVMField sclSet_Field;
  // Necessary for getInheritedAccessControlContext
  public static final RVMField inheritedAccessControlContext_Field;

  public static final RVMMethod getClassFromStackFrame =
    getMethod(org.jikesrvm.classloader.RVMClass.class, "getClassFromStackFrame", "(I)Lorg/jikesrvm/classloader/RVMClass;");
  public static final RVMMethod getClassLoaderFromStackFrame =
    getMethod(org.jikesrvm.classloader.RVMClass.class, "getClassLoaderFromStackFrame", "(I)Ljava/lang/ClassLoader;");

  public static final RVMField magicObjectRemapperField =
      getField(org.jikesrvm.runtime.Magic.class,
               "objectAddressRemapper",
               org.jikesrvm.runtime.ObjectAddressRemapper.class);

  public static final NormalMethod ldivMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "ldiv", "(JJ)J");
  public static final NormalMethod lremMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "lrem", "(JJ)J");

  public static final NormalMethod instanceOfMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final NormalMethod checkcastMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "checkcast", "(Ljava/lang/Object;I)V");
  public static final NormalMethod checkstoreMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final NormalMethod aastoreMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "aastore", "([Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final NormalMethod aastoreUninterruptibleMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "aastoreUninterruptible", "([Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final NormalMethod athrowMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final NormalMethod resolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class,
                "resolvedNewScalar",
                "(ILorg/jikesrvm/objectmodel/TIB;ZIIII)Ljava/lang/Object;");
  public static final NormalMethod unresolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final NormalMethod unresolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final NormalMethod resolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class,
                "resolvedNewArray",
                "(IIILorg/jikesrvm/objectmodel/TIB;IIII)Ljava/lang/Object;");

  public static final RVMField sysWriteLockField = getField(org.jikesrvm.VM.class, "sysWriteLock", int.class);
  public static final RVMField intBufferLockField =
      getField(org.jikesrvm.util.Services.class, "intBufferLock", int.class);
  public static final RVMField dumpBufferLockField =
      getField(org.jikesrvm.util.Services.class, "dumpBufferLock", int.class);

  public static final NormalMethod unexpectedAbstractMethodCallMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unexpectedAbstractMethodCall", "()V");
  public static final NormalMethod deliverHardwareExceptionMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "deliverHardwareException", "(ILorg/vmmagic/unboxed/Word;)V");
  public static final NormalMethod unlockAndThrowMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  // Special methods for raising exceptions. Note that these methods need special
  // treatment to remove them from stack traces. Removing them is necessary to get
  // stack traces that are consistent across all of our architectures and compilers.
  public static final NormalMethod raiseNullPointerException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseNullPointerException", "()V");
  public static final NormalMethod raiseArrayBoundsException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final NormalMethod raiseArithmeticException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseArithmeticException", "()V");
  public static final NormalMethod raiseAbstractMethodError =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseAbstractMethodError", "()V");
  public static final NormalMethod raiseIllegalAccessError =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseIllegalAccessError", "()V");
  // Hard failures for execution of instructions that should never be reachable. This is used to rewrite instructions that are in
  // the boot image but unused on some architectures (e.g. Magic.unsignedDivide on PPC).
  public static final NormalMethod unimplementedButUnreachable =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unimplementedButUnreachable", "()V");

  public static final RVMField gcLockField = getField("Lorg/jikesrvm/classlibrary/JavaLangSupport$GCLock;", "gcLock", int.class);

  public static final NormalMethod invokeInterfaceMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/compilers/common/CodeArray;");
  public static final NormalMethod findItableMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "findITable",
                "(Lorg/jikesrvm/objectmodel/TIB;I)Lorg/jikesrvm/objectmodel/ITable;");
  public static final NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "unresolvedInvokeinterfaceImplementsTest",
                "(ILjava/lang/Object;)V");

  public static final NormalMethod lockMethod =
      getMethod(org.jikesrvm.objectmodel.ObjectModel.class, "genericLock", "(Ljava/lang/Object;)V");
  public static final NormalMethod unlockMethod =
      getMethod(org.jikesrvm.objectmodel.ObjectModel.class, "genericUnlock", "(Ljava/lang/Object;)V");

  public static final NormalMethod inlineLockMethod =
      getMethod(org.jikesrvm.scheduler.ThinLock.class,
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final NormalMethod inlineUnlockMethod =
      getMethod(org.jikesrvm.scheduler.ThinLock.class,
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final NormalMethod lazyMethodInvokerMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "lazyMethodInvoker", "()V");
  public static final NormalMethod unimplementedNativeMethodMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "unimplementedNativeMethod", "()V");
  public static final NormalMethod sysCallMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "sysCallMethod", "()V");

  public static final NormalMethod resolveMemberMethod =
      getMethod(org.jikesrvm.classloader.TableBasedDynamicLinker.class, "resolveMember", "(I)I");
  public static final RVMField memberOffsetsField =
      getField(org.jikesrvm.classloader.TableBasedDynamicLinker.class, "memberOffsets", int[].class);

  /** 1L */
  public static final RVMField longOneField = getField(org.jikesrvm.runtime.MathConstants.class, "longOne", long.class);
  /** -1.0F */
  public static final RVMField minusOneField = getField(org.jikesrvm.runtime.MathConstants.class, "minusOne", float.class);
  /** 0.0F */
  public static final RVMField zeroFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "zero", float.class);
  /**0.5F */
  public static final RVMField halfFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "half", float.class);
  /** 1.0F */
  public static final RVMField oneFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "one", float.class);
  /** 2.0F */
  public static final RVMField twoFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "two", float.class);
  /** 2.0F^32 */
  public static final RVMField two32Field = getField(org.jikesrvm.runtime.MathConstants.class, "two32", float.class);
  /** 0.5F^32 */
  public static final RVMField half32Field = getField(org.jikesrvm.runtime.MathConstants.class, "half32", float.class);
  /** 1e-9 */
  public static final RVMField billionthField = getField(org.jikesrvm.runtime.MathConstants.class, "billionth", double.class);
  /** 0.0 */
  public static final RVMField zeroDoubleField = getField(org.jikesrvm.runtime.MathConstants.class, "zeroD", double.class);
  /** 1.0 */
  public static final RVMField oneDoubleField = getField(org.jikesrvm.runtime.MathConstants.class, "oneD", double.class);
  /** largest double that can be rounded to an int */
  public static final RVMField maxintField =
      getField(org.jikesrvm.runtime.MathConstants.class, "maxint", double.class);
  /** largest double that can be rounded to a long */
  public static final RVMField maxlongField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxlong", double.class);
  /** smallest double that can be rounded to an int */
  public static final RVMField minintField =
      getField(org.jikesrvm.runtime.MathConstants.class, "minint", double.class);
  /** largest float that can be rounded to an int */
  public static final RVMField maxintFloatField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxintF", float.class);
  /** largest float that can be rounded to a long */
  public static final RVMField maxlongFloatField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxlongF", float.class);
  /** IEEEmagic constant */
  public static final RVMField IEEEmagicField =
      getField(org.jikesrvm.runtime.MathConstants.class, "IEEEmagic", double.class);
  /** special double value for use in int &lt;--&gt; double conversions */
  public static final RVMField I2DconstantField =
      getField(org.jikesrvm.runtime.MathConstants.class,
               "I2Dconstant",
               double.class);

  public static final RVMField bootThreadField =
    getField(org.jikesrvm.scheduler.RVMThread.class, "bootThread",
             org.jikesrvm.scheduler.RVMThread.class);

  public static final RVMField scratchStorageField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "scratchStorage", double.class);
  public static final RVMField takeYieldpointField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "takeYieldpoint", int.class);

  public static final RVMField execStatusField = getField(org.jikesrvm.scheduler.RVMThread.class, "execStatus", int.class);

  public static final RVMField referenceReferentField =
      getField(java.lang.ref.Reference.class, "_referent", org.vmmagic.unboxed.Address.class);

  /** Used in deciding which stack frames we can elide when printing. */
  public static final NormalMethod mainThreadRunMethod =
      getMethod(org.jikesrvm.scheduler.MainThread.class, "run", "()V");

  public static final NormalMethod yieldpointFromPrologueMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromPrologue", "()V");
  public static final NormalMethod yieldpointFromBackedgeMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromBackedge", "()V");
  public static final NormalMethod yieldpointFromEpilogueMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromEpilogue", "()V");
  public static final NormalMethod enterJNIBlockedFromJNIFunctionCallMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "enterJNIBlockedFromJNIFunctionCall", "()V");
  public static final NormalMethod enterJNIBlockedFromCallIntoNativeMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "enterJNIBlockedFromCallIntoNative", "()V");
  public static final NormalMethod leaveJNIBlockedFromJNIFunctionCallMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "leaveJNIBlockedFromJNIFunctionCall", "()V");
  public static final NormalMethod leaveJNIBlockedFromCallIntoNativeMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "leaveJNIBlockedFromCallIntoNative", "()V");

  public static final NormalMethod threadRunMethod = getMethod(org.jikesrvm.scheduler.RVMThread.class, "run", "()V");
  public static final NormalMethod threadStartoffMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "startoff", "()V");
  public static final RVMField threadStackField = getField(org.jikesrvm.scheduler.RVMThread.class, "stack", byte[].class);
  public static final RVMField stackLimitField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "stackLimit", org.vmmagic.unboxed.Address.class);

  public static final RVMField threadSlotField = getField(org.jikesrvm.scheduler.RVMThread.class, "threadSlot", int.class);
  public static final RVMField jniEnvField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "jniEnv", org.jikesrvm.jni.JNIEnvironment.class);
  public static final RVMField threadContextRegistersField =
      getField(org.jikesrvm.scheduler.RVMThread.class,
               "contextRegisters",
               org.jikesrvm.architecture.AbstractRegisters.class);
  public static final RVMField threadContextRegistersSaveField =
      getField(org.jikesrvm.scheduler.RVMThread.class,
               "contextRegistersSave",
               org.jikesrvm.architecture.AbstractRegisters.class);
  public static final RVMField threadExceptionRegistersField =
      getField(org.jikesrvm.scheduler.RVMThread.class,
               "exceptionRegisters",
               org.jikesrvm.architecture.AbstractRegisters.class);
  public static final NormalMethod returnBarrierMethod = getMethod(org.jikesrvm.scheduler.RVMThread.class, "returnBarrier", "()V");

  public static final RVMField tracePrevAddressField =
      getField(org.jikesrvm.objectmodel.MiscHeader.class, "prevAddress", org.vmmagic.unboxed.Word.class);
  public static final RVMField traceOIDField =
      getField(org.jikesrvm.objectmodel.MiscHeader.class, "oid", org.vmmagic.unboxed.Word.class);
  /*
  public static final RVMField dispenserField = getField(org.jikesrvm.mm.mmtk.Lock.class, "dispenser", int.class);
  public static final RVMField servingField = getField(org.jikesrvm.mm.mmtk.Lock.class, "serving", int.class);
  public static final RVMField lockThreadField =
      getField(org.jikesrvm.mm.mmtk.Lock.class, "thread", org.jikesrvm.scheduler.RVMThread.class);
  */
  public static final RVMField lockStateField = getField(org.jikesrvm.mm.mmtk.Lock.class, "state", int.class);
  public static final RVMField SQCFField = getField(org.mmtk.utility.deque.SharedDeque.class, "completionFlag", int.class);
  public static final RVMField SQNCField = getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumers", int.class);
  public static final RVMField SQNCWField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumersWaiting", int.class);
  public static final RVMField SQheadField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "head", org.vmmagic.unboxed.Address.class);
  public static final RVMField SQtailField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "tail", org.vmmagic.unboxed.Address.class);
  public static final RVMField SQBEField = getField(org.mmtk.utility.deque.SharedDeque.class, "bufsenqueued", int.class);
  public static final RVMField synchronizedCounterField =
      getField(org.jikesrvm.mm.mmtk.SynchronizedCounter.class, "count", int.class);

  public static final NormalMethod booleanFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "booleanFieldWrite", "(Ljava/lang/Object;ZLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod booleanArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "booleanArrayWrite", "([ZIZ)V");
  public static final NormalMethod booleanFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "booleanFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Z");
  public static final NormalMethod booleanArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "booleanArrayRead", "([ZI)Z");

  public static final NormalMethod byteFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "byteFieldWrite", "(Ljava/lang/Object;BLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod byteArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "byteArrayWrite", "([BIB)V");
  public static final NormalMethod byteFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "byteFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)B");
  public static final NormalMethod byteArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "byteArrayRead", "([BI)B");

  public static final NormalMethod charFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "charFieldWrite", "(Ljava/lang/Object;CLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod charArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "charArrayWrite", "([CIC)V");
  public static final NormalMethod charFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "charFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)C");
  public static final NormalMethod charArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "charArrayRead", "([CI)C");

  public static final NormalMethod shortFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "shortFieldWrite", "(Ljava/lang/Object;SLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod shortArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "shortArrayWrite", "([SIS)V");
  public static final NormalMethod shortFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "shortFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)S");
  public static final NormalMethod shortArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "shortArrayRead", "([SI)S");

  public static final NormalMethod intFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "intFieldWrite", "(Ljava/lang/Object;ILorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod intArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "intArrayWrite", "([III)V");
  public static final NormalMethod intFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "intFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)I");
  public static final NormalMethod intArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "intArrayRead", "([II)I");

  public static final NormalMethod longFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "longFieldWrite", "(Ljava/lang/Object;JLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod longArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "longArrayWrite", "([JIJ)V");
  public static final NormalMethod longFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "longFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)J");
  public static final NormalMethod longArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "longArrayRead", "([JI)J");

  public static final NormalMethod floatFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "floatFieldWrite", "(Ljava/lang/Object;FLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod floatArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "floatArrayWrite", "([FIF)V");
  public static final NormalMethod floatFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "floatFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)F");
  public static final NormalMethod floatArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "floatArrayRead", "([FI)F");

  public static final NormalMethod doubleFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "doubleFieldWrite", "(Ljava/lang/Object;DLorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod doubleArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "doubleArrayWrite", "([DID)V");
  public static final NormalMethod doubleFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "doubleFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)D");
  public static final NormalMethod doubleArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "doubleArrayRead", "([DI)D");

  public static final NormalMethod objectFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectFieldWrite", "(Ljava/lang/Object;Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod objectArrayWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectArrayWrite", "([Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final NormalMethod objectFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");
  public static final NormalMethod objectArrayReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectArrayRead", "([Ljava/lang/Object;I)Ljava/lang/Object;");

  public static final NormalMethod wordFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "wordFieldWrite", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Word;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod wordFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "wordFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Lorg/vmmagic/unboxed/Word;");

  public static final NormalMethod addressFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "addressFieldWrite", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Address;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod addressFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "addressFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Lorg/vmmagic/unboxed/Address;");

  public static final NormalMethod offsetFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "offsetFieldWrite", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod offsetFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "offsetFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Lorg/vmmagic/unboxed/Offset;");

  public static final NormalMethod extentFieldWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "extentFieldWrite", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Extent;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod extentFieldReadBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "extentFieldRead", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Lorg/vmmagic/unboxed/Extent;");

  public static final NormalMethod objectStaticWriteBarrierMethod =
    getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectStaticWrite", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)V");
  public static final NormalMethod objectStaticReadBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.Barriers.class, "objectStaticRead", "(Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");

  public static final NormalMethod modifyCheckMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "modifyCheck", "(Ljava/lang/Object;)V");

  // used in boot image writer
  public static final RVMField debugRequestedField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "debugRequested", boolean.class);
  public static final NormalMethod dumpStackAndDieMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final RVMField latestContenderField =
      getField(org.jikesrvm.scheduler.SpinLock.class, "latestContender", org.jikesrvm.scheduler.RVMThread.class);

  public static final RVMField depthField = getField(org.jikesrvm.classloader.RVMType.class, "depth", int.class);
  public static final RVMField idField = getField(org.jikesrvm.classloader.RVMType.class, "id", int.class);
  public static final RVMField dimensionField = getField(org.jikesrvm.classloader.RVMType.class, "dimension", int.class);

  public static final RVMField innermostElementTypeDimensionField =
      getField(org.jikesrvm.classloader.RVMArray.class, "innermostElementTypeDimension", int.class);

  public static final RVMField JNIEnvSavedTRField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "savedTRreg", org.jikesrvm.scheduler.RVMThread.class);
  public static final RVMField JNITopJavaFPField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "JNITopJavaFP", org.vmmagic.unboxed.Address.class);
  public static final RVMField JNIExternalFunctionsField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "externalJNIFunctions", org.vmmagic.unboxed.Address.class);
  public static final RVMField JNIEnvSavedJTOCField;

  public static final RVMField JNIEnvBasePointerOnEntryToNative;
  public static final RVMField JNIGlobalRefsField;
  public static final RVMField JNIRefsField;
  public static final RVMField JNIRefsTopField;
  public static final RVMField JNIRefsSavedFPField;
  public static final RVMField JNIHasPendingExceptionField;
  public static final RVMMethod jniThrowPendingException;
  public static final RVMMethod jniEntry;
  public static final RVMMethod jniExit;

  // Deal with currently differing JNI implementations for PowerPC and IA32

  static {
    if (VM.BuildForPowerPC) {
      JNIEnvBasePointerOnEntryToNative = null;
      JNIGlobalRefsField = getField(org.jikesrvm.jni.JNIGlobalRefTable.class,
                                              "JNIGlobalRefs",
                                              org.vmmagic.unboxed.AddressArray.class);
      JNIRefsField =  getField(org.jikesrvm.jni.JNIEnvironment.class,
                                              "JNIRefs",
                                              org.vmmagic.unboxed.AddressArray.class);
      JNIRefsTopField = getField(org.jikesrvm.jni.JNIEnvironment.class,
                                              "JNIRefsTop",
                                              int.class);
      JNIRefsSavedFPField = getField(org.jikesrvm.jni.JNIEnvironment.class,
                                              "JNIRefsSavedFP",
                                              int.class);
      JNIHasPendingExceptionField = getField(org.jikesrvm.jni.JNIEnvironment.class,
                                              "hasPendingException",
                                              int.class);
      JNIEnvSavedJTOCField =  getField(org.jikesrvm.jni.JNIEnvironment.class,
                                              "savedJTOC",
                                              org.vmmagic.unboxed.Address.class);
      jniThrowPendingException = getMethod(org.jikesrvm.jni.JNIEnvironment.class,
                                              "throwPendingException",
                                              "()V");
      jniEntry = null;
      jniExit = null;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForIA32);
      JNIEnvBasePointerOnEntryToNative = getField(org.jikesrvm.jni.JNIEnvironment.class,
          "basePointerOnEntryToNative",
           org.vmmagic.unboxed.Address.class);
      JNIGlobalRefsField = null;
      JNIRefsField = null;
      JNIRefsTopField = null;
      JNIRefsSavedFPField = null;
      JNIHasPendingExceptionField = null;
      JNIEnvSavedJTOCField = null;
      jniThrowPendingException = null;
      jniEntry = getMethod(org.jikesrvm.jni.JNIEnvironment.class,
                                            "entryToJNI",
                                            "(I)V");
      jniExit = getMethod(org.jikesrvm.jni.JNIEnvironment.class,
                                            "exitFromJNI",
                                            "(I)Ljava/lang/Object;");
    }

    if (VM.BuildForOpenJDK) {
      java_lang_reflect_Method_getCallerClass = getMethod(java.lang.reflect.Method.class, "getCallerClass",
          "()Ljava/lang/Class;");
      usr_paths_Field = getField(java.lang.ClassLoader.class, "usr_paths", String[].class);
      sys_paths_Field = getField(java.lang.ClassLoader.class, "sys_paths", String[].class);
      scl_Field = getField(java.lang.ClassLoader.class, "scl", ClassLoader.class);
      sclSet_Field = getField(java.lang.ClassLoader.class, "sclSet", boolean.class);
      inheritedAccessControlContext_Field = getField(java.lang.Thread.class, "inheritedAccessControlContext", java.security.AccessControlContext.class);
    } else {
      java_lang_reflect_Method_getCallerClass = null;
      usr_paths_Field = null;
      sys_paths_Field = null;
      scl_Field = null;
      sclSet_Field = null;
      inheritedAccessControlContext_Field = null;
    }
  }

  public static final RVMField the_boot_recordField =
      getField(org.jikesrvm.runtime.BootRecord.class, "the_boot_record", org.jikesrvm.runtime.BootRecord.class);
  public static final RVMField externalSignalFlagField =
      getField(org.jikesrvm.runtime.BootRecord.class, "externalSignalFlag", int.class);
  public static final RVMField sysLongDivideIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongDivideIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongRemainderIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongRemainderIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongToFloatIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongToFloatIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongToDoubleIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongToDoubleIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysFloatToIntIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysFloatToIntIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleToIntIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleToIntIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysFloatToLongIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysFloatToLongIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleToLongIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleToLongIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleRemainderIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleRemainderIP", org.vmmagic.unboxed.Address.class);

  public static final RVMField edgeCountersField =
      getField(org.jikesrvm.compilers.baseline.EdgeCounts.class, "data", int[][].class);

  public static final RVMField classLoadedCountField =
      getField(org.jikesrvm.classloader.JMXSupport.class, "classLoadedCount", int.class);

  //////////////////
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  public static final RVMField specializedMethodsField;

  public static final NormalMethod optThreadSwitchFromOsrOptMethod;
  public static final NormalMethod optThreadSwitchFromPrologueMethod;
  public static final NormalMethod optThreadSwitchFromBackedgeMethod;
  public static final NormalMethod optThreadSwitchFromEpilogueMethod;
  public static final NormalMethod optResolveMethod;
  public static final NormalMethod optNewArrayArrayMethod;
  public static final NormalMethod optNew2DArrayMethod;
  public static final NormalMethod sysArrayCopy;

  // Initialize opt-compiler specific fields
  static {
    if (VM.BuildForOptCompiler) {
      specializedMethodsField =
          getField(org.jikesrvm.compilers.opt.specialization.SpecializedMethodPool.class,
                   "specializedMethods",
                   org.jikesrvm.compilers.common.CodeArray[].class);
      optThreadSwitchFromOsrOptMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromEpilogue", "()V");
      optResolveMethod = getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "resolve", "()V");

      optNewArrayArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptLinker.class, "newArrayArray", "(I[II)Ljava/lang/Object;");
      optNew2DArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptLinker.class, "new2DArray", "(IIII)Ljava/lang/Object;");
      sysArrayCopy = getMethod("Lorg/jikesrvm/classlibrary/JavaLangSupport;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
      sysArrayCopy.setRuntimeServiceMethod(false);
    } else {
      specializedMethodsField = null;
      optThreadSwitchFromOsrOptMethod = null;
      optThreadSwitchFromPrologueMethod = null;
      optThreadSwitchFromBackedgeMethod = null;
      optThreadSwitchFromEpilogueMethod = null;
      optResolveMethod = null;
      optNewArrayArrayMethod = null;
      optNew2DArrayMethod = null;
      sysArrayCopy = null;
    }
  }

  public static final RVMField classLoaderDefinedPackages;

  static {
    // Initialize fields specific to the class library
    if (VM.BuildForGnuClasspath) {
      classLoaderDefinedPackages = getField(java.lang.ClassLoader.class, "definedPackages", java.util.HashMap.class);
    } else {
      classLoaderDefinedPackages = null;
    }
  }

  /**
   * Is this a special exception-raising method that must be invisible in stack traces?
   * <p>
   * In some configurations, the optimizing compiler may insert calls to special exception
   * raising methods in RuntimeEntrypoints if it recognizes that code throws unconditional
   * exceptions. Those calls must not appear in the stack trace.
   *
   * @param m a method
   * @return {@code true} when the given me is a special exception-raising method that
   *  must be invisible in stack traces, {@code false} otherwise
   */
  public static final boolean isInvisibleRaiseMethod(RVMMethod m) {
    if (!m.isRuntimeServiceMethod()) {
      return false;
    }
    NormalMethod nm = (NormalMethod) m;
    return nm == raiseAbstractMethodError || nm == raiseArithmeticException ||
        nm == raiseArrayBoundsException || nm == raiseNullPointerException ||
        nm == raiseIllegalAccessError;
  }

}
