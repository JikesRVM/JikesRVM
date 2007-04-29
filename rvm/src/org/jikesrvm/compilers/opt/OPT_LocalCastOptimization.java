/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_NullConstantOperand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_NOTNULL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NULL_CHECK;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_IFCMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_MOVE;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.TypeCheck;

/**
 * Perform simple peephole optimizations to reduce the overhead of
 * checking casts.  This code was inspired by some special cases in
 * handling checkcast in HIR2LIR, but the actual code is all different.
 *
 * <p> There are currently the following optimizations:
 * <ul>
 * <li> 1.  If a checkcast is just before a nullcheck, invert them and
 * convert the checkcast into a checkcast_not_null
 * <li> 2.  If a checkcast is followed by a branch based on a null test of 
 * the same variable, then push the cast below the conditional on
 * the path where the obejct is known not to be null.  And convert
 * it to a checkcast_not_null
 * </ul>
 *
 * @author Mauricio Serrano
 * @author Julian Dolby
 */
public final class OPT_LocalCastOptimization extends OPT_CompilerPhase {

  public String getName() {
    return "Local Cast Optimizations";
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
   * Main routine: perform the transformation.
   * @param ir the IR to transform
   */
  public void perform(OPT_IR ir) {
    // loop over all basic blocks ...
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
         e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      // visit each instruction in the basic block
      for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator(); 
           ie.hasMoreElements();) {
        OPT_Instruction s = ie.next();
        if (TypeCheck.conforms(s) && (invertNullAndTypeChecks(s) || 
                                      pushTypeCheckBelowIf(s, ir)))
          // hack: we may have modified the instructions; start over
          ie = bb.forwardInstrEnumerator();
      }
    }
  }

  /**
   * If there's a checkcast followed by a null check, move the checkcast
   * after the null check, since the null pointer exception must be thrown
   * anyway.
   * @param s the potential checkcast instruction
   * @return true iff the transformation happened
   */
  private boolean invertNullAndTypeChecks(OPT_Instruction s) {
    if (s.operator() == CHECKCAST) {
      OPT_Register r = TypeCheck.getRef(s).asRegister().register;
      OPT_Instruction n = s.nextInstructionInCodeOrder();
      while (n.operator() == REF_MOVE && Move.getVal(n) 
             instanceof OPT_RegisterOperand
             && Move.getVal(n).asRegister().register == r) {
        r = Move.getResult(n).asRegister().register;
        n = n.nextInstructionInCodeOrder();
      }
      if (n.operator() == NULL_CHECK && TypeCheck.getRef(s).asRegister().register == 
          NullCheck.getRef(n).asRegister().register) {
        s.remove();
        TypeCheck.mutate(s, CHECKCAST_NOTNULL, TypeCheck.getClearRef(s), 
                         TypeCheck.getClearType(s), NullCheck.getGuardResult(n).copy());
        n.insertFront(s);
        return  true;
      }
    }
    return  false;
  }

  /**
   * Where legal, move a type check below an if instruction.
   * @param s the potential typecheck instruction
   * @param ir the governing IR
   */
  private boolean pushTypeCheckBelowIf(OPT_Instruction s, OPT_IR ir) {
    if (s.operator() == CHECKCAST) {
      OPT_Register r = TypeCheck.getRef(s).asRegister().register;
      OPT_Instruction n = s.nextInstructionInCodeOrder();
      /* find moves of the checked value, so that we can also
         optimize cases where the checked value is moved before
         it is used
      */
      while (n.operator() == REF_MOVE && Move.getVal(n) 
             instanceof OPT_RegisterOperand
             && Move.getVal(n).asRegister().register == r) {
        r = Move.getResult(n).asRegister().register;
        n = n.nextInstructionInCodeOrder();
      }
      if (n.operator() == REF_IFCMP && 
          IfCmp.getVal2(n) instanceof OPT_NullConstantOperand &&
          IfCmp.getVal1(n) instanceof OPT_RegisterOperand &&
          r == IfCmp.getVal1(n).asRegister().register) {
        OPT_BasicBlock newBlock, patchBlock;
        OPT_BasicBlock myBlock = n.getBasicBlock();
        OPT_Instruction after = n.nextInstructionInCodeOrder();
        if (IfCmp.getCond(n).isEQUAL())
          /*  We fall through on non-NULL values, so the
              checkcast must be on the not-taken path
              from the branch.  There are 3 cases:
              1. n is the last instruction in its basic block, 
              in which case control falls through to the next
              block in code order.  This case is if the
              instruction after n is a BBEND
          */
          if (after.operator() == BBEND)
            patchBlock = myBlock.nextBasicBlockInCodeOrder();          
        /* 2. n is followed by an unconditional goto.  In
           this case control jumps to the target of the
           goto.                                     
        */
          else if (after.operator() == GOTO)
            patchBlock = after.getBranchTarget();          
        /* 3. n is followed by another conditional branch. In
           this case, we will split the basic block to make
           n the last instruction in the block, and then
           we have the fall through case again.
        */
          else if (after.operator() == REF_IFCMP) {
            patchBlock = myBlock.splitNodeAt(n, ir);
            myBlock.insertOut(patchBlock);
            ir.cfg.linkInCodeOrder(myBlock, patchBlock);
          } 

        /* this is a bad thing */
          else 
            return  false; 
        else 
          /* We branch on not-NULL values, so the checkcast 
             must be spliced in before the branch target
          */
          patchBlock = n.getBranchTarget();
        /* add block between branch and appropriate successor */

        newBlock = OPT_IRTools.makeBlockOnEdge(myBlock, patchBlock, ir);

        /* put check in new block */
        s.remove();
        TypeCheck.mutate(s, CHECKCAST_NOTNULL, TypeCheck.getClearRef(s), 
                         TypeCheck.getClearType(s), IfCmp.getGuardResult(n).copyRO());
        newBlock.prependInstruction(s);
        return  true;
      }
    }
    return  false;
  }
}
