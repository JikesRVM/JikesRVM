/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code or C runtime code.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_Entrypoints implements VM_Constants {
  public static final VM_NormalMethod bootMethod            = getMethod("Lcom/ibm/JikesRVM/VM;", "boot", "()V");

  public static final VM_Field magicObjectRemapperField = getField("Lcom/ibm/JikesRVM/VM_Magic;", "objectAddressRemapper","Lcom/ibm/JikesRVM/VM_ObjectAddressRemapper;");
 
  public static final VM_NormalMethod instanceOfMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfResolvedClassMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "instanceOfResolvedClass", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod instanceOfFinalMethod    = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
  public static final VM_NormalMethod checkcastMethod          = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastResolvedClassMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkcastResolvedClass", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkcastFinalMethod     = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkcastFinal", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod checkstoreMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_NormalMethod athrowMethod             = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");

  public static final VM_Field threadLocalValueMap = 
    getField("Ljava/lang/ThreadLocal;", "valueMap", "Ljava/util/Map;");

  //-#if !RVM_WITH_OWN_JAVA_LANG_CLASS && RVM_WITH_CLASSPATH_0_10_OR_LATER && !RVM_WITH_CLASSPATH_0_11_OR_LATER
  public static final VM_Field javaLangClassProtectionDomain =
    getField("Ljava/lang/Class;", "pd", "Ljava/security/ProtectionDomain;");
  //-#endif


  // Allocation-related entry points
  //
  public static final VM_NormalMethod resolvedNewScalarMethod  = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "resolvedNewScalar", "(I[Ljava/lang/Object;ZIII)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewScalarMethod= getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unresolvedNewScalar", "(I)Ljava/lang/Object;");
  public static final VM_NormalMethod unresolvedNewArrayMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unresolvedNewArray", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod resolvedNewArrayMethod   = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "resolvedNewArray", "(III[Ljava/lang/Object;III)Ljava/lang/Object;");
  public static final VM_NormalMethod newArrayArrayMethod   = getMethod("Lcom/ibm/JikesRVM/VM_MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");


  public static final VM_Field sysWriteLockField        = getField("Lcom/ibm/JikesRVM/VM;", "sysWriteLock", "I");  

  public static final VM_NormalMethod unimplementedBytecodeMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unimplementedBytecode", "(I)V");
  public static final VM_NormalMethod unexpectedAbstractMethodCallMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  public static final VM_NormalMethod raiseNullPointerException= getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseNullPointerException", "()V");
  public static final VM_NormalMethod raiseArrayBoundsException= getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_NormalMethod raiseArithmeticException = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseArithmeticException", "()V");
  public static final VM_NormalMethod raiseAbstractMethodError = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseAbstractMethodError", "()V");
  public static final VM_NormalMethod raiseIllegalAccessError  = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseIllegalAccessError", "()V");
  public static final VM_NormalMethod deliverHardwareExceptionMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "deliverHardwareException", "(II)V");
  public static final VM_NormalMethod unlockAndThrowMethod      = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_NormalMethod invokeInterfaceMethod                          = getMethod("Lcom/ibm/JikesRVM/classloader/VM_InterfaceInvocation;", "invokeInterface", "(Ljava/lang/Object;I)Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_NormalMethod findItableMethod                               = getMethod("Lcom/ibm/JikesRVM/classloader/VM_InterfaceInvocation;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  public static final VM_NormalMethod invokeinterfaceImplementsTestMethod            = getMethod("Lcom/ibm/JikesRVM/classloader/VM_InterfaceInvocation;", "invokeinterfaceImplementsTest", "(Lcom/ibm/JikesRVM/classloader/VM_Class;[Ljava/lang/Object;)V");
  public static final VM_NormalMethod unresolvedInvokeinterfaceImplementsTestMethod  = getMethod("Lcom/ibm/JikesRVM/classloader/VM_InterfaceInvocation;", "unresolvedInvokeinterfaceImplementsTest", "(I[Ljava/lang/Object;)V");

  public static final VM_NormalMethod lockMethod          = getMethod("Lcom/ibm/JikesRVM/VM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_NormalMethod unlockMethod        = getMethod("Lcom/ibm/JikesRVM/VM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_NormalMethod inlineLockMethod    = getMethod("Lcom/ibm/JikesRVM/VM_ThinLock;", "inlineLock", "(Ljava/lang/Object;I)V");
  public static final VM_NormalMethod inlineUnlockMethod  = getMethod("Lcom/ibm/JikesRVM/VM_ThinLock;", "inlineUnlock", "(Ljava/lang/Object;I)V");

  public static final VM_NormalMethod lazyMethodInvokerMethod         = getMethod("Lcom/ibm/JikesRVM/VM_DynamicLinker;", "lazyMethodInvoker", "()V");
  public static final VM_NormalMethod unimplementedNativeMethodMethod = getMethod("Lcom/ibm/JikesRVM/VM_DynamicLinker;", "unimplementedNativeMethod", "()V");

  public static final VM_NormalMethod resolveMemberMethod     = getMethod("Lcom/ibm/JikesRVM/classloader/VM_TableBasedDynamicLinker;", "resolveMember", "(I)I");
  public static final VM_Field  memberOffsetsField      = getField("Lcom/ibm/JikesRVM/classloader/VM_TableBasedDynamicLinker;", "memberOffsets", "[I");   

  public static final VM_Field longOneField        = getField("Lcom/ibm/JikesRVM/VM_Math;", "longOne", "J");  // 1L
  public static final VM_Field minusOneField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "minusOne", "F"); // -1.0F
  public static final VM_Field zeroFloatField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "zero", "F");     // 0.0F
  public static final VM_Field halfFloatField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "half", "F");     // 0.5F
  public static final VM_Field oneFloatField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "one", "F");      // 1.0F
  public static final VM_Field twoFloatField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "two", "F");      // 2.0F
  public static final VM_Field two32Field          = getField("Lcom/ibm/JikesRVM/VM_Math;", "two32", "F");    // 2.0F^32
  public static final VM_Field half32Field         = getField("Lcom/ibm/JikesRVM/VM_Math;", "half32", "F");   // 0.5F^32
  public static final VM_Field billionthField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "billionth", "D");// 1e-9
  public static final VM_Field zeroDoubleField     = getField("Lcom/ibm/JikesRVM/VM_Math;", "zeroD", "D");    // 0.0
  public static final VM_Field oneDoubleField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "oneD", "D");     // 1.0
  public static final VM_Field maxintField         = getField("Lcom/ibm/JikesRVM/VM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  public static final VM_Field minintField         = getField("Lcom/ibm/JikesRVM/VM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  public static final VM_Field IEEEmagicField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  public static final VM_Field I2DconstantField    = getField("Lcom/ibm/JikesRVM/VM_Math;", "I2Dconstant", "D");//  special double value for use in int <--> double conversions
  //-#if RVM_FOR_IA32  
  public static final VM_Field FPUControlWordField = getField("Lcom/ibm/JikesRVM/VM_Math;", "FPUControlWord", "I");
  //-#endif
   
  public static final VM_Field reflectiveMethodInvokerInstructionsField       = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", "Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_Field saveThreadStateInstructionsField               = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "saveThreadStateInstructions", "Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_Field threadSwitchInstructionsField                  = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "threadSwitchInstructions", "Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_Field restoreHardwareExceptionStateInstructionsField = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", "Lcom/ibm/JikesRVM/VM_CodeArray;");
  public static final VM_Field invokeNativeFunctionInstructionsField          = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", "Lcom/ibm/JikesRVM/VM_CodeArray;");

  public static final VM_Field deterministicThreadSwitchCountField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "deterministicThreadSwitchCount", "I");

  public static final VM_Field scratchStorageField        = getField("Lcom/ibm/JikesRVM/VM_Processor;", "scratchStorage", "D");
  public static final VM_Field threadSwitchRequestedField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "threadSwitchRequested", "I");
  public static final VM_Field activeThreadField          = getField("Lcom/ibm/JikesRVM/VM_Processor;", "activeThread", "Lcom/ibm/JikesRVM/VM_Thread;");
  public static final VM_Field activeThreadStackLimitField= getField("Lcom/ibm/JikesRVM/VM_Processor;", "activeThreadStackLimit", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field pthreadIDField             = getField("Lcom/ibm/JikesRVM/VM_Processor;", "pthread_id", "I");
  public static final VM_Field epochField                 = getField("Lcom/ibm/JikesRVM/VM_Processor;", "epoch", "I");
  public static final VM_Field vpStatusField              = getField("Lcom/ibm/JikesRVM/VM_Processor;", "vpStatus", "I");
  public static final VM_Field threadIdField              = getField("Lcom/ibm/JikesRVM/VM_Processor;", "threadId", "I");
  //-#if RVM_FOR_IA32
  public static final VM_Field jtocField               = getField("Lcom/ibm/JikesRVM/VM_Processor;", "jtoc", "Ljava/lang/Object;");
  public static final VM_Field framePointerField       = getField("Lcom/ibm/JikesRVM/VM_Processor;", "framePointer", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field hiddenSignatureIdField  = getField("Lcom/ibm/JikesRVM/VM_Processor;", "hiddenSignatureId", "I");
  public static final VM_Field arrayIndexTrapParamField= getField("Lcom/ibm/JikesRVM/VM_Processor;", "arrayIndexTrapParam", "I");
  //-#endif
   
  public static final VM_Field referenceReferentField = getField("Ljava/lang/ref/Reference;", "referent", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field referenceNextAsAddressField = getField("Ljava/lang/ref/Reference;", "nextAsAddress", "Lorg/vmmagic/unboxed/Address;");

  /** Used in deciding which stack frames we can elide when printing. */
  public static final VM_NormalMethod mainThreadRunMethod = 
    getMethod("Lcom/ibm/JikesRVM/MainThread;", "run", "()V");

  //-#if RVM_WITH_OSR
  public static final VM_NormalMethod threadSwitchFromOsrBaseMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromOsrBase", "()V");
  //-#endif
  public static final VM_NormalMethod threadSwitchFromPrologueMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromPrologue", "()V");
  public static final VM_NormalMethod threadSwitchFromBackedgeMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromBackedge", "()V");
  public static final VM_NormalMethod threadSwitchFromEpilogueMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromEpilogue", "()V");

  public static final VM_NormalMethod threadRunMethod           = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "run", "()V");
  public static final VM_NormalMethod threadStartoffMethod           = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "startoff", "()V");
  public static final VM_Field threadStackField                = getField("Lcom/ibm/JikesRVM/VM_Thread;", "stack", "[B");
  public static final VM_Field stackLimitField                 = getField("Lcom/ibm/JikesRVM/VM_Thread;", "stackLimit", "Lorg/vmmagic/unboxed/Address;");

  public static final VM_Field beingDispatchedField            = getField("Lcom/ibm/JikesRVM/VM_Thread;", "beingDispatched", "Z");
  public static final VM_Field threadSlotField                 = getField("Lcom/ibm/JikesRVM/VM_Thread;", "threadSlot", "I");
  public static final VM_Field jniEnvField                     = getField("Lcom/ibm/JikesRVM/VM_Thread;", "jniEnv", "Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;");
  public static final VM_Field threadContextRegistersField     = getField("Lcom/ibm/JikesRVM/VM_Thread;", "contextRegisters", "Lcom/ibm/JikesRVM/VM_Registers;");
  public static final VM_Field threadHardwareExceptionRegistersField = getField("Lcom/ibm/JikesRVM/VM_Thread;", "hardwareExceptionRegisters", "Lcom/ibm/JikesRVM/VM_Registers;");

  public static final VM_Field tracePrevAddressField = getField("Lcom/ibm/JikesRVM/VM_MiscHeader;", "prevAddress", "I");
  public static final VM_Field traceOIDField = getField("Lcom/ibm/JikesRVM/VM_MiscHeader;", "oid", "Lorg/vmmagic/unboxed/Word;");
  public static final VM_Field dispenserField = getField("Lorg/mmtk/vm/Lock;", "dispenser","I");
  public static final VM_Field servingField = getField("Lorg/mmtk/vm/Lock;", "serving","I");
  public static final VM_Field lockThreadField = getField("Lorg/mmtk/vm/Lock;", "thread","Lcom/ibm/JikesRVM/VM_Thread;");
  public static final VM_Field lockStartField = getField("Lorg/mmtk/vm/Lock;", "start","J");
  public static final VM_Field gcStatusField = getField("Lorg/mmtk/plan/BasePlan;", "gcStatus","I");
  public static final VM_Field tailField = getField("Lorg/mmtk/utility/deque/LocalSSB;", "tail","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQCFField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "completionFlag","I");
  public static final VM_Field SQNCField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numClients","I");
  public static final VM_Field SQNCWField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "numClientsWaiting","I");
  public static final VM_Field SQheadField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "head","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQtailField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "tail","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field LQheadField = getField("Lorg/mmtk/utility/deque/LocalQueue;", "head","Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field SQBEField = getField("Lorg/mmtk/utility/deque/SharedDeque;", "bufsenqueued","I");
  public static final VM_Field synchronizedCounterField = getField("Lorg/mmtk/vm/SynchronizedCounter;", "count", "I");
  public static final VM_NormalMethod arrayStoreWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers.mmInterface/MM_Interface;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_NormalMethod putfieldWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers.mmInterface/MM_Interface;", "putfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;I)V");
  public static final VM_NormalMethod putstaticWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers.mmInterface/MM_Interface;", "putstaticWriteBarrier", "(ILjava/lang/Object;)V");
  public static final VM_NormalMethod modifyCheckMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers.mmInterface/MM_Interface;", "modifyCheck", "(Ljava/lang/Object;)V");

  public static final VM_Field registersIPField   = getField("Lcom/ibm/JikesRVM/VM_Registers;",   "ip",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field registersFPRsField = getField("Lcom/ibm/JikesRVM/VM_Registers;", "fprs", "[D");
  public static final VM_Field registersGPRsField = getField("Lcom/ibm/JikesRVM/VM_Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  public static final VM_Field registersInUseField= getField("Lcom/ibm/JikesRVM/VM_Registers;", "inuse", "Z");
  //-#if RVM_FOR_POWERPC
  public static final VM_Field registersLRField   = getField("Lcom/ibm/JikesRVM/VM_Registers;", "lr", "Lorg/vmmagic/unboxed/Address;");
  static final VM_Field toSyncProcessorsField          = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "toSyncProcessors", "I");
  //-#endif
  //-#if RVM_FOR_IA32
  public static final VM_Field registersFPField   = getField("Lcom/ibm/JikesRVM/VM_Registers;",   "fp",  "Lorg/vmmagic/unboxed/Address;");
  //-#endif

  static final VM_Field outputLockField                = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "outputLock", "I");

  public static final VM_Field processorsField                = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "processors", "[Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Field debugRequestedField            = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "debugRequested", "Z");
  public static final VM_NormalMethod dumpStackAndDieMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Scheduler;", "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final VM_Field latestContenderField            = getField("Lcom/ibm/JikesRVM/VM_ProcessorLock;", "latestContender", "Lcom/ibm/JikesRVM/VM_Processor;");

  public static final VM_Field classForTypeField              = getField("Lcom/ibm/JikesRVM/classloader/VM_Type;", "classForType", "Ljava/lang/Class;");
  public static final VM_Field depthField                     = getField("Lcom/ibm/JikesRVM/classloader/VM_Type;", "depth", "I");
  public static final VM_Field idField                        = getField("Lcom/ibm/JikesRVM/classloader/VM_Type;", "id", "I");
  public static final VM_Field dimensionField                 = getField("Lcom/ibm/JikesRVM/classloader/VM_Type;", "dimension", "I");

  public static final VM_Field innermostElementTypeField      = getField("Lcom/ibm/JikesRVM/classloader/VM_Array;", "innermostElementType", "Lcom/ibm/JikesRVM/classloader/VM_Type;");

  public static final VM_Field JNIEnvSavedPRField         = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "savedPRreg", "Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Field JNIRefsField               = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "JNIRefs", "Lorg/vmmagic/unboxed/AddressArray;");
  public static final VM_Field JNIRefsTopField            = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "JNIRefsTop", "I");
  public static final VM_Field JNIRefsMaxField            = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "JNIRefsMax", "I");
  public static final VM_Field JNIRefsSavedFPField        = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  public static final VM_Field JNITopJavaFPField          = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "JNITopJavaFP", "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field JNIPendingExceptionField   = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  public static final VM_Field JNIExternalFunctionsField  = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "externalJNIFunctions",  "Lorg/vmmagic/unboxed/Address;");
  //-#if RVM_FOR_POWERPC
  public static final VM_Field JNIEnvSavedJTOCField       = getField("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", "savedJTOC", "Lorg/vmmagic/unboxed/Address;");
  //-#endif

  public static final VM_Field the_boot_recordField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "the_boot_record", "Lcom/ibm/JikesRVM/VM_BootRecord;");
  public static final VM_Field sysVirtualProcessorYieldIPField = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysVirtualProcessorYieldIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field externalSignalFlagField         = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "externalSignalFlag", "I");
  public static final VM_Field sysLongDivideIPField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongDivideIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongRemainderIPField         = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongRemainderIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToFloatIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongToFloatIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysLongToDoubleIPField          = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongToDoubleIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToIntIPField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysFloatToIntIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToIntIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleToIntIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysFloatToLongIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysFloatToLongIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysDoubleToLongIPField          = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleToLongIP",  "Lorg/vmmagic/unboxed/Address;");
  //-#if RVM_FOR_POWERPC
  public static final VM_Field sysDoubleRemainderIPField       = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleRemainderIP",  "Lorg/vmmagic/unboxed/Address;");
  public static final VM_Field sysTOCField                     = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysTOC",  "Lorg/vmmagic/unboxed/Address;");
  //-#endif

  public static final VM_Field edgeCountersField               = getField("Lcom/ibm/JikesRVM/VM_EdgeCounts;", "data", "[[I");

  public static final VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  public static final VM_Field inetAddressFamilyField  = getField("Ljava/net/InetAddress;", "family", "I");
  
  public static final VM_Field socketImplAddressField  = getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  public static final VM_Field socketImplPortField     = getField("Ljava/net/SocketImpl;", "port", "I");


  //-#if RVM_WITH_OPT_COMPILER
  ////////////////// 
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  public static final VM_Field specializedMethodsField = getField("Lcom/ibm/JikesRVM/opt/OPT_SpecializedMethodPool;", "specializedMethods", "[Lcom/ibm/JikesRVM/VM_CodeArray;");

