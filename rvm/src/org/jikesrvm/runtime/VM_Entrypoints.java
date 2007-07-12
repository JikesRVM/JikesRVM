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
  public static final VM_NormalMethod bootMethod = getMethod("Lorg/jikesrvm/VM;", "boot", "()V");

  public static final VM_Method java_lang_reflect_Method_invokeMethod =
      getMethod("Ljava/lang/reflect/Method;", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  public static final VM_Field magicObjectRemapperField =
      getField("Lorg/jikesrvm/runtime/VM_Magic;",
               "objectAddressRemapper",
               "Lorg/jikesrvm/runtime/VM_ObjectAddressRemapper;");

  public static final VM_NormalMethod instanceOfMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfResolvedClassMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfFinalMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "instanceOfFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)Z");
  public static final VM_NormalMethod checkcastMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastResolvedClassMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastFinalMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "checkcastFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod checkstoreMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_NormalMethod athrowMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final VM_NormalMethod resolvedNewScalarMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "resolvedNewScalar",
                "(I[Ljava/lang/Object;ZIIII)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewScalarMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewArrayMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final VM_NormalMethod resolvedNewArrayMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "resolvedNewArray",
                "(III[Ljava/lang/Object;IIII)Ljava/lang/Object;");
  public static final VM_Field gcLockField = getField("Ljava/lang/VMRuntime;", "gcLock", "I");

  public static final VM_Field sysWriteLockField = getField("Lorg/jikesrvm/VM;", "sysWriteLock", "I");
  public static final VM_Field intBufferLockField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "intBufferLock", "I");
  public static final VM_Field dumpBufferLockField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "dumpBufferLock", "I");

  public static final VM_NormalMethod unexpectedAbstractMethodCallMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  public static final VM_NormalMethod raiseNullPointerException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseNullPointerException", "()V");
  public static final VM_NormalMethod raiseArrayBoundsException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_NormalMethod raiseArithmeticException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseArithmeticException", "()V");
  public static final VM_NormalMethod raiseAbstractMethodError =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseAbstractMethodError", "()V");
  public static final VM_NormalMethod raiseIllegalAccessError =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseIllegalAccessError", "()V");
  public static final VM_NormalMethod deliverHardwareExceptionMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "deliverHardwareException", "(II)V");
  public static final VM_NormalMethod unlockAndThrowMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_NormalMethod invokeInterfaceMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/ArchitectureSpecific$VM_CodeArray;");
  public static final VM_NormalMethod findItableMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "findITable",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  public static final VM_NormalMethod invokeinterfaceImplementsTestMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "invokeinterfaceImplementsTest",
                "(Lorg/jikesrvm/classloader/VM_Class;[Ljava/lang/Object;)V");
  public static final VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "unresolvedInvokeinterfaceImplementsTest",
                "(I[Ljava/lang/Object;)V");

  public static final VM_NormalMethod lockMethod =
      getMethod("Lorg/jikesrvm/objectmodel/VM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_NormalMethod unlockMethod =
      getMethod("Lorg/jikesrvm/objectmodel/VM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_NormalMethod inlineLockMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_ThinLock;",
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod inlineUnlockMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_ThinLock;",
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final VM_NormalMethod lazyMethodInvokerMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "lazyMethodInvoker", "()V");
  public static final VM_NormalMethod unimplementedNativeMethodMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "unimplementedNativeMethod", "()V");
  public static final VM_NormalMethod sysCallMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "sysCallMethod", "()V");

  public static final VM_NormalMethod resolveMemberMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "resolveMember", "(I)I");
  public static final VM_Field memberOffsetsField =
      getField("Lorg/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "memberOffsets", "[I");

  public static final VM_Field longOneField = getField("Lorg/jikesrvm/runtime/VM_Math;", "longOne", "J");  // 1L
  public static final VM_Field minusOneField = getField("Lorg/jikesrvm/runtime/VM_Math;", "minusOne", "F"); // -1.0F
  public static final VM_Field zeroFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "zero", "F");     // 0.0F
  public static final VM_Field halfFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "half", "F");     // 0.5F
  public static final VM_Field oneFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "one", "F");      // 1.0F
  public static final VM_Field twoFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "two", "F");      // 2.0F
  public static final VM_Field two32Field = getField("Lorg/jikesrvm/runtime/VM_Math;", "two32", "F");    // 2.0F^32
  public static final VM_Field half32Field = getField("Lorg/jikesrvm/runtime/VM_Math;", "half32", "F");   // 0.5F^32
  public static final VM_Field billionthField = getField("Lorg/jikesrvm/runtime/VM_Math;", "billionth", "D");// 1e-9
  public static final VM_Field zeroDoubleField = getField("Lorg/jikesrvm/runtime/VM_Math;", "zeroD", "D");    // 0.0
  public static final VM_Field oneDoubleField = getField("Lorg/jikesrvm/runtime/VM_Math;", "oneD", "D");     // 1.0
  public static final VM_Field maxintField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  public static final VM_Field minintField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  /** largest float that can be rounded to an int */
  public static final VM_Field maxintFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "maxintF", "F");
  public static final VM_Field maxlongFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "maxlongF", "F");
  public static final VM_Field IEEEmagicField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  public static final VM_Field I2DconstantField =
      getField("Lorg/jikesrvm/runtime/VM_Math;",
               "I2Dconstant",
               "D");//  special double value for use in int <--> double conversions
  public static final VM_Field scratchStorageField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "scratchStorage", "D");
  public static final VM_Field timeSliceExpiredField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "timeSliceExpired", "I");
  public static final VM_Field takeYieldpointField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "takeYieldpoint", "I");
  public static final VM_Field activeThreadField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "activeThread", "Lorg/jikesrvm/scheduler/VM_Thread;");
  public static final VM_Field activeThreadStackLimitField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "activeThreadStackLimit", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field pthreadIDField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "pthread_id", "I");
  public static final VM_Field timerTicksField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "timerTicks", "I");
  public static final VM_Field reportedTimerTicksField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "reportedTimerTicks", "I");
  public static final VM_Field vpStatusField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "vpStatus", "I");
  public static final VM_Field threadIdField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "threadId", "I");
  public static final VM_Field jtocField =
      (VM.BuildForIA32) ? getField("Lorg/jikesrvm/scheduler/VM_Processor;",
                                   "jtoc",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  public static final VM_Field framePointerField =
      (VM.BuildForIA32) ? getField("Lorg/jikesrvm/scheduler/VM_Processor;",
                                   "framePointer",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  public static final VM_Field hiddenSignatureIdField =
      (VM.BuildForIA32) ? getField("Lorg/jikesrvm/scheduler/VM_Processor;", "hiddenSignatureId", "I") : null;
  public static final VM_Field arrayIndexTrapParamField =
      (VM.BuildForIA32) ? getField("Lorg/jikesrvm/scheduler/VM_Processor;", "arrayIndexTrapParam", "I") : null;

  public static final VM_Field referenceReferentField =
      getField("Ljava/lang/ref/Reference;", "referent", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field referenceNextAsAddressField =
      getField("Ljava/lang/ref/Reference;", "nextAsAddress", "Lorg/vmmagic/unboxed/Address;");

  /** Used in deciding which stack frames we can elide when printing. */
  public static final VM_NormalMethod mainThreadRunMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_MainThread;", "run", "()V");

  public static final VM_NormalMethod yieldpointFromPrologueMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromPrologue", "()V");
  public static final VM_NormalMethod yieldpointFromBackedgeMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromBackedge", "()V");
  public static final VM_NormalMethod yieldpointFromEpilogueMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromEpilogue", "()V");

  public static final VM_NormalMethod threadRunMethod = getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "run", "()V");
  public static final VM_NormalMethod threadStartoffMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "startoff", "()V");
  public static final VM_Field threadStackField = getField("Lorg/jikesrvm/scheduler/VM_Thread;", "stack", "[B");
  public static final VM_Field stackLimitField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "stackLimit", "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field beingDispatchedField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "beingDispatched", "Z");
  public static final VM_Field threadSlotField = getField("Lorg/jikesrvm/scheduler/VM_Thread;", "threadSlot", "I");
  public static final VM_Field jniEnvField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "jniEnv", "Lorg/jikesrvm/jni/VM_JNIEnvironment;");
  public static final VM_Field threadContextRegistersField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;",
               "contextRegisters",
               "Lorg/jikesrvm/ArchitectureSpecific$VM_Registers;");
  public static final VM_Field threadHardwareExceptionRegistersField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;",
               "hardwareExceptionRegisters",
               "Lorg/jikesrvm/ArchitectureSpecific$VM_Registers;");

  public static final VM_Field tracePrevAddressField =
      getField("Lorg/jikesrvm/objectmodel/VM_MiscHeader;", "prevAddress", "Lorg/vmmagic/unboxed/Word;");
  public static final VM_Field traceOIDField =
      getField("Lorg/jikesrvm/objectmodel/VM_MiscHeader;", "oid", "Lorg/vmmagic/unboxed/Word;");
  public static final VM_Field dispenserField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "dispenser", "I");
  public static final VM_Field servingField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "serving", "I");
  public static final VM_Field lockThreadField =
      getField("Lorg/jikesrvm/mm/mmtk/Lock;", "thread", "Lorg/jikesrvm/scheduler/VM_Thread;");
  public static final VM_Field lockStartField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "start", "J");
  public static final VM_Field gcStatusField = getField("Lorg/mmtk/plan/Plan;", "gcStatus", "I");
  public static final VM_Field tailField =
      getField("Lorg/mmtk/utility/deque/LocalSSB;", "tail", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQCFField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "completionFlag", "I");
  public static final VM_Field SQNCField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumers", "I");
  public static final VM_Field SQNCWField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumersWaiting", "I");
  public static final VM_Field SQheadField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "head", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQtailField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "tail", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field LQheadField =
      getField("Lorg/mmtk/utility/deque/LocalQueue;", "head", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQBEField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "bufsenqueued", "I");
  public static final VM_Field synchronizedCounterField =
      getField("Lorg/jikesrvm/mm/mmtk/SynchronizedCounter;", "count", "I");
  public static final VM_NormalMethod arrayStoreWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "arrayStoreWriteBarrier",
                "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_NormalMethod putfieldWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "putfieldWriteBarrier",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  public static final VM_NormalMethod putstaticWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "putstaticWriteBarrier",
                "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;)V");
  public static final VM_NormalMethod modifyCheckMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;", "modifyCheck", "(Ljava/lang/Object;)V");

  public static final VM_Field outputLockField = getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "outputLock", "I");

  public static final VM_Field processorsField =
      getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "processors", "[Lorg/jikesrvm/scheduler/VM_Processor;");
  public static final VM_Field debugRequestedField =
      getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "debugRequested", "Z");
  public static final VM_NormalMethod dumpStackAndDieMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Scheduler;", "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final VM_Field latestContenderField =
      getField("Lorg/jikesrvm/scheduler/VM_ProcessorLock;", "latestContender", "Lorg/jikesrvm/scheduler/VM_Processor;");

  public static final VM_Field classForTypeField =
      getField("Lorg/jikesrvm/classloader/VM_Type;", "classForType", "Ljava/lang/Class;");
  public static final VM_Field depthField = getField("Lorg/jikesrvm/classloader/VM_Type;", "depth", "I");
  public static final VM_Field idField = getField("Lorg/jikesrvm/classloader/VM_Type;", "id", "I");
  public static final VM_Field dimensionField = getField("Lorg/jikesrvm/classloader/VM_Type;", "dimension", "I");

  public static final VM_Field innermostElementTypeField =
      getField("Lorg/jikesrvm/classloader/VM_Array;", "innermostElementType", "Lorg/jikesrvm/classloader/VM_Type;");

  public static final VM_Field JNIEnvSavedPRField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "savedPRreg", "Lorg/jikesrvm/scheduler/VM_Processor;");
  public static final VM_Field JNIGlobalRefsField =
      getField("Lorg/jikesrvm/jni/VM_JNIGlobalRefTable;", "refs", "[Ljava/lang/Object;");
  public static final VM_Field JNIRefsField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefs", "Lorg/vmmagic/unboxed/AddressArray;");
  public static final VM_Field JNIRefsTopField = getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsTop", "I");
  public static final VM_Field JNIRefsMaxField = getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsMax", "I");
  public static final VM_Field JNIRefsSavedFPField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  public static final VM_Field JNITopJavaFPField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNITopJavaFP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field JNIPendingExceptionField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  public static final VM_Field JNIExternalFunctionsField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "externalJNIFunctions", "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field the_boot_recordField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "the_boot_record", "Lorg/jikesrvm/runtime/VM_BootRecord;");
  public static final VM_Field sysVirtualProcessorYieldIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysVirtualProcessorYieldIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field externalSignalFlagField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "externalSignalFlag", "I");
  public static final VM_Field sysLongDivideIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongDivideIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongRemainderIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongRemainderIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToFloatIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongToFloatIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToDoubleIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongToDoubleIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToIntIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysFloatToIntIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToIntIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleToIntIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToLongIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysFloatToLongIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToLongIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleToLongIP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleRemainderIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleRemainderIP", "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field edgeCountersField =
      getField("Lorg/jikesrvm/compilers/baseline/VM_EdgeCounts;", "data", "[[I");

  public static final VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  public static final VM_Field inetAddressFamilyField = getField("Ljava/net/InetAddress;", "family", "I");

  public static final VM_Field socketImplAddressField =
      getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  public static final VM_Field socketImplPortField = getField("Ljava/net/SocketImpl;", "port", "I");

  //////////////////
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////

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
      osrOrganizerQueueLockField = getField("Lorg/jikesrvm/adaptive/OSR_OrganizerThread;", "queueLock", "I");
      optThreadSwitchFromOsrOptMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromEpilogue", "()V");
      yieldpointFromNativePrologueMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativePrologue", "()V");
      yieldpointFromNativeEpilogueMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativeEpilogue", "()V");
      optResolveMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_resolve", "()V");

      optNewArrayArrayMethod =
          getMethod("Lorg/jikesrvm/compilers/opt/VM_OptLinker;", "newArrayArray", "(I[II)Ljava/lang/Object;");

      sysArrayCopy = getMethod("Ljava/lang/VMSystem;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
      sysArrayCopy.setRuntimeServiceMethod(false);
    } else {
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

  public static final VM_Field classLoaderDefinedPackages =
      getField("Ljava/lang/ClassLoader;", "definedPackages", "Ljava/util/HashMap;");
}
