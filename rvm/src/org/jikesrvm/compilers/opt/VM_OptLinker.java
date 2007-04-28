/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.*;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.classloader.*;
import org.vmmagic.unboxed.Offset;

/**
 * Routines for dynamic linking and other misc hooks from opt-compiled code to
 * runtime services.
 *
 * @see org.jikesrvm.ArchitectureSpecific.OPT_FinalMIRExpansion
 * @see VM_OptSaveVolatile (transitions from compiled code to resolveDynamicLink)
 * @see VM_TableBasedDynamicLinker 
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
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
  public static void resolveDynamicLink (VM_OptCompiledMethod cm, Offset offset) 
    throws NoClassDefFoundError {
    VM_OptMachineCodeMap map = cm.getMCMap();
    int bci = map.getBytecodeIndexForMCOffset(offset);
    VM_NormalMethod realMethod = map.getMethodForMCOffset(offset);
    if (bci == -1 || realMethod == null)
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    VM_BytecodeStream bcodes = realMethod.getBytecodes();
    bcodes.reset(bci);
    int opcode = bcodes.nextInstruction();
    switch (opcode) {
    case JBC_getfield: case JBC_putfield: 
    case JBC_getstatic: case JBC_putstatic: 
      VM_TableBasedDynamicLinker.resolveMember(bcodes.getFieldReference());
      break;
    case JBC_invokevirtual:case JBC_invokestatic:case JBC_invokespecial:       
      VM_TableBasedDynamicLinker.resolveMember(bcodes.getMethodReference());
      break;
    case JBC_invokeinterface:
    default:
      if (VM.VerifyAssertions)
        VM._assert(VM.NOT_REACHED, 
                  "Unexpected case in VM_OptLinker.resolveDynamicLink");
      break;
    }
  }
}
