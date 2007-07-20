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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getField;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getMethod;

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

  public static final VM_Method java_lang_reflect_Method_invokeMethod =
      VM_EntrypointHelper.getMethod(java.lang.reflect.Method.class, "invoke",
          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  public static final VM_Field magicObjectRemapperField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Magic.class,
               "objectAddressRemapper",
               org.jikesrvm.runtime.VM_ObjectAddressRemapper.class);

  public static final VM_NormalMethod instanceOfMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfResolvedClassMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfFinalMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "instanceOfFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)Z");
  public static final VM_NormalMethod checkcastMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastResolvedClassMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastFinalMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "checkcastFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod checkstoreMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_NormalMethod athrowMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final VM_NormalMethod resolvedNewScalarMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "resolvedNewScalar",
                "(I[Ljava/lang/Object;ZIIII)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewScalarMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewArrayMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final VM_NormalMethod resolvedNewArrayMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class,
                "resolvedNewArray",
                "(III[Ljava/lang/Object;IIII)Ljava/lang/Object;");
  public static final VM_Field gcLockField = VM_EntrypointHelper.getField(java.lang.VMRuntime.class, "gcLock", int.class);

  public static final VM_Field sysWriteLockField = VM_EntrypointHelper.getField(org.jikesrvm.VM.class, "sysWriteLock", int.class);
  public static final VM_Field intBufferLockField =
      VM_EntrypointHelper.getField(org.jikesrvm.VM_Services.class, "intBufferLock", int.class);
  public static final VM_Field dumpBufferLockField =
      VM_EntrypointHelper.getField(org.jikesrvm.VM_Services.class, "dumpBufferLock", int.class);

  public static final VM_NormalMethod unexpectedAbstractMethodCallMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unexpectedAbstractMethodCall", "()V");
  public static final VM_NormalMethod raiseNullPointerException =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseNullPointerException", "()V");
  public static final VM_NormalMethod raiseArrayBoundsException =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_NormalMethod raiseArithmeticException =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseArithmeticException", "()V");
  public static final VM_NormalMethod raiseAbstractMethodError =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseAbstractMethodError", "()V");
  public static final VM_NormalMethod raiseIllegalAccessError =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "raiseIllegalAccessError", "()V");
  public static final VM_NormalMethod deliverHardwareExceptionMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "deliverHardwareException", "(II)V");
  public static final VM_NormalMethod unlockAndThrowMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_Runtime.class, "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_NormalMethod invokeInterfaceMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/ArchitectureSpecific$VM_CodeArray;");
  public static final VM_NormalMethod findItableMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "findITable",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  public static final VM_NormalMethod invokeinterfaceImplementsTestMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "invokeinterfaceImplementsTest",
                "(Lorg/jikesrvm/classloader/VM_Class;[Ljava/lang/Object;)V");
  public static final VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.classloader.VM_InterfaceInvocation.class,
                "unresolvedInvokeinterfaceImplementsTest",
                "(I[Ljava/lang/Object;)V");

  public static final VM_NormalMethod lockMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.objectmodel.VM_ObjectModel.class, "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_NormalMethod unlockMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.objectmodel.VM_ObjectModel.class, "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_NormalMethod inlineLockMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_ThinLock.class,
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod inlineUnlockMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_ThinLock.class,
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final VM_NormalMethod lazyMethodInvokerMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "lazyMethodInvoker", "()V");
  public static final VM_NormalMethod unimplementedNativeMethodMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "unimplementedNativeMethod", "()V");
  public static final VM_NormalMethod sysCallMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.runtime.VM_DynamicLinker.class, "sysCallMethod", "()V");

  public static final VM_NormalMethod resolveMemberMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.classloader.VM_TableBasedDynamicLinker.class, "resolveMember", "(I)I");
  public static final VM_Field memberOffsetsField =
      VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_TableBasedDynamicLinker.class, "memberOffsets", int[].class);

  /** 1L */
  public static final VM_Field longOneField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "longOne", long.class);
  /** -1.0F */
  public static final VM_Field minusOneField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "minusOne", float.class);
  /** 0.0F */
  public static final VM_Field zeroFloatField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "zero", float.class);
  /**0.5F */
  public static final VM_Field halfFloatField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "half", float.class);
  /** 1.0F */
  public static final VM_Field oneFloatField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "one", float.class);
  /** 2.0F */
  public static final VM_Field twoFloatField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "two", float.class);
  /** 2.0F^32 */
  public static final VM_Field two32Field = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "two32", float.class);
  /** 0.5F^32 */
  public static final VM_Field half32Field = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "half32", float.class);
  /** 1e-9 */
  public static final VM_Field billionthField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "billionth", double.class);
  /** 0.0 */
  public static final VM_Field zeroDoubleField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "zeroD", double.class);
  /** 1.0 */
  public static final VM_Field oneDoubleField = VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "oneD", double.class);
  /**  largest double that can be rounded to an int */
  public static final VM_Field maxintField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "maxint", double.class);
  /** smallest double that can be rounded to an int */
  public static final VM_Field minintField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "minint", double.class); 
  /** largest float that can be rounded to an int */
  public static final VM_Field maxintFloatField =
    VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "maxintF", float.class);
  /** IEEEmagic constant */
  public static final VM_Field maxlongFloatField =
    VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "maxlongF", float.class);
  public static final VM_Field IEEEmagicField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class, "IEEEmagic", double.class);
  /** special double value for use in int <--> double conversions */
  public static final VM_Field I2DconstantField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_Math.class,
               "I2Dconstant",
               double.class);

  public static final VM_Field suspendPendingField = 
    VM_EntrypointHelper.getField(org.jikesrvm.scheduler.greenthreads.VM_GreenThread.class, "suspendPending", int.class);
  public static final VM_Field scratchStorageField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "scratchStorage", double.class);
  public static final VM_Field timeSliceExpiredField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "timeSliceExpired", int.class);
  public static final VM_Field takeYieldpointField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "takeYieldpoint", int.class);
  public static final VM_Field activeThreadField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "activeThread", org.jikesrvm.scheduler.VM_Thread.class);
  public static final VM_Field activeThreadStackLimitField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "activeThreadStackLimit", org.vmmagic.unboxed.Address.class);
  public static final VM_Field pthreadIDField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "pthread_id", int.class);
  public static final VM_Field timerTicksField = 
    VM_EntrypointHelper.getField(org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor.class, "timerTicks", int.class);
  public static final VM_Field reportedTimerTicksField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor.class, "reportedTimerTicks", int.class);
  public static final VM_Field vpStatusField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "vpStatus", int.class);
  public static final VM_Field threadIdField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "threadId", int.class);

  public static final VM_Field referenceReferentField =
      VM_EntrypointHelper.getField(java.lang.ref.Reference.class, "referent", org.vmmagic.unboxed.Address.class);
  public static final VM_Field referenceNextAsAddressField =
      VM_EntrypointHelper.getField(java.lang.ref.Reference.class, "nextAsAddress", org.vmmagic.unboxed.Address.class);

  /** Used in deciding which stack frames we can elide when printing. */
  public static final VM_NormalMethod mainThreadRunMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_MainThread.class, "run", "()V");

  public static final VM_NormalMethod yieldpointFromPrologueMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromPrologue", "()V");
  public static final VM_NormalMethod yieldpointFromBackedgeMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromBackedge", "()V");
  public static final VM_NormalMethod yieldpointFromEpilogueMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Thread.class, "yieldpointFromEpilogue", "()V");

  public static final VM_NormalMethod threadRunMethod = VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Thread.class, "run", "()V");
  public static final VM_NormalMethod threadStartoffMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Thread.class, "startoff", "()V");
  public static final VM_Field threadStackField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class, "stack", byte[].class);
  public static final VM_Field stackLimitField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class, "stackLimit", org.vmmagic.unboxed.Address.class);

  public static final VM_Field beingDispatchedField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class, "beingDispatched", boolean.class);
  public static final VM_Field threadSlotField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class, "threadSlot", int.class);
  public static final VM_Field jniEnvField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class, "jniEnv", org.jikesrvm.jni.VM_JNIEnvironment.class);
  public static final VM_Field threadContextRegistersField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class,
               "contextRegisters",
               org.jikesrvm.ArchitectureSpecific.VM_Registers.class);
  public static final VM_Field threadHardwareExceptionRegistersField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Thread.class,
               "hardwareExceptionRegisters",
               org.jikesrvm.ArchitectureSpecific.VM_Registers.class);

  public static final VM_Field tracePrevAddressField =
      VM_EntrypointHelper.getField(org.jikesrvm.objectmodel.VM_MiscHeader.class, "prevAddress", org.vmmagic.unboxed.Word.class);
  public static final VM_Field traceOIDField =
      VM_EntrypointHelper.getField(org.jikesrvm.objectmodel.VM_MiscHeader.class, "oid", org.vmmagic.unboxed.Word.class);
  public static final VM_Field dispenserField = VM_EntrypointHelper.getField(org.jikesrvm.mm.mmtk.Lock.class, "dispenser", int.class);
  public static final VM_Field servingField = VM_EntrypointHelper.getField(org.jikesrvm.mm.mmtk.Lock.class, "serving", int.class);
  public static final VM_Field lockThreadField =
      VM_EntrypointHelper.getField(org.jikesrvm.mm.mmtk.Lock.class, "thread", org.jikesrvm.scheduler.VM_Thread.class);
  public static final VM_Field lockStartField = VM_EntrypointHelper.getField(org.jikesrvm.mm.mmtk.Lock.class, "start", long.class);
  public static final VM_Field gcStatusField = VM_EntrypointHelper.getField(org.mmtk.plan.Plan.class, "gcStatus", int.class);
  public static final VM_Field SQCFField = VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "completionFlag", int.class);
  public static final VM_Field SQNCField = VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumers", int.class);
  public static final VM_Field SQNCWField =
      VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumersWaiting", int.class);
  public static final VM_Field SQheadField =
      VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "head", org.vmmagic.unboxed.Address.class);
  public static final VM_Field SQtailField =
      VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "tail", org.vmmagic.unboxed.Address.class);
  public static final VM_Field SQBEField = VM_EntrypointHelper.getField(org.mmtk.utility.deque.SharedDeque.class, "bufsenqueued", int.class);
  public static final VM_Field synchronizedCounterField =
      VM_EntrypointHelper.getField(org.jikesrvm.mm.mmtk.SynchronizedCounter.class, "count", int.class);
  public static final VM_NormalMethod arrayStoreWriteBarrierMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class,
                "arrayStoreWriteBarrier",
                "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_NormalMethod putfieldWriteBarrierMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class,
                "putfieldWriteBarrier",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  public static final VM_NormalMethod putstaticWriteBarrierMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class,
                "putstaticWriteBarrier",
                "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;)V");
  public static final VM_NormalMethod modifyCheckMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.memorymanagers.mminterface.MM_Interface.class,
          "modifyCheck", "(Ljava/lang/Object;)V");

  public static final VM_Field outputLockField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Scheduler.class, "outputLock", int.class);

  // used in boot image writer
  public static final VM_Field greenProcessorsField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler.class, "processors", org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor[].class);
  public static final VM_Field debugRequestedField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Scheduler.class, "debugRequested", boolean.class);
  public static final VM_NormalMethod dumpStackAndDieMethod =
      VM_EntrypointHelper.getMethod(org.jikesrvm.scheduler.VM_Scheduler.class, "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final VM_Field latestContenderField =
      VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_ProcessorLock.class, "latestContender", org.jikesrvm.scheduler.VM_Processor.class);

  public static final VM_Field classForTypeField =
      VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_Type.class, "classForType", java.lang.Class.class);
  public static final VM_Field depthField = VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_Type.class, "depth", int.class);
  public static final VM_Field idField = VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_Type.class, "id", int.class);
  public static final VM_Field dimensionField = VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_Type.class, "dimension", int.class);

  public static final VM_Field innermostElementTypeField =
      VM_EntrypointHelper.getField(org.jikesrvm.classloader.VM_Array.class, "innermostElementType", org.jikesrvm.classloader.VM_Type.class);

  public static final VM_Field JNIEnvSavedPRField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "savedPRreg", org.jikesrvm.scheduler.VM_Processor.class);
  public static final VM_Field JNIGlobalRefsField =
    VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIGlobalRefTable.class, "refs", java.lang.Object[].class);
  public static final VM_Field JNIRefsField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefs", org.vmmagic.unboxed.AddressArray.class);
  public static final VM_Field JNIRefsTopField = VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsTop", int.class);
  public static final VM_Field JNIRefsMaxField = VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsMax", int.class);
  public static final VM_Field JNIRefsSavedFPField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNIRefsSavedFP", int.class);
  public static final VM_Field JNITopJavaFPField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "JNITopJavaFP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field JNIPendingExceptionField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "pendingException", java.lang.Throwable.class);
  public static final VM_Field JNIExternalFunctionsField =
      VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class, "externalJNIFunctions", org.vmmagic.unboxed.Address.class);
  public static final VM_Field JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField(org.jikesrvm.jni.VM_JNIEnvironment.class,
                                      "savedJTOC",
                                      org.vmmagic.unboxed.Address.class) : null;

  public static final VM_Field the_boot_recordField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "the_boot_record", org.jikesrvm.runtime.VM_BootRecord.class);
  public static final VM_Field sysVirtualProcessorYieldIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysVirtualProcessorYieldIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field externalSignalFlagField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "externalSignalFlag", int.class);
  public static final VM_Field sysLongDivideIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongDivideIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongRemainderIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongRemainderIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongToFloatIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongToFloatIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysLongToDoubleIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysLongToDoubleIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysFloatToIntIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysFloatToIntIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleToIntIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleToIntIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysFloatToLongIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysFloatToLongIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleToLongIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleToLongIP", org.vmmagic.unboxed.Address.class);
  public static final VM_Field sysDoubleRemainderIPField =
      VM_EntrypointHelper.getField(org.jikesrvm.runtime.VM_BootRecord.class, "sysDoubleRemainderIP", org.vmmagic.unboxed.Address.class);

  public static final VM_Field edgeCountersField =
      VM_EntrypointHelper.getField(org.jikesrvm.compilers.baseline.VM_EdgeCounts.class, "data", int[][].class);

  public static final VM_Field inetAddressAddressField = VM_EntrypointHelper.getField(java.net.InetAddress.class, "address", int.class);
  public static final VM_Field inetAddressFamilyField = VM_EntrypointHelper.getField(java.net.InetAddress.class, "family", int.class);

  public static final VM_Field socketImplAddressField =
      VM_EntrypointHelper.getField(java.net.SocketImpl.class, "address", java.net.InetAddress.class);
  public static final VM_Field socketImplPortField = VM_EntrypointHelper.getField(java.net.SocketImpl.class, "port", int.class);

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
  public static final VM_NormalMethod sysArrayCopy;

  static {
    if (VM.BuildForOptCompiler) {
      specializedMethodsField =
          VM_EntrypointHelper.getField(org.jikesrvm.compilers.opt.OPT_SpecializedMethodPool.class,
                   "specializedMethods",
                   org.jikesrvm.ArchitectureSpecific.VM_CodeArray[].class);
      osrOrganizerQueueLockField = VM_EntrypointHelper.getField(org.jikesrvm.adaptive.OSR_OrganizerThread.class, "queueLock", int.class);
      optThreadSwitchFromOsrOptMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromEpilogue", "()V");
      yieldpointFromNativePrologueMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromNativePrologue", "()V");
      yieldpointFromNativeEpilogueMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_yieldpointFromNativeEpilogue", "()V");
      optResolveMethod = VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptSaveVolatile.class, "OPT_resolve", "()V");

      optNewArrayArrayMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.compilers.opt.VM_OptLinker.class, "newArrayArray", "(I[II)Ljava/lang/Object;");

      sysArrayCopy = VM_EntrypointHelper.getMethod(java.lang.VMSystem.class, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
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
      sysArrayCopy = null;
    }
  }

  public static final VM_NormalMethod osrGetRefAtMethod;
  public static final VM_NormalMethod osrCleanRefsMethod;
  
  static {
    if (VM.BuildForAdaptiveSystem) {
      osrGetRefAtMethod = VM_EntrypointHelper.getMethod(org.jikesrvm.osr.OSR_ObjectHolder.class, "getRefAt", "(II)Ljava/lang/Object;");
      osrCleanRefsMethod = VM_EntrypointHelper.getMethod(org.jikesrvm.osr.OSR_ObjectHolder.class, "cleanRefs", "(I)V");
    } else {
      osrGetRefAtMethod = null;
      osrCleanRefsMethod = null;
    }
  }

  //////////////////
  // Entrypoints that are valid only when the adaptive optimization system is included in the build
  //////////////////
  public static final VM_Field methodListenerNumSamplesField;

  public static final VM_Field edgeListenerUpdateCalledField;
  public static final VM_Field edgeListenerSamplesTakenField;

  public static final VM_Field yieldCountListenerNumYieldsField;

  public static final VM_Field counterArrayManagerCounterArraysField;

  public static final VM_Field invocationCountsField;
  public static final VM_NormalMethod invocationCounterTrippedMethod;

  // Counter-based sampling fields
  public static final VM_Field globalCBSField;
  public static final VM_Field processorCBSField;
  public static final VM_Field cbsResetValueField;

  static {
    if (VM.BuildForAdaptiveSystem) {
      methodListenerNumSamplesField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.measurements.listeners.VM_MethodListener.class, "numSamples", int.class);
      edgeListenerUpdateCalledField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.measurements.listeners.VM_EdgeListener.class, "updateCalled", int.class);
      edgeListenerSamplesTakenField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.measurements.listeners.VM_EdgeListener.class, "samplesTaken", int.class);
      yieldCountListenerNumYieldsField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.measurements.listeners.VM_YieldCounterListener.class, "numYields", int.class);

      counterArrayManagerCounterArraysField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.measurements.instrumentation.VM_CounterArrayManager.class,
                   "counterArrays",
                   double[][].class);

      invocationCountsField = VM_EntrypointHelper.getField(org.jikesrvm.adaptive.recompilation.VM_InvocationCounts.class, "counts", int[].class);
      invocationCounterTrippedMethod =
          VM_EntrypointHelper.getMethod(org.jikesrvm.adaptive.recompilation.VM_InvocationCounts.class, "counterTripped", "(I)V");

      globalCBSField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.recompilation.instrumentation.VM_CounterBasedSampling.class,
                   "globalCounter",
                   int.class);
      processorCBSField = VM_EntrypointHelper.getField(org.jikesrvm.scheduler.VM_Processor.class, "processor_cbs_counter", int.class);
      cbsResetValueField =
          VM_EntrypointHelper.getField(org.jikesrvm.adaptive.recompilation.instrumentation.VM_CounterBasedSampling.class, "resetValue", int.class);
    } else {
      methodListenerNumSamplesField = null;
      edgeListenerUpdateCalledField = null;
      edgeListenerSamplesTakenField = null;
      yieldCountListenerNumYieldsField = null;
      counterArrayManagerCounterArraysField = null;
      invocationCountsField = null;
      invocationCounterTrippedMethod = null;
      globalCBSField = null;
      processorCBSField = null;
      cbsResetValueField = null;
    }
  }

  public static final VM_Field classLoaderDefinedPackages =
      VM_EntrypointHelper.getField(java.lang.ClassLoader.class, "definedPackages", java.util.HashMap.class);
}
