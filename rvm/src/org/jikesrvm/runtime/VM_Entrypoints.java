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

import static org.jikesrvm.runtime.VM_EntrypointHelper.getField;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getMethod;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;

/**
 * Fields and methods of the virtual machine that are needed by
 * compiler-generated machine code or C runtime code.
 */
public class VM_Entrypoints {
  // The usual causes for getField/Method() to fail are:
  //  1. you mispelled the class name, member name, or member signature
  //  2. the class containing the specified member didn't get compiled
  //

  public static final VM_NormalMethod bootMethod = VM_EntrypointHelper.getMethod(org.jikesrvm.VM.class, "boot", "()V");

  public static final VM_Method java_lang_Class_forName =
    getMethod(java.lang.Class.class, "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
  public static final VM_Method java_lang_Class_forName_withLoader =
    getMethod(java.lang.Class.class, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
  public static final VM_Method java_lang_reflect_Method_invokeMethod =
      getMethod(java.lang.reflect.Method.class, "invoke",
          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  public static final VM_Field magicObjectRemapperField =
      getField(org.jikesrvm.runtime.VM_Magic.class,
               "objectAddressRemapper",
               org.jikesrvm.runtime.VM_ObjectAddressRemapper.class);

  public static final VM_NormalMethod instanceOfMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfResolvedClassMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfFinalMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "instanceOfFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)Z");
  public static final VM_NormalMethod checkcastMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastResolvedClassMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastFinalMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "checkcastFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod checkstoreMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_NormalMethod athrowMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final VM_NormalMethod resolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "resolvedNewScalar",
                "(ILorg/jikesrvm/objectmodel/VM_TIB;ZIIII)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final VM_NormalMethod resolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "resolvedNewArray",
                "(IIILorg/jikesrvm/objectmodel/VM_TIB;IIII)Ljava/lang/Object;");
  public static final VM_Field gcLockField = getField(java.lang.VMRuntime.class, "gcLock", int.class);

  public static final VM_Field sysWriteLockField = getField(org.jikesrvm.VM.class, "sysWriteLock", int.class);
  public static final VM_Field intBufferLockField =
      getField(org.jikesrvm.VM_Services.class, "intBufferLock", int.class);
  public static final VM_Field dumpBufferLockField =
      getField(org.jikesrvm.VM_Services.class, "dumpBufferLock", int.class);

  public static final VM_NormalMethod unexpectedAbstractMethodCallMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unexpectedAbstractMethodCall", "()V");
  public static final VM_NormalMethod raiseNullPointerException =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseNullPointerException", "()V");
  public static final VM_NormalMethod raiseArrayBoundsException =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_NormalMethod raiseArithmeticException =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseArithmeticException", "()V");
  public static final VM_NormalMethod raiseAbstractMethodError =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseAbstractMethodError", "()V");
  public static final VM_NormalMethod raiseIllegalAccessError =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseIllegalAccessError", "()V");
  public static final VM_NormalMethod deliverHardwareExceptionMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "deliverHardwareException", "(II)V");
  public static final VM_NormalMethod unlockAndThrowMethod =
      getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_NormalMethod invokeInterfaceMethod =
      getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/ArchitectureSpecific$VM_CodeArray;");
  public static final VM_NormalMethod findItableMethod =
      getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "findITable",
                "(Lorg/jikesrvm/objectmodel/VM_TIB;I)Lorg/jikesrvm/objectmodel/VM_ITable;");
  public static final VM_NormalMethod invokeinterfaceImplementsTestMethod =
      getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "invokeinterfaceImplementsTest",
                "(Lorg/jikesrvm/classloader/VM_Class;Lorg/jikesrvm/objectmodel/VM_TIB;)V");
  public static final VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "unresolvedInvokeinterfaceImplementsTest",
                "(ILorg/jikesrvm/objectmodel/VM_TIB;)V");

  public static final VM_NormalMethod lockMethod =
      getMethod(org.jikesrvm.objectmodel.VM_ObjectModel.class, "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_NormalMethod unlockMethod =
      getMethod(org.jikesrvm.objectmodel.VM_ObjectModel.class, "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_NormalMethod inlineLockMethod =
      getMethod(org.jikesrvm.scheduler.VM_ThinLock.class,
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod inlineUnlockMethod =
      getMethod(org.jikesrvm.scheduler.VM_ThinLock.class,
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final VM_NormalMethod lazyMethodInvokerMethod =
      getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "lazyMethodInvoker", "()V");
  public static final VM_NormalMethod unimplementedNativeMethodMethod =
      getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "unimplementedNativeMethod", "()V");
  public static final VM_NormalMethod sysCallMethod =
      getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "sysCallMethod", "()V");

  public static final VM_NormalMethod resolveMemberMethod =
      getMethod(org.jikesrvm.classloader.VM_TableBasedDynamicLinker.class, "resolveMember", "(I)I");
  public static final VM_Field memberOffsetsField =
      getField(org.jikesrvm.classloader.VM_TableBasedDynamicLinker.class, "memberOffsets", int[].class);

  /** 1L */
  public static final VM_Field longOneField = getField(org.jikesrvm.runtime.VM_Math.class, "longOne", long.class);
  /** -1.0F */
  public static final VM_Field minusOneField = getField(org.jikesrvm.runtime.VM_Math.class, "minusOne", float.class);
  /** 0.0F */
  public static final VM_Field zeroFloatField = getField(org.jikesrvm.runtime.VM_Math.class, "zero", float.class);
  /**0.5F */
  public static final VM_Field halfFloatField = getField(org.jikesrvm.runtime.VM_Math.class, "half", float.class);
  /** 1.0F */
  public static final VM_Field oneFloatField = getField(org.jikesrvm.runtime.VM_Math.class, "one", float.class);
  /** 2.0F */
  public static final VM_Field twoFloatField = getField(org.jikesrvm.runtime.VM_Math.class, "two", float.class);
  /** 2.0F^32 */
  public static final VM_Field two32Field = getField(org.jikesrvm.runtime.VM_Math.class, "two32", float.class);
  /** 0.5F^32 */
  public static final VM_Field half32Field = getField(org.jikesrvm.runtime.VM_Math.class, "half32", float.class);
  /** 1e-9 */
  public static final VM_Field billionthField = getField(org.jikesrvm.runtime.VM_Math.class, "billionth", double.class);
  /** 0.0 */
  public static final VM_Field zeroDoubleField = getField(org.jikesrvm.runtime.VM_Math.class, "zeroD", double.class);
  /** 1.0 */
  public static final VM_Field oneDoubleField = getField(org.jikesrvm.runtime.VM_Math.class, "oneD", double.class);
  /** largest double that can be rounded to an int */
  public static final VM_Field maxintField =
      getField(org.jikesrvm.runtime.VM_Math.class, "maxint", double.class);
  /** largest double that can be rounded to a long */
  public static final VM_Field maxlongField =
    getField(org.jikesrvm.runtime.VM_Math.class, "maxlong", double.class);
  /** smallest double that can be rounded to an int */
  public static final VM_Field minintField =
      getField(org.jikesrvm.runtime.VM_Math.class, "minint", double.class);
  /** largest float that can be rounded to an int */
  public static final VM_Field maxintFloatField =
    getField(org.jikesrvm.runtime.VM_Math.class, "maxintF", float.class);
  /** largest float that can be rounded to a long */
  public static final VM_Field maxlongFloatField =
    getField(org.jikesrvm.runtime.VM_Math.class, "maxlongF", float.class);
  /** IEEEmagic constant */
  public static final VM_Field IEEEmagicField =
      getField(org.jikesrvm.runtime.VM_Math.class, "IEEEmagic", double.class);
  /** special double value for use in int <--> double conversions */
  public static final VM_Field I2DconstantField =
      getField(org.jikesrvm.runtime.VM_Math.class,
               "I2Dconstant",
               double.class);

  public static final VM_Field suspendPendingField =
    getField(org.jikesrvm.scheduler.greenthreads.VM_GreenThread.class, "suspendPending", int.class);
  public static final VM_Field scratchStorageField =
      getField(org.jikesrvm.scheduler.VM_Processor.class, "scratchStorage", double.class);
  public static final VM_Field timeSliceExpiredField =
      getField(org.jikesrvm.scheduler.VM_Processor.class, "timeSliceExpired", int.class);
  public static final VM_Field takeYieldpointField =
      getField(org.jikesrvm.scheduler.VM_Processor.class, "takeYieldpoint", int.class);
  public static final VM_Field activeThreadField =
      getField(org.jikesrvm.scheduler.VM_Processor.class, "activeThread", org.jikesrvm.scheduler.VM_Thread.class);
  public static final VM_Field activeThreadStackLimitField =
      getField(org.jikesrvm.scheduler.VM_Processor.class, "activeThreadStackLimit", org.vmmagic.unboxed.Address.class);
  public static final VM_Field pthreadIDField = getField(org.jikesrvm.scheduler.VM_Processor.class, "pthread_id", int.class);
  public static final VM_Field timerTicksField =
    getField(org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor.class, "timerTicks", int.class);
  public static final VM_Field reportedTimerTicksField =
      getField(org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor.class, "reportedTimerTicks", int.class);
  public static final VM_Field vpStatusField = getField(org.jikesrvm.scheduler.VM_Processor.class, "vpStatus", int.class);
  public static final VM_Field threadIdField = getField(org.jikesrvm.scheduler.VM_Processor.class, "threadId", int.class);

  public static final VM_Field referenceReferentField =
      getField(java.lang.ref.Reference.class, "referent", org.vmmagic.unboxed.Address.class);

  /** Used in deciding which stack frames we can elide when printing. */
  public static final VM_NormalMethod mainThreadRunMethod =
      getMethod(org.jikesrvm.scheduler.VM_MainThread.class, "run", "()V");

  public static final VM_NormalMethod yieldpointFromPrologueMethod =
      getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromPrologue", "()V");
  public static final VM_NormalMethod yieldpointFromBackedgeMethod =
      getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromBackedge", "()V");
  public static final VM_NormalMethod yieldpointFromEpilogueMethod =
      getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromEpilogue", "()V");

  public static final VM_NormalMethod threadRunMethod = getMethod(org.jikesrvm.scheduler.VM_Thread.class, "run", "()V");
  public static final VM_NormalMethod threadStartoffMethod =
      getMethod(org.jikesrvm.scheduler.VM_Thread.class, "startoff", "()V");
  public static final VM_Field threadStackField = getField(org.jikesrvm.scheduler.VM_Thread.class, "stack", byte[].class);
  public static final VM_Field stackLimitField =
      getField(org.jikesrvm.scheduler.VM_Thread.class, "stackLimit", org.vmmagic.unboxed.Address.class);

  public static final VM_Field beingDispatchedField =
      getField(org.jikesrvm.scheduler.VM_Thread.class, "beingDispatched", boolean.class);
  public static final VM_Field threadSlotField = getField(org.jikesrvm.scheduler.VM_Thread.class, "threadSlot", int.class);
  public static final VM_Field jniEnvField =
      getField(org.jikesrvm.scheduler.VM_Thread.class, "jniEnv", org.jikesrvm.jni.VM_JNIEnvironment.class);
  public static final VM_Field threadContextRegistersField =
      getField(org.jikesrvm.scheduler.VM_Thread.class,
               "contextRegisters",
               org.jikesrvm.ArchitectureSpecific.VM_Registers.class);
  public static final VM_Field threadExceptionRegistersField =
      getField(org.jikesrvm.scheduler.VM_Thread.class,
               "exceptionRegisters",
               org.jikesrvm.ArchitectureSpecific.VM_Registers.class);

  public static final VM_Field tracePrevAddressField =
      getField(org.jikesrvm.objectmodel.VM_MiscHeader.class, "prevAddress", org.vmmagic.unboxed.Word.class);
  public static final VM_Field traceOIDField =
      getField(org.jikesrvm.objectmodel.VM_MiscHeader.class, "oid", org.vmmagic.unboxed.Word.class);
  public static final VM_Field dispenserField = getField(org.jikesrvm.mm.mmtk.Lock.class, "dispenser", int.class);
  public static final VM_Field servingField = getField(org.jikesrvm.mm.mmtk.Lock.class, "serving", int.class);
  public static final VM_Field lockThreadField =
      getField(org.jikesrvm.mm.mmtk.Lock.class, "thread", org.jikesrvm.scheduler.VM_Thread.class);
  public static final VM_Field gcStatusField = getField(org.mmtk.plan.Plan.class, "gcStatus", int.class);
  public static final VM_Field SQCFField = getField(org.mmtk.utility.deque.SharedDeque.class, "completionFlag", int.class);
  public static final VM_Field SQNCField = getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumers", int.class);
  public static final VM_Field SQNCWField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumersWaiting", int.class);
  public static final VM_Field SQheadField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "head", org.vmmagic.unboxed.Address.class);
  public static final VM_Field SQtailField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "tail", org.vmmagic.unboxed.Address.class);
  public static final VM_Field SQBEField = getField(org.mmtk.utility.deque.SharedDeque.class, "bufsenqueued", int.class);
  public static final VM_Field synchronizedCounterField =
      getField(org.jikesrvm.mm.mmtk.SynchronizedCounter.class, "count", int.class);

  public static final VM_NormalMethod arrayStoreWriteBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_NormalMethod putfieldWriteBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "putfieldWriteBarrier", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  public static final VM_NormalMethod putstaticWriteBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "putstaticWriteBarrier", "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");

  public static final VM_NormalMethod arrayLoadReadBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "arrayLoadReadBarrier", "(Ljava/lang/Object;I)Ljava/lang/Object;");
  public static final VM_NormalMethod getfieldReadBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "getfieldReadBarrier", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");
  public static final VM_NormalMethod getstaticReadBarrierMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "getstaticReadBarrier", "(Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");

  public static final VM_NormalMethod modifyCheckMethod =
      getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class, "modifyCheck", "(Ljava/lang/Object;)V");

  public static final VM_Field outputLockField = getField(org.jikesrvm.scheduler.VM_Scheduler.class, "outputLock", int.class);

  // used in boot image writer
  public static final VM_Field greenProcessorsField =
      getField(org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler.class, "processors", org.jikesrvm.scheduler.VM_ProcessorTable.class);
  public static final VM_Field debugRequestedField =
      getField(org.jikesrvm.scheduler.VM_Scheduler.class, "debugRequested", boolean.class);
  public static final VM_NormalMethod dumpStackAndDieMethod =
      getMethod(org.jikesrvm.scheduler.VM_Scheduler.class, "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final VM_Field latestContenderField =
      getField(org.jikesrvm.scheduler.VM_ProcessorLock.class, "latestContender", org.jikesrvm.scheduler.VM_Processor.class);

  public static final VM_Field depthField = getField(org.jikesrvm.classloader.VM_Type.class, "depth", int.class);
  public static final VM_Field idField = getField(org.jikesrvm.classloader.VM_Type.class, "id", int.class);
  public static final VM_Field dimensionField = getField(org.jikesrvm.classloader.VM_Type.class, "dimension", int.class);

  public static final VM_Field innermostElementTypeDimensionField =
      getField(org.jikesrvm.classloader.VM_Array.class, "innermostElementTypeDimension", int.class);

  public static final VM_Field JNIEnvSavedPRField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "savedPRreg", org.jikesrvm.scheduler.VM_Processor.class);
  public static final VM_Field JNIGlobalRefsField =
    getField(org.jikesrvm.jni.VM_JNIGlobalRefTable.class, "refs", java.lang.Object[].class);
  public static final VM_Field JNIRefsField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefs", org.vmmagic.unboxed.AddressArray.class);
  public static final VM_Field JNIRefsTopField = getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsTop", int.class);
  public static final VM_Field JNIRefsMaxField = getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsMax", int.class);
  public static final VM_Field JNIRefsSavedFPField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsSavedFP", int.class);
  public static final VM_Field JNITopJavaFPField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNITopJavaFP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field JNIPendingExceptionField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "pendingException", java.lang.Throwable.class);
  public static final VM_Field JNIExternalFunctionsField =
      getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "externalJNIFunctions", org.vmmagic.unboxed.Address.class);
  public static final VM_Field JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? getField(org.jikesrvm.jni.VM_JNIEnvironment.class,
                                      "savedJTOC",
                                      org.vmmagic.unboxed.Address.class) : null;

  public static final VM_Field the_boot_recordField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "the_boot_record", org.jikesrvm.runtime.VM_BootRecord.class);
  public static final VM_Field sysVirtualProcessorYieldIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysVirtualProcessorYieldIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field externalSignalFlagField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "externalSignalFlag", int.class);
  public static final VM_Field sysLongDivideIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongDivideIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongRemainderIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongRemainderIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongToFloatIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongToFloatIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongToDoubleIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongToDoubleIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysFloatToIntIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysFloatToIntIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleToIntIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleToIntIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysFloatToLongIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysFloatToLongIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleToLongIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleToLongIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleRemainderIPField =
      getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleRemainderIP", org.vmmagic.unboxed.Address.class);

  public static final VM_Field edgeCountersField =
      getField(org.jikesrvm.compilers.baseline.VM_EdgeCounts.class, "data", int[][].class);

  public static final VM_Field inetAddressAddressField = getField(java.net.InetAddress.class, "address", int.class);
  public static final VM_Field inetAddressFamilyField = getField(java.net.InetAddress.class, "family", int.class);

  public static final VM_Field socketImplAddressField =
      getField(java.net.SocketImpl.class, "address", java.net.InetAddress.class);
  public static final VM_Field socketImplPortField = getField(java.net.SocketImpl.class, "port", int.class);

  //////////////////
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  public static final VM_Field specializedMethodsField;

  public static final VM_Field osrOrganizerQueueLockField;
  public static final VM_NormalMethod optThreadSwitchFromOsrOptMethod;
  public static final VM_NormalMethod optThreadSwitchFromPrologueMethod;
  public static final VM_NormalMethod optThreadSwitchFromBackedgeMethod;
  public static final VM_NormalMethod optThreadSwitchFromEpilogueMethod;
  public static final VM_NormalMethod yieldpointFromNativePrologueMethod;
  public static final VM_NormalMethod yieldpointFromNativeEpilogueMethod;
  public static final VM_NormalMethod optResolveMethod;
  public static final VM_NormalMethod optNewArrayArrayMethod;
  public static final VM_NormalMethod optNew2DArrayMethod;
  public static final VM_NormalMethod sysArrayCopy;

  static {
    if (VM.BuildForOptCompiler) {
      specializedMethodsField =
          getField(org.jikesrvm.compilers.opt.SpecializedMethodPool.class,
                   "specializedMethods",
                   org.jikesrvm.ArchitectureSpecific.VM_CodeArray[].class);
      osrOrganizerQueueLockField = getField(org.jikesrvm.adaptive.OSR_OrganizerThread.class, "queueLock", int.class);
      optThreadSwitchFromOsrOptMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromEpilogue", "()V");
      yieldpointFromNativePrologueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromNativePrologue", "()V");
      yieldpointFromNativeEpilogueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "yieldpointFromNativeEpilogue", "()V");
      optResolveMethod = getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile.class, "resolve", "()V");

      optNewArrayArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptLinker.class, "newArrayArray", "(I[II)Ljava/lang/Object;");
      optNew2DArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.VM_OptLinker.class, "new2DArray", "(IIII)Ljava/lang/Object;");

      sysArrayCopy = getMethod(java.lang.VMSystem.class, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
      sysArrayCopy.setRuntimeServiceMethod(false);
    } else {
      specializedMethodsField = null;
      osrOrganizerQueueLockField = null;
      optThreadSwitchFromOsrOptMethod = null;
      optThreadSwitchFromPrologueMethod = null;
      optThreadSwitchFromBackedgeMethod = null;
      optThreadSwitchFromEpilogueMethod = null;
      yieldpointFromNativePrologueMethod = null;
      yieldpointFromNativeEpilogueMethod = null;
      optResolveMethod = null;
      optNewArrayArrayMethod = null;
      optNew2DArrayMethod = null;
      sysArrayCopy = null;
    }
  }

  public static final VM_Field classLoaderDefinedPackages =
      getField(java.lang.ClassLoader.class, "definedPackages", java.util.HashMap.class);
}
