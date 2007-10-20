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

import java.util.HashMap;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_ConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * Perform local constant propagation for a factored basic block.
 * Orthogonal to the constant propagation performed in OPT_Simple
 * since here we use flow-sensitive analysis within a basic block.
 */
public class OPT_LocalConstantProp extends OPT_CompilerPhase {

  public final boolean shouldPerform(OPT_Options options) {
    return options.LOCAL_CONSTANT_PROP;
  }

  public final String getName() {
    return "Local ConstantProp";
  }

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
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Perform Local Constant propagation for a method.
   *
   * @param ir the IR to optimize
   */
  public void perform(OPT_IR ir) {
    // info is a mapping from OPT_Register to OPT_ConstantOperand.
    HashMap<OPT_Register, OPT_ConstantOperand> info = new HashMap<OPT_Register, OPT_ConstantOperand>();
    boolean runBranchOpts = false;
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      // iterate over all instructions in the basic block
      for (OPT_Instruction s = bb.firstRealInstruction(),
          sentinel = bb.lastInstruction(); s != sentinel; s = s.nextInstructionInCodeOrder()) {

        if (!info.isEmpty()) {
          // PROPAGATE CONSTANTS
          int numUses = s.getNumberOfUses();
          if (numUses > 0) {
            boolean didSomething = false;
            int numDefs = s.getNumberOfDefs();
            for (int idx = numDefs; idx < numUses + numDefs; idx++) {
              OPT_Operand use = s.getOperand(idx);
              if (use instanceof OPT_RegisterOperand) {
                OPT_RegisterOperand rUse = (OPT_RegisterOperand) use;
                OPT_Operand value = info.get(rUse.getRegister());
                if (value != null) {
                  didSomething = true;
                  s.putOperand(idx, value.copy());
                }
              }
            }
            if (didSomething) OPT_Simplifier.simplify(ir.regpool, s);
          }
          // KILL
          for (OPT_OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
            OPT_Operand def = e.next();
            if (def != null) {
              info.remove(((OPT_RegisterOperand) def).getRegister());
            }
          }
        }
        // GEN
        if (Move.conforms(s) && Move.getVal(s).isConstant()) {
          info.put(Move.getResult(s).getRegister(), (OPT_ConstantOperand) Move.getVal(s));
        }
      }
      info.clear();
      runBranchOpts |= OPT_BranchSimplifier.simplify(bb, ir);
    }
    if (runBranchOpts) {
      new OPT_BranchOptimizations(0, true, false, false).perform(ir);
    }
  }
}
