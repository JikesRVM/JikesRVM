/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * This module holds utility functions for SSA form.
 *
 * Our SSA form is <em> Heap Array SSA Form </em>, an extension of
 * SSA that allows analysis of scalars, arrays, and object fields
 * in a unified framework.  See our SAS 2000 paper
 * <a href="http://www.research.ibm.com/jalapeno/publication.html#sas00">
 *  Unified Analysis of Arrays and Object References in Strongly Typed
 *  Languages </a>
 * <p> Details about our current implementation include:
 * <ul>
 *  <li> We explicitly place phi-functions as instructions in the IR.
 *  <li> Scalar registers are explicitly renamed in the IR.
 *  <li> Heap variables are represented implicitly. Each instruction
 *       that reads or writes from the heap implicitly uses a Heap variable.
 *       The particular heap variable for each instruction is cached
 *       in {@link OPT_SSADictionary <code> ir.HIRInfo.SSADictionary </code>}.
 *       dphi functions do <em> not </em> 
 *       explicitly appear in the IR.
 *  <p> 
 *  For example, consider the code:
 *  <p>
 *  <pre>
 *              a.x = z;
 *              b[100] = 5;
 *              y = a.x;
 *  </pre>
 *
 *  <p>Logically, we translate to Array SSA form (before renumbering) as:
 *  <pre>
 *              HEAP_x[a] = z
 *              HEAP_x = dphi(HEAP_x,HEAP_x)
 *              HEAP_I[] = { < b,100,5 > }
 *              HEAP_I[] = dphi(HEAP_I[], HEAP_I[])
 *              y = HEAP_x[a]
 *  </pre>
 *
 *  <p> However, the implementation does not actually modify the instruction
 *      stream. Instead, we keep track of the following information with
 *  <code> ir.HIRInfo.SSADictionary </code>:
 *  <pre>
 *              a.x = z  (implicit: reads HEAP_x, writes HEAP_x)
 *              b[100] =5 (implicit: reads HEAP_I[], writes HEAP_I[])
 *              y = a.x   (implicit: reads HEAP_x)
 *  </pre>
 *
 *  <p>Similarly, phi functions for the implicit heap 
 *  variables <em> will not </em>
 *  appear explicitly in the instruction stream. Instead, the
 *  OPT_SSADictionary data structure keeps the heap control phi 
 *  functions for each basic block in a lookaside table.
 *  </ul>
 *
 * @see OPT_EnterSSA
 * @see OPT_LeaveSSA
 * @see OPT_SSADictionary
 * @see OPT_HIRInfo
 *
 * @author Stephen Fink
 * @modified Julian Dolby
 */
class OPT_SSA implements OPT_Operators, OPT_Constants {

  /**
   * Add a move instruction at the end of a basic block, renaming
   * with a temporary register if needed to protect conditional branches
   * at the end of the block.
   *
   * @param ir governing IR
   * @param bb the basic block
   * @param c  the move instruction to insert
   * @param exp whether or not to respect exception control flow at the
   *            end of the block
   */
  static void addAtEnd (OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction c, 
      boolean exp) {
    if (exp)
      bb.appendInstructionRespectingTerminalBranchOrPEI(c); 
    else 
      bb.appendInstructionRespectingTerminalBranch(c);
    OPT_InstructionEnumeration e = bb.enumerateBranchInstructions();
    OPT_RegisterOperand aux = null;
    if (VM.VerifyAssertions)
      VM._assert(Move.conforms(c));
    OPT_RegisterOperand lhs = Move.getResult(c);
    OPT_Instruction i = c.nextInstructionInCodeOrder();
    while (!BBend.conforms(i)) {
      OPT_OperandEnumeration os = i.getUses();
      while (os.hasMoreElements()) {
        OPT_Operand op = os.next();
        if (lhs.similar(op)) {
          if (aux == null) {
            aux = ir.regpool.makeTemp(lhs);
            c.insertBefore(makeMoveInstruction(ir, aux.register, lhs.register, 
                lhs.type));
          }
          op.asRegister().register = aux.register;
        }
      }
      i = i.nextInstructionInCodeOrder();
    }
  }

