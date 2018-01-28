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
package org.jikesrvm.compilers.opt;

import static org.jikesrvm.compilers.opt.ir.Operators.PHI;

import java.util.Arrays;
import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.vmmagic.pragma.NoInline;

/**
 * This class computes du-lists and associated information.
 *
 * <P> Note: DU operands are stored on the USE lists, but not the DEF
 * lists.
 */
public final class DefUse {
  static final boolean DEBUG = false;
  static final boolean TRACE_DU_ACTIONS = false;
  static final boolean SUPRESS_DU_FOR_PHYSICALS = true;

  /**
   * Clear defList, useList for an IR.
   *
   * @param ir the IR in question
   */
  public static void clearDU(IR ir) {
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      reg.defList = null;
      reg.useList = null;
      reg.clearSeenUse();
    }
    for (Enumeration<Register> e = ir.regpool.getPhysicalRegisterSet().enumerateAll(); e.hasMoreElements();) {
      Register reg = e.nextElement();
      reg.defList = null;
      reg.useList = null;
      reg.clearSeenUse();
    }

    if (TRACE_DU_ACTIONS || DEBUG) {
      VM.sysWriteln("Cleared DU");
    }
  }

  /**
   * Compute the register list and def-use lists for a method.
   *
   * @param ir the IR in question
   */
  @NoInline
  public static void computeDU(IR ir) {
    // Clear old register list (if any)
    clearDU(ir);
    // Create register defList and useList
    for (Instruction instr = ir.firstInstructionInCodeOrder(); instr != null; instr =
        instr.nextInstructionInCodeOrder()) {

      Enumeration<Operand> defs = instr.getPureDefs();
      Enumeration<Operand> uses = instr.getUses();

      while (defs.hasMoreElements()) {
        Operand op = defs.nextElement();
        if (op instanceof RegisterOperand) {
          RegisterOperand rop = (RegisterOperand) op;
          recordDef(rop);
        }
      }         // for ( defs = ... )

      while (uses.hasMoreElements()) {
        Operand op = uses.nextElement();
        if (op instanceof RegisterOperand) {
          RegisterOperand rop = (RegisterOperand) op;
          recordUse(rop);
        }
      }         // for ( uses = ... )
    }           // for ( instr = ... )
    // Remove any symbloic registers with no uses/defs from
    // the register pool.  We'll waste analysis time keeping them around.
    Register next;
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = next) {
      next = reg.getNext();
      if (reg.defList == null && reg.useList == null) {
        if (DEBUG) {
          VM.sysWriteln("Removing " + reg + " from the register pool");
        }
        ir.regpool.removeRegister(reg);
      }
    }
  }

  /**
   * Record a use of a register
   * @param regOp the operand that uses the register
   */
  public static void recordUse(RegisterOperand regOp) {
    Register reg = regOp.getRegister();
    regOp.setNext(reg.useList);
    reg.useList = regOp;
    reg.useCount++;
  }

  /**
   * Record a def/use of a register
   * TODO: For now we just pretend this is a use!!!!
   *
   * @param regOp the operand that uses the register
   */
  public static void recordDefUse(RegisterOperand regOp) {
    Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    regOp.setNext(reg.useList);
    reg.useList = regOp;
  }

  /**
   * Record a def of a register
   * @param regOp the operand that uses the register
   */
  public static void recordDef(RegisterOperand regOp) {
    Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    regOp.setNext(reg.defList);
    reg.defList = regOp;
  }

  /**
   * Record that a use of a register no longer applies
   * @param regOp the operand that uses the register
   */
  public static void removeUse(RegisterOperand regOp) {
    Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    if (regOp == reg.useList) {
      reg.useList = reg.useList.getNext();
    } else {
      RegisterOperand prev = reg.useList;
      RegisterOperand curr = prev.getNext();
      while (curr != regOp) {
        prev = curr;
        curr = curr.getNext();
      }
      prev.setNext(curr.getNext());
    }
    reg.useCount--;
    if (DEBUG) {
      VM.sysWriteln("removed a use " + regOp.instruction);
      printUses(reg);
    }
  }

  /**
   * Record that a def of a register no longer applies
   * @param regOp the operand that uses the register
   */
  public static void removeDef(RegisterOperand regOp) {
    Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    if (regOp == reg.defList) {
      reg.defList = reg.defList.getNext();
    } else {
      RegisterOperand prev = reg.defList;
      RegisterOperand curr = prev.getNext();
      while (curr != regOp) {
        prev = curr;
        curr = curr.getNext();
      }
      prev.setNext(curr.getNext());
    }
    if (DEBUG) {
      VM.sysWriteln("removed a def " + regOp.instruction);
      printDefs(reg);
    }
  }

  /**
   *  This code changes the use in <code>origRegOp</code> to use
   *    the use in <code>newRegOp</code>.
   *
   *  <p> If the type of <code>origRegOp</code> is not a reference, but the
   *  type of <code>newRegOp</code> is a reference, we need to update
   *  <code>origRegOp</code> to be a reference.
   *  Otherwise, the GC map code will be incorrect.   -- Mike Hind
   *  @param origRegOp the register operand to change
   *  @param newRegOp the register operand to use for the change
   */
  public static void transferUse(RegisterOperand origRegOp, RegisterOperand newRegOp) {
    if (VM.VerifyAssertions) {
      VM._assert(origRegOp.getRegister().getType() == newRegOp.getRegister().getType());
    }
    Instruction inst = origRegOp.instruction;
    if (DEBUG) {
      VM.sysWriteln("Transfering a use of " + origRegOp + " in " + inst + " to " + newRegOp);
    }
    removeUse(origRegOp);
    // check to see if the regOp type is NOT a ref, but the newRegOp type
    // is a reference.   This can occur because of magic calls.
    if (!origRegOp.getType().isReferenceType() && newRegOp.getType().isReferenceType()) {
      // clone the newRegOp object and use it to replace the regOp object
      RegisterOperand copiedRegOp = (RegisterOperand) newRegOp.copy();
      inst.replaceOperand(origRegOp, copiedRegOp);
      recordUse(copiedRegOp);
    } else {
      // just copy the register
      origRegOp.setRegister(newRegOp.getRegister());
      if (newRegOp.getType() != TypeReference.ObjectReference &&
          !newRegOp.getType().isUnboxedType() && !origRegOp.isPreciseType()) {
        // copy type information from new to orig unless its an unboxed type
        // (we don't want to copy type information for unboxed types as it is
        // likely the result of inlining new) or the type of the original is
        // precise
        origRegOp.copyTypeFrom(newRegOp);
      }
      recordUse(origRegOp);
    }
    if (DEBUG) {
      printUses(origRegOp.getRegister());
      printUses(newRegOp.getRegister());
    }
  }

  public static void removeInstructionAndUpdateDU(Instruction s) {
    for (Enumeration<Operand> e = s.getPureDefs(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op instanceof RegisterOperand) {
        removeDef((RegisterOperand) op);
      }
    }
    for (Enumeration<Operand> e = s.getUses(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op instanceof RegisterOperand) {
        removeUse((RegisterOperand) op);
      }
    }
    s.remove();
  }

  public static void updateDUForNewInstruction(Instruction s) {
    for (Enumeration<Operand> e = s.getPureDefs(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op instanceof RegisterOperand) {
        recordDef((RegisterOperand) op);
      }
    }
    for (Enumeration<Operand> e = s.getUses(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op instanceof RegisterOperand) {
        recordUse((RegisterOperand) op);
      }
    }
  }

  public static void replaceInstructionAndUpdateDU(Instruction oldI, Instruction newI) {
    oldI.insertBefore(newI);
    removeInstructionAndUpdateDU(oldI);
    updateDUForNewInstruction(newI);
  }

  public static Enumeration<RegisterOperand> uses(Register reg) {
    return new RegOpListWalker(reg.useList);
  }

  public static Enumeration<RegisterOperand> defs(Register reg) {
    return new RegOpListWalker(reg.defList);
  }

  static boolean exactlyOneUse(Register reg) {
    return (reg.useList != null) && (reg.useList.getNext() == null);
  }

  static void printDefs(Register reg) {
    VM.sysWriteln("Definitions of " + reg);
    for (Enumeration<RegisterOperand> e = defs(reg); e.hasMoreElements();) {
      VM.sysWriteln("\t" + e.nextElement().instruction);
    }
  }

  static void printUses(Register reg) {
    VM.sysWriteln("Uses of " + reg);
    for (Enumeration<RegisterOperand> e = uses(reg); e.hasMoreElements();) {
      VM.sysWriteln("\t" + e.nextElement().instruction);
    }
  }

  /**
   * Recompute <code> isSSA </code> for all registers by traversing register
   * list.
   * NOTE: the DU MUST be computed BEFORE calling this function
   *
   * @param ir the IR in question
   */
  public static void recomputeSSA(IR ir) {
    // Use register /ist to enumerate register objects (FAST)
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      // Set isSSA = true iff reg has exactly one static definition.
      reg.putSSA((reg.defList != null && reg.defList.getNext() == null));
    }
  }

  /**
   * Merges a register into another register and removes the
   * merged register from the DU information.
   *
   * @param reg1 the register that is being merged into
   * @param reg2 the register that's being merged (and then removed from the
   *  DU information)
   * @param ir the governing IR
   */
  public static void mergeRegisters(IR ir, Register reg1, Register reg2) {
    RegisterOperand lastOperand;
    if (reg1 == reg2) {
      return;
    }
    if (DEBUG) {
      VM.sysWriteln("Merging " + reg2 + " into " + reg1);
      printDefs(reg2);
      printUses(reg2);
      printDefs(reg1);
      printUses(reg1);
    }
    // first loop through defs of reg2 (currently, there will only be one def)
    lastOperand = null;
    for (RegisterOperand def = reg2.defList; def != null; lastOperand = def, def = def.getNext()) {
      // Change def to refer to reg1 instead
      def.setRegister(reg1);
      // Track lastOperand
      lastOperand = def;
    }
    if (lastOperand != null) {
      // Set reg1.defList = concat(reg2.defList, reg1.deflist)
      lastOperand.setNext(reg1.defList);
      reg1.defList = reg2.defList;
    }
    // now loop through uses
    lastOperand = null;
    for (RegisterOperand use = reg2.useList; use != null; use = use.getNext()) {
      // Change use to refer to reg1 instead
      use.setRegister(reg1);
      // Track lastOperand
      lastOperand = use;
    }
    if (lastOperand != null) {
      // Set reg1.useList = concat(reg2.useList, reg1.uselist)
      lastOperand.setNext(reg1.useList);
      reg1.useList = reg2.useList;
    }
    // Remove reg2 from RegisterPool
    ir.regpool.removeRegister(reg2);
    if (DEBUG) {
      VM.sysWriteln("Merge complete");
      printDefs(reg1);
      printUses(reg1);
    }
  }

  /**
   * Recompute spansBasicBlock flags for all registers.
   *
   * @param ir the IR in question
   */
  public static void recomputeSpansBasicBlock(IR ir) {
    // clear fields
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      reg.clearSpansBasicBlock();
    }

    int[] lastBBNums = new int[ir.regpool.getTotalNumberOfRegisters()];
    Arrays.fill(lastBBNums, -1);
    // iterate over the basic blocks
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      int bbNum = bb.getNumber();
      // enumerate the instructions in the basic block
      for (Enumeration<Instruction> e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
        Instruction inst = e.nextElement();
        // check each Operand in the instruction
        for (Enumeration<Operand> ops = inst.getOperands(); ops.hasMoreElements();) {
          Operand op = ops.nextElement();
          if (op instanceof RegisterOperand) {
            Register reg = ((RegisterOperand) op).getRegister();
            if (reg.isPhysical()) {
              continue;
            }
            if (reg.spansBasicBlock()) {
              continue;
            }
            if (seenInDifferentBlock(reg, bbNum, lastBBNums)) {
              reg.setSpansBasicBlock();
              continue;
            }
            if (inst.operator() == PHI) {
              reg.setSpansBasicBlock();
              continue;
            }
            logAppearance(reg, bbNum, lastBBNums);
          }
        }
      }
    }
  }

  /**
   * Mark that we have seen a register in a particular
   * basic block.
   *
   * @param reg the register
   * @param bbNum the number of the basic block
   * @param bbNums last block were each register was seen
   */
  private static void logAppearance(Register reg, int bbNum, int[] bbNums) {
    bbNums[reg.number] = bbNum;
  }

  /**
   * @param reg the register
   * @param bbNum the number of the basic block
   * @param bbNums last block were each register was seen
   * @return {@code true} if the register was seen in a different basic block
   *  than the one that was passed to this method
   */
  private static boolean seenInDifferentBlock(Register reg, int bbNum, int[] bbNums) {
    int bb = bbNums[reg.number];
    return (bb != -1) && (bb != bbNum);
  }

  /**
   * Utility class to encapsulate walking a use/def list.
   */
  private static final class RegOpListWalker implements Enumeration<RegisterOperand> {

    private RegisterOperand current;

    RegOpListWalker(RegisterOperand start) {
      current = start;
    }

    @Override
    public boolean hasMoreElements() {
      return current != null;
    }

    @Override
    public RegisterOperand nextElement() {
      if (current == null) raiseNoSuchElementException();
      RegisterOperand tmp = current;
      current = current.getNext();
      return tmp;
    }

    @NoInline
    private static void raiseNoSuchElementException() {
      throw new java.util.NoSuchElementException("RegOpListWalker");
    }
  }
}
