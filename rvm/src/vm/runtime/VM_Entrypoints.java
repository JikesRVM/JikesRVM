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
class VM_Entrypoints implements VM_Constants
   {
   // Methods of VM (method description + offset of code pointer within jtoc).
   //
   static VM_Method debugBreakpointMethod;  
   static int       debugBreakpointOffset;  
 
   // Methods of VM_Runtime 
   // (method description + offset of code pointer within jtoc).
   //
   static VM_Method instanceOfMethod;       
   static int       instanceOfOffset;       

   static VM_Method instanceOfFinalMethod;  
   static int       instanceOfFinalOffset;  

   static VM_Method checkcastMethod;        
   static int       checkcastOffset;        
   
   static VM_Method checkstoreMethod;       
   static int       checkstoreOffset;       
   
   static VM_Method athrowMethod;           
   static int       athrowOffset;           

   static VM_Method newScalarMethod;        
   static int       newScalarOffset;        

   static VM_Method quickNewArrayMethod;    
   static int       quickNewArrayOffset;    

   static VM_Method quickNewScalarMethod;   
   static int       quickNewScalarOffset;   
   
   static VM_Method lockMethod;             
   static int       lockOffset;             
   
   static VM_Method unlockMethod;           
   static int       unlockOffset;           
   
   static int       unimplementedBytecodeOffset;
   

//-#if RVM_FOR_POWERPC // baseline compiler entrypoints
   // Method offsets of VM_Linker (offset of code pointer within jtoc).
   //
   static int getstaticOffset;
   static int putstaticOffset;
   static int getfieldOffset;
   static int putfieldOffset;
   static int invokestaticOffset;
   static int invokevirtualOffset;
   static int invokespecialOffset;
//-#endif // baseline compiler entrypoints
   static int mandatoryInstanceOfInterfaceOffset;
   static int unresolvedInterfaceMethodOffset;
   static int invokeInterfaceOffset;
   static int newArrayArrayOffset;

   static VM_Method raiseArrayBoundsError;  
   static int raiseArrayBoundsErrorOffset;  

   static VM_Method findItableMethod;           
   static int       findItableOffset;
   //-#if RVM_FOR_IA32  
   static int fieldOffsetsOffset;     
   static int methodOffsetsOffset;    
   static int loadClassOnDemandOffset;
   static int jtocOffset;              
   static int threadIdOffset;
   static int framePointerOffset;      
   static int hiddenSignatureIdOffset;
   static int arrayIndexTrapParamOffset;
   //-#endif
   
   //-#if RVM_FOR_IA32  
   static int FPUControlWordOffset;
 
   //-#endif

//-#if RVM_WITH_GCTk_ALLOC_ADVICE
   static VM_Method allocAdviceNewScalarMethod;
   static int       allocAdviceNewScalarOffset;

   static VM_Method allocAdviceQuickNewArrayMethod;    
   static int       allocAdviceQuickNewArrayOffset;    

   static VM_Method allocAdviceQuickNewScalarMethod;   
   static int       allocAdviceQuickNewScalarOffset;   

   static VM_Method allocAdviceQuickNewScalarMethodNEW;   
   static int       allocAdviceQuickNewScalarOffsetNEW;   
//-#endif

   // Methods of VM_Math (method description + offset of code pointer within jtoc).
   //
   static VM_Method longMultiplyMethod;     
   static int       longMultiplyOffset;     

   static VM_Method longDivideMethod;       
   static int       longDivideOffset;       
   
   static VM_Method longRemainderMethod;    
   static int       longRemainderOffset;    
   
   static VM_Method longToDoubleMethod;     
   static int       longToDoubleOffset;     
   
   static VM_Method doubleToIntMethod;      
   static int       doubleToIntOffset;      
   
   static VM_Method doubleToLongMethod;     
   static int       doubleToLongOffset;     

//-#if RVM_WITH_GCTk_ALLOC_ADVICE
   static VM_Method allocAdviceNewScalarMethod;
   static int       allocAdviceNewScalarOffset;

   static VM_Method allocAdviceQuickNewArrayMethod;    
   static int       allocAdviceQuickNewArrayOffset;    

   static VM_Method allocAdviceQuickNewScalarMethod;   
   static int       allocAdviceQuickNewScalarOffset;   

   static VM_Method allocAdviceQuickNewScalarMethodNEW;   
   static int       allocAdviceQuickNewScalarOffsetNEW;   
//-#endif

   // Fields of VM_Math (offset of field within jtoc).
   //
   static int longOneOffset;     //  1L
   static int minusOneOffset;    // -1.0F
   static VM_Field zeroFloat;
   static int zeroOffset;        //  0.0F
   static VM_Field halfFloat;
   static int halfOffset;        //  0.5F
   static VM_Field oneFloat;
   static int oneOffset;         //  1.0F
   static VM_Field zeroDouble;   //  0.0
   static VM_Field oneDouble;    //  1.0
   static int oneDoubleOffset;   
   static VM_Field twoFloat;
   static int twoOffset;         //  2.0F
   static int two32Offset;       //  2.0F^32
   static int half32Offset;      //  0.5F^32
   static int billionthOffset;   //  1e-9
   static int maxintOffset;      //  largest double that can be rounded 
                                 //  to an int
   static int minintOffset;      //  smallest double that can be rounded to 
                                 //  an int
   static VM_Field IEEEmagic;
   static int IEEEmagicOffset;   //  special double value for use in int <--> double conversions
   static VM_Field I2Dconstant;
   static int I2DconstantOffset; //  special constant for integer to double conversion
   
   // Fields of VM_OutOfLineMachineCode (offset of field within jtoc).
   //
   static int reflectiveMethodInvokerInstructionsOffset;
   static int saveThreadStateInstructionsOffset;
   static int resumeThreadExecutionInstructionsOffset;
   static int restoreHardwareExceptionStateInstructionsOffset;
   static int getTimeInstructionsOffset;
   static int invokeNativeFunctionInstructionsOffset;

   // Fields of VM_Scheduler (offset of field within jtoc).
   //
   static int outputLockOffset;
   static int doublewordVolatileMutexOffset; // for use with RVM_WITH_STRONG_VOLATILE_SEMANTICS
   
   // Fields of VM_Processor (offset of field off the processor register).
   //
   static int deterministicThreadSwitchCountOffset;
     
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
   static int modifiedOldObjectsTopOffset;
   static int modifiedOldObjectsMaxOffset;
//-#endif
   static int incDecBufferTopOffset;
   static int incDecBufferMaxOffset;
   static int scratchSecondsOffset;
   static int scratchNanosecondsOffset;
   static int threadSwitchRequestedOffset;
   static int activeThreadOffset;
//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
   static int vpStateOffset;
//-#else
   // default implementation of jni
   static int processorModeOffset;
   static int vpStatusAddressOffset;
//-#endif
   // Methods of VM_Thread (offset of code pointer within jtoc).
   //
   static int threadSwitchFromPrologueOffset;
   static int threadSwitchFromBackedgeOffset;
   static int threadSwitchFromEpilogueOffset;
   static int threadYieldOffset;

   // Instance Methods of VM_Thread
   //
   static int becomeNativeThreadOffset;
   static int becomeRVMThreadOffset;

   // Fields of VM_Thread (offset of field within instance).
   //
   static int stackLimitOffset;
   static int beingDispatchedOffset;
   static int jniEnvOffset;
   static int processorAffinityOffset;
   static int nativeAffinityOffset;
   static int threadSlotOffset;

   // Fields of VM_Allocator (offset of field within jtoc).
   //
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
   static int areaCurrentAddressOffset;
   static int matureCurrentAddressOffset;
//-#endif

   // Fields of VM_FinalizerListElement (offset of field within instance
   //  
   static int finalizerListElementValueOffset;
   static int finalizerListElementPointerOffset;

   // Fields of VM_BlockControl (offset of field within instance).
   //
   static int allocCountOffset;

   // Fields of VM_Scheduler (offset of field within jtoc).
   //
   static int processorsOffset;
   static int threadsOffset;

   // Fields and static methods of VM_ProcessorLock.
   //
   static int latestContenderOffset;
   static int processorLockOffset;
   static int processorUnlockOffset;

   // Fields of VM_Type (offset of field within instance).
   //
   static int classForTypeOffset;

   // Static methods of VM_Class (offset of code pointer within jtoc).
   //
     static int initializeClassIfNecessaryOffset; // for use with RVM_WITH_PREMATURE_CLASS_RESOLUTION

   // Methods of VM_WriteBuffer (offset of code pointer within jtoc).
   //
   // static int growWriteBufferOffset;

   //-#if RVM_WITH_CONCURRENT_GC
   // Methods of VM_RCBuffer (offset of code pointer within jtoc).
   // used in baseline VM_Compiler for reference counting write barrier
   //
   static int processIncDecBufferOffset;
   //-#endif

   // Fields of VM_JNIEnvironment (offset of field within instance).
   //
   static int JNIEnvAddressOffset;
   static int JNIEnvSavedPROffset;
   static int JNIEnvSavedTIOffset;
   static int JNIRefsOffset;
   static int JNIRefsTopOffset;
   static int JNIRefsMaxOffset;
   static int JNIRefsSavedFPOffset;
   static int JNITopJavaFPOffset;
   static int JNIPendingExceptionOffset;

   // Fields of VM_JNIEnvironment (offset of field within jtoc).
   //
   static int JNIFunctionPointersOffset;

   // Fields of VM_BootRecord
   //
   static int the_boot_recordOffset;
   static int globalGCInProgressFlagOffset;
   static int lockoutProcessorOffset;
//-#if RVM_FOR_POWERPC
   static int sysTOCOffset;
//-#endif
   static int sysVirtualProcessorYieldIPOffset;
   static int externalSignalFlagOffset;

   // Methods of VM_WriteBarrier (method description + offset of code pointer within jtoc).
   //
   static VM_Method arrayStoreWriteBarrierMethod;
   static int       arrayStoreWriteBarrierOffset;

   static VM_Method unresolvedPutfieldWriteBarrierMethod;
   static int       unresolvedPutfieldWriteBarrierOffset;

   static VM_Method resolvedPutfieldWriteBarrierMethod;
   static int       resolvedPutfieldWriteBarrierOffset;

   static VM_Method unresolvedPutStaticWriteBarrierMethod;
   static int       unresolvedPutStaticWriteBarrierOffset;

   static VM_Method resolvedPutStaticWriteBarrierMethod;
   static int       resolvedPutStaticWriteBarrierOffset;

//-#if RVM_WITH_READ_BARRIER
   static VM_Method arrayLoadReadBarrierMethod;
   static int       arrayLoadReadBarrierOffset;

   static VM_Method unresolvedGetfieldReadBarrierMethod;
   static int       unresolvedGetfieldReadBarrierOffset;

   static VM_Method resolvedGetfieldReadBarrierMethod;
   static int       resolvedGetfieldReadBarrierOffset;

   static VM_Method unresolvedGetStaticReadBarrierMethod;
   static int       unresolvedGetStaticReadBarrierOffset;

   static VM_Method resolvedGetStaticReadBarrierMethod;
   static int       resolvedGetStaticReadBarrierOffset;
//-#endif

//-#if RVM_WITH_GCTk
   static ADDRESS GCTk_WriteBufferBase;
   static ADDRESS GCTk_BumpPointerBase;
   static ADDRESS GCTk_SyncPointerBase;
   static ADDRESS GCTk_ChunkAllocatorBase;
   static ADDRESS GCTk_TraceBufferBase;
//-#endif

   static void
   init()
      {
      VM_Member m;
      
      m = debugBreakpointMethod = (VM_Method)VM.getMember("LVM;", "debugBreakpoint", "()V");
      debugBreakpointOffset = m.getOffset();
    
      m = instanceOfMethod = (VM_Method)VM.getMember("LVM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
      instanceOfOffset = m.getOffset();

      m = instanceOfFinalMethod = (VM_Method)VM.getMember("LVM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
      instanceOfFinalOffset = m.getOffset();

      m = checkcastMethod = (VM_Method)VM.getMember("LVM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
      checkcastOffset = m.getOffset();

      m = checkstoreMethod = (VM_Method)VM.getMember("LVM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
      checkstoreOffset = m.getOffset();

      m = athrowMethod = (VM_Method)VM.getMember("LVM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");
      athrowOffset = m.getOffset();

      m = newScalarMethod = (VM_Method)VM.getMember("LVM_Runtime;", "newScalar", "(I)Ljava/lang/Object;");
      newScalarOffset = m.getOffset();

      m = quickNewArrayMethod = (VM_Method)VM.getMember("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;)Ljava/lang/Object;");
      quickNewArrayOffset = m.getOffset();

      m = quickNewScalarMethod = (VM_Method)VM.getMember("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;Z)Ljava/lang/Object;");
      quickNewScalarOffset = m.getOffset();

//-#if RVM_WITH_GCTk_ALLOC_ADVICE
      m = allocAdviceNewScalarMethod = (VM_Method)VM.getMember("LVM_Runtime;", "newScalar", "(II)Ljava/lang/Object;");
      allocAdviceNewScalarOffset = m.getOffset();

      m = allocAdviceQuickNewArrayMethod = (VM_Method)VM.getMember("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;I)Ljava/lang/Object;");
      allocAdviceQuickNewArrayOffset = m.getOffset();

      m = allocAdviceQuickNewScalarMethod = (VM_Method)VM.getMember("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;I)Ljava/lang/Object;");
      allocAdviceQuickNewScalarOffset = m.getOffset();

      m = allocAdviceQuickNewScalarMethodNEW = (VM_Method)VM.getMember("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;ZI)Ljava/lang/Object;");
      allocAdviceQuickNewScalarOffsetNEW = m.getOffset();
//-#endif
      m = lockMethod = (VM_Method)VM.getMember("LVM_Lock;", "lock", "(Ljava/lang/Object;)V");
      lockOffset = m.getOffset();

      m = unlockMethod = (VM_Method)VM.getMember("LVM_Lock;", "unlock", "(Ljava/lang/Object;)V");
      unlockOffset = m.getOffset();
   
      newArrayArrayOffset   = VM.getMember("LVM_Linker;", "newArrayArray", "(III)Ljava/lang/Object;").getOffset();
      
      unimplementedBytecodeOffset = VM.getMember("LVM_Runtime;", "unimplementedBytecode", "(I)V").getOffset();

//-#if RVM_FOR_POWERPC // baseline compiler entrypoints
      invokeInterfaceOffset   = VM.getMember("LVM_Runtime;", "invokeInterface", "(Ljava/lang/Object;I)[I").getOffset();
      getstaticOffset         = VM.getMember("LVM_Linker;", "getstatic",     "()V").getOffset();
      putstaticOffset         = VM.getMember("LVM_Linker;", "putstatic",     "()V").getOffset();
      getfieldOffset          = VM.getMember("LVM_Linker;", "getfield",      "()V").getOffset();
      putfieldOffset          = VM.getMember("LVM_Linker;", "putfield",      "()V").getOffset();
      invokestaticOffset      = VM.getMember("LVM_Linker;", "invokestatic",  "()V").getOffset();
      invokevirtualOffset     = VM.getMember("LVM_Linker;", "invokevirtual", "()V").getOffset();
      invokespecialOffset     = VM.getMember("LVM_Linker;", "invokespecial", "()V").getOffset();
      findItableMethod        = (VM_Method)VM.getMember("LVM_Runtime;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
      findItableOffset        = findItableMethod.getOffset();
//-#elif RVM_FOR_IA32
      invokeInterfaceOffset   = VM.getMember("LVM_Runtime;", "invokeInterface", "(Ljava/lang/Object;I)[B").getOffset();
      loadClassOnDemandOffset = VM.getMember("LVM_Linker;", "loadClassOnDemand", "(I)V").getOffset();
      fieldOffsetsOffset      = VM.getMember("LVM_ClassLoader;", "fieldOffsets", "[I").getOffset();    
      methodOffsetsOffset     = VM.getMember("LVM_ClassLoader;", "methodOffsets", "[I").getOffset();   
      jtocOffset              = VM.getMember("LVM_Processor;", "jtoc", "Ljava/lang/Object;").getOffset(); 
      threadIdOffset          = VM.getMember("LVM_Processor;", "threadId", "I").getOffset();        
      framePointerOffset      = VM.getMember("LVM_Processor;", "framePointer", "I").getOffset();      
      hiddenSignatureIdOffset = VM.getMember("LVM_Processor;", "hiddenSignatureId", "I").getOffset();
      arrayIndexTrapParamOffset = VM.getMember("LVM_Processor;", "arrayIndexTrapParam", "I").getOffset();
//-#endif
      mandatoryInstanceOfInterfaceOffset = VM.getMember("LVM_DynamicTypeCheck;", "mandatoryInstanceOfInterface", "(LVM_Class;[Ljava/lang/Object;)V").getOffset();
      unresolvedInterfaceMethodOffset = VM.getMember("LVM_DynamicTypeCheck;", "unresolvedInterfaceMethod", "(I[Ljava/lang/Object;)V").getOffset();

      m = raiseArrayBoundsError = (VM_Method)VM.getMember("LVM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
      raiseArrayBoundsErrorOffset = m.getOffset();

      m = longMultiplyMethod = (VM_Method)VM.getMember("LVM_Math;", "longMultiply", "(JJ)J");
      longMultiplyOffset = m.getOffset();

      m = longDivideMethod = (VM_Method)VM.getMember("LVM_Math;", "longDivide", "(JJ)J");
      longDivideOffset = m.getOffset();

      m = longRemainderMethod = (VM_Method)VM.getMember("LVM_Math;", "longRemainder", "(JJ)J");
      longRemainderOffset = m.getOffset();

      m = longToDoubleMethod = (VM_Method)VM.getMember("LVM_Math;", "longToDouble", "(J)D");
      longToDoubleOffset = m.getOffset();

      m = doubleToIntMethod = (VM_Method)VM.getMember("LVM_Math;", "doubleToInt", "(D)I");
      doubleToIntOffset = m.getOffset();

      m = doubleToLongMethod = (VM_Method)VM.getMember("LVM_Math;", "doubleToLong", "(D)J");
      doubleToLongOffset = m.getOffset();

      longOneOffset               = VM.getMember("LVM_Math;", "longOne", "J").getOffset();
      minusOneOffset              = VM.getMember("LVM_Math;", "minusOne", "F").getOffset();
      zeroFloat                   = (VM_Field)VM.getMember("LVM_Math;", "zero", "F");
      zeroOffset                  = zeroFloat.getOffset();
      halfFloat                   = (VM_Field)VM.getMember("LVM_Math;", "half", "F");
      halfOffset                  = halfFloat.getOffset();
      oneFloat                    = (VM_Field)VM.getMember("LVM_Math;", "one", "F");
      oneOffset                   = oneFloat.getOffset();
      twoFloat                    = (VM_Field)VM.getMember("LVM_Math;", "two", "F");
      twoOffset                   = twoFloat.getOffset();
      two32Offset                 = VM.getMember("LVM_Math;", "two32", "F").getOffset();
      half32Offset                = VM.getMember("LVM_Math;", "half32", "F").getOffset();
      zeroDouble                  = (VM_Field)VM.getMember("LVM_Math;", "zeroD", "D");
      oneDouble                   = (VM_Field)VM.getMember("LVM_Math;", "oneD", "D");
      oneDoubleOffset             = oneDouble.getOffset();
      billionthOffset             = VM.getMember("LVM_Math;", "billionth", "D").getOffset();
      maxintOffset                = VM.getMember("LVM_Math;", "maxint", "D").getOffset();
      minintOffset                = VM.getMember("LVM_Math;", "minint", "D").getOffset();
      IEEEmagic                   = (VM_Field)VM.getMember("LVM_Math;", "IEEEmagic", "D");
      IEEEmagicOffset             = IEEEmagic.getOffset();
      I2Dconstant                 = (VM_Field)VM.getMember("LVM_Math;", "I2Dconstant", "D");
      I2DconstantOffset           = I2Dconstant.getOffset();
 
      reflectiveMethodInvokerInstructionsOffset       = VM.getMember("LVM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();
      saveThreadStateInstructionsOffset               = VM.getMember("LVM_OutOfLineMachineCode;", "saveThreadStateInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();
      resumeThreadExecutionInstructionsOffset         = VM.getMember("LVM_OutOfLineMachineCode;", "resumeThreadExecutionInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();
      restoreHardwareExceptionStateInstructionsOffset = VM.getMember("LVM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();
//-#if RVM_FOR_POWERPC
      getTimeInstructionsOffset                       = VM.getMember("LVM_OutOfLineMachineCode;", "getTimeInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();
//-#endif
      invokeNativeFunctionInstructionsOffset          = VM.getMember("LVM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", INSTRUCTION_ARRAY_SIGNATURE).getOffset();


      outputLockOffset                                = VM.getMember("LVM_Scheduler;", "outputLock", "I").getOffset();

//-#if RVM_WITH_STRONG_VOLATILE_SEMANTICS
      doublewordVolatileMutexOffset                   = VM.getMember("LVM_Scheduler;", "doublewordVolatileMutex", "LVM_ProcessorLock;").getOffset();
//-#endif
      
      deterministicThreadSwitchCountOffset            = VM.getMember("LVM_Processor;", "deterministicThreadSwitchCount", "I").getOffset();

//-#if RVM_WITH_GCTk
      GCTk_WriteBufferBase = VM.getMember("LVM_Processor;", "writeBuffer0", "I").getOffset();
      GCTk_TraceBufferBase        = VM.getMember("LGCTk_TraceBuffer;", "bumpPtr_", "I").getOffset();
//-#endif
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
      modifiedOldObjectsTopOffset = VM.getMember("LVM_Processor;", "modifiedOldObjectsTop", "I").getOffset();
      modifiedOldObjectsMaxOffset = VM.getMember("LVM_Processor;", "modifiedOldObjectsMax", "I").getOffset();
      incDecBufferTopOffset       = VM.getMember("LVM_Processor;", "incDecBufferTop", "I").getOffset();
      incDecBufferMaxOffset       = VM.getMember("LVM_Processor;", "incDecBufferMax", "I").getOffset();
//-#endif
      scratchSecondsOffset        = VM.getMember("LVM_Processor;", "scratchSeconds", "D").getOffset();
      scratchNanosecondsOffset    = VM.getMember("LVM_Processor;", "scratchNanoseconds", "D").getOffset();
      threadSwitchRequestedOffset = VM.getMember("LVM_Processor;", "threadSwitchRequested", "I").getOffset();
      activeThreadOffset          = VM.getMember("LVM_Processor;", "activeThread", "LVM_Thread;").getOffset();

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
      vpStateOffset               = VM.getMember("LVM_Processor;", "vpState", "I").getOffset();
//-#else
      // default implementation of jni
      processorModeOffset         = VM.getMember("LVM_Processor;", "processorMode", "I").getOffset();
      vpStatusAddressOffset       = VM.getMember("LVM_Processor;", "vpStatusAddress", "I").getOffset();
//-#endif

      //-#if RVM_WITH_GCTk  // not supported by GCTk yet
      //-#else
      finalizerListElementValueOffset = VM.getMember("LVM_FinalizerListElement;", "value", "I").getOffset();
      finalizerListElementPointerOffset = VM.getMember("LVM_FinalizerListElement;", "pointer", "Ljava/lang/Object;").getOffset();
      //-#endif

      threadSwitchFromPrologueOffset = VM.getMember("LVM_Thread;", "threadSwitchFromPrologue", "()V").getOffset();
      threadSwitchFromBackedgeOffset = VM.getMember("LVM_Thread;", "threadSwitchFromBackedge", "()V").getOffset();
      threadSwitchFromEpilogueOffset = VM.getMember("LVM_Thread;", "threadSwitchFromEpilogue", "()V").getOffset();
      threadYieldOffset              = VM.getMember("LVM_Thread;", "yield", "()V").getOffset();
      becomeNativeThreadOffset       = VM.getMember("LVM_Thread;", "becomeNativeThread", "()V").getOffset();
      becomeRVMThreadOffset          = VM.getMember("LVM_Thread;", "becomeRVMThread", "()V").getOffset();
      stackLimitOffset               = VM.getMember("LVM_Thread;", "stackLimit", "I").getOffset();
      beingDispatchedOffset          = VM.getMember("LVM_Thread;", "beingDispatched", "Z").getOffset();
      threadSlotOffset               = VM.getMember("LVM_Thread;", "threadSlot", "I").getOffset();

//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
      areaCurrentAddressOffset        = VM.getMember("LVM_Allocator;", "areaCurrentAddress", "I").getOffset();
      matureCurrentAddressOffset      = VM.getMember("LVM_Allocator;", "matureCurrentAddress", "I").getOffset();

      allocCountOffset                = VM.getMember("LVM_BlockControl;", "allocCount", "I").getOffset();
//-#endif
      processorsOffset                = VM.getMember("LVM_Scheduler;", "processors", "[LVM_Processor;").getOffset();
      threadsOffset                   = VM.getMember("LVM_Scheduler;", "threads", "[LVM_Thread;").getOffset();

      latestContenderOffset           = VM.getMember("LVM_ProcessorLock;", "latestContender", "LVM_Processor;").getOffset();
      processorLockOffset             = VM.getMember("LVM_ProcessorLock;", "lock", "()V").getOffset();
      processorUnlockOffset           = VM.getMember("LVM_ProcessorLock;", "unlock", "()V").getOffset();

      classForTypeOffset              = VM.getMember("LVM_Type;", "classForType", "Ljava/lang/Class;").getOffset();

      //      growWriteBufferOffset           = VM.getMember("LVM_WriteBuffer;", "growWriteBuffer", "()V").getOffset();

      //-#if RVM_WITH_CONCURRENT_GC
      processIncDecBufferOffset       = VM.getMember("LVM_RCBuffers;", "processIncDecBuffer", "()V").getOffset();
      //-#endif

	jniEnvOffset                = VM.getMember("LVM_Thread;", "jniEnv", "LVM_JNIEnvironment;").getOffset();
	processorAffinityOffset     = VM.getMember("LVM_Thread;", "processorAffinity", "LVM_Processor;").getOffset();
	nativeAffinityOffset        = VM.getMember("LVM_Thread;", "nativeAffinity", "LVM_Processor;").getOffset();
	JNIEnvAddressOffset         = VM.getMember("LVM_JNIEnvironment;", "JNIEnvAddress", "I").getOffset();
	JNIEnvSavedTIOffset         = VM.getMember("LVM_JNIEnvironment;", "savedTIreg", "I").getOffset();
	JNIEnvSavedPROffset         = VM.getMember("LVM_JNIEnvironment;", "savedPRreg", "LVM_Processor;").getOffset();
	JNIRefsOffset               = VM.getMember("LVM_JNIEnvironment;", "JNIRefs", "[I").getOffset();
	JNIRefsTopOffset            = VM.getMember("LVM_JNIEnvironment;", "JNIRefsTop", "I").getOffset();
	JNIRefsMaxOffset            = VM.getMember("LVM_JNIEnvironment;", "JNIRefsMax", "I").getOffset();
	JNIRefsSavedFPOffset        = VM.getMember("LVM_JNIEnvironment;", "JNIRefsSavedFP", "I").getOffset();
	JNITopJavaFPOffset          = VM.getMember("LVM_JNIEnvironment;", "JNITopJavaFP", "I").getOffset();
	JNIPendingExceptionOffset   = VM.getMember("LVM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;").getOffset();
	JNIFunctionPointersOffset   = VM.getMember("LVM_JNIEnvironment;", "JNIFunctionPointers", "[I").getOffset();
        //-#if RVM_FOR_IA32
	FPUControlWordOffset        = VM.getMember("LVM_Math;", 
                                                   "FPUControlWord", 
                                                   "I").getOffset();
        //-#endif RVM_FOR_IA32
        
        //-#if RVM_WITH_GCTk
	ADDRESS top, bot;
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

        the_boot_recordOffset            = VM.getMember("LVM_BootRecord;", "the_boot_record", "LVM_BootRecord;").getOffset();
        globalGCInProgressFlagOffset     = VM.getMember("LVM_BootRecord;", "globalGCInProgressFlag", "I").getOffset();
        lockoutProcessorOffset           = VM.getMember("LVM_BootRecord;", "lockoutProcessor", "I").getOffset();
//-#if RVM_FOR_POWERPC
        sysTOCOffset                     = VM.getMember("LVM_BootRecord;", "sysTOC", "I").getOffset();
//-#endif
        sysVirtualProcessorYieldIPOffset = VM.getMember("LVM_BootRecord;", "sysVirtualProcessorYieldIP", "I").getOffset();
        externalSignalFlagOffset         = VM.getMember("LVM_BootRecord;", "externalSignalFlag", "I").getOffset();

//-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
      initializeClassIfNecessaryOffset = VM.getMember("LVM_Class;", "initializeClassIfNecessary", "(I)V").getOffset();
//-#endif

      m = arrayStoreWriteBarrierMethod = (VM_Method)VM.getMember("LVM_WriteBarrier;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      arrayStoreWriteBarrierOffset = m.getOffset();

      m = resolvedPutfieldWriteBarrierMethod = (VM_Method)VM.getMember("LVM_WriteBarrier;", "resolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      resolvedPutfieldWriteBarrierOffset = m.getOffset();

      m = unresolvedPutfieldWriteBarrierMethod = (VM_Method)VM.getMember("LVM_WriteBarrier;", "unresolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      unresolvedPutfieldWriteBarrierOffset = m.getOffset();

      m = resolvedPutStaticWriteBarrierMethod = (VM_Method)VM.getMember("LVM_WriteBarrier;", "resolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
      resolvedPutStaticWriteBarrierOffset = m.getOffset();

      m = unresolvedPutStaticWriteBarrierMethod = (VM_Method)VM.getMember("LVM_WriteBarrier;", "unresolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
      unresolvedPutStaticWriteBarrierOffset = m.getOffset();
//-#if RVM_WITH_READ_BARRIER
      m = arrayLoadReadBarrierMethod = (VM_Method)VM.getMember("LVM_ReadBarrier;", "arrayLoadReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      arrayLoadReadBarrierOffset = m.getOffset();

      m = resolvedGetfieldReadBarrierMethod = (VM_Method)VM.getMember("LVM_ReadBarrier;", "resolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      resolvedGetfieldReadBarrierOffset = m.getOffset();

      m = unresolvedGetfieldReadBarrierMethod = (VM_Method)VM.getMember("LVM_ReadBarrier;", "unresolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
      unresolvedGetfieldReadBarrierOffset = m.getOffset();

      m = resolvedGetStaticReadBarrierMethod = (VM_Method)VM.getMember("LVM_ReadBarrier;", "resolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
      resolvedGetStaticReadBarrierOffset = m.getOffset();

      m = unresolvedGetStaticReadBarrierMethod = (VM_Method)VM.getMember("LVM_ReadBarrier;", "unresolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
      unresolvedGetStaticReadBarrierOffset = m.getOffset();
//-#endif
   }
}
