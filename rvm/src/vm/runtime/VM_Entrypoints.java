/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_Entrypoints implements VM_Constants {
  // Methods of VM 
  //
  static VM_Method debugBreakpointMethod;  
 
  // Methods of VM_Runtime 
  //
  static VM_Method instanceOfMethod;       
  static VM_Method instanceOfFinalMethod;  
  static VM_Method checkcastMethod;        
  static VM_Method checkstoreMethod;       
  static VM_Method athrowMethod;           
  static VM_Method newScalarMethod;        
  static VM_Method quickNewArrayMethod;    
  static VM_Method quickNewScalarMethod;   
  static VM_Method lockMethod;             
  static VM_Method unlockMethod;           
  static VM_Method unimplementedBytecodeMethod;
  static VM_Method mandatoryInstanceOfInterfaceMethod;
  static VM_Method unresolvedInterfaceMethodMethod;
  static VM_Method invokeInterfaceMethod;
  static VM_Method newArrayArrayMethod;
  static VM_Method raiseArrayBoundsError;  
  static VM_Method findItableMethod;           
  static VM_Method resolveMethodMethod;
  static VM_Field  methodOffsetsField;    
  static VM_Method resolveFieldMethod;
  static VM_Field  fieldOffsetsField;     

  //-#if RVM_FOR_IA32  
  static VM_Field jtocField;
  static VM_Field threadIdField;
  static VM_Field framePointerField;      
  static VM_Field hiddenSignatureIdField;
  static VM_Field arrayIndexTrapParamField;
  //-#endif
   
  //-#if RVM_FOR_IA32  
  static VM_Field FPUControlWordField;
  //-#endif

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static VM_Method allocAdviceNewScalarMethod;
  static VM_Method allocAdviceQuickNewArrayMethod;    
  static VM_Method allocAdviceQuickNewScalarMethod;   
  static VM_Method allocAdviceQuickNewScalarMethodNEW;   
  //-#endif

  // Methods of VM_Math (method description + offset of code pointer within jtoc).
  //
  static VM_Method longMultiplyMethod;     
  static VM_Method longDivideMethod;       
  static VM_Method longRemainderMethod;    
  static VM_Method longToDoubleMethod;     
  static VM_Method doubleToIntMethod;      
  static VM_Method doubleToLongMethod;     

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static VM_Method allocAdviceNewScalarMethod;
  static VM_Method allocAdviceQuickNewArrayMethod;    
  static VM_Method allocAdviceQuickNewScalarMethod;   
  static VM_Method allocAdviceQuickNewScalarMethodNEW;   
  //-#endif

  // Fields of VM_Math (offset of field within jtoc).
  //
  static VM_Field longOneField;     //  1L
  static VM_Field minusOneField;    // -1.0F
  static VM_Field zeroFloatField;   // 0,0F
  static VM_Field halfFloatField;   // 0.5F
  static VM_Field oneFloatField;    // 1.0F
  static VM_Field zeroDoubleField;  //  0.0
  static VM_Field oneDoubleField;   //  1.0
  static VM_Field twoFloatField;    //  2.0F
  static VM_Field two32Field;       //  2.0F^32
  static VM_Field half32Field;      //  0.5F^32
  static VM_Field billionthField;   //  1e-9
  static VM_Field maxintField;      //  largest double that can be rounded to an int
  static VM_Field minintField;      //  smallest double that can be rounded to an int
  static VM_Field IEEEmagicField;   //  special double value for use in int <--> double conversions
  static VM_Field I2DconstantField; //  special constant for integer to double conversion
   
  // Fields of VM_OutOfLineMachineCode (offset of field within jtoc).
  //
  static VM_Field reflectiveMethodInvokerInstructionsField;
  static VM_Field saveThreadStateInstructionsField;
  static VM_Field threadSwitchInstructionsField;
  static VM_Field restoreHardwareExceptionStateInstructionsField;
  static VM_Field getTimeInstructionsField;
  static VM_Field invokeNativeFunctionInstructionsField;

  // Fields of VM_Scheduler (offset of field within jtoc).
  //
  static VM_Field outputLockField;
  static VM_Field doublewordVolatileMutexField; // for use with RVM_WITH_STRONG_VOLATILE_SEMANTICS
   
  // Fields of VM_Processor (offset of field off the processor register).
  //
  static VM_Field deterministicThreadSwitchCountField;
     
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static VM_Field modifiedOldObjectsTopField;
  static VM_Field modifiedOldObjectsMaxField;
  static VM_Field incDecBufferTopField;
  static VM_Field incDecBufferMaxField;
  //-#endif
  static VM_Field scratchSecondsField;
  static VM_Field scratchNanosecondsField;
  static VM_Field threadSwitchRequestedField;
  static VM_Field activeThreadField;
  static VM_Field activeThreadStackLimitField;
  //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
  static VM_Field vpStateField;
  //-#else
  // default implementation of jni
  static VM_Field processorModeField;
  static VM_Field vpStatusAddressField;
  //-#endif
  // Methods of VM_Thread (offset of code pointer within jtoc).
  //
  static VM_Method threadSwitchFromPrologueMethod;
  static VM_Method threadSwitchFromBackedgeMethod;
  static VM_Method threadSwitchFromEpilogueMethod;
  static VM_Method threadYieldMethod;

  // Instance Methods of VM_Thread
  //
  static VM_Method becomeNativeThreadMethod;
  static VM_Method becomeRVMThreadMethod;

  // Fields of VM_Thread (offset of field within instance).
  //
  static VM_Field stackLimitField;
  static VM_Field beingDispatchedField;
  static VM_Field jniEnvField;
  static VM_Field processorAffinityField;
  static VM_Field nativeAffinityField;
  static VM_Field threadSlotField;

  // Fields of VM_Allocator (offset of field within jtoc).
  //
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static VM_Field areaCurrentAddressField;
  static VM_Field matureCurrentAddressField;
  //-#endif

  // Fields of VM_FinalizerListElement (offset of field within instance
  //  
  static VM_Field finalizerListElementValueField;
  static VM_Field finalizerListElementPointerField;

  // Fields of VM_BlockControl (offset of field within instance).
  //
  static VM_Field allocCountField;

  // Fields of VM_Scheduler (offset of field within jtoc).
  //
  static VM_Field processorsField;
  static VM_Field threadsField;

  // Fields and static methods of VM_ProcessorLock.
  //
  static VM_Field latestContenderField;
  static VM_Method processorLockMethod;
  static VM_Method processorUnlockMethod;

  // Fields of VM_Type (offset of field within instance).
  //
  static VM_Field classForTypeField;

  // Static methods of VM_Class (offset of code pointer within jtoc).
  //
  static VM_Method initializeClassIfNecessaryMethod; // for use with RVM_WITH_PREMATURE_CLASS_RESOLUTION

  //-#if RVM_WITH_CONCURRENT_GC
  // Methods of VM_RCBuffer (offset of code pointer within jtoc).
  // used in baseline VM_Compiler for reference counting write barrier
  //
  static VM_Method processIncDecBufferMethod;
  //-#endif

  // Fields of VM_JNIEnvironment (offset of field within instance).
  //
  static VM_Field JNIEnvAddressField;
  static VM_Field JNIEnvSavedPRField;
  static VM_Field JNIEnvSavedTIField;
  static VM_Field JNIRefsField;
  static VM_Field JNIRefsTopField;
  static VM_Field JNIRefsMaxField;
  static VM_Field JNIRefsSavedFPField;
  static VM_Field JNITopJavaFPField;
  static VM_Field JNIPendingExceptionField;

  // Fields of VM_JNIEnvironment (offset of field within jtoc).
  //
  static VM_Field JNIFunctionPointersField;

  // Fields of VM_BootRecord
  //
  static VM_Field the_boot_recordField;
  static VM_Field globalGCInProgressFlagField;
  static VM_Field lockoutProcessorField;
  //-#if RVM_FOR_POWERPC
  static VM_Field sysTOCField;
  //-#endif
  static VM_Field sysVirtualProcessorYieldIPField;
  static VM_Field externalSignalFlagField;

  // Methods of VM_WriteBarrier (method description + offset of code pointer within jtoc).
  //
  static VM_Method arrayStoreWriteBarrierMethod;
  static VM_Method unresolvedPutfieldWriteBarrierMethod;
  static VM_Method resolvedPutfieldWriteBarrierMethod;
  static VM_Method unresolvedPutStaticWriteBarrierMethod;
  static VM_Method resolvedPutStaticWriteBarrierMethod;

  //-#if RVM_WITH_READ_BARRIER2
  static VM_Method arrayLoadReadBarrierMethod;
  static VM_Method unresolvedGetfieldReadBarrierMethod;
  static VM_Method resolvedGetfieldReadBarrierMethod;
  static VM_Method unresolvedGetStaticReadBarrierMethod;
  static VM_Method resolvedGetStaticReadBarrierMethod;
  //-#endif RVM_WITH_READ_BARRIER2

  //-#if RVM_WITH_GCTk
  static ADDRESS GCTk_WriteBufferBase;
  static ADDRESS GCTk_BumpPointerBase;
  static ADDRESS GCTk_SyncPointerBase;
  static ADDRESS GCTk_ChunkAllocatorBase;
  static ADDRESS GCTk_TraceBufferBase;
  //-#endif

  private static VM_Method getMethod(String klass, String member, String descriptor) {
    return (VM_Method)VM.getMember(klass, member, descriptor);
  }

  private static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field)VM.getMember(klass, member, descriptor);
  }


  static void init() {
    VM_Member m;
      
    debugBreakpointMethod = getMethod("LVM;", "debugBreakpoint", "()V");

    instanceOfMethod = getMethod("LVM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
    instanceOfFinalMethod = getMethod("LVM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
    checkcastMethod = getMethod("LVM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
    checkstoreMethod = getMethod("LVM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
    athrowMethod = getMethod("LVM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");
    newScalarMethod = getMethod("LVM_Runtime;", "newScalar", "(I)Ljava/lang/Object;");
    quickNewArrayMethod = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;)Ljava/lang/Object;");
    quickNewScalarMethod = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;Z)Ljava/lang/Object;");

    //-#if RVM_WITH_GCTk_ALLOC_ADVICE
    allocAdviceNewScalarMethod = getMethod("LVM_Runtime;", "newScalar", "(II)Ljava/lang/Object;");
    allocAdviceQuickNewArrayMethod = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;I)Ljava/lang/Object;");
    allocAdviceQuickNewScalarMethod = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;I)Ljava/lang/Object;");
    allocAdviceQuickNewScalarMethodNEW = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;ZI)Ljava/lang/Object;");
    //-#endif

    lockMethod = getMethod("LVM_Lock;", "lock", "(Ljava/lang/Object;)V");
    unlockMethod = getMethod("LVM_Lock;", "unlock", "(Ljava/lang/Object;)V");
   
    newArrayArrayMethod   = getMethod("LVM_MultianewarrayHelper;", "newArrayArray", "(III)Ljava/lang/Object;");
    unimplementedBytecodeMethod = getMethod("LVM_Runtime;", "unimplementedBytecode", "(I)V");

    resolveMethodMethod     = getMethod("LVM_TableBasedDynamicLinker;", "resolveMethod", "(I)V");
    methodOffsetsField      = getField("LVM_TableBasedDynamicLinker;", "methodOffsets", "[I");   
    resolveFieldMethod      = getMethod("LVM_TableBasedDynamicLinker;", "resolveField", "(I)V");
    fieldOffsetsField       = getField("LVM_TableBasedDynamicLinker;", "fieldOffsets", "[I");    
    //-#if RVM_FOR_POWERPC 
    invokeInterfaceMethod   = getMethod("LVM_Runtime;", "invokeInterface", "(Ljava/lang/Object;I)[I");
    findItableMethod        = getMethod("LVM_Runtime;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
    //-#elif RVM_FOR_IA32
    invokeInterfaceMethod   = getMethod("LVM_Runtime;", "invokeInterface", "(Ljava/lang/Object;I)[B");
    jtocField               = getField("LVM_Processor;", "jtoc", "Ljava/lang/Object;");
    threadIdField           = getField("LVM_Processor;", "threadId", "I");
    framePointerField       = getField("LVM_Processor;", "framePointer", "I");
    hiddenSignatureIdField  = getField("LVM_Processor;", "hiddenSignatureId", "I");
    arrayIndexTrapParamField = getField("LVM_Processor;", "arrayIndexTrapParam", "I");
    //-#endif
    mandatoryInstanceOfInterfaceMethod = getMethod("LVM_DynamicTypeCheck;", "mandatoryInstanceOfInterface", "(LVM_Class;[Ljava/lang/Object;)V");
    unresolvedInterfaceMethodMethod = getMethod("LVM_DynamicTypeCheck;", "unresolvedInterfaceMethod", "(I[Ljava/lang/Object;)V");

    raiseArrayBoundsError = getMethod("LVM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");

    longMultiplyMethod = getMethod("LVM_Math;", "longMultiply", "(JJ)J");
    longDivideMethod = getMethod("LVM_Math;", "longDivide", "(JJ)J");
    longRemainderMethod = getMethod("LVM_Math;", "longRemainder", "(JJ)J");
    longToDoubleMethod = getMethod("LVM_Math;", "longToDouble", "(J)D");
    doubleToIntMethod = getMethod("LVM_Math;", "doubleToInt", "(D)I");
    doubleToLongMethod = getMethod("LVM_Math;", "doubleToLong", "(D)J");

    longOneField               = getField("LVM_Math;", "longOne", "J");
    minusOneField              = getField("LVM_Math;", "minusOne", "F");
    zeroFloatField             = getField("LVM_Math;", "zero", "F");
    halfFloatField             = getField("LVM_Math;", "half", "F");
    oneFloatField              = getField("LVM_Math;", "one", "F");
    twoFloatField              = getField("LVM_Math;", "two", "F");
    two32Field                 = getField("LVM_Math;", "two32", "F");
    half32Field                = getField("LVM_Math;", "half32", "F");
    zeroDoubleField            = getField("LVM_Math;", "zeroD", "D");
    oneDoubleField             = getField("LVM_Math;", "oneD", "D");
    billionthField             = getField("LVM_Math;", "billionth", "D");
    maxintField                = getField("LVM_Math;", "maxint", "D");
    minintField                = getField("LVM_Math;", "minint", "D");
    IEEEmagicField             = getField("LVM_Math;", "IEEEmagic", "D");
    I2DconstantField           = getField("LVM_Math;", "I2Dconstant", "D");
 
    reflectiveMethodInvokerInstructionsField       = getField("LVM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", INSTRUCTION_ARRAY_SIGNATURE);
    saveThreadStateInstructionsField               = getField("LVM_OutOfLineMachineCode;", "saveThreadStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
    threadSwitchInstructionsField                  = getField("LVM_OutOfLineMachineCode;", "threadSwitchInstructions", INSTRUCTION_ARRAY_SIGNATURE);
    restoreHardwareExceptionStateInstructionsField = getField("LVM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
    //-#if RVM_FOR_POWERPC
    getTimeInstructionsField                       = getField("LVM_OutOfLineMachineCode;", "getTimeInstructions", INSTRUCTION_ARRAY_SIGNATURE);
    //-#endif
    invokeNativeFunctionInstructionsField          = getField("LVM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", INSTRUCTION_ARRAY_SIGNATURE);

    outputLockField                                = getField("LVM_Scheduler;", "outputLock", "I");

    //-#if RVM_WITH_STRONG_VOLATILE_SEMANTICS
    doublewordVolatileMutexField                   = getField("LVM_Scheduler;", "doublewordVolatileMutex", "LVM_ProcessorLock;");
    //-#endif
      
    deterministicThreadSwitchCountField            = getField("LVM_Processor;", "deterministicThreadSwitchCount", "I");

    //-#if RVM_WITH_GCTk
    ADDRESS top = VM.getMember("LVM_Processor;", "writeBuffer0", "I").getOffset();
    ADDRESS bot = VM.getMember("LVM_Processor;", "writeBuffer1", "I").getOffset();
    GCTk_WriteBufferBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean discontigious = (((top > bot) && ((top - bot) != 4))
			       || ((top < bot) && ((bot - top) != 4)));
      if (discontigious)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_WriteBufferBase+"<----\n");
      VM.assert(!discontigious);
    }
    GCTk_TraceBufferBase        = VM.getMember("LGCTk_TraceBuffer;", "bumpPtr_", "I").getOffset();
    //-#endif
    //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    modifiedOldObjectsTopField = getField("LVM_Processor;", "modifiedOldObjectsTop", "I");
    modifiedOldObjectsMaxField = getField("LVM_Processor;", "modifiedOldObjectsMax", "I");
    incDecBufferTopField       = getField("LVM_Processor;", "incDecBufferTop", "I");
    incDecBufferMaxField       = getField("LVM_Processor;", "incDecBufferMax", "I");
    //-#endif
    scratchSecondsField        = getField("LVM_Processor;", "scratchSeconds", "D");
    scratchNanosecondsField    = getField("LVM_Processor;", "scratchNanoseconds", "D");
    threadSwitchRequestedField = getField("LVM_Processor;", "threadSwitchRequested", "I");
    activeThreadField          = getField("LVM_Processor;", "activeThread", "LVM_Thread;");
    activeThreadStackLimitField= getField("LVM_Processor;", "activeThreadStackLimit", "I");

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    vpStateField               = getField("LVM_Processor;", "vpState", "I");
    //-#else
    // default implementation of jni
    processorModeField         = getField("LVM_Processor;", "processorMode", "I");
    vpStatusAddressField       = getField("LVM_Processor;", "vpStatusAddress", "I");
    //-#endif

    //-#if RVM_WITH_GCTk  // not supported by GCTk yet
    //-#else
    finalizerListElementValueField = getField("LVM_FinalizerListElement;", "value", "I");
    finalizerListElementPointerField = getField("LVM_FinalizerListElement;", "pointer", "Ljava/lang/Object;");
    //-#endif

    threadSwitchFromPrologueMethod = getMethod("LVM_Thread;", "threadSwitchFromPrologue", "()V");
    threadSwitchFromBackedgeMethod = getMethod("LVM_Thread;", "threadSwitchFromBackedge", "()V");
    threadSwitchFromEpilogueMethod = getMethod("LVM_Thread;", "threadSwitchFromEpilogue", "()V");
    threadYieldMethod              = getMethod("LVM_Thread;", "yield", "()V");
    becomeNativeThreadMethod       = getMethod("LVM_Thread;", "becomeNativeThread", "()V");
    becomeRVMThreadMethod          = getMethod("LVM_Thread;", "becomeRVMThread", "()V");
    stackLimitField                = getField("LVM_Thread;", "stackLimit", "I");
    beingDispatchedField           = getField("LVM_Thread;", "beingDispatched", "Z");
    threadSlotField                = getField("LVM_Thread;", "threadSlot", "I");

    //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    areaCurrentAddressField        = getField("LVM_Allocator;", "areaCurrentAddress", "I");
    matureCurrentAddressField      = getField("LVM_Allocator;", "matureCurrentAddress", "I");
    allocCountField                = getField("LVM_BlockControl;", "allocCount", "I");
    //-#endif
    processorsField                = getField("LVM_Scheduler;", "processors", "[LVM_Processor;");
    threadsField                   = getField("LVM_Scheduler;", "threads", "[LVM_Thread;");

    latestContenderField           = getField("LVM_ProcessorLock;", "latestContender", "LVM_Processor;");
    processorLockMethod            = getMethod("LVM_ProcessorLock;", "lock", "()V");
    processorUnlockMethod          = getMethod("LVM_ProcessorLock;", "unlock", "()V");

    classForTypeField              = getField("LVM_Type;", "classForType", "Ljava/lang/Class;");

    //-#if RVM_WITH_CONCURRENT_GC
    processIncDecBufferMethod      = getMethod("LVM_RCBuffers;", "processIncDecBuffer", "()V");
    //-#endif

    jniEnvField                = getField("LVM_Thread;", "jniEnv", "LVM_JNIEnvironment;");
    processorAffinityField     = getField("LVM_Thread;", "processorAffinity", "LVM_Processor;");
    nativeAffinityField        = getField("LVM_Thread;", "nativeAffinity", "LVM_Processor;");
    JNIEnvAddressField         = getField("LVM_JNIEnvironment;", "JNIEnvAddress", "I");
    JNIEnvSavedTIField         = getField("LVM_JNIEnvironment;", "savedTIreg", "I");
    JNIEnvSavedPRField         = getField("LVM_JNIEnvironment;", "savedPRreg", "LVM_Processor;");
    JNIRefsField               = getField("LVM_JNIEnvironment;", "JNIRefs", "[I");
    JNIRefsTopField            = getField("LVM_JNIEnvironment;", "JNIRefsTop", "I");
    JNIRefsMaxField            = getField("LVM_JNIEnvironment;", "JNIRefsMax", "I");
    JNIRefsSavedFPField        = getField("LVM_JNIEnvironment;", "JNIRefsSavedFP", "I");
    JNITopJavaFPField          = getField("LVM_JNIEnvironment;", "JNITopJavaFP", "I");
    JNIPendingExceptionField   = getField("LVM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
    JNIFunctionPointersField   = getField("LVM_JNIEnvironment;", "JNIFunctionPointers", "[I");
    //-#if RVM_FOR_IA32
    FPUControlWordField        = getField("LVM_Math;", "FPUControlWord", "I");
    //-#endif RVM_FOR_IA32
        
    //-#if RVM_WITH_GCTk
    top = VM.getMember("LVM_Processor;", "allocBump0", "I").getOffset();
    bot = VM.getMember("LVM_Processor;", "allocBump7", "I").getOffset();
    GCTk_BumpPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_BumpPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
  
    top = VM.getMember("LVM_Processor;", "allocSync0", "I").getOffset();
    bot = VM.getMember("LVM_Processor;", "allocSync7", "I").getOffset();
    GCTk_SyncPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_SyncPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
	  
    top = VM.getMember("LGCTk_ChunkAllocator;", "allocChunkStart0", "I").getOffset();
    bot = VM.getMember("LGCTk_ChunkAllocator;", "allocChunkEnd7", "I").getOffset();
    GCTk_ChunkAllocatorBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 60)) 
			   || ((top < bot) && ((bot - top) != 60)));
      if (unaligned) 
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_ChunkAllocatorBase+"<----\n");
      VM.assert(!unaligned);
    }
    //-#endif

    the_boot_recordField            = getField("LVM_BootRecord;", "the_boot_record", "LVM_BootRecord;");
    globalGCInProgressFlagField     = getField("LVM_BootRecord;", "globalGCInProgressFlag", "I");
    lockoutProcessorField           = getField("LVM_BootRecord;", "lockoutProcessor", "I");
    //-#if RVM_FOR_POWERPC
    sysTOCField                     = getField("LVM_BootRecord;", "sysTOC", "I");
    //-#endif
    sysVirtualProcessorYieldIPField = getField("LVM_BootRecord;", "sysVirtualProcessorYieldIP", "I");
    externalSignalFlagField         = getField("LVM_BootRecord;", "externalSignalFlag", "I");

    //-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
    initializeClassIfNecessaryMethod = getMethod("LVM_Class;", "initializeClassIfNecessary", "(I)V");
    //-#endif

    arrayStoreWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    resolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    unresolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    resolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
    unresolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
    //-#if RVM_WITH_READ_BARRIER2
    arrayLoadReadBarrierMethod = getMethod("LVM_ReadBarrier;", "arrayLoadReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    resolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "resolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    unresolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
    resolvedGetStaticReadBarrierMethod = getMethodVM.getMember("LVM_ReadBarrier;", "resolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
    unresolvedGetStaticReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
    //-#endif RVM_WITH_READ_BARRIER2
  }
}
