/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 *
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
  public static void resolveDynamicLink (VM_OptCompiledMethod cm, int offset) 
    throws VM_ResolutionException {
    VM_OptMachineCodeMap map = cm.getMCMap();
    int bci = map.getBytecodeIndexForMCOffset(offset);
    VM_Method realMethod = map.getMethodForMCOffset(offset);
    if (bci == -1 || realMethod == null)
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    byte[] bytecodes = realMethod.getRawBytecodes();
    int bytecode = bytecodes[bci] & 0xFF;
    int cpi = ((bytecodes[bci + 1] & 0xFF) << 8) | (bytecodes[bci + 2] & 0xFF);
    switch (bytecode) {
    case JBC_getfield: case JBC_putfield: 
    case JBC_getstatic: case JBC_putstatic: 
      { 
	int fid = realMethod.getDeclaringClass().getFieldRefId(cpi);
	VM_TableBasedDynamicLinker.resolveField(fid);
      }
      break;
    case JBC_invokevirtual:case JBC_invokestatic:
      {
	int mid = realMethod.getDeclaringClass().getMethodRefId(cpi);
	VM_TableBasedDynamicLinker.resolveMethod(mid);
      }
      break;
    case JBC_invokeinterface:
      // We believe that in the RVM this cannot cause dynamic linking
    case JBC_invokespecial:       
      // We believe that the offsets will always be valid for an 
      // invokespecial; As part of creating the uninitialized instance 
      // of an object, the runtime system must have invoked the class loader 
      // to load the class and therefore by the time the call to the init 
      // code is executed, the method offset table will contain a valid value.
    default:
      if (VM.VerifyAssertions)
	VM._assert(VM.NOT_REACHED, 
		  "Unexpected case in VM_OptLinker.resolveDynamicLink");
      break;
    }
  }

  public static Object newArrayArray (int[] dimensions, int dictionaryId) 
      throws VM_ResolutionException, 
	     NegativeArraySizeException, 
	     OutOfMemoryError {
    // validate arguments
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] < 0) throw new NegativeArraySizeException();
    }
    // create array
    //
    VM_Array aType = VM_ClassLoader.getTypeFromId(dictionaryId).asArray();
    return VM_Runtime.buildMultiDimensionalArray(dimensions, 0, aType);
  }

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  public static Object allocAdviceNewArrayArray(int[] dimensions, 
						int dictionaryId, 
						int generation) 
    throws VM_ResolutionException, 
	   NegativeArraySizeException, 
	   OutOfMemoryError { 
    
    // validate arguments
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] < 0)
	throw new NegativeArraySizeException();
    }
    
    // create array
    //
    return VM_Runtime.buildMultiDimensionalArray(dimensions, 
						 0, VM_ClassLoader.getTypeFromId(dictionaryId).asArray(), generation);
  }
  //-#endif
}