//-#if RVM_WITH_OSR
  public static final VM_Field osrOrganizerQueueLockField = getField("Lcom/ibm/JikesRVM/adaptive/OSR_OrganizerThread;", "queueLock", "I");
  public static final VM_NormalMethod optThreadSwitchFromOsrOptMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromOsrOpt", "()V");
//-#endif
  public static final VM_NormalMethod optThreadSwitchFromPrologueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromPrologue", "()V");
  public static final VM_NormalMethod optThreadSwitchFromBackedgeMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromBackedge", "()V");
  public static final VM_NormalMethod optThreadSwitchFromEpilogueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromEpilogue", "()V");
  public static final VM_NormalMethod threadSwitchFromNativePrologueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromNativePrologue", "()V");
  public static final VM_NormalMethod threadSwitchFromNativeEpilogueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromNativeEpilogue", "()V");
  public static final VM_NormalMethod optResolveMethod                  = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_resolve", "()V");

  public static final VM_NormalMethod optNewArrayArrayMethod            = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptLinker;", "newArrayArray", "(I[II)Ljava/lang/Object;");

  public static final VM_NormalMethod sysArrayCopy = getMethod("Ljava/lang/VMSystem;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
  //-#endif

  //-#if RVM_WITH_OSR
  public static final VM_NormalMethod osrGetRefAtMethod = getMethod("Lcom/ibm/JikesRVM/OSR/OSR_ObjectHolder;", "getRefAt", "(II)Ljava/lang/Object;");
  public static final VM_NormalMethod osrCleanRefsMethod = getMethod("Lcom/ibm/JikesRVM/OSR/OSR_ObjectHolder;", "cleanRefs", "(I)V");
  //-#endif

  //-#if RVM_WITH_ADAPTIVE_SYSTEM
  ////////////////// 
  // Entrypoints that are valid only when the adaptive optimization system is included in the build
  //////////////////
  public static final VM_Field methodListenerNumSamplesField     = getField("Lcom/ibm/JikesRVM/adaptive/VM_MethodListener;", "numSamples", "I");

  public static final VM_Field edgeListenerUpdateCalledField     = getField("Lcom/ibm/JikesRVM/adaptive/VM_EdgeListener;", "updateCalled", "I");
  public static final VM_Field edgeListenerSamplesTakenField     = getField("Lcom/ibm/JikesRVM/adaptive/VM_EdgeListener;", "samplesTaken", "I");

  public static final VM_Field yieldCountListenerNumYieldsField= getField("Lcom/ibm/JikesRVM/adaptive/VM_YieldCounterListener;", "numYields", "I");
  
  public static final VM_Field counterArrayManagerCounterArraysField = getField("Lcom/ibm/JikesRVM/adaptive/VM_CounterArrayManager;","counterArrays","[[D");

  public static final VM_Field invocationCountsField             = getField("Lcom/ibm/JikesRVM/adaptive/VM_InvocationCounts;", "counts", "[I");
  public static final VM_NormalMethod invocationCounterTrippedMethod   = getMethod("Lcom/ibm/JikesRVM/adaptive/VM_InvocationCounts;", "counterTripped", "(I)V");

  // Counter-based sampling fields
  public static final VM_Field globalCBSField =  getField("Lcom/ibm/JikesRVM/adaptive/VM_CounterBasedSampling;", "globalCounter", "I");
  public static final VM_Field processorCBSField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "processor_cbs_counter", "I");
  public static final VM_Field cbsResetValueField   = getField("Lcom/ibm/JikesRVM/adaptive/VM_CounterBasedSampling;","resetValue", "I");
  //-#endif

  public static final VM_Field classLoaderDefinedPackages =
    //-#if RVM_WITH_CLASSPATH_POST_0_11_CVS_HEAD
    getField("Ljava/lang/ClassLoader;", "definedPackages", "Ljava/util/HashMap;");
    //-#else
    getField("Ljava/lang/ClassLoader;", "definedPackages", "Ljava/util/Map;");
    //-#endif

  static {

    // Don't mark the following as runtime serivce methods;
    // they are included in VM_Entrypoints for other reasons
    // than being called under-the-covers from generated code.
    //-#if RVM_WITH_OPT_COMPILER
    sysArrayCopy.setRuntimeServiceMethod(false);
    //-#endif
  };

  
  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lcom/ibm/JikesRVM/VM_Runtime;"
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
      VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), clsDescriptor);
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
    return (VM_Field)getMember("Lcom/ibm/JikesRVM/VM_BootRecord;", 
                               name+"IP", 
                               "Lorg/vmmagic/unboxed/Address;");
  }

}
