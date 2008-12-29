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

import static org.jikesrvm.compilers.opt.driver.OptConstants.YES;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CAUGHT_EXCEPTION;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY_UNRESOLVED;
import static org.jikesrvm.compilers.opt.ir.Operators.NOP;
import static org.jikesrvm.compilers.opt.ir.Operators.PHI;
import static org.jikesrvm.compilers.opt.ir.Operators.SET_CAUGHT_EXCEPTION;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_BEGIN;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_END;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.BranchSimplifier;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;

/*
 * Simple flow-insensitive optimizations.
 *
 * <p> Except for the "CompilerPhase" methods, all fields and methods in
 * this class are declared static.
 */
public final class Simple extends CompilerPhase {

  private final BranchOptimizations branchOpts = new BranchOptimizations(-1, false, false, false);

  /**
   * At what optimization level should this phase be run?
   */
  private final int level;
  /**
   * Perform type propagation?
   */
  private final boolean typeProp;
  /**
   * Attempt to eliminate bounds and cast checks?
   */
  private final boolean foldChecks;
  /**
   * Fold conditional branches with constant operands?
   */
  private final boolean foldBranches;
  /**
   * Sort registers used by commutative operators
   */
  private final boolean sortRegisters;

  public boolean shouldPerform(OptOptions options) {
    return options.getOptLevel() >= level;
  }

  public String getName() {
    return "Simple Opts";
  }

  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * The constructor is used to specify what pieces of Simple will
   * be enabled for this instance.  Some pieces are always enabled.
   * Customizing can be useful because some of the optimizations are not
   * valid/useful on LIR or even on "late stage" HIR.
   *
   * @param level at what optimization level should the phase be enabled?
   * @param typeProp should type propagation be peformed?
   * @param foldChecks should we attempt to eliminate boundscheck?
   * @param foldBranches should we attempt to constant fold conditional
   * @param sortRegisters should we sort use operands?
   * branches?
   */
  public Simple(int level, boolean typeProp, boolean foldChecks, boolean foldBranches, boolean sortRegisters) {
    super(new Object[]{level, typeProp, foldChecks, foldBranches, sortRegisters});
    this.level = level;
    this.typeProp = typeProp;
    this.foldChecks = foldChecks;
    this.foldBranches = foldBranches;
    this.sortRegisters = sortRegisters;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(Simple.class,
                                  new Class[]{Integer.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE});

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Main driver for the simple optimizations
   *
   * @param ir the IR to optimize
   */
  public void perform(IR ir) {
    // Compute defList, useList, useCount fields for each register.
    DefUse.computeDU(ir);
    // Recompute isSSA flags
    DefUse.recomputeSSA(ir);
    // Simple copy propagation.
    // This pass incrementally updates the register list.
    copyPropagation(ir);
    // Simple type propagation.
    // This pass uses the register list, but doesn't modify it.
    if (typeProp) {
      typePropagation(ir);
    }
    // Perform simple bounds-check and arraylength elimination.
    // This pass incrementally updates the register list
    if (foldChecks) {
      arrayPropagation(ir);
    }
    // Simple dead code elimination.
    // This pass incrementally updates the register list
    eliminateDeadInstructions(ir);
    // constant folding
    // This pass usually doesn't modify the DU, but
    // if it does it will recompute it.
    foldConstants(ir);
    // Simple local expression folding respecting DU
    if (ir.options.LOCAL_EXPRESSION_FOLDING && ExpressionFolding.performLocal(ir)) {
      // constant folding again
      foldConstants(ir);
    }
    // Try to remove conditional branches with constant operands
    // If it actually constant folds a branch,
    // this pass will recompute the DU
    if (foldBranches) {
      simplifyConstantBranches(ir);
    }
    // Should we sort commutative use operands
    if (sortRegisters) {
      sortCommutativeRegisterUses(ir);
    }
  }

  /**
   * Sort commutative use operands so that those defined most are on the lhs
   *
   * @param ir the IR to work on
   */
  private static void sortCommutativeRegisterUses(IR ir) {
    // Pass over instructions
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      // Sort most frequently defined operands onto lhs
      if (Binary.conforms(s) && s.operator.isCommutative() &&
          Binary.getVal1(s).isRegister() && Binary.getVal2(s).isRegister()) {
        RegisterOperand rop1 = Binary.getVal1(s).asRegister();
        RegisterOperand rop2 = Binary.getVal2(s).asRegister();
        // Simple SSA based test
        if (rop1.register.isSSA()) {
          if(rop2.register.isSSA()) {
            // ordering is arbitrary, ignore
          } else {
            // swap
            Binary.setVal1(s, rop2);
            Binary.setVal2(s, rop1);
          }
        } else if (rop2.register.isSSA()) {
          // already have prefered ordering
        } else {
          // neither registers are SSA so place registers used more on the RHS
          // (we don't have easy access to a count of the number of definitions)
          if (rop1.register.useCount > rop2.register.useCount) {
            // swap
            Binary.setVal1(s, rop2);
            Binary.setVal2(s, rop1);
          }
        }
      }
    }
  }

