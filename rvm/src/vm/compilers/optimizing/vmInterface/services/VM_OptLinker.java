/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 *
 * @see OPT_ConvertToLowerLevelIR
 * @see OPT_FinalMIRExpansion
 * @see VM_OptSaveVolatile (transitions from compiled code to resolveDynamicLink)
 * @see VM_TableBasedDynamicLinker 
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 */
public final class VM_OptLinker implements VM_BytecodeConstants {

  /**
   * Given an opt compiler info and a machine code offset in that method's 
   * instruction array, perform the dynamic linking required by that instruction.
   * We do this by mapping back to the source VM_Method and bytecode offset, 
   * then examining the bytecodes to see what field/method was being referenced,
   * then calling VM_TableBasedDynamicLinker to do the actually work.
   */
  public static void resolveDynamicLink (VM_OptCompilerInfo info, int offset) 
    throws VM_ResolutionException {
    VM_OptMachineCodeMap map = info.getMCMap();
    int bci = map.getBytecodeIndexForMCOffset(offset);
    VM_Method realMethod = map.getMethodForMCOffset(offset);
    if (bci == -1 || realMethod == null)
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    byte[] bytecodes = realMethod.getBytecodes();
    int bytecode = bytecodes[bci] & 0xFF;
    int constantPoolIndex = ((bytecodes[bci + 1] & 0xFF) << 8) | (bytecodes[
        bci + 2] & 0xFF);
    VM_Member target = null;
    switch (bytecode) {
    case JBC_getfield: case JBC_putfield: 
    case JBC_getstatic: case JBC_putstatic:
      target = realMethod.getDeclaringClass().getFieldRef(constantPoolIndex);
      break;
    case JBC_invokevirtual:case JBC_invokestatic:
      target = realMethod.getDeclaringClass().getMethodRef(constantPoolIndex);
      break;
    case JBC_invokeinterface:
      // We believe that in the RVM this cannot cause dynamic linking
    case JBC_invokespecial:       
      // We believe that this cannot cause dynamic linking
    default:
      if (VM.VerifyAssertions)
	VM.assert(VM.NOT_REACHED, 
		  "Unexpected case in VM_OptLinker.resolveDynamicLink");
      break;
    }
    if (VM.TraceDynamicLinking) {
      VM_Class declaringClass = target.getDeclaringClass();
      VM.sysWrite(realMethod);
      VM.sysWrite(" at bci ");
      VM.sysWrite(bci, false);
      VM.sysWrite(" is causing dynamic loading of ");
      VM.sysWrite(declaringClass.getDescriptor());
      VM.sysWrite(" due to ");
      VM.sysWrite(target);
      VM.sysWrite("\n");
    }
    VM_TableBasedDynamicLinker.resolveMember(target);
  }

  public static Object newArrayArray (int[] dimensions, int dictionaryId) 
      throws VM_ResolutionException, 
      NegativeArraySizeException, OutOfMemoryError {
    // validate arguments
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] < 0)
        throw  new NegativeArraySizeException();
    }
    // create array
    //
    VM_Array aType = VM_TypeDictionary.getValue(dictionaryId).asArray();
    return VM_Runtime.buildMultiDimensionalArray(dimensions, 0, aType);
  }

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  public static Object allocAdviceNewArrayArray(int[] dimensions, int dictionaryId, int generation) 
    throws VM_ResolutionException, NegativeArraySizeException, 
	   OutOfMemoryError { 
    
    // validate arguments
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] < 0)
	throw new NegativeArraySizeException();
    }
    
    // create array
    //
    Object arrayObject;
    //synchronized (VM_Runtime.lock) { removed since the callee acquires and lock and the references lock does not exit. Maria
    arrayObject = VM_Runtime.buildMultiDimensionalArray(dimensions, 
							  0, VM_TypeDictionary.getValue(dictionaryId).asArray(), generation);
    //}
    return arrayObject;
  }
  //-#endif

  // These are cached references to VM_Methods and VM_Fields for
  // "machine code helper" methods that provide services for java 
  // bytecodes that cannot be mapped directly into native machine 
  // code.
  //
  static final VM_Field threadTable = (VM_Field)VM.getMember("LVM_Scheduler;", 
      "threads", "[LVM_Thread;");
  static final VM_Method optThreadSwitchFromPrologueMethod = 
      (VM_Method)VM.getMember("LVM_OptSaveVolatile;", 
			      "OPT_threadSwitchFromPrologue", "()V");
  static final VM_Method optThreadSwitchFromBackedgeMethod = 
      (VM_Method)VM.getMember("LVM_OptSaveVolatile;", 
			      "OPT_threadSwitchFromBackedge", "()V");
  static final VM_Method optThreadSwitchFromEpilogueMethod = 
      (VM_Method)VM.getMember("LVM_OptSaveVolatile;", 
      "OPT_threadSwitchFromEpilogue", "()V");
  static final VM_Method optResolveMethod = 
    (VM_Method)VM.getMember("LVM_OptSaveVolatile;", 
			    "OPT_resolve", "()V");
  static final VM_Method invokeinterfaceMethod = 
    (VM_Method)VM.getMember("LVM_Runtime;", 
			    "invokeInterface", 
			    "(Ljava/lang/Object;I)"+VM.INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Method newArrayArrayMethod = 
    (VM_Method)VM.getMember("LVM_OptLinker;", 
			    "newArrayArray", 
			    "([II)Ljava/lang/Object;");
  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method allocAdviceNewArrayArrayMethod = 
    (VM_Method)VM.getMember("LVM_OptLinker;",
			    "allocAdviceNewArrayArray",
			    "([III)Ljava/lang/Object;");
  //-#endif
  static final VM_Field fieldOffsetsField = 
    (VM_Field)VM.getMember("LVM_ClassLoader;", 
			   "fieldOffsets", "[I");
  static final VM_Field methodOffsetsField = 
    (VM_Field)VM.getMember("LVM_ClassLoader;", 
			   "methodOffsets", "[I");
  static final VM_Field longDivIP = 
    (VM_Field)VM.getMember("LVM_BootRecord;", "sysLongDivideIP", "I");
  static final VM_Field longDivTOC = 
    (VM_Field)VM.getMember("LVM_BootRecord;", "sysLongDivideTOC", "I");
  static final VM_Field longRemIP = 
    (VM_Field)VM.getMember("LVM_BootRecord;", "sysLongRemainderIP", "I");
  static final VM_Field longRemTOC = 
    (VM_Field)VM.getMember("LVM_BootRecord;", "sysLongRemainderTOC", "I");
}



