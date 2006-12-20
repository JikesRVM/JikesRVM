/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2005
 */
//$Id$
package com.ibm.jikesrvm;

import com.ibm.jikesrvm.classloader.*;

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code or C runtime code.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_Entrypoints implements VM_Constants {

  public static final VM_NormalMethod bootMethod               =
    getMethod("Lcom/ibm/jikesrvm/VM;", "boot", "()V");

  public static final VM_Method java_lang_reflect_Method_invokeMethod =
    getMethod("Ljava/lang/reflect/Method;", "invoke", 
              "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  public static final VM_Field magicObjectRemapperField = getField("Lcom/ibm/jikesrvm/VM_Magic;", "objectAddressRemapper","Lcom/ibm/jikesrvm/VM_ObjectAddressRemapper;");
 
  public static final VM_NormalMethod instanceOfMethod         = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfResolvedClassMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfFinalMethod    = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)Z");
  public static final VM_NormalMethod checkcastMethod          = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastResolvedClassMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastFinalMethod     = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "checkcastFinal", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod checkstoreMethod         = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_NormalMethod athrowMethod             = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final VM_NormalMethod resolvedNewScalarMethod  = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "resolvedNewScalar", "(I[Ljava/lang/Object;ZIIII)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewScalarMethod= getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewArrayMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final VM_NormalMethod resolvedNewArrayMethod   = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "resolvedNewArray", "(III[Ljava/lang/Object;IIII)Ljava/lang/Object;");
  public static final VM_NormalMethod newArrayArrayMethod   = getMethod("Lcom/ibm/jikesrvm/VM_MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  public static final VM_Field gcLockField            = getField("Ljava/lang/VMRuntime;", "gcLock", "I");

  public static final VM_Field sysWriteLockField     = getField("Lcom/ibm/jikesrvm/VM;", "sysWriteLock", "I");  
  public static final VM_Field intBufferLockField    = getField("Lcom/ibm/jikesrvm/VM_Thread;", "intBufferLock", "I");  
  public static final VM_Field dumpBufferLockField   = getField("Lcom/ibm/jikesrvm/VM_Thread;", "dumpBufferLock", "I");  

  public static final VM_NormalMethod unimplementedBytecodeMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "unimplementedBytecode", "(I)V");
  public static final VM_NormalMethod unexpectedAbstractMethodCallMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  public static final VM_NormalMethod raiseNullPointerException= getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "raiseNullPointerException", "()V");
  public static final VM_NormalMethod raiseArrayBoundsException= getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_NormalMethod raiseArithmeticException = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "raiseArithmeticException", "()V");
  public static final VM_NormalMethod raiseAbstractMethodError = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "raiseAbstractMethodError", "()V");
  public static final VM_NormalMethod raiseIllegalAccessError  = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "raiseIllegalAccessError", "()V");
  public static final VM_NormalMethod deliverHardwareExceptionMethod = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "deliverHardwareException", "(II)V");
  public static final VM_NormalMethod unlockAndThrowMethod      = getMethod("Lcom/ibm/jikesrvm/VM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_NormalMethod invokeInterfaceMethod                          = getMethod("Lcom/ibm/jikesrvm/classloader/VM_InterfaceInvocation;", "invokeInterface", "(Ljava/lang/Object;I)Lcom/ibm/jikesrvm/VM_CodeArray;");
  public static final VM_NormalMethod findItableMethod                               = getMethod("Lcom/ibm/jikesrvm/classloader/VM_InterfaceInvocation;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  public static final VM_NormalMethod invokeinterfaceImplementsTestMethod            = getMethod("Lcom/ibm/jikesrvm/classloader/VM_InterfaceInvocation;", "invokeinterfaceImplementsTest", "(Lcom/ibm/jikesrvm/classloader/VM_Class;[Ljava/lang/Object;)V");
  public static final VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod  = getMethod("Lcom/ibm/jikesrvm/classloader/VM_InterfaceInvocation;", "unresolvedInvokeinterfaceImplementsTest", "(I[Ljava/lang/Object;)V");

  public static final VM_NormalMethod lockMethod          = getMethod("Lcom/ibm/jikesrvm/VM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_NormalMethod unlockMethod        = getMethod("Lcom/ibm/jikesrvm/VM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_NormalMethod inlineLockMethod    = getMethod("Lcom/ibm/jikesrvm/VM_ThinLock;", "inlineLock", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final VM_NormalMethod inlineUnlockMethod  = getMethod("Lcom/ibm/jikesrvm/VM_ThinLock;", "inlineUnlock", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final VM_NormalMethod lazyMethodInvokerMethod         = getMethod("Lcom/ibm/jikesrvm/VM_DynamicLinker;", "lazyMethodInvoker", "()V");
  public static final VM_NormalMethod unimplementedNativeMethodMethod = getMethod("Lcom/ibm/jikesrvm/VM_DynamicLinker;", "unimplementedNativeMethod", "()V");

  public static final VM_NormalMethod resolveMemberMethod     = getMethod("Lcom/ibm/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "resolveMember", "(I)I");
  public static final VM_Field  memberOffsetsField      = getField("Lcom/ibm/jikesrvm/classloader/VM_TableBasedDynamicLinker;", "memberOffsets", "[I");   

  public static final VM_Field longOneField        = getField("Lcom/ibm/jikesrvm/VM_Math;", "longOne", "J");  // 1L
  public static final VM_Field minusOneField       = getField("Lcom/ibm/jikesrvm/VM_Math;", "minusOne", "F"); // -1.0F
  public static final VM_Field zeroFloatField      = getField("Lcom/ibm/jikesrvm/VM_Math;", "zero", "F");     // 0.0F
  public static final VM_Field halfFloatField      = getField("Lcom/ibm/jikesrvm/VM_Math;", "half", "F");     // 0.5F
  public static final VM_Field oneFloatField       = getField("Lcom/ibm/jikesrvm/VM_Math;", "one", "F");      // 1.0F
  public static final VM_Field twoFloatField       = getField("Lcom/ibm/jikesrvm/VM_Math;", "two", "F");      // 2.0F
  public static final VM_Field two32Field          = getField("Lcom/ibm/jikesrvm/VM_Math;", "two32", "F");    // 2.0F^32
  public static final VM_Field half32Field         = getField("Lcom/ibm/jikesrvm/VM_Math;", "half32", "F");   // 0.5F^32
  public static final VM_Field billionthField      = getField("Lcom/ibm/jikesrvm/VM_Math;", "billionth", "D");// 1e-9
  public static final VM_Field zeroDoubleField     = getField("Lcom/ibm/jikesrvm/VM_Math;", "zeroD", "D");    // 0.0
  public static final VM_Field oneDoubleField      = getField("Lcom/ibm/jikesrvm/VM_Math;", "oneD", "D");     // 1.0
  public static final VM_Field maxintField         = getField("Lcom/ibm/jikesrvm/VM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  public static final VM_Field minintField         = getField("Lcom/ibm/jikesrvm/VM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  public static final VM_Field IEEEmagicField      = getField("Lcom/ibm/jikesrvm/VM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  public static final VM_Field I2DconstantField    = getField("Lcom/ibm/jikesrvm/VM_Math;", "I2Dconstant", "D");//  special double value for use in int <--> double conversions
  public static final VM_Field FPUControlWordField = (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_MachineSpecificIA;", "FPUControlWord", "I") : null;
   
  public static final VM_Field reflectiveMethodInvokerInstructionsField       = getField("Lcom/ibm/jikesrvm/VM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", "Lcom/ibm/jikesrvm/VM_CodeArray;");
  public static final VM_Field saveThreadStateInstructionsField               = getField("Lcom/ibm/jikesrvm/VM_OutOfLineMachineCode;", "saveThreadStateInstructions", "Lcom/ibm/jikesrvm/VM_CodeArray;");
  public static final VM_Field threadSwitchInstructionsField                  = getField("Lcom/ibm/jikesrvm/VM_OutOfLineMachineCode;", "threadSwitchInstructions", "Lcom/ibm/jikesrvm/VM_CodeArray;");
  public static final VM_Field restoreHardwareExceptionStateInstructionsField = getField("Lcom/ibm/jikesrvm/VM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", "Lcom/ibm/jikesrvm/VM_CodeArray;");
  public static final VM_Field invokeNativeFunctionInstructionsField          = getField("Lcom/ibm/jikesrvm/VM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", "Lcom/ibm/jikesrvm/VM_CodeArray;");

  public static final VM_Field scratchStorageField        = getField("Lcom/ibm/jikesrvm/VM_Processor;", "scratchStorage", "D");
  public static final VM_Field timeSliceExpiredField      = getField("Lcom/ibm/jikesrvm/VM_Processor;", "timeSliceExpired", "I");
  public static final VM_Field takeYieldpointField        = getField("Lcom/ibm/jikesrvm/VM_Processor;", "takeYieldpoint", "I");
  public static final VM_Field activeThreadField          = getField("Lcom/ibm/jikesrvm/VM_Processor;", "activeThread", "Lcom/ibm/jikesrvm/VM_Thread;");
  public static final VM_Field activeThreadStackLimitField= getField("Lcom/ibm/jikesrvm/VM_Processor;", "activeThreadStackLimit", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field pthreadIDField             = getField("Lcom/ibm/jikesrvm/VM_Processor;", "pthread_id", "I");
  public static final VM_Field timerTicksField            = getField("Lcom/ibm/jikesrvm/VM_Processor;", "timerTicks", "I");
  public static final VM_Field reportedTimerTicksField    = getField("Lcom/ibm/jikesrvm/VM_Processor;", "reportedTimerTicks", "I");
  public static final VM_Field vpStatusField              = getField("Lcom/ibm/jikesrvm/VM_Processor;", "vpStatus", "I");
  public static final VM_Field threadIdField              = getField("Lcom/ibm/jikesrvm/VM_Processor;", "threadId", "I");
  public static final VM_Field jtocField               = (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_Processor;", "jtoc", "Ljava/lang/Object;") : null;
  public static final VM_Field framePointerField       = (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_Processor;", "framePointer", "Lorg/vmmagic/unboxed/Address;") : null;
  public static final VM_Field hiddenSignatureIdField  = (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_Processor;", "hiddenSignatureId", "I") : null;
  public static final VM_Field arrayIndexTrapParamField= (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_Processor;", "arrayIndexTrapParam", "I") : null;
   
  public static final VM_Field referenceReferentField = getField("Ljava/lang/ref/Reference;", "referent", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field referenceNextAsAddressField = getField("Ljava/lang/ref/Reference;", "nextAsAddress", "Lorg/vmmagic/unboxed/Address;");

  /** Used in deciding which stack frames we can elide when printing. */
  public static final VM_NormalMethod mainThreadRunMethod = getMethod("Lcom/ibm/jikesrvm/MainThread;", "run", "()V");

  public static final VM_NormalMethod yieldpointFromPrologueMethod = getMethod("Lcom/ibm/jikesrvm/VM_Thread;", "yieldpointFromPrologue", "()V");
  public static final VM_NormalMethod yieldpointFromBackedgeMethod = getMethod("Lcom/ibm/jikesrvm/VM_Thread;", "yieldpointFromBackedge", "()V");
  public static final VM_NormalMethod yieldpointFromEpilogueMethod = getMethod("Lcom/ibm/jikesrvm/VM_Thread;", "yieldpointFromEpilogue", "()V");

  public static final VM_NormalMethod threadRunMethod           = getMethod("Lcom/ibm/jikesrvm/VM_Thread;", "run", "()V");
  public static final VM_NormalMethod threadStartoffMethod           = getMethod("Lcom/ibm/jikesrvm/VM_Thread;", "startoff", "()V");
  public static final VM_Field threadStackField                = getField("Lcom/ibm/jikesrvm/VM_Thread;", "stack", "[B");
  public static final VM_Field stackLimitField                 = getField("Lcom/ibm/jikesrvm/VM_Thread;", "stackLimit", "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field beingDispatchedField            = getField("Lcom/ibm/jikesrvm/VM_Thread;", "beingDispatched", "Z");
  public static final VM_Field threadSlotField                 = getField("Lcom/ibm/jikesrvm/VM_Thread;", "threadSlot", "I");
  public static final VM_Field jniEnvField                     = getField("Lcom/ibm/jikesrvm/VM_Thread;", "jniEnv", "Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;");
  public static final VM_Field threadContextRegistersField     = getField("Lcom/ibm/jikesrvm/VM_Thread;", "contextRegisters", "Lcom/ibm/jikesrvm/VM_Registers;");
  public static final VM_Field threadHardwareExceptionRegistersField = getField("Lcom/ibm/jikesrvm/VM_Thread;", "hardwareExceptionRegisters", "Lcom/ibm/jikesrvm/VM_Registers;");

  public static final VM_Field tracePrevAddressField = getField("Lcom/ibm/jikesrvm/VM_MiscHeader;", "prevAddress", "Lorg/vmmagic/unboxed/Word;");
  public static final VM_Field traceOIDField = getField("Lcom/ibm/jikesrvm/VM_MiscHeader;", "oid", "Lorg/vmmagic/unboxed/Word;");
  public static final VM_Field dispenserField = getField("Lcom/ibm/jikesrvm/mm/mmtk/Lock;", "dispenser","I");
  public static final VM_Field servingField = getField("Lcom/ibm/jikesrvm/mm/mmtk/Lock;", "serving","I");
  public static final VM_Field lockThreadField = getField("Lcom/ibm/jikesrvm/mm/mmtk/Lock;", "thread","Lcom/ibm/jikesrvm/VM_Thread;");
  public static final VM_Field lockStartField = getField("Lcom/ibm/jikesrvm/mm/mmtk/Lock;", "start","J");
  public static final VM_Field gcStatusField = getField("Lorg/mmtk/plan/Plan;", "gcStatus","I");
  public static final VM_Field tailField = getField("Lorg/mmtk/utility/deque/LocalSSB;", "tail","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQCFField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "completionFlag","I");
  public static final VM_Field SQNCField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumers","I");
  public static final VM_Field SQNCWField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numConsumersWaiting","I");
  public static final VM_Field SQheadField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "head","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQtailField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "tail","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field LQheadField = getField("Lorg/mmtk/utility/deque/LocalQueue;", "head","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQBEField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "bufsenqueued","I");
  public static final VM_Field synchronizedCounterField = getField("Lcom/ibm/jikesrvm/mm/mmtk/SynchronizedCounter;", "count", "I");
  public static final VM_NormalMethod arrayStoreWriteBarrierMethod = getMethod("Lcom/ibm/jikesrvm/memorymanagers/mminterface/MM_Interface;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_NormalMethod putfieldWriteBarrierMethod = getMethod("Lcom/ibm/jikesrvm/memorymanagers/mminterface/MM_Interface;", "putfieldWriteBarrier", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  public static final VM_NormalMethod putstaticWriteBarrierMethod = getMethod("Lcom/ibm/jikesrvm/memorymanagers/mminterface/MM_Interface;", "putstaticWriteBarrier", "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;)V");
  public static final VM_NormalMethod modifyCheckMethod = getMethod("Lcom/ibm/jikesrvm/memorymanagers/mminterface/MM_Interface;", "modifyCheck", "(Ljava/lang/Object;)V");

  public static final VM_Field registersIPField   = getField("Lcom/ibm/jikesrvm/VM_Registers;",   "ip",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field registersFPRsField = getField("Lcom/ibm/jikesrvm/VM_Registers;", "fprs", "[D");
  public static final VM_Field registersGPRsField = getField("Lcom/ibm/jikesrvm/VM_Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  public static final VM_Field registersInUseField= getField("Lcom/ibm/jikesrvm/VM_Registers;", "inuse", "Z");
  public static final VM_Field registersLRField   = (VM.BuildForPowerPC) ? getField("Lcom/ibm/jikesrvm/VM_Registers;", "lr", "Lorg/vmmagic/unboxed/Address;") : null;
  static final VM_Field toSyncProcessorsField     = (VM.BuildForPowerPC) ? getField("Lcom/ibm/jikesrvm/VM_Scheduler;", "toSyncProcessors", "I") : null;
  public static final VM_Field registersFPField   = (VM.BuildForIA32) ? getField("Lcom/ibm/jikesrvm/VM_Registers;",   "fp",  "Lorg/vmmagic/unboxed/Address;") : null;

  static final VM_Field outputLockField                = getField("Lcom/ibm/jikesrvm/VM_Scheduler;", "outputLock", "I");

  public static final VM_Field processorsField                = getField("Lcom/ibm/jikesrvm/VM_Scheduler;", "processors", "[Lcom/ibm/jikesrvm/VM_Processor;");
  public static final VM_Field debugRequestedField            = getField("Lcom/ibm/jikesrvm/VM_Scheduler;", "debugRequested", "Z");
  public static final VM_NormalMethod dumpStackAndDieMethod         = getMethod("Lcom/ibm/jikesrvm/VM_Scheduler;", "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final VM_Field latestContenderField            = getField("Lcom/ibm/jikesrvm/VM_ProcessorLock;", "latestContender", "Lcom/ibm/jikesrvm/VM_Processor;");

  public static final VM_Field classForTypeField              = getField("Lcom/ibm/jikesrvm/classloader/VM_Type;", "classForType", "Ljava/lang/Class;");
  public static final VM_Field depthField                     = getField("Lcom/ibm/jikesrvm/classloader/VM_Type;", "depth", "I");
  public static final VM_Field idField                        = getField("Lcom/ibm/jikesrvm/classloader/VM_Type;", "id", "I");
  public static final VM_Field dimensionField                 = getField("Lcom/ibm/jikesrvm/classloader/VM_Type;", "dimension", "I");

  public static final VM_Field innermostElementTypeField      = getField("Lcom/ibm/jikesrvm/classloader/VM_Array;", "innermostElementType", "Lcom/ibm/jikesrvm/classloader/VM_Type;");

  public static final VM_Field JNIEnvSavedPRField         = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "savedPRreg", "Lcom/ibm/jikesrvm/VM_Processor;");
  public static final VM_Field JNIRefsField               = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefs", "Lorg/vmmagic/unboxed/AddressArray;");
  public static final VM_Field JNIRefsTopField            = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsTop", "I");
  public static final VM_Field JNIRefsMaxField            = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsMax", "I");
  public static final VM_Field JNIRefsSavedFPField        = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  public static final VM_Field JNITopJavaFPField          = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "JNITopJavaFP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field JNIPendingExceptionField   = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  public static final VM_Field JNIExternalFunctionsField  = getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "externalJNIFunctions",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field JNIEnvSavedJTOCField       = (VM.BuildForPowerPC) ? getField("Lcom/ibm/jikesrvm/jni/VM_JNIEnvironment;", "savedJTOC", "Lorg/vmmagic/unboxed/Address;") : null;

  public static final VM_Field the_boot_recordField            = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "the_boot_record", "Lcom/ibm/jikesrvm/VM_BootRecord;");
  public static final VM_Field sysVirtualProcessorYieldIPField = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysVirtualProcessorYieldIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field externalSignalFlagField         = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "externalSignalFlag", "I");
  public static final VM_Field sysLongDivideIPField            = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysLongDivideIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongRemainderIPField         = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysLongRemainderIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToFloatIPField           = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysLongToFloatIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToDoubleIPField          = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysLongToDoubleIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToIntIPField            = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysFloatToIntIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToIntIPField           = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysDoubleToIntIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToLongIPField           = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysFloatToLongIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToLongIPField          = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysDoubleToLongIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleRemainderIPField       = getField("Lcom/ibm/jikesrvm/VM_BootRecord;", "sysDoubleRemainderIP",  "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field edgeCountersField               = getField("Lcom/ibm/jikesrvm/VM_EdgeCounts;", "data", "[[I");

  public static final VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  public static final VM_Field inetAddressFamilyField  = getField("Ljava/net/InetAddress;", "family", "I");
  
  public static final VM_Field socketImplAddressField  = getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  public static final VM_Field socketImplPortField     = getField("Ljava/net/SocketImpl;", "port", "I");


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
      specializedMethodsField = getField("Lcom/ibm/jikesrvm/opt/OPT_SpecializedMethodPool;", "specializedMethods", "[Lcom/ibm/jikesrvm/VM_CodeArray;");
      osrOrganizerQueueLockField = getField("Lcom/ibm/jikesrvm/adaptive/OSR_OrganizerThread;", "queueLock", "I");
      optThreadSwitchFromOsrOptMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromEpilogue", "()V");
      yieldpointFromNativePrologueMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativePrologue", "()V");
      yieldpointFromNativeEpilogueMethod = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativeEpilogue", "()V");
      optResolveMethod  = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptSaveVolatile;", "OPT_resolve", "()V");

      optNewArrayArrayMethod            = getMethod("Lcom/ibm/jikesrvm/opt/VM_OptLinker;", "newArrayArray", "(I[II)Ljava/lang/Object;");

      sysArrayCopy = getMethod("Ljava/lang/VMSystem;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
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
  };

  public static final VM_NormalMethod osrGetRefAtMethod;
  public static final VM_NormalMethod osrCleanRefsMethod;

  static {
    if (VM.BuildForAdaptiveSystem) {
      osrGetRefAtMethod = getMethod("Lcom/ibm/jikesrvm/osr/OSR_ObjectHolder;", "getRefAt", "(II)Ljava/lang/Object;");
      osrCleanRefsMethod = getMethod("Lcom/ibm/jikesrvm/osr/OSR_ObjectHolder;", "cleanRefs", "(I)V");
    } else {
      osrGetRefAtMethod = null;
      osrCleanRefsMethod = null;
    }
  };      
  
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
      methodListenerNumSamplesField     = getField("Lcom/ibm/jikesrvm/adaptive/VM_MethodListener;", "numSamples", "I");
      edgeListenerUpdateCalledField     = getField("Lcom/ibm/jikesrvm/adaptive/VM_EdgeListener;", "updateCalled", "I");
      edgeListenerSamplesTakenField     = getField("Lcom/ibm/jikesrvm/adaptive/VM_EdgeListener;", "samplesTaken", "I");
      yieldCountListenerNumYieldsField= getField("Lcom/ibm/jikesrvm/adaptive/VM_YieldCounterListener;", "numYields", "I");

      counterArrayManagerCounterArraysField = getField("Lcom/ibm/jikesrvm/adaptive/VM_CounterArrayManager;","counterArrays","[[D");

      invocationCountsField = getField("Lcom/ibm/jikesrvm/adaptive/VM_InvocationCounts;", "counts", "[I");
      invocationCounterTrippedMethod   = getMethod("Lcom/ibm/jikesrvm/adaptive/VM_InvocationCounts;", "counterTripped", "(I)V");

      globalCBSField =  getField("Lcom/ibm/jikesrvm/adaptive/VM_CounterBasedSampling;", "globalCounter", "I");
      processorCBSField = getField("Lcom/ibm/jikesrvm/VM_Processor;", "processor_cbs_counter", "I");
      cbsResetValueField   = getField("Lcom/ibm/jikesrvm/adaptive/VM_CounterBasedSampling;","resetValue", "I");
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
  };

  public static final VM_Field classLoaderDefinedPackages =
    getField("Ljava/lang/ClassLoader;", "definedPackages", "Ljava/util/HashMap;");

  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lcom/ibm/jikesrvm/VM_Runtime;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return corresponding VM_Member object
   */
  private static VM_Member getMember(String classDescriptor, String memberName, 
                                     String memberDescriptor) {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom(classDescriptor);
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom(memberName);
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), clsDescriptor);
      VM_Class cls = (VM_Class)tRef.resolve();
      cls.resolve();

      VM_Member member;
      if ((member = cls.findDeclaredField(memName, memDescriptor)) != null)
        return member;
      if ((member = cls.findDeclaredMethod(memName, memDescriptor)) != null)
        return member;

      // The usual causes for getMember() to fail are:
      //  1. you mispelled the class name, member name, or member signature
      //  2. the class containing the specified member didn't get compiled
      //
      VM.sysWrite("VM_Entrypoints.getMember: can't find class="+classDescriptor+" member="+memberName+" desc="+memberDescriptor+"\n");
      VM._assert(NOT_REACHED);
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysWrite("VM_Entrypoints.getMember: can't resolve class=" + classDescriptor+
                  " member=" + memberName + " desc=" + memberDescriptor + "\n");
      VM._assert(NOT_REACHED);
    }
    return null; // placate jikes
  }

  private static VM_NormalMethod getMethod(String klass, String member, String descriptor) {
    VM_NormalMethod m = (VM_NormalMethod)getMember(klass, member, descriptor);
    m.setRuntimeServiceMethod(true);
    return m;
  }

  private static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field)getMember(klass, member, descriptor);
  }

  public static VM_Field getSysCallField(String name) {
    return (VM_Field)getMember("Lcom/ibm/jikesrvm/VM_BootRecord;", 
                               name+"IP", 
                               "Lorg/vmmagic/unboxed/Address;");
  }

}