  /**
   * Perform flow-insensitive copy and constant propagation using
   * register list information.
   *
   * <ul>
   * <li> Note: register list MUST be initialized BEFORE calling this routine.
   * <li> Note: this function incrementally maintains the register list.
   * </ul>
   *
   * @param ir the IR in question
   */
  public static void copyPropagation(IR ir) {
    // Use register list to enumerate register objects
    Register elemNext;
    boolean reiterate = true;
    while (reiterate) {         // /MT/ better think about proper ordering.
      reiterate = false;
      for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = elemNext) {
        elemNext = reg.getNext(); // we may remove reg, so get elemNext up front
        if (reg.useList == null ||   // Copy propagation not possible if reg
            // has no uses
            reg.defList == null ||   // Copy propagation not possible if reg
            // has no defs
            !reg.isSSA()) {           // Flow-insensitive copy prop only possible
          // for SSA registers.
          continue;
        }
        // isSSA => reg has exactly one definition, reg.defList.
        RegisterOperand lhs = reg.defList;
        Instruction defInstr = lhs.instruction;
        Operand rhs;
        // Copy/constant propagation only possible when defInstr is a move
        if (defInstr.isMove()) {
          rhs = Move.getVal(defInstr);
        } else if (defInstr.operator() == PHI) {
          Operand phiVal = equivalentValforPHI(defInstr);
          if (phiVal == null) continue;
          rhs = phiVal;
        } else {
          continue;
        }

        if (rhs.isRegister()) {
          Register rrhs = rhs.asRegister().getRegister();
          // If rhs is a non-SSA register, then we can't propagate it
          // because we can't be sure that the same definition reaches
          // all uses.
          if (!rrhs.isSSA()) continue;

          // If rhs is a physical register, then we can't safely propagate
          // it to uses of lhs because we don't understand the implicit
          // uses/defs of physical registers well enough to do so safely.
          if (rrhs.isPhysical()) continue;
        }

        reiterate = ir.options.getOptLevel() > 1;
        // Now substitute rhs for all uses of lhs, updating the
        // register list as we go.
        if (rhs.isRegister()) {
          RegisterOperand nextUse;
          RegisterOperand rhsRegOp = rhs.asRegister();
          for (RegisterOperand use = reg.useList; use != null; use = nextUse) {
            nextUse = use.getNext(); // get early before reg's useList is updated.
            if (VM.VerifyAssertions) VM._assert(rhsRegOp.getRegister().getType() == use.getRegister().getType());
            DefUse.transferUse(use, rhsRegOp);
          }
        } else if (rhs.isConstant()) {
          // NOTE: no need to incrementally update use's register list since we are going
          //       to blow it all away as soon as this loop is done.
          for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
            int index = use.getIndexInInstruction();
            use.instruction.putOperand(index, rhs.copy());
          }
        } else {
          throw new OptimizingCompilerException("Simple.copyPropagation: unexpected operand type");
        }
        // defInstr is now dead. Remove it.
        defInstr.remove();
        if (rhs.isRegister()) {
          DefUse.removeUse(rhs.asRegister());
        }
        ir.regpool.removeRegister(lhs.getRegister());
      }
    }
  }

  /**
   * Try to find an operand that is equivalent to the result of a
   * given phi instruction.
   *
   * @param phi the instruction to be simplified
   * @return one of the phi's operands that is equivalent to the phi's result,
   * or null if the phi can not be simplified.
   */
  static Operand equivalentValforPHI(Instruction phi) {
    if (!Phi.conforms(phi)) return null;
    // search for the first input that is different from the result
    Operand result = Phi.getResult(phi), equiv = result;
    int i = 0, n = Phi.getNumberOfValues(phi);
    while (i < n) {
      equiv = Phi.getValue(phi, i++);
      if (!equiv.similar(result)) break;
    }
    // no luck if result and equiv aren't the only distinct inputs
    while (i < n) {
      Operand opi = Phi.getValue(phi, i++);
      if (!opi.similar(equiv) && !opi.similar(result)) return null;
    }
    return equiv;
  }

  /**
   * Perform flow-insensitive type propagation using register list
   * information. Note: register list MUST be initialized BEFORE
   * calling this routine.
   *
   * <p> Kept separate from copyPropagation loop to enable clients
   * more flexibility.
   *
   * @param ir the IR in question
   */
  static void typePropagation(IR ir) {
    // Use register list to enumerate register objects (FAST)
    Register elemNext;
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = elemNext) {
      elemNext = reg.getNext();
      // Type propagation not possible if reg has no uses
      if (reg.useList == null) {
        continue;
      }
      // Type propagation not possible if reg has no defs
      if (reg.defList == null) {
        continue;
      }
      // Do not attempt type propagation if reg has multiple defs
      if (!reg.isSSA()) {
        continue;
      }
      // Now reg has exactly one definition
      RegisterOperand lhs = reg.defList;
      Instruction instr = lhs.instruction;
      Operator op = instr.operator();
      // Type propagation not possible if lhs is not in a move instr
      if (!op.isMove()) {
        continue;
      }
      Operand rhsOp = Move.getVal(instr);
      // Do not attempt type propagation if RHS is not a register
      if (!(rhsOp instanceof RegisterOperand)) {
        continue;
      }
      RegisterOperand rhs = (RegisterOperand) rhsOp;
      // Propagate the type in the def
      lhs.copyType(rhs);

      // Now propagate lhs into all uses; substitute rhs.type for lhs.type
      for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
        // if rhs.type is a supertype of use.type, don't do it
        // because use.type has more detailed information
        if (ClassLoaderProxy.includesType(rhs.getType(), use.getType()) == YES) {
          continue;
        }
        // If Magic has been employed to convert an int to a reference,
        // don't undo the effects!
        if (rhs.getType().isPrimitiveType() && !use.getType().isPrimitiveType()) {
          continue;
        }
        use.copyType(rhs);
      }
    }
  }

  /**
   * Perform flow-insensitive propagation to eliminate bounds checks
   * and arraylength for arrays with static lengths. Only useful on the HIR
   * (because BOUNDS_CHECK is expanded in LIR into multiple instrs)
   *
   * <p> Note: this function incrementally maintains the register list.
   *
   * @param ir the IR in question
   */
  static void arrayPropagation(IR ir) {
    // Use register list to enumerate register objects (FAST)
    Register elemNext;
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = elemNext) {
      elemNext = reg.getNext();
      if (reg.useList == null) {
        continue;
      }
      if (reg.defList == null) {
        continue;
      }
      if (!reg.isSSA()) {
        continue;
      }
      // Now reg has exactly one definition
      RegisterOperand lhs = reg.defList;
      Instruction instr = lhs.instruction;
      Operator op = instr.operator();
      if (!(op == NEWARRAY || op == NEWARRAY_UNRESOLVED)) {
        continue;
      }
      Operand sizeOp = NewArray.getSize(instr);
      // check for an array whose length is a compile-time constant
      // or an SSA register
      boolean boundsCheckOK = false;
      boolean arraylengthOK = false;
      int size = -1;
      if (sizeOp instanceof IntConstantOperand) {
        size = ((IntConstantOperand) sizeOp).value;
        boundsCheckOK = true;
        arraylengthOK = true;
      } else if (sizeOp instanceof RegisterOperand) {
        if (sizeOp.asRegister().getRegister().isSSA()) {
          arraylengthOK = true;
        }
      }
      // Now propagate
      for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
        Instruction i = use.instruction;
        // bounds-check elimination
        if (boundsCheckOK && i.getOpcode() == BOUNDS_CHECK_opcode) {
          Operand indexOp = BoundsCheck.getIndex(i);
          if (indexOp instanceof IntConstantOperand) {
            if (((IntConstantOperand) indexOp).value <= size) {
              Instruction s =
                  Move.create(GUARD_MOVE, BoundsCheck.getGuardResult(i).copyD2D(), new TrueGuardOperand());
              s.position = i.position;
              s.bcIndex = i.bcIndex;
              i.insertAfter(s);
              DefUse.updateDUForNewInstruction(s);
              DefUse.removeInstructionAndUpdateDU(i);
            }
          }
        } else if (arraylengthOK && i.getOpcode() == ARRAYLENGTH_opcode) {
          Operand newSizeOp = sizeOp.copy();
          RegisterOperand result = (RegisterOperand) GuardedUnary.getResult(i).copy();
          Instruction s = Move.create(INT_MOVE, result, newSizeOp);
          s.position = i.position;
          s.bcIndex = i.bcIndex;
          i.insertAfter(s);
          DefUse.updateDUForNewInstruction(s);
          DefUse.removeInstructionAndUpdateDU(i);
        }
      }
    }
  }

  /**
   * Simple conservative dead code elimination.
   * An instruction is eliminated if:
   * <ul>
   *  <li> 1. it is not a PEI, store or call
   *  <li> 2. it DEFs only registers
   *  <li> 3. all registers it DEFS are dead
   * </ul>
   *
   * <p> Note: this function incrementally maintains the register list.
   *
   * @param ir the IR to optimize
   */
  static void eliminateDeadInstructions(IR ir) {
    eliminateDeadInstructions(ir, false);
  }

  /**
   * Simple conservative dead code elimination.
   * An instruction is eliminated if:
   * <ul>
   *  <li> 1. it is not a PEI, store or non-pure call
   *  <li> 2. it DEFs only registers
   *  <li> 3. all registers it DEFS are dead
   * </ul>
   *
   * <p> Note: this function incrementally maintains the register list.
   *
   * @param ir IR to optimize
   * @param preserveImplicitSSA if this is true, do not eliminate dead
   * instructions that have implicit operands for heap array SSA form
   */
  public static void eliminateDeadInstructions(IR ir, boolean preserveImplicitSSA) {
    // (USE BACKWARDS PASS FOR INCREASED EFFECTIVENESS)
    ArrayList<Instruction> setCaughtExceptionInstructions = null;
    int getCaughtExceptionInstructions = 0;
    for (Instruction instr = ir.lastInstructionInCodeOrder(),
        prevInstr = null; instr != null; instr = prevInstr) {
      prevInstr = instr.prevInstructionInCodeOrder(); // cache because
      // remove nulls next/prev fields
      // if instr is a PEI, store, branch, or call, then it's not dead ...
      if (instr.isPEI() || instr.isImplicitStore() || instr.isBranch() || instr.isNonPureCall()) {
        continue;
      }
      if (preserveImplicitSSA && (instr.isImplicitLoad() || instr.isAllocation() || instr.operator() == PHI)) {
        continue;
      }

      if (instr.operator() == SET_CAUGHT_EXCEPTION) {
        if (setCaughtExceptionInstructions == null) {
          setCaughtExceptionInstructions = new ArrayList<Instruction>();
        }
        setCaughtExceptionInstructions.add(instr);
      }

      // remove NOPs
      if (instr.operator() == NOP) {
        DefUse.removeInstructionAndUpdateDU(instr);
      }

      // remove UNINT_BEGIN/UNINT_END with nothing in between them
      if (instr.operator() == UNINT_BEGIN) {
        Instruction s = instr.nextInstructionInCodeOrder();
        if (s.operator() == UNINT_END) {
          DefUse.removeInstructionAndUpdateDU(s);
          DefUse.removeInstructionAndUpdateDU(instr);
        }
      }

      // remove trivial assignments
      if (Move.conforms(instr)) {
        Register lhs = Move.getResult(instr).asRegister().getRegister();
        if (Move.getVal(instr).isRegister()) {
          Register rhs = Move.getVal(instr).asRegister().getRegister();
          if (lhs == rhs) {
            DefUse.removeInstructionAndUpdateDU(instr);
            continue;
          }
        }
      }
      if (instr.operator() == GET_CAUGHT_EXCEPTION) {
        getCaughtExceptionInstructions++;
      }

      // check that all defs are to dead registers and that
      // there is at least 1 def.
      boolean isDead = true;
      boolean foundRegisterDef = false;
      for (OperandEnumeration defs = instr.getDefs(); defs.hasMoreElements();) {
        Operand def = defs.nextElement();
        if (!def.isRegister()) {
          isDead = false;
          break;
        }
        foundRegisterDef = true;
        RegisterOperand r = def.asRegister();
        if (r.getRegister().useList != null) {
          isDead = false;
          break;
        }
        if (r.getRegister().isPhysical()) {
          isDead = false;
          break;
        }
      }
      if (!isDead) {
        continue;
      }
      if (!foundRegisterDef) {
        continue;
      }
      if (instr.operator() == GET_CAUGHT_EXCEPTION) {
        getCaughtExceptionInstructions--;
      }
      // There are 1 or more register defs, but all of them are dead.
      // Remove instr.
      DefUse.removeInstructionAndUpdateDU(instr);
    }
    if (false && // temporarily disabled - see RVM-410
        (getCaughtExceptionInstructions == 0) &&
        (setCaughtExceptionInstructions != null)) {
      for (Instruction instr : setCaughtExceptionInstructions) {
        DefUse.removeInstructionAndUpdateDU(instr);
      }
    }
  }

  /**
   * Perform constant folding.
   *
   * @param ir the IR to optimize
   */
  void foldConstants(IR ir) {
    boolean recomputeRegList = false;
    for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
      Simplifier.DefUseEffect code = Simplifier.simplify(ir.IRStage == IR.HIR, ir.regpool, ir.options, s);
      // If something was reduced (as opposed to folded) then its uses may
      // be different. This happens so infrequently that it's cheaper to
      // handle it by  recomputing the DU from
      // scratch rather than trying to do the incremental bookkeeping.
      recomputeRegList |=
          (code == Simplifier.DefUseEffect.MOVE_REDUCED ||
           code == Simplifier.DefUseEffect.TRAP_REDUCED ||
           code == Simplifier.DefUseEffect.REDUCED);
    }
    if (recomputeRegList) {
      DefUse.computeDU(ir);
      DefUse.recomputeSSA(ir);
    }
  }

  /**
   * Simplify branches whose operands are constants.
   *
   * <p> NOTE: This pass ensures that the register list is still valid after it
   * is done.
   *
   * @param ir the IR to optimize
   */
  void simplifyConstantBranches(IR ir) {
    boolean didSomething = false;
    for (BasicBlockEnumeration e = ir.forwardBlockEnumerator(); e.hasMoreElements();) {
      BasicBlock bb = e.next();
      didSomething |= BranchSimplifier.simplify(bb, ir);
    }
    if (didSomething) {
      // killed at least one branch, cleanup the CFG removing dead code.
      // Then recompute register list and isSSA info
      branchOpts.perform(ir, true);
      DefUse.computeDU(ir);
      DefUse.recomputeSSA(ir);
    }
  }
}
