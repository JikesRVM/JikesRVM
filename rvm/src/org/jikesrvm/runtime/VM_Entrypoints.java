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

import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getField;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getMethod;

/**
 * Fields and methods of the virtual machine that are needed by
 * compiler-generated machine code or C runtime code.
 */
public interface VM_Entrypoints {

  VM_NormalMethod bootMethod = getMethod("Lorg/jikesrvm/VM;", "boot", "()V");

  VM_Method java_lang_reflect_Method_invokeMethod =
      getMethod("Ljava/lang/reflect/Method;", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  VM_Field magicObjectRemapperField =
      getField("Lorg/jikesrvm/runtime/VM_Magic;",
               "objectAddressRemapper",
               "Lorg/jikesrvm/runtime/VM_ObjectAddressRemapper;");

  VM_NormalMethod instanceOfMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  VM_NormalMethod instanceOfResolvedClassMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  VM_NormalMethod instanceOfFinalMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "instanceOfFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)Z");
  VM_NormalMethod checkcastMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  VM_NormalMethod checkcastResolvedClassMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  VM_NormalMethod checkcastFinalMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "checkcastFinal",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  VM_NormalMethod checkstoreMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  VM_NormalMethod athrowMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  VM_NormalMethod resolvedNewScalarMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "resolvedNewScalar",
                "(I[Ljava/lang/Object;ZIIII)Ljava/lang/Object;");
  VM_NormalMethod unresolvedNewScalarMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  VM_NormalMethod unresolvedNewArrayMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unresolvedNewArray", "(III)Ljava/lang/Object;");
  VM_NormalMethod resolvedNewArrayMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;",
                "resolvedNewArray",
                "(III[Ljava/lang/Object;IIII)Ljava/lang/Object;");
  VM_Field gcLockField = getField("Ljava/lang/VMRuntime;", "gcLock", "I");

  VM_Field sysWriteLockField = getField("Lorg/jikesrvm/VM;", "sysWriteLock", "I");
  VM_Field intBufferLockField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "intBufferLock", "I");
  VM_Field dumpBufferLockField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "dumpBufferLock", "I");

  VM_NormalMethod unexpectedAbstractMethodCallMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  VM_NormalMethod raiseNullPointerException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseNullPointerException", "()V");
  VM_NormalMethod raiseArrayBoundsException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  VM_NormalMethod raiseArithmeticException =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseArithmeticException", "()V");
  VM_NormalMethod raiseAbstractMethodError =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseAbstractMethodError", "()V");
  VM_NormalMethod raiseIllegalAccessError =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "raiseIllegalAccessError", "()V");
  VM_NormalMethod deliverHardwareExceptionMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "deliverHardwareException", "(II)V");
  VM_NormalMethod unlockAndThrowMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  VM_NormalMethod invokeInterfaceMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/ArchitectureSpecific$VM_CodeArray;");
  VM_NormalMethod findItableMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "findITable",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  VM_NormalMethod invokeinterfaceImplementsTestMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "invokeinterfaceImplementsTest",
                "(Lorg/jikesrvm/classloader/VM_Class;[Ljava/lang/Object;)V");
  VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_InterfaceInvocation;",
                "unresolvedInvokeinterfaceImplementsTest",
                "(I[Ljava/lang/Object;)V");

  VM_NormalMethod lockMethod =
      getMethod("Lorg/jikesrvm/objectmodel/VM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  VM_NormalMethod unlockMethod =
      getMethod("Lorg/jikesrvm/objectmodel/VM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  VM_NormalMethod inlineLockMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_ThinLock;",
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  VM_NormalMethod inlineUnlockMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_ThinLock;",
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  VM_NormalMethod lazyMethodInvokerMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "lazyMethodInvoker", "()V");
  VM_NormalMethod unimplementedNativeMethodMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "unimplementedNativeMethod", "()V");
  VM_NormalMethod sysCallMethod =
      getMethod("Lorg/jikesrvm/runtime/VM_DynamicLinker;", "sysCallMethod", "()V");

  VM_NormalMethod resolveMemberMethod =
      getMethod("Lorg/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "resolveMember", "(I)I");
  VM_Field memberOffsetsField =
      getField("Lorg/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "memberOffsets", "[I");

  VM_Field longOneField = getField("Lorg/jikesrvm/runtime/VM_Math;", "longOne", "J");  // 1L
  VM_Field minusOneField = getField("Lorg/jikesrvm/runtime/VM_Math;", "minusOne", "F"); // -1.0F
  VM_Field zeroFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "zero", "F");     // 0.0F
  VM_Field halfFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "half", "F");     // 0.5F
  VM_Field oneFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "one", "F");      // 1.0F
  VM_Field twoFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "two", "F");      // 2.0F
  VM_Field two32Field = getField("Lorg/jikesrvm/runtime/VM_Math;", "two32", "F");    // 2.0F^32
  VM_Field half32Field = getField("Lorg/jikesrvm/runtime/VM_Math;", "half32", "F");   // 0.5F^32
  VM_Field billionthField = getField("Lorg/jikesrvm/runtime/VM_Math;", "billionth", "D");// 1e-9
  VM_Field zeroDoubleField = getField("Lorg/jikesrvm/runtime/VM_Math;", "zeroD", "D");    // 0.0
  VM_Field oneDoubleField = getField("Lorg/jikesrvm/runtime/VM_Math;", "oneD", "D");     // 1.0
  VM_Field maxintField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  VM_Field minintField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  /** largest float that can be rounded to an int */
  VM_Field maxintFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "maxintF", "F");
  VM_Field maxlongFloatField = getField("Lorg/jikesrvm/runtime/VM_Math;", "maxlongF", "F");
  VM_Field IEEEmagicField =
      getField("Lorg/jikesrvm/runtime/VM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  VM_Field I2DconstantField =
      getField("Lorg/jikesrvm/runtime/VM_Math;",
               "I2Dconstant",
               "D");//  special double value for use in int <--> double conversions

  VM_Field scratchStorageField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "scratchStorage", "D");
  VM_Field timeSliceExpiredField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "timeSliceExpired", "I");
  VM_Field takeYieldpointField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "takeYieldpoint", "I");
  VM_Field activeThreadField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "activeThread", "Lorg/jikesrvm/scheduler/VM_Thread;");
  VM_Field activeThreadStackLimitField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "activeThreadStackLimit", "Lorg/vmmagic/unboxed/Address;");
  VM_Field pthreadIDField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "pthread_id", "I");
  VM_Field timerTicksField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "timerTicks", "I");
  VM_Field reportedTimerTicksField =
      getField("Lorg/jikesrvm/scheduler/VM_Processor;", "reportedTimerTicks", "I");
  VM_Field vpStatusField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "vpStatus", "I");
  VM_Field threadIdField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "threadId", "I");

  VM_Field referenceReferentField =
      getField("Ljava/lang/ref/Reference;", "referent", "Lorg/vmmagic/unboxed/Address;");
  VM_Field referenceNextAsAddressField =
      getField("Ljava/lang/ref/Reference;", "nextAsAddress", "Lorg/vmmagic/unboxed/Address;");

  /** Used in deciding which stack frames we can elide when printing. */
  VM_NormalMethod mainThreadRunMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_MainThread;", "run", "()V");

  VM_NormalMethod yieldpointFromPrologueMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromPrologue", "()V");
  VM_NormalMethod yieldpointFromBackedgeMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromBackedge", "()V");
  VM_NormalMethod yieldpointFromEpilogueMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "yieldpointFromEpilogue", "()V");

  VM_NormalMethod threadRunMethod = getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "run", "()V");
  VM_NormalMethod threadStartoffMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Thread;", "startoff", "()V");
  VM_Field threadStackField = getField("Lorg/jikesrvm/scheduler/VM_Thread;", "stack", "[B");
  VM_Field stackLimitField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "stackLimit", "Lorg/vmmagic/unboxed/Address;");

  VM_Field beingDispatchedField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "beingDispatched", "Z");
  VM_Field threadSlotField = getField("Lorg/jikesrvm/scheduler/VM_Thread;", "threadSlot", "I");
  VM_Field jniEnvField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;", "jniEnv", "Lorg/jikesrvm/jni/VM_JNIEnvironment;");
  VM_Field threadContextRegistersField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;",
               "contextRegisters",
               "Lorg/jikesrvm/ArchitectureSpecific$VM_Registers;");
  VM_Field threadHardwareExceptionRegistersField =
      getField("Lorg/jikesrvm/scheduler/VM_Thread;",
               "hardwareExceptionRegisters",
               "Lorg/jikesrvm/ArchitectureSpecific$VM_Registers;");

  VM_Field tracePrevAddressField =
      getField("Lorg/jikesrvm/objectmodel/VM_MiscHeader;", "prevAddress", "Lorg/vmmagic/unboxed/Word;");
  VM_Field traceOIDField =
      getField("Lorg/jikesrvm/objectmodel/VM_MiscHeader;", "oid", "Lorg/vmmagic/unboxed/Word;");
  VM_Field dispenserField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "dispenser", "I");
  VM_Field servingField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "serving", "I");
  VM_Field lockThreadField =
      getField("Lorg/jikesrvm/mm/mmtk/Lock;", "thread", "Lorg/jikesrvm/scheduler/VM_Thread;");
  VM_Field lockStartField = getField("Lorg/jikesrvm/mm/mmtk/Lock;", "start", "J");
  VM_Field gcStatusField = getField("Lorg/mmtk/plan/Plan;", "gcStatus", "I");
  VM_Field tailField =
      getField("Lorg/mmtk/utility/deque/LocalSSB;", "tail", "Lorg/vmmagic/unboxed/Address;");
  VM_Field SQCFField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "completionFlag", "I");
  VM_Field SQNCField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumers", "I");
  VM_Field SQNCWField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumersWaiting", "I");
  VM_Field SQheadField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "head", "Lorg/vmmagic/unboxed/Address;");
  VM_Field SQtailField =
      getField("Lorg/mmtk/utility/deque/SharedDeque;", "tail", "Lorg/vmmagic/unboxed/Address;");
  VM_Field LQheadField =
      getField("Lorg/mmtk/utility/deque/LocalQueue;", "head", "Lorg/vmmagic/unboxed/Address;");
  VM_Field SQBEField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "bufsenqueued", "I");
  VM_Field synchronizedCounterField =
      getField("Lorg/jikesrvm/mm/mmtk/SynchronizedCounter;", "count", "I");
  VM_NormalMethod arrayStoreWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "arrayStoreWriteBarrier",
                "(Ljava/lang/Object;ILjava/lang/Object;)V");
  VM_NormalMethod putfieldWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "putfieldWriteBarrier",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  VM_NormalMethod putstaticWriteBarrierMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;",
                "putstaticWriteBarrier",
                "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;)V");
  VM_NormalMethod modifyCheckMethod =
      getMethod("Lorg/jikesrvm/memorymanagers/mminterface/MM_Interface;", "modifyCheck", "(Ljava/lang/Object;)V");

  VM_Field outputLockField = getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "outputLock", "I");

  VM_Field processorsField =
      getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "processors", "[Lorg/jikesrvm/scheduler/VM_Processor;");
  VM_Field debugRequestedField =
      getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "debugRequested", "Z");
  VM_NormalMethod dumpStackAndDieMethod =
      getMethod("Lorg/jikesrvm/scheduler/VM_Scheduler;", "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  VM_Field latestContenderField =
      getField("Lorg/jikesrvm/scheduler/VM_ProcessorLock;", "latestContender", "Lorg/jikesrvm/scheduler/VM_Processor;");

  VM_Field classForTypeField =
      getField("Lorg/jikesrvm/classloader/VM_Type;", "classForType", "Ljava/lang/Class;");
  VM_Field depthField = getField("Lorg/jikesrvm/classloader/VM_Type;", "depth", "I");
  VM_Field idField = getField("Lorg/jikesrvm/classloader/VM_Type;", "id", "I");
  VM_Field dimensionField = getField("Lorg/jikesrvm/classloader/VM_Type;", "dimension", "I");

  VM_Field innermostElementTypeField =
      getField("Lorg/jikesrvm/classloader/VM_Array;", "innermostElementType", "Lorg/jikesrvm/classloader/VM_Type;");

  VM_Field JNIEnvSavedPRField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "savedPRreg", "Lorg/jikesrvm/scheduler/VM_Processor;");
  VM_Field JNIGlobalRefsField =
      getField("Lorg/jikesrvm/jni/VM_JNIGlobalRefTable;", "refs", "[Ljava/lang/Object;");
  VM_Field JNIRefsField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefs", "Lorg/vmmagic/unboxed/AddressArray;");
  VM_Field JNIRefsTopField = getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsTop", "I");
  VM_Field JNIRefsMaxField = getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsMax", "I");
  VM_Field JNIRefsSavedFPField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  VM_Field JNITopJavaFPField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "JNITopJavaFP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field JNIPendingExceptionField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  VM_Field JNIExternalFunctionsField =
      getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;", "externalJNIFunctions", "Lorg/vmmagic/unboxed/Address;");

  VM_Field the_boot_recordField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "the_boot_record", "Lorg/jikesrvm/runtime/VM_BootRecord;");
  VM_Field sysVirtualProcessorYieldIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysVirtualProcessorYieldIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field externalSignalFlagField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "externalSignalFlag", "I");
  VM_Field sysLongDivideIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongDivideIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysLongRemainderIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongRemainderIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysLongToFloatIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongToFloatIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysLongToDoubleIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysLongToDoubleIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysFloatToIntIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysFloatToIntIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysDoubleToIntIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleToIntIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysFloatToLongIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysFloatToLongIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysDoubleToLongIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleToLongIP", "Lorg/vmmagic/unboxed/Address;");
  VM_Field sysDoubleRemainderIPField =
      getField("Lorg/jikesrvm/runtime/VM_BootRecord;", "sysDoubleRemainderIP", "Lorg/vmmagic/unboxed/Address;");

  VM_Field edgeCountersField =
      getField("Lorg/jikesrvm/compilers/baseline/VM_EdgeCounts;", "data", "[[I");

  VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  VM_Field inetAddressFamilyField = getField("Ljava/net/InetAddress;", "family", "I");

  VM_Field socketImplAddressField =
      getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  VM_Field socketImplPortField = getField("Ljava/net/SocketImpl;", "port", "I");

  VM_Field classLoaderDefinedPackages =
      getField("Ljava/lang/ClassLoader;", "definedPackages", "Ljava/util/HashMap;");
}
