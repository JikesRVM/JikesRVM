/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.runtimesupport;

import static org.jikesrvm.classloader.BytecodeConstants.JBC_getfield;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_getstatic;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokeinterface;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokespecial;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokestatic;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_invokevirtual;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_putfield;
import static org.jikesrvm.classloader.BytecodeConstants.JBC_putstatic;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.TableBasedDynamicLinker;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.unboxed.Offset;

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 * <p>
 * This class is used in the final mir expansion which is done in an
 * architecture-dependent way by the FinalMIRExpansion classes.
 *
 * @see OptSaveVolatile (transitions from compiled code to resolveDynamicLink)
 * @see TableBasedDynamicLinker
 */
public final class OptLinker {

  /**
   * Given an opt compiler info and a machine code offset in that method's
   * instruction array, performs the dynamic linking required by that
   * instruction.
   * <p>
   * We do this by mapping back to the source RVMMethod and bytecode offset,
   * then examining the bytecodes to see what field/method was being
   * referenced, then calling TableBasedDynamicLinker to do the real work.
   *
   * @param cm the opt compiled method
   * @param offset machine code offset
   */
  public static void resolveDynamicLink(OptCompiledMethod cm, Offset offset) throws NoClassDefFoundError {
    OptMachineCodeMap map = cm.getMCMap();
    int bci = map.getBytecodeIndexForMCOffset(offset);
    NormalMethod realMethod = map.getMethodForMCOffset(offset);
    if (bci == -1 || realMethod == null) {
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    }
    BytecodeStream bcodes = realMethod.getBytecodes();
    bcodes.reset(bci);
    int opcode = bcodes.nextInstruction();
    switch (opcode) {
      case JBC_getfield:
      case JBC_putfield:
      case JBC_getstatic:
      case JBC_putstatic:
        TableBasedDynamicLinker.resolveMember(bcodes.getFieldReference());
        break;
      case JBC_invokevirtual:
      case JBC_invokestatic:
      case JBC_invokespecial:
        TableBasedDynamicLinker.resolveMember(bcodes.getMethodReference());
        break;
      case JBC_invokeinterface:
      default:
        if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED, "Unexpected case in OptLinker.resolveDynamicLink");
        }
        break;
    }
  }

  @Entrypoint
  public static Object newArrayArray(int methodId, int[] dimensions, int typeId)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    // validate arguments
    for (int dimension : dimensions) {
      if (dimension < 0) throw new NegativeArraySizeException();
    }
    // create array
    //
    RVMArray aType = (RVMArray) TypeReference.getTypeRef(typeId).resolve();
    return RuntimeEntrypoints.buildMultiDimensionalArray(methodId, dimensions, aType);
  }

  @Entrypoint
  public static Object new2DArray(int methodId, int dim0, int dim1, int typeId)
      throws NoClassDefFoundError, NegativeArraySizeException, OutOfMemoryError {
    // validate arguments
    if ((dim0 < 0) || (dim1 < 0)) throw new NegativeArraySizeException();

    // create array
    //
    RVMArray aType = (RVMArray) TypeReference.getTypeRef(typeId).resolve();
    return RuntimeEntrypoints.buildTwoDimensionalArray(methodId, dim0, dim1, aType);
  }
}
