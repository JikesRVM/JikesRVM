/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Perform local constant propagation for a factored basic block.
 * Orthogonal to the constant propagation performed in OPT_Simple
 * since here we use flow-sensitive analysis within a basic block.
 * 
 * @author Dave Grove
 */
public class OPT_LocalConstantProp extends OPT_CompilerPhase implements OPT_Operators {

  public final boolean shouldPerform (OPT_Options options) {
    return options.LOCAL_CONSTANT_PROP;
  }

  public final String getName () {
    return "Local ConstantProp";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1/container.counter2*100, 2);
    VM.sysWrite("% Infrequent BBs");
  }

  /**
   * Perform Local Constant propagation for a method.
   * 
   * @param ir the IR to optimize
   */
  public void perform (OPT_IR ir) {
    // info is a mapping from OPT_Register to OPT_ConstantOperand.
    java.util.HashMap info = new java.util.HashMap();
    boolean runBranchOpts = false;
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
         bb != null; 
         bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      // iterate over all instructions in the basic block
      for (OPT_Instruction s = bb.firstRealInstruction(), 
             sentinel = bb.lastInstruction();
           s != sentinel; 
           s = s.nextInstructionInCodeOrder()) {

        if (!info.isEmpty()) {
          // PROPAGATE CONSTANTS
          int numUses = s.getNumberOfUses();
          if (numUses > 0) {
            boolean didSomething = false;
            int numDefs = s.getNumberOfDefs();
            for (int idx = numDefs; idx < numUses + numDefs; idx++) {
              OPT_Operand use = s.getOperand(idx);
              if (use instanceof OPT_RegisterOperand) {
                OPT_RegisterOperand rUse = (OPT_RegisterOperand)use;
                OPT_Operand value = (OPT_Operand)info.get(rUse.register);
                if (value != null) {
                  didSomething = true;
                  s.putOperand(idx, value.copy());
                }
              }
            }
            if (didSomething) OPT_Simplifier.simplify(s);
          }
          // KILL
          for (OPT_OperandEnumeration e = s.getDefs();
               e.hasMoreElements();) {
            OPT_Operand def = e.next();
            if (def != null) {
              info.remove(((OPT_RegisterOperand)def).register);
            }
          }
        }
        // GEN
        if (Move.conforms(s) && Move.getVal(s).isConstant()) {
          info.put(Move.getResult(s).register, Move.getVal(s));
        } 
      }
      info.clear();
      runBranchOpts |= OPT_BranchSimplifier.simplify(bb, ir);
    }
    if (runBranchOpts) {
      new OPT_BranchOptimizations(0, true, false).perform(ir);
    }
  }
}
