/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/** 
 * Methods and indices needed by the OPT compiler for code generation.
 * 
 * @author Stephen Fink
 */
public class OPT_Entrypoints implements VM_Constants {
   // methods
  static VM_Method optLockMethod;
  static VM_Method optUnlockMethod;
  static VM_Method unlockAndThrow;
  //-#if RVM_WITH_CONCURRENT_GC
  static VM_Method RCGC_aastoreMethod;
  static VM_Method RCGC_resolvedPutfieldMethod;
  static VM_Method RCGC_unresolvedPutfieldMethod;
  static VM_Method RCGC_resolvedPutstaticMethod;
  static VM_Method RCGC_unresolvedPutstaticMethod;
  //-#endif
  static VM_Field specializedMethodsField;
  static int specializedMethodsOffset;
  static VM_Method threadSwitchFromPrologueMethod;
  static VM_Method threadSwitchFromBackedgeMethod;

  /**
   * Initialize the static members of this class.
   */
  static void init () {
    threadSwitchFromPrologueMethod = (VM_Method) VM.getMember("LVM_Thread;", "threadSwitchFromPrologue", "()V");
    threadSwitchFromBackedgeMethod = (VM_Method) VM.getMember("LVM_Thread;", "threadSwitchFromBackedge", "()V");
    optLockMethod = 
      (VM_Method)VM.getMember("LVM_Lock;", "inlineLock", "(Ljava/lang/Object;)V");
    optUnlockMethod = 
      (VM_Method)VM.getMember("LVM_Lock;", "inlineUnlock", "(Ljava/lang/Object;)V");
    unlockAndThrow = 
      (VM_Method)VM.getMember("LVM_Runtime;", "unlockAndThrow", 
			      "(Ljava/lang/Object;Ljava/lang/Throwable;)V");
    //-#if RVM_WITH_CONCURRENT_GC
    RCGC_aastoreMethod = 
      (VM_Method)VM.getMember("LVM_OptRCWriteBarrier;", 
			      "aastore", 
			      "(Ljava/lang/Object;ILjava/lang/Object;)V");
    RCGC_resolvedPutfieldMethod = 
      (VM_Method)VM.getMember("LVM_OptRCWriteBarrier;", 
			      "resolvedPutfield", 
			      "(Ljava/lang/Object;ILjava/lang/Object;)V");
    RCGC_unresolvedPutfieldMethod = 
      (VM_Method)VM.getMember("LVM_OptRCWriteBarrier;", 
			      "unresolvedPutfield", 
			      "(Ljava/lang/Object;ILjava/lang/Object;)V");
    RCGC_resolvedPutstaticMethod = 
      (VM_Method)VM.getMember("LVM_OptRCWriteBarrier;", 
			      "resolvedPutstatic", 
			      "(ILjava/lang/Object;)V");
    RCGC_unresolvedPutstaticMethod = 
      (VM_Method)VM.getMember("LVM_OptRCWriteBarrier;", 
			      "unresolvedPutstatic", 
			      "(ILjava/lang/Object;)V");
    //-#endif
    specializedMethodsField = 
      (VM_Field)VM.getMember("LOPT_SpecializedMethodPool;", 
			     "specializedMethods", 
			     INSTRUCTION_ARRAY_ARRAY_SIGNATURE);
    specializedMethodsOffset = 
      VM.getMember("LOPT_SpecializedMethodPool;", 
		   "specializedMethods", 
		   INSTRUCTION_ARRAY_ARRAY_SIGNATURE).getOffset();

  }
  static private String INSTRUCTION_ARRAY_ARRAY_SIGNATURE = "[" + 
      INSTRUCTION_ARRAY_SIGNATURE;
}





