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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Perform local copy propagation for a factored basic block.
 * Orthogonal to the copy propagation performed in Simple
 * since here we use flow-sensitive analysis within a basic block.
 *
 * TODO: factor out common functionality in the various local propagation
 * phases?
 */
public class LocalCopyProp extends CompilerPhase {

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.LOCAL_COPY_PROP;
  }

  @Override
  public final String getName() {
    return "Local CopyProp";
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
   * Perform local constant propagation for a method.
   *
   * @param ir the IR to optimize
   */
  @Override
  public void perform(IR ir) {
    HashMap<Register, Operand> info = new HashMap<Register, Operand>();
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      // iterate over all instructions in the basic block
      for (Instruction s = bb.firstRealInstruction(),
          sentinel = bb.lastInstruction(); s != sentinel; s = s.nextInstructionInCodeOrder()) {

        if (!info.isEmpty()) {
          // PROPAGATE COPIES
          int numUses = s.getNumberOfPureUses();
          if (numUses > 0) {
            boolean didSomething = false;
            for (OperandEnumeration e = s.getUses(); e.hasMoreElements();) {
              Operand use = e.next();
              if (use instanceof RegisterOperand) {
                RegisterOperand rUse = (RegisterOperand) use;
                Operand value = info.get(rUse.getRegister());
                if (value != null) {
                  didSomething = true;
                  value = value.copy();
                  if (value instanceof RegisterOperand) {
                    // preserve program point specific typing!
                    ((RegisterOperand) value).copyType(rUse);
                  }
                  s.replaceOperand(use, value);
                }
              }
            }
            if (didSomething) {
              Simplifier.simplify(ir.IRStage == IR.HIR, ir.regpool, ir.options, s);
            }
          }
          // KILL
          boolean killPhysicals = s.isTSPoint() || s.operator().implicitDefs != 0;
          // kill any physical registers
          // TODO: use a better data structure for efficiency.
          // I'm being lazy for now in the name of avoiding
          // premature optimization.
          if (killPhysicals) {
            HashSet<Register> toRemove = new HashSet<Register>();
            for (Map.Entry<Register, Operand> entry : info.entrySet()) {
              Register eR = entry.getValue().asRegister().getRegister();
              if (killPhysicals && eR.isPhysical()) {
                // delay the removal to avoid ConcurrentModification with iterator.
                toRemove.add(entry.getKey());
              }
            }
            // Now perform the removals.
            for (final Register aToRemove : toRemove) {
              info.remove(aToRemove);
            }
          }

          for (OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
            Operand def = e.next();
            if (def != null && def.isRegister()) {
              Register r = def.asRegister().getRegister();
              info.remove(r);
              // also must kill any registers mapped to r
              // TODO: use a better data structure for efficiency.
              // I'm being lazy for now in the name of avoiding
              // premature optimization.
              HashSet<Register> toRemove = new HashSet<Register>();
              for (Map.Entry<Register, Operand> entry : info.entrySet()) {
                Register eR = ((RegisterOperand) entry.getValue()).getRegister();
                if (eR == r) {
                  // delay the removal to avoid ConcurrentModification
                  // with iterator.
                  toRemove.add(entry.getKey());
                }
              }
              // Now perform the removals.
              for (final Register register : toRemove) {
                info.remove(register);
              }
            }
          }
        }
        // GEN
        if (Move.conforms(s)) {
          Operand val = Move.getVal(s);
          if (val.isRegister()) {
            RegisterOperand rhs = val.asRegister();
            if (!rhs.getRegister().isPhysical()) {
              RegisterOperand lhs = Move.getResult(s);
              /* Only gen if the move instruction does not represent a Magic <==> non-Magic coercion */
              if (lhs.getType().isReferenceType() == rhs.getType().isReferenceType()) {
                info.put(lhs.getRegister(), val);
              }
            }
          }
        }
      }
      info.clear();
    }
  }
}
