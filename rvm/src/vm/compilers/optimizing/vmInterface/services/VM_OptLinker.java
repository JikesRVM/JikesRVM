/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 *
 * <p> The main idea for dynamic linking is that VM_Classloader maintains 
 * an array of field and method offsets indexed by fid and mid.  
 * The generated code at a dynamically linked site will 
 * load the appropriate value from the field/method offset table and 
 * check to see if the value is valid. If it is, then no dynamic linking 
 * is required.  If the value is invalid, then either resolveDynamicLink
 * is invoked to perfrom dynamic class loading.  During the
 * process of class loading, the required value will be stored in the 
 * appropriate offset table.
 * Thus when the resolve method returns, execution can be restarted 
 * by reloading/indexing the offset table.
 *
 * <p> NOTE: We believe that only use of invokespecial that could possibly 
 * require dynamic linking is that of invoking an object initializer.
 * As part of creating the uninitialized instance of an object, the 
 * runtime system must have invoked the class loader to load the class 
 * and therefore by the time the call to the init code is executed, 
 * the method offset table will contain a valid value.
 *
 * @see OPT_ConvertToLowerLevelIR
 * @see OPT_FinalMIRExpansion
 * @see VM_OptSaveVolatile (resolve method transitions from compiled code to 
 *                          resolveDynamicLink)
 * @see VM_Linker (used by the baseline compiler, but takes a different 
 *                 approach)
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 */
public final class VM_OptLinker implements VM_BytecodeConstants {

  //---------------------------------------------------------------//
  //                     Dynamic Linking.                          //
  //---------------------------------------------------------------//
  /**
   * Given an opt compiler info and a machine code offset in that method's 
   * instruction array,
   * perform the dynamic linking required by that instruction.
   * We do this by mapping back to the source VM_Method and bytecode offset, 
   * then examining the bytecodes to
   * see what field/method was being referenced, then calling into 
   * the runtime system to cause the declaring
   * class of the desired field/method to be fully loaded. 
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
    VM_Class declaringClass = target.getDeclaringClass();
    if (VM.TraceDynamicLinking) {
      VM.sysWrite(realMethod);
      VM.sysWrite(" at bci ");
      VM.sysWrite(bci, false);
      VM.sysWrite(" is causing dynamic loading of ");
      VM.sysWrite(declaringClass.getDescriptor());
      VM.sysWrite(" due to ");
      VM.sysWrite(target);
      VM.sysWrite("\n");
    }
    VM_Runtime.initializeClassForDynamicLink(declaringClass);

    // Check for a ghost reference and patch the extra table entry if necessary
    if (target instanceof VM_Field) {
      VM_Field t = (VM_Field)target;
      VM_Field rt = t.resolve();
      if (rt != t) {
	VM_ClassLoader.setFieldOffset(t, rt.getOffset());
      }
    } else {
      VM_Method t = (VM_Method)target;
      VM_Method rt = t.resolve();
      if (rt != t) {
	VM_ClassLoader.setMethodOffset(t, rt.getOffset());
      }
    }
  }

  //---------------------------------------------------------------//
  //                     Misc Runtime Services                     //
  //---------------------------------------------------------------//
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
  static final VM_Method optWriteBufferMethod = 
      (VM_Method)VM.getMember("LVM_OptSaveVolatile;", 
      "OPT_growWriteBuffer", "()V");
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