  /**
   * Print the instructions in SSA form.
   *
   * @param ir the IR, assumed to be in SSA form
   */
  public static void printInstructions (OPT_IR ir) {
    OPT_SSADictionary dictionary = ir.HIRInfo.SSADictionary;
    System.out.println("********* START OF IR DUMP in SSA FOR " + ir.method);
    for (OPT_BasicBlockEnumeration be = ir.forwardBlockEnumerator(); be.hasMoreElements();) {
      OPT_BasicBlock bb = be.next();
      // print the explicit instructions for basic block bb
      for (Enumeration e = dictionary.getAllInstructions(bb); 
          e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        System.out.print(s.bcIndex + "\t" + s);
        if (dictionary.defsHeapVariable(s) && s.operator!=PHI) {
          System.out.print("  (Implicit Defs: ");
          OPT_HeapOperand[] defs = dictionary.getHeapDefs(s);
          if (defs != null)
            for (int i = 0; i < defs.length; i++)
              System.out.print(defs[i] + " ");
          System.out.print(" )");
        }
        if (dictionary.usesHeapVariable(s) && s.operator!=PHI) {
          System.out.print("  (Implicit Uses: ");
          OPT_HeapOperand[] uses = dictionary.getHeapUses(s);
          if (uses != null)
            for (int i = 0; i < uses.length; i++)
              System.out.print(uses[i] + " ");
          System.out.print(" )");
        }
        System.out.print("\n");
      }
    }
    System.out.println("*********   END OF IR DUMP in SSA FOR " + ir.method);
  }

  /** 
   * Create a move instruction r1 := r2.
   *
   * TODO: This utility function should be moved elsewhere.
   *
   * @param ir the governing ir
   * @param r1 the destination
   * @param r2 the source
   * @param t the type of r1 and r2.
   */
  static OPT_Instruction makeMoveInstruction (OPT_IR ir, OPT_Register r1, 
                                              OPT_Register r2, 
                                              VM_TypeReference t) {
    OPT_Operator mv = OPT_IRTools.getMoveOp(t);
    OPT_RegisterOperand o1 = new OPT_RegisterOperand(r1, t);
    OPT_RegisterOperand o2 = new OPT_RegisterOperand(r2, t);
    OPT_Instruction s = Move.create(mv, o1, o2);
    s.position = ir.gc.inlineSequence;
    s.bcIndex = SSA_SYNTH_BCI;
    return  s;
  }

  /** 
   * Create a move instruction r1 := c.
   *
   * !!TODO: put this functionality elsewhere.
   * 
   * @param ir the governing ir
   * @param r1 the destination
   * @param c the source
   */
  static OPT_Instruction makeMoveInstruction (OPT_IR ir, OPT_Register r1, 
                                              OPT_ConstantOperand c) {
    OPT_Operator mv = OPT_IRTools.getMoveOp(c.getType());
    OPT_RegisterOperand o1 = new OPT_RegisterOperand(r1, c.getType());
    OPT_Operand o2 = c.copy();
    OPT_Instruction s = Move.create(mv, o1, o2);
    s.position = ir.gc.inlineSequence;
    s.bcIndex = SSA_SYNTH_BCI;
    return  s;
  }


  /**
   * Fix up any PHI instructions in the given target block to reflect that
   * the given source block is no longer a predecessor of target.
   * The basic algorithm is to erase the PHI operands related to the edge
   * from source to target by sliding the other PHI operands down as required.
   * 
   * @param source the source block to remove from PHIs in target
   * @param target the target block that may contain PHIs to update.
   */
  static void purgeBlockFromPHIs(OPT_BasicBlock source,
                                 OPT_BasicBlock target) {
    for (OPT_InstructionEnumeration e = target.forwardRealInstrEnumerator();
         e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.operator() != PHI) return; // all done (assume PHIs are first!)
      int numPairs = Phi.getNumberOfPreds(s);
      int dst = 0;
      for (int src=0; src<numPairs; src++) {
        OPT_BasicBlockOperand bbop = Phi.getPred(s, src);
        if (bbop.block == source) {
          Phi.setValue(s, src, null);
          Phi.setPred(s, src, null);
        } else {
          if (src != dst) {
            Phi.setValue(s, dst, Phi.getClearValue(s, src));
            Phi.setPred(s, dst, Phi.getClearPred(s, src));
          } 
          dst++;
        }
      }
      for (int i=dst; i<numPairs; i++) {
        Phi.setValue(s, i, null);
        Phi.setPred(s, i, null);
      }
    }
  }
  /**
   * Update PHI instructions in the target block so that any PHIs that
   * come from basic block B1, now come from basic block B2.
   * 
   * @param target the target block that may contain PHIs to update.
   * @param B1 the block to replace in the phi instructions
   * @param B2 the replacement block for B1
   */
  static void replaceBlockInPhis(OPT_BasicBlock target,
                                 OPT_BasicBlock B1, OPT_BasicBlock B2) {
    for (OPT_InstructionEnumeration e = target.forwardRealInstrEnumerator();
         e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.operator() != PHI) return; // all done (assume PHIs are first!)
      int numPairs = Phi.getNumberOfPreds(s);
      int dst = 0;
      for (int src=0; src<numPairs; src++) {
        OPT_BasicBlockOperand bbop = Phi.getPred(s, src);
        if (bbop.block == B1) {
          Phi.setPred(s, src, new OPT_BasicBlockOperand(B2));
        }
      }
    }
  }
}
