/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code or C runtime code.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_Entrypoints implements VM_Constants {
  static final VM_Method debugBreakpointMethod = getMethod("LVM;", "debugBreakpoint", "()V");
  static final VM_Method bootMethod            = getMethod("LVM;", "boot", "()V");

  static final VM_Field magicObjectRemapperField = getField("LVM_Magic;", "objectAddressRemapper","LVM_ObjectAddressRemapper;");
 
  static final VM_Method instanceOfMethod         = getMethod("LVM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  static final VM_Method instanceOfFinalMethod    = getMethod("LVM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
  static final VM_Method checkcastMethod          = getMethod("LVM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  static final VM_Method checkstoreMethod         = getMethod("LVM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  static final VM_Method athrowMethod             = getMethod("LVM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");
  static final VM_Method newScalarMethod          = getMethod("LVM_Runtime;", "newScalar", "(I)Ljava/lang/Object;");
  static final VM_Method quickNewArrayMethod      = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;)Ljava/lang/Object;");
  static final VM_Method quickNewScalarMethod     = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;Z)Ljava/lang/Object;");
  static final VM_Method unimplementedBytecodeMethod = getMethod("LVM_Runtime;", "unimplementedBytecode", "(I)V");
  static final VM_Method unexpectedAbstractMethodCallMethod = getMethod("LVM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  static final VM_Method raiseNullPointerException= getMethod("LVM_Runtime;", "raiseNullPointerException", "()V");
  static final VM_Method raiseArrayBoundsException= getMethod("LVM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  static final VM_Method raiseArithmeticException = getMethod("LVM_Runtime;", "raiseArithmeticException", "()V");
  static final VM_Method raiseAbstractMethodError = getMethod("LVM_Runtime;", "raiseAbstractMethodError", "()V");
  static final VM_Method raiseIllegalAccessError  = getMethod("LVM_Runtime;", "raiseIllegalAccessError", "()V");
  static final VM_Method deliverHardwareExceptionMethod = getMethod("LVM_Runtime;", "deliverHardwareException", "(II)V");
  static final VM_Method unlockAndThrowMethod      = getMethod("LVM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  static final VM_Method invokeInterfaceMethod                          = getMethod("LVM_InterfaceInvocation;", "invokeInterface", "(Ljava/lang/Object;I)"+INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Method findItableMethod                               = getMethod("LVM_InterfaceInvocation;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  static final VM_Method invokeinterfaceImplementsTestMethod            = getMethod("LVM_InterfaceInvocation;", "invokeinterfaceImplementsTest", "(LVM_Class;[Ljava/lang/Object;)V");
  static final VM_Method unresolvedInvokeinterfaceImplementsTestMethod  = getMethod("LVM_InterfaceInvocation;", "unresolvedInvokeinterfaceImplementsTest", "(I[Ljava/lang/Object;)V");

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method allocAdviceNewScalarMethod = getMethod("LVM_Runtime;", "newScalar", "(II)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewArrayMethod = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethod = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethodNEW = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;ZI)Ljava/lang/Object;");
  //-#endif

  static final VM_Method instanceOfUnresolvedMethod         = getMethod("LVM_DynamicTypeCheck;", "instanceOfUnresolved", "(LVM_Class;[Ljava/lang/Object;)Z");
  static final VM_Method instanceOfArrayMethod              = getMethod("LVM_DynamicTypeCheck;", "instanceOfArray", "(LVM_Class;ILVM_Type;)Z");
  static final VM_Method instanceOfUnresolvedArrayMethod    = getMethod("LVM_DynamicTypeCheck;", "instanceOfUnresolvedArray", "(LVM_Class;ILVM_Type;)Z");

  static final VM_Method lockMethod          = getMethod("LVM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  static final VM_Method unlockMethod        = getMethod("LVM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  static final VM_Method inlineLockMethod    = getMethod("LVM_ThinLock;", "inlineLock", "(Ljava/lang/Object;I)V");
  static final VM_Method inlineUnlockMethod  = getMethod("LVM_ThinLock;", "inlineUnlock", "(Ljava/lang/Object;I)V");

  static final VM_Method newArrayArrayMethod   = getMethod("LVM_MultianewarrayHelper;", "newArrayArray", "(III)Ljava/lang/Object;");

  static final VM_Method lazyMethodInvokerMethod         = getMethod("LVM_DynamicLinker;", "lazyMethodInvoker", "()V");
  static final VM_Method unimplementedNativeMethodMethod = getMethod("LVM_DynamicLinker;", "unimplementedNativeMethod", "()V");

  static final VM_Method resolveMethodMethod     = getMethod("LVM_TableBasedDynamicLinker;", "resolveMethod", "(I)V");
  static final VM_Field  methodOffsetsField      = getField("LVM_TableBasedDynamicLinker;", "methodOffsets", "[I");   
  static final VM_Method resolveFieldMethod      = getMethod("LVM_TableBasedDynamicLinker;", "resolveField", "(I)V");
  static final VM_Field  fieldOffsetsField       = getField("LVM_TableBasedDynamicLinker;", "fieldOffsets", "[I");    

  static final VM_Field longOneField        = getField("LVM_Math;", "longOne", "J");  // 1L
  static final VM_Field minusOneField       = getField("LVM_Math;", "minusOne", "F"); // -1.0F
  static final VM_Field zeroFloatField      = getField("LVM_Math;", "zero", "F");     // 0.0F
  static final VM_Field halfFloatField      = getField("LVM_Math;", "half", "F");     // 0.5F
  static final VM_Field oneFloatField       = getField("LVM_Math;", "one", "F");      // 1.0F
  static final VM_Field twoFloatField       = getField("LVM_Math;", "two", "F");      // 2.0F
  static final VM_Field two32Field          = getField("LVM_Math;", "two32", "F");    // 2.0F^32
  static final VM_Field half32Field         = getField("LVM_Math;", "half32", "F");   // 0.5F^32
  static final VM_Field billionthField      = getField("LVM_Math;", "billionth", "D");// 1e-9
  static final VM_Field zeroDoubleField     = getField("LVM_Math;", "zeroD", "D");    // 0.0
  static final VM_Field oneDoubleField      = getField("LVM_Math;", "oneD", "D");     // 1.0
  static final VM_Field maxintField         = getField("LVM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  static final VM_Field minintField         = getField("LVM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  static final VM_Field IEEEmagicField      = getField("LVM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  static final VM_Field I2DconstantField    = getField("LVM_Math;", "I2Dconstant", "D");//  special double value for use in int <--> double conversions
  //-#if RVM_FOR_IA32  
  static final VM_Field FPUControlWordField = getField("LVM_Math;", "FPUControlWord", "I");
  //-#endif
   
  static final VM_Field reflectiveMethodInvokerInstructionsField       = getField("LVM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field saveThreadStateInstructionsField               = getField("LVM_OutOfLineMachineCode;", "saveThreadStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field threadSwitchInstructionsField                  = getField("LVM_OutOfLineMachineCode;", "threadSwitchInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field restoreHardwareExceptionStateInstructionsField = getField("LVM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#if RVM_FOR_POWERPC
  static final VM_Field getTimeInstructionsField                       = getField("LVM_OutOfLineMachineCode;", "getTimeInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#endif
  static final VM_Field invokeNativeFunctionInstructionsField          = getField("LVM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", INSTRUCTION_ARRAY_SIGNATURE);

  static final VM_Field deterministicThreadSwitchCountField = getField("LVM_Processor;", "deterministicThreadSwitchCount", "I");
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field modifiedOldObjectsTopField = getField("LVM_Processor;", "modifiedOldObjectsTop", "LVM_Address;");
  static final VM_Field modifiedOldObjectsMaxField = getField("LVM_Processor;", "modifiedOldObjectsMax", "LVM_Address;");
  //-#endif
  static final VM_Field scratchSecondsField        = getField("LVM_Processor;", "scratchSeconds", "D");
  static final VM_Field scratchNanosecondsField    = getField("LVM_Processor;", "scratchNanoseconds", "D");
  static final VM_Field threadSwitchRequestedField = getField("LVM_Processor;", "threadSwitchRequested", "I");
  static final VM_Field activeThreadField          = getField("LVM_Processor;", "activeThread", "LVM_Thread;");
  static final VM_Field activeThreadStackLimitField= getField("LVM_Processor;", "activeThreadStackLimit", "LVM_Address;");
  static final VM_Field pthreadIDField             = getField("LVM_Processor;", "pthread_id", "I");
  static final VM_Field epochField                 = getField("LVM_Processor;", "epoch", "I");
  static final VM_Field processorModeField         = getField("LVM_Processor;", "processorMode", "I");
  static final VM_Field vpStatusAddressField       = getField("LVM_Processor;", "vpStatusAddress", "LVM_Address;");
  //-#if RVM_FOR_IA32
  static final VM_Field processorThreadIdField     = getField("LVM_Processor;", "threadId", "I");
  static final VM_Field processorFPField           = getField("LVM_Processor;", "framePointer", "LVM_Address;");
  static final VM_Field processorJTOCField         = getField("LVM_Processor;", "jtoc", "Ljava/lang/Object;");
  static final VM_Field processorTrapParamField    = getField("LVM_Processor;", "arrayIndexTrapParam", "I");
  static final VM_Field jtocField               = getField("LVM_Processor;", "jtoc", "Ljava/lang/Object;");
  static final VM_Field threadIdField           = getField("LVM_Processor;", "threadId", "I");
  static final VM_Field framePointerField       = getField("LVM_Processor;", "framePointer", "LVM_Address;");
  static final VM_Field hiddenSignatureIdField  = getField("LVM_Processor;", "hiddenSignatureId", "I");
  static final VM_Field arrayIndexTrapParamField= getField("LVM_Processor;", "arrayIndexTrapParam", "I");
  //-#endif
   
  static final VM_Method threadSwitchFromPrologueMethod = getMethod("LVM_Thread;", "threadSwitchFromPrologue", "()V");
  static final VM_Method threadSwitchFromBackedgeMethod = getMethod("LVM_Thread;", "threadSwitchFromBackedge", "()V");
  static final VM_Method threadSwitchFromEpilogueMethod = getMethod("LVM_Thread;", "threadSwitchFromEpilogue", "()V");
  static final VM_Method threadYieldMethod              = getMethod("LVM_Thread;", "yield", "()V");
  static final VM_Method becomeNativeThreadMethod       = getMethod("LVM_Thread;", "becomeNativeThread", "()V");
  static final VM_Method becomeRVMThreadMethod          = getMethod("LVM_Thread;", "becomeRVMThread", "()V");

  static final VM_Method threadStartoffMethod           = getMethod("LVM_Thread;", "startoff", "()V");
  static final VM_Field threadStackField                = getField("LVM_Thread;", "stack", "[I");
  static final VM_Field stackLimitField                 = getField("LVM_Thread;", "stackLimit", "LVM_Address;");

  static final VM_Field beingDispatchedField            = getField("LVM_Thread;", "beingDispatched", "Z");
  static final VM_Field threadSlotField                 = getField("LVM_Thread;", "threadSlot", "I");
  static final VM_Field jniEnvField                     = getField("LVM_Thread;", "jniEnv", "LVM_JNIEnvironment;");
  static final VM_Field processorAffinityField          = getField("LVM_Thread;", "processorAffinity", "LVM_Processor;");
  static final VM_Field nativeAffinityField             = getField("LVM_Thread;", "nativeAffinity", "LVM_Processor;");
  static final VM_Field threadContextRegistersField     = getField("LVM_Thread;", "contextRegisters", "LVM_Registers;");
  static final VM_Field threadHardwareExceptionRegistersField = getField("LVM_Thread;", "hardwareExceptionRegisters", "LVM_Registers;");

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field contiguousHeapCurrentField      = getField("LVM_ContiguousHeap;", "current", "LVM_Address;");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field finalizerListElementValueField   = getField("LVM_FinalizerListElement;", "value", "LVM_Address;");
  static final VM_Field finalizerListElementPointerField = getField("LVM_FinalizerListElement;", "pointer", "Ljava/lang/Object;");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field allocCountField                = getField("LVM_BlockControl;", "allocCount", "I");
  //-#endif

  static final VM_Field registersIPField   = getField("LVM_Registers;",   "ip",  "LVM_Address;");
  static final VM_Field registersFPRsField = getField("LVM_Registers;", "fprs", "[D");
  static final VM_Field registersGPRsField = getField("LVM_Registers;", "gprs", "[I");
  static final VM_Field registersInUseField= getField("LVM_Registers;", "inuse", "Z");
  //-#if RVM_FOR_POWERPC
  static final VM_Field registersLRField   = getField("LVM_Registers;", "lr", "LVM_Address;");
  //-#endif
  //-#if RVM_FOR_IA32
  static final VM_Field registersFPField   = getField("LVM_Registers;",   "fp",  "LVM_Address;");
  //-#endif

  //-#if RVM_WITH_CONCURRENT_GC
  static final VM_Field incDecBufferTopField            = getField("LVM_Processor;", "incDecBufferTop", "LVM_Address;");
  static final VM_Field incDecBufferMaxField            = getField("LVM_Processor;", "incDecBufferMax", "LVM_Address;");
  static final VM_Method processIncDecBufferMethod      = getMethod("LVM_RCBuffers;", "processIncDecBuffer", "()V");
  static final VM_Method RCGC_aastoreMethod             = getMethod("LVM_OptRCWriteBarrier;", "aastore", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_resolvedPutfieldMethod    = getMethod("LVM_OptRCWriteBarrier;", "resolvedPutfield", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_unresolvedPutfieldMethod  = getMethod("LVM_OptRCWriteBarrier;", "unresolvedPutfield", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_resolvedPutstaticMethod   = getMethod("LVM_OptRCWriteBarrier;", "resolvedPutstatic", "(ILjava/lang/Object;)V");
  static final VM_Method RCGC_unresolvedPutstaticMethod = getMethod("LVM_OptRCWriteBarrier;", "unresolvedPutstatic", "(ILjava/lang/Object;)V");
  //-#endif

  static final VM_Field outputLockField                = getField("LVM_Scheduler;", "outputLock", "I");
  //-#if RVM_WITH_STRONG_VOLATILE_SEMANTICS
  static final VM_Field doublewordVolatileMutexField   = getField("LVM_Scheduler;", "doublewordVolatileMutex", "LVM_ProcessorLock;");
  //-#else
  static final VM_Field doublewordVolatileMutexField   = null; // GACK!
  //-#endif
  static final VM_Field processorsField                = getField("LVM_Scheduler;", "processors", "[LVM_Processor;");
  static final VM_Field threadsField                   = getField("LVM_Scheduler;", "threads", "[LVM_Thread;");
  static final VM_Field debugRequestedField            = getField("LVM_Scheduler;", "debugRequested", "Z");
  static final VM_Field attachThreadRequestedField     = getField("LVM_Scheduler;", "attachThreadRequested", "LVM_Address;");
  static final VM_Method dumpStackAndDieMethod         = getMethod("LVM_Scheduler;", "dumpStackAndDie", "(LVM_Address;)V");

  static final VM_Field latestContenderField            = getField("LVM_ProcessorLock;", "latestContender", "LVM_Processor;");
  static final VM_Method processorLockMethod            = getMethod("LVM_ProcessorLock;", "lock", "()V");
  static final VM_Method processorUnlockMethod          = getMethod("LVM_ProcessorLock;", "unlock", "()V");

  static final VM_Field classForTypeField              = getField("LVM_Type;", "classForType", "Ljava/lang/Class;");
  static final VM_Field depthField                     = getField("LVM_Type;", "depth", "I");
  static final VM_Field idField                        = getField("LVM_Type;", "dictionaryId", "I");
  static final VM_Field dimensionField                 = getField("LVM_Type;", "dimension", "I");

  static final VM_Field innermostElementTypeField      = getField("LVM_Array;", "innermostElementType", "LVM_Type;");


  //-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
  static final VM_Method initializeClassIfNecessaryMethod = getMethod("LVM_Class;", "initializeClassIfNecessary", "(I)V");
  //-#else
  static final VM_Method initializeClassIfNecessaryMethod = null; // GACK
  //-#endif

  static final VM_Field JNIEnvAddressField         = getField("LVM_JNIEnvironment;", "JNIEnvAddress", "LVM_Address;");
  static final VM_Field JNIEnvSavedTIField         = getField("LVM_JNIEnvironment;", "savedTIreg", "I");
  static final VM_Field JNIEnvSavedPRField         = getField("LVM_JNIEnvironment;", "savedPRreg", "LVM_Processor;");
  static final VM_Field JNIRefsField               = getField("LVM_JNIEnvironment;", "JNIRefs", "[I");
  static final VM_Field JNIRefsTopField            = getField("LVM_JNIEnvironment;", "JNIRefsTop", "I");
  static final VM_Field JNIRefsMaxField            = getField("LVM_JNIEnvironment;", "JNIRefsMax", "I");
  static final VM_Field JNIRefsSavedFPField        = getField("LVM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  static final VM_Field JNITopJavaFPField          = getField("LVM_JNIEnvironment;", "JNITopJavaFP", "LVM_Address;");
  static final VM_Field JNIPendingExceptionField   = getField("LVM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  static final VM_Field JNIFunctionPointersField   = getField("LVM_JNIEnvironment;", "JNIFunctionPointers", "[I");

  static final VM_Field the_boot_recordField            = getField("LVM_BootRecord;", "the_boot_record", "LVM_BootRecord;");
  static final VM_Field tiRegisterField                 = getField("LVM_BootRecord;", "tiRegister", "I");
  static final VM_Field spRegisterField                 = getField("LVM_BootRecord;", "spRegister", "I");
  static final VM_Field ipRegisterField                 = getField("LVM_BootRecord;", "ipRegister", "I");
  static final VM_Field tocRegisterField                = getField("LVM_BootRecord;", "tocRegister", "I");
  static final VM_Field processorsOffsetField           = getField("LVM_BootRecord;", "processorsOffset", "I");
  static final VM_Field threadsOffsetField              = getField("LVM_BootRecord;", "threadsOffset", "I");
  static final VM_Field globalGCInProgressFlagField     = getField("LVM_BootRecord;", "globalGCInProgressFlag", "I");
  static final VM_Field lockoutProcessorField           = getField("LVM_BootRecord;", "lockoutProcessor", "I");
  static final VM_Field sysVirtualProcessorYieldIPField = getField("LVM_BootRecord;", "sysVirtualProcessorYieldIP", "I");
  static final VM_Field externalSignalFlagField         = getField("LVM_BootRecord;", "externalSignalFlag", "I");
  static final VM_Field sysLongDivideIPField            = getField("LVM_BootRecord;", "sysLongDivideIP", "I");
  static final VM_Field sysLongRemainderIPField         = getField("LVM_BootRecord;", "sysLongRemainderIP", "I");
  static final VM_Field sysLongToFloatIPField           = getField("LVM_BootRecord;", "sysLongToFloatIP", "I");
  static final VM_Field sysLongToDoubleIPField          = getField("LVM_BootRecord;", "sysLongToDoubleIP", "I");
  static final VM_Field sysFloatToIntIPField            = getField("LVM_BootRecord;", "sysFloatToIntIP", "I");
  static final VM_Field sysDoubleToIntIPField           = getField("LVM_BootRecord;", "sysDoubleToIntIP", "I");
  static final VM_Field sysFloatToLongIPField           = getField("LVM_BootRecord;", "sysFloatToLongIP", "I");
  static final VM_Field sysDoubleToLongIPField          = getField("LVM_BootRecord;", "sysDoubleToLongIP", "I");
  //-#if RVM_FOR_POWERPC
  static final VM_Field sysTOCField                     = getField("LVM_BootRecord;", "sysTOC", "I");
  //-#endif

  static final VM_Method arrayStoreWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method unresolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
  static final VM_Method unresolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");


  static final VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  static final VM_Field inetAddressFamilyField  = getField("Ljava/net/InetAddress;", "family", "I");
  
  static final VM_Field socketImplAddressField  = getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  static final VM_Field socketImplPortField     = getField("Ljava/net/SocketImpl;", "port", "I");

  //-#if RVM_WITH_READ_BARRIER2
  static final VM_Method arrayLoadReadBarrierMethod = getMethod("LVM_ReadBarrier;", "arrayLoadReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "resolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetStaticReadBarrierMethod = getMethod("LVM_ReadBarrier;", "resolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetStaticReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  //-#endif RVM_WITH_READ_BARRIER2

  //-#if RVM_WITH_GCTk
  static ADDRESS GCTk_WriteBufferBase;
  static ADDRESS GCTk_BumpPointerBase;
  static ADDRESS GCTk_SyncPointerBase;
  static ADDRESS GCTk_ChunkAllocatorBase;
  static ADDRESS GCTk_TraceBufferBase;
  //-#endif


  //-#if RVM_WITH_OPT_COMPILER
  ////////////////// 
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  static final VM_Field specializedMethodsField = getField("LOPT_SpecializedMethodPool;", "specializedMethods", "["+INSTRUCTION_ARRAY_SIGNATURE);

  static final VM_Method optThreadSwitchFromPrologueMethod = getMethod("LVM_OptSaveVolatile;", "OPT_threadSwitchFromPrologue", "()V");
  static final VM_Method optThreadSwitchFromBackedgeMethod = getMethod("LVM_OptSaveVolatile;", "OPT_threadSwitchFromBackedge", "()V");
  static final VM_Method optThreadSwitchFromEpilogueMethod = getMethod("LVM_OptSaveVolatile;", "OPT_threadSwitchFromEpilogue", "()V");
  static final VM_Method optResolveMethod                  = getMethod("LVM_OptSaveVolatile;", "OPT_resolve", "()V");

  static final VM_Method optNewArrayArrayMethod            = getMethod("LVM_OptLinker;", "newArrayArray", "([II)Ljava/lang/Object;");
  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method optAllocAdviceNewArrayArrayMethod = getMethod("LVM_OptLinker;", "allocAdviceNewArrayArray", "([III)Ljava/lang/Object;");
  //-#endif

  static final VM_Method sysArrayCopy = getMethod("Lcom/ibm/JikesRVM/librarySupport/SystemSupport;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
  //-#endif



  //-#if RVM_WITH_ADAPTIVE_SYSTEM
  ////////////////// 
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  static final VM_Field methodListenerNextIndexField      = getField("LVM_MethodListener;", "nextIndex", "I");
  static final VM_Field methodListenerNumSamplesField     = getField("LVM_MethodListener;", "numSamples", "I");

  static final VM_Field edgeListenerNextIndexField        = getField("LVM_EdgeListener;", "nextIndex", "I");
  static final VM_Field edgeListenerSamplesTakenField     = getField("LVM_EdgeListener;", "samplesTaken", "I");
  static final VM_Field counterArrayManagerCounterArraysField = getField("LVM_CounterArrayManager;","counterArrays","[[D");
  //-#endif


  static void init() {
    //-#if RVM_WITH_GCTk
    ADDRESS top = getMember("LVM_Processor;", "writeBuffer0", "I").getOffset();
    ADDRESS bot = getMember("LVM_Processor;", "writeBuffer1", "I").getOffset();
    GCTk_WriteBufferBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean discontigious = (((top > bot) && ((top - bot) != 4))
			       || ((top < bot) && ((bot - top) != 4)));
      if (discontigious)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_WriteBufferBase+"<----\n");
      VM.assert(!discontigious);
    }
    GCTk_TraceBufferBase        = getMember("LGCTk_TraceBuffer;", "bumpPtr_", "I").getOffset();

    top = getMember("LVM_Processor;", "allocBump0", "I").getOffset();
    bot = getMember("LVM_Processor;", "allocBump7", "I").getOffset();
    GCTk_BumpPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_BumpPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
  
    top = getMember("LVM_Processor;", "allocSync0", "I").getOffset();
    bot = getMember("LVM_Processor;", "allocSync7", "I").getOffset();
    GCTk_SyncPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_SyncPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
	  
    top = getMember("LGCTk_ChunkAllocator;", "allocChunkStart0", "I").getOffset();
    bot = getMember("LGCTk_ChunkAllocator;", "allocChunkEnd7", "I").getOffset();
    GCTk_ChunkAllocatorBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 60)) 
			   || ((top < bot) && ((bot - top) != 60)));
      if (unaligned) 
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_ChunkAllocatorBase+"<----\n");
      VM.assert(!unaligned);
    }
    //-#endif
  }


  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "LVM_Runtime;"
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
      VM_Class cls = VM_ClassLoader.findOrCreateType(clsDescriptor, VM_SystemClassLoader.getVMClassLoader()).asClass();
      cls.load();
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
      VM.assert(NOT_REACHED);
    } catch (VM_ResolutionException e) {
      VM.sysWrite("VM_Entrypoints.getMember: can't resolve class=" + classDescriptor+
		  " member=" + memberName + " desc=" + memberDescriptor + "\n");
      VM.assert(NOT_REACHED);
    }
    return null; // placate jikes
  }

  private static VM_Method getMethod(String klass, String member, String descriptor) {
    return (VM_Method)getMember(klass, member, descriptor);
  }

  private static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field)getMember(klass, member, descriptor);
  }
}
