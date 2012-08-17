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

import java.util.Enumeration;
import java.util.HashMap;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.BranchSimplifier;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Perform local constant propagation for a factored basic block.
 * Orthogonal to the constant propagation performed in Simple
 * since here we use flow-sensitive analysis within a basic block.
 */
public class LocalConstantProp extends CompilerPhase {

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.LOCAL_CONSTANT_PROP;
  }

  @Override
  public final String getName() {
    return "Local ConstantProp";
  }

  @Override
  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent BBs");
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Perform Local Constant propagation for a method.
   *
   * @param ir the IR to optimize
   */
  @Override
  public void perform(IR ir) {
    // info is a mapping from Register to ConstantOperand.
    HashMap<Register, ConstantOperand> info = new HashMap<Register, ConstantOperand>();
    boolean runBranchOpts = false;

    /* Visit each basic block and apply the optimization */
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue; /* skip over trivial blocks */
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }

      /* Iterate over all instructions in the basic block */
      for (Instruction s = bb.firstRealInstruction(), next, sentinel = bb.lastInstruction(); s != sentinel; s = next) {
        next = s.nextInstructionInCodeOrder();

        /* Do we known anything ? */
        if (!info.isEmpty()) {
          /* Transform: attempt to propagate constants */
          int numUses = s.getNumberOfPureUses();
          if (numUses > 0) {
            boolean didSomething = false;
            int numDefs = s.getNumberOfDefs();
            for (int idx = numDefs; idx < numUses + numDefs; idx++) {
              Operand use = s.getOperand(idx);
              if (use instanceof RegisterOperand) {
                RegisterOperand rUse = (RegisterOperand)use;
                Operand value = info.get(rUse.getRegister());
                if (value != null) {
                  didSomething = true;
                  s.putOperand(idx, value.copy());
                }
              }
            }
            if (didSomething) {
              Simplifier.simplify(ir.IRStage == IR.HIR, ir.regpool, ir.options, s);
            }
          }

          /* KILL: Remove bindings for all registers defined by this instruction */
          for (Enumeration<Operand> e = s.getDefs(); e.hasMoreElements();) {
            Operand def = e.nextElement();
            if (def != null) {
              /* Don't bother special casing the case where we are defining another constant; GEN will handle that */
              /* Don't attempt to remove redundant assignments; let dead code elimination handle that */
              Register defReg = ((RegisterOperand)def).getRegister();
              info.remove(defReg);
            }
          }
        }

        /* GEN: If this is a move operation with a constant RHS, then it defines a constant */
        if (Move.conforms(s) && Move.getVal(s).isConstant()) {
          info.put(Move.getResult(s).getRegister(), (ConstantOperand)Move.getVal(s));
        }
      }

      /* End of basic block; clean up and prepare for next block */
      info.clear();
      runBranchOpts |= BranchSimplifier.simplify(bb, ir);
    }

    /* End of IR.  If we simplified a branch instruction, then run branch optimizations */
    if (runBranchOpts) {
      new BranchOptimizations(0, true, false, false).perform(ir);
    }

  }
}
