/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.*;
import org.jikesrvm.opt.ir.*;
import java.util.*;

/**
 * Perform local copy propagation for a factored basic block.
 * Orthogonal to the copy propagation performed in OPT_Simple
 * since here we use flow-sensitive analysis within a basic block.
 * 
 * TODO: factor out common functionality in the various local propagation
 * phases?
 *
 * @author Dave Grove
 * @author Stephen Fink
 */
public class OPT_LocalCopyProp extends OPT_CompilerPhase {

  public final boolean shouldPerform (OPT_Options options) {
    return options.LOCAL_COPY_PROP;
  }

  public final String getName () {
    return "Local CopyProp";
  }

 public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1/container.counter2*100, 2);
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
   * Perform local constant propagation for a method.
   * 
   * @param ir the IR to optimize
   */
  public void perform (OPT_IR ir) {
    // info is a mapping from OPT_Register to OPT_Register
    HashMap<OPT_Register,OPT_Operand> info =
      new HashMap<OPT_Register,OPT_Operand>();
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
          // PROPAGATE COPIES
          int numUses = s.getNumberOfUses();
          if (numUses > 0) {
            boolean didSomething = false;
            for (OPT_OperandEnumeration e = s.getUses(); e.hasMoreElements(); ) {
              OPT_Operand use = e.next();
              if (use instanceof OPT_RegisterOperand) {
                OPT_RegisterOperand rUse = (OPT_RegisterOperand)use;
                OPT_Operand value = info.get(rUse.register);
                if (value != null) {
                  didSomething = true;
                  value = value.copy();
                  if (value instanceof OPT_RegisterOperand) {
                    // preserve program point specific typing!
                    ((OPT_RegisterOperand)value).copyType(rUse);
                  }
                  s.replaceOperand(use, value);
                }
              }
            }
            if (didSomething) OPT_Simplifier.simplify(ir.regpool, s);
          }
          // KILL
          boolean killPhysicals = s.isTSPoint() || s.operator().implicitDefs != 0;
          // kill any physical registers
          // TODO: use a better data structure for efficiency.
          // I'm being lazy for now in the name of avoiding
          // premature optimization.
          if (killPhysicals) {
            HashSet<OPT_Register> toRemove =
              new HashSet<OPT_Register>();
            for (Map.Entry<OPT_Register, OPT_Operand> entry : info.entrySet()) {
              OPT_Register eR = entry.getValue().
                  asRegister().register;
              if (killPhysicals && eR.isPhysical()) {
                // delay the removal to avoid ConcurrentModification
                // with iterator.
                toRemove.add(entry.getKey());
              }
            }
            // Now perform the removals.
            for (final OPT_Register aToRemove : toRemove) {
              info.remove(aToRemove);
            }
          }

          for (OPT_OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
            OPT_Operand def = e.next();
            if (def != null && def.isRegister()) {
              OPT_Register r = def.asRegister().register;
              info.remove(r);
              // also must kill any registers mapped to r
              // TODO: use a better data structure for efficiency.
              // I'm being lazy for now in the name of avoiding
              // premature optimization.
              HashSet<OPT_Register> toRemove = new HashSet<OPT_Register>();
              for (Map.Entry<OPT_Register, OPT_Operand> entry : info.entrySet()) {
                OPT_Register eR =
                    ((OPT_RegisterOperand) entry.getValue()).register;
                if (eR == r) {
                  // delay the removal to avoid ConcurrentModification
                  // with iterator.
                  toRemove.add(entry.getKey());
                }
              }
              // Now perform the removals.
              for (final OPT_Register register : toRemove) {
                info.remove(register);
              }
            }
          }
        }
        // GEN
        if (Move.conforms(s)) {
          OPT_Operand val = Move.getVal(s);
          if (val.isRegister() && !val.asRegister().register.isPhysical()) {
            info.put(Move.getResult(s).register, val);
          } 
        }
      }
      info.clear();
    }
  }
}
