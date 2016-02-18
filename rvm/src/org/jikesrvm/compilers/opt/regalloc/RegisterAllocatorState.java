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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * The register allocator currently caches a bunch of state in the IR;
 * This class provides accessors to this state.
 * <ul>
 *   <li>TODO: Consider caching the state in a lookaside structure.
 *   <li>TODO: Currently, the physical registers are STATIC! fix this.
 * </ul>
 */
public class RegisterAllocatorState {

  private final int[] spills;

  private final CompoundInterval[] intervals;

  private Map<Instruction, Integer> depthFirstNumbers;

  RegisterAllocatorState(int registerCount) {
    spills = new int[registerCount];
    intervals = new CompoundInterval[registerCount];
  }

  /**
   * Resets the physical register info.
   *
   * @param ir the IR whose info is to be reset
   */
  void resetPhysicalRegisters(IR ir) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Enumeration<Register> e = phys.enumerateAll(); e.hasMoreElements();) {
      Register reg = e.nextElement();
      reg.deallocateRegister();
      reg.mapsToRegister = null;  // mapping from real to symbolic
      reg.defList = null;
      reg.useList = null;
      setSpill(reg, 0);
    }
  }

  void setSpill(Register reg, int spill) {
    reg.spillRegister();
    spills[reg.number] = spill;
  }

  public int getSpill(Register reg) {
    return spills[reg.number];
  }

  /**
   * Records that register A and register B are associated with each other
   * in a bijection.<p>
   *
   * The register allocator uses this state to indicate that a symbolic
   * register is presently allocated to a physical register.
   *
   * @param A first register
   * @param B second register
   */
  void mapOneToOne(Register A, Register B) {
    Register aFriend = getMapping(A);
    Register bFriend = getMapping(B);
    if (aFriend != null) {
      aFriend.mapsToRegister = null;
    }
    if (bFriend != null) {
      bFriend.mapsToRegister = null;
    }
    A.mapsToRegister = B;
    B.mapsToRegister = A;
  }

  /**
   * @param r a register
   * @return the register currently mapped 1-to-1 to r
   */
  Register getMapping(Register r) {
    return r.mapsToRegister;
  }

  /**
   * Clears any 1-to-1 mapping for a register.
   *
   * @param r the register whose mapping is to be cleared
   */
  void clearOneToOne(Register r) {
    if (r != null) {
      Register s = getMapping(r);
      if (s != null) {
        s.mapsToRegister = null;
      }
      r.mapsToRegister = null;
    }
  }

  /**
   *  Returns the interval associated with the passed register.
   *  @param reg the register
   *  @return the live interval or {@code null}
   */
  CompoundInterval getInterval(Register reg) {
    return intervals[reg.number];
  }

  /**
   * Initializes data structures for depth first numbering.
   * @param instructionCount an estimate of the total number of instructions.
   */
  void initializeDepthFirstNumbering(int instructionCount) {
    int noRehashCapacity = (int) (instructionCount * 1.5f);
    depthFirstNumbers = new HashMap<Instruction, Integer>(noRehashCapacity);
  }

  /**
   *  Associates the passed live interval with the passed register.
   *
   *  @param reg the register
   *  @param interval the live interval
   */
  void setInterval(Register reg, CompoundInterval interval) {
    intervals[reg.number] = interval;
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  void setDFN(Instruction inst, int dfn) {
    depthFirstNumbers.put(inst, Integer.valueOf(dfn));
  }

  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  public int getDFN(Instruction inst) {
    return depthFirstNumbers.get(inst);
  }

  /**
   *  Prints the DFN numbers associated with each instruction.
   *
   *  @param ir the IR that contains the instructions
   */
  void printDfns(IR ir) {
    System.out.println("DFNS: **** " + ir.getMethod() + "****");
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      System.out.println(getDFN(inst) + " " + inst);
    }
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the beginning of the live interval. If the
   * interval is open-ended, the dfn for the beginning of the basic block will
   * be returned instead.
   */
  int getDfnBegin(LiveIntervalElement live, BasicBlock bb) {
    Instruction begin = live.getBegin();
    int dfnBegin;
    if (begin != null) {
      dfnBegin = getDFN(begin);
    } else {
      dfnBegin = getDFN(bb.firstInstruction());
    }
    return dfnBegin;
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the end of the live interval. If the
   * interval is open-ended, the dfn for the end of the basic block will
   * be returned instead.
   */
  int getDfnEnd(LiveIntervalElement live, BasicBlock bb) {
    Instruction end = live.getEnd();
    int dfnEnd;
    if (end != null) {
      dfnEnd = getDFN(end);
    } else {
      dfnEnd = getDFN(bb.lastInstruction());
    }
    return dfnEnd;
  }

}
