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
package org.jikesrvm.compilers.opt.mir2mc;

import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE_opcode;

import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.VM;

/**
 * Saves machine code offsets during the compilation of the method.
 * <p>
 * Information that is needed at runtime is saved in other classes,
 * e.g. {@link org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap}.
 */
public final class MachineCodeOffsets {

  private final Map<Instruction, Integer> mcOffsets;

  MachineCodeOffsets() {
    this.mcOffsets = new HashMap<Instruction, Integer>();
  }

  /**
   * This method is only for use by opt assemblers to generate code.
   * It sets the machine code offset of the instruction as described in
   * {@link #getMachineCodeOffset(Instruction)}.
   *
   * @param inst the instruction whose offset will be set
   * @param mcOffset the offset (in bytes) for the instruction
   */
  public void setMachineCodeOffset(Instruction inst, int mcOffset) {
    mcOffsets.put(inst, Integer.valueOf(mcOffset));
  }

  /**
   * Gets the offset into the machine code array (in bytes) that
   * corresponds to the first byte after this instruction.<p>
   * This method only returns a valid value after it has been set as a
   * side-effect of a call to generateCode in AssemblerOpt during final assembly.<p>
   * To get the offset in INSTRUCTIONs you must shift by LG_INSTRUCTION_SIZE.<p>
   *
   * @param inst the instruction whose offset is queried
   * @return the offset (in bytes) of the machinecode instruction
   *  generated for the IR instruction in the final machinecode
   * @throws OptimizingCompilerException when no machine code offset is present for
   *  the instruction
   */
  public int getMachineCodeOffset(Instruction inst) {
    Integer offset = mcOffsets.get(inst);
    if (offset == null) {
      throw new OptimizingCompilerException("No valid machine code offset was ever set for instruction " + inst);
    }
    return offset.intValue();
  }

  /**
   * Checks whether a machine code offset is missing for the instruction.
   *
   * @param inst the instruction to check
   * @return {@code true} if the instruction never had a machine code offset
   *  set
   */
  public boolean lacksMachineCodeOffset(Instruction inst) {
    return mcOffsets.get(inst) == null;
  }

  /**
   * Fabricates an offset for prologue instructions in methods that are not
   * interruptible to deal with an oddity.
   * <p>
   * Note: General clients must not call this method.
   *
   * @param instr a prologue instruction in a method that's not interruptible
   */
  public void fabricateMachineCodeOffsetForPrologueInstruction(Instruction instr) {
    if (VM.VerifyAssertions) {
      boolean prologueInstr = instr.getOpcode() == IR_PROLOGUE_opcode;
      boolean hasNoValidOffset = lacksMachineCodeOffset(instr);
      if (!prologueInstr || !hasNoValidOffset) {
        VM.sysWriteln("Instruction " + instr);
      }
      VM._assert(prologueInstr, "Instruction was not a valid argument for this method!");
      VM._assert(hasNoValidOffset, "Instruction already had a valid machine code offset!");
    }
    // Use zero as a value because this value was used for instructions that had no
    // machine code offset set before the machine code offset information was
    // moved to this class.
    mcOffsets.put(instr, Integer.valueOf(0));
  }

}
