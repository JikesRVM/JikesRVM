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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_BytecodeConstants;
import org.jikesrvm.classloader.VM_BytecodeStream;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TableBasedDynamicLinker;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Runtime;
import org.vmmagic.unboxed.Offset;

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 *
 * @see org.jikesrvm.ArchitectureSpecific.FinalMIRExpansion
 * @see VM_OptSaveVolatile (transitions from compiled code to resolveDynamicLink)
 * @see VM_TableBasedDynamicLinker
 */
public final class VM_OptLinker implements VM_BytecodeConstants {

  /**
   * Given an opt compiler info and a machine code offset in that method's
   * instruction array, perform the dynamic linking required by that
   * instruction.
   * <p>
   * We do this by mapping back to the source VM_Method and bytecode offset,
   * then examining the bytecodes to see what field/method was being
   * referenced, then calling VM_TableBasedDynamicLinker to do the real work.
   */
  public static void resolveDynamicLink(VM_OptCompiledMethod cm, Offset offset) throws NoClassDefFoundError {
    VM_OptMachineCodeMap map = cm.getMCMap();
    int bci = map.getBytecodeIndexForMCOffset(offset);
    VM_NormalMethod realMethod = map.getMethodForMCOffset(offset);
    if (bci == -1 || realMethod == null) {
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    }
    VM_BytecodeStream bcodes = realMethod.getBytecodes();
    bcodes.reset(bci);
    int opcode = bcodes.nextInstruction();
    switch (opcode) {
      case JBC_getfield:
      case JBC_putfield:
      case JBC_getstatic:
      case JBC_putstatic:
        VM_TableBasedDynamicLinker.resolveMember(bcodes.getFieldReference());
        break;
      case JBC_invokevirtual:
      case JBC_invokestatic:
      case JBC_invokespecial:
        VM_TableBasedDynamicLinker.resolveMember(bcodes.getMethodReference());
        break;
      case JBC_invokeinterface:
      default:
        if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED, "Unexpected case in VM_OptLinker.resolveDynamicLink");
        }
        break;
    }
  }

  /*
   * Method referenced from VM_Entrypoints
   */
  public static Object newArrayArray(int methodId, int[] dimensions, int typeId)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    // validate arguments
    for (int dimension : dimensions) {
      if (dimension < 0) throw new NegativeArraySizeException();
    }
    // create array
    //
    VM_Array aType = (VM_Array) VM_TypeReference.getTypeRef(typeId).resolve();
    return VM_Runtime.buildMultiDimensionalArray(methodId, dimensions, aType);
  }
}
