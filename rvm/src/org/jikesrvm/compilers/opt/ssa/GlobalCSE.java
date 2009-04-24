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
package org.jikesrvm.compilers.opt.ssa;

import static org.jikesrvm.compilers.opt.ir.Operators.*;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.controlflow.DominatorTreeNode;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.util.TreeNode;

/**
 * This class provides global common sub expression elimination.
 */
public final class GlobalCSE extends CompilerPhase {

  /** Output debug messages */
  public boolean verbose = true;
  /** Cache of IR being processed by this phase */
  private IR ir;
  /** Cache of the value numbers from the IR  */
  private GlobalValueNumberState valueNumbers;
  /**
   * Cache of dominator tree that should be computed prior to this
   * phase
   */
  private DominatorTree dominator;
  /**
   * Available expressions. From Muchnick, "an expression
   * <em>exp</em>is said to be </em>available</em> at the entry to a
   * basic block if along every control-flow path from the entry block
   * to this block there is an evaluation of exp that is not
   * subsequently killed by having one or more of its operands
   * assigned a new value." Our available expressions are a mapping
   * from a value number to the first instruction to define it as we
   * traverse the dominator tree.
   */
  private final HashMap<Integer, Instruction> avail;

  /**
   * Constructor
   */
  public GlobalCSE() {
    avail = new HashMap<Integer, Instruction>();
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  public boolean shouldPerform(OptOptions options) {
    return options.SSA_GCSE;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor = getCompilerPhaseConstructor(GlobalCSE.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Returns the name of the phase
   */
  public String getName() {
    return "Global CSE";
  }

  /**
   * Perform the GlobalCSE compiler phase
   */
  public void perform(IR ir) {
    // conditions to leave early
    if (ir.hasReachableExceptionHandlers() || GCP.tooBig(ir)) {
      return;
    }
    // cache useful values
    verbose = ir.options.DEBUG_GCP;
    this.ir = ir;
    dominator = ir.HIRInfo.dominatorTree;

    // perform GVN
    (new GlobalValueNumber()).perform(ir);
    valueNumbers = ir.HIRInfo.valueNumbers;

    if (verbose) VM.sysWrite("in GCSE for " + ir.method + "\n");

    // compute DU and perform copy propagation
    DefUse.computeDU(ir);
    Simple.copyPropagation(ir);
    DefUse.computeDU(ir);

    // perform GCSE starting at the entry block
    globalCSE(ir.firstBasicBlockInCodeOrder());

    if (VM.VerifyAssertions) {
      VM._assert(avail.isEmpty(), avail.toString());
    }
    ir.actualSSAOptions.setScalarValid(false);
  }

  /**
   * Recursively descend over all blocks dominated by b. For each
   * instruction in the block, if it defines a GVN then record it in
   * the available expressions. If the GVN already exists in the
   * available expressions then eliminate the instruction and change
   * all uses of the result of the instruction to be uses of the first
   * instruction to define the result of this expression.
   * @param b the current block to process
   */
  private void globalCSE(BasicBlock b) {
    Instruction next, inst;
    // Iterate over instructions in b
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      // check instruction is safe for GCSE, {@see shouldCSE}
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      // check the instruction defines a result
      RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      // get the value number for this result. The value number for
      // the same sub-expression is shared by all results showing they
      // can be eliminated. If the value number is UNKNOWN the result
      // is negative.
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      // was this the first definition of the value number?
      Integer Vn = vn;
      Instruction former = avail.get(Vn);
      if (former == null) {
        // first occurance of value number, record it in the available
        // expressions
        avail.put(Vn, inst);
      } else {
        // this value number has been seen before so we can use the
        // earlier version
        // NB instead of trying to repair Heap SSA, we rebuild it
        // after CSE

        // relink scalar dependencies - make all uses of the current
        // instructions result use the first definition of the result
        // by the earlier expression
        RegisterOperand formerDef = getResult(former);
        Register reg = result.getRegister();
        formerDef.getRegister().setSpansBasicBlock();
        RegisterOperandEnumeration uses = DefUse.uses(reg);
        while (uses.hasMoreElements()) {
          RegisterOperand use = uses.next();
          DefUse.transferUse(use, formerDef);
        }
        if (verbose) {
          VM.sysWrite("using      " + former + "\n" + "instead of " + inst + "\n");
        }
        // remove the redundant instruction
        inst.remove();
      }
      inst = next;
    } // end of instruction iteration
    // Recurse over all blocks that this block dominates
    Enumeration<TreeNode> e = dominator.getChildren(b);
    while (e.hasMoreElements()) {
      DominatorTreeNode n = (DominatorTreeNode) e.nextElement();
      BasicBlock bl = n.getBlock();
      // don't process infrequently executed basic blocks
      if (ir.options.FREQ_FOCUS_EFFORT && bl.getInfrequent()) continue;
      globalCSE(bl);
    }
    // Iterate over instructions in this basic block removing
    // available expressions that had been created for this block
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      Integer Vn = vn;
      Instruction former = avail.get(Vn);
      if (former == inst) {
        avail.remove(Vn);
      }
      inst = next;
    }
  }

  /**
   * Get the result operand of the instruction
   * @param inst
   */
  private RegisterOperand getResult(Instruction inst) {
    if (ResultCarrier.conforms(inst)) {
      return ResultCarrier.getResult(inst);
    }
    if (GuardResultCarrier.conforms(inst)) {
      return GuardResultCarrier.getGuardResult(inst);
    }
    return null;
  }

  /**
   * should this instruction be cse'd  ?
   * @param inst
   */
  private boolean shouldCSE(Instruction inst) {

    if ((inst.isAllocation()) ||
        inst.isDynamicLinkingPoint() ||
        inst.isImplicitLoad() ||
        inst.isImplicitStore() ||
        inst.operator.opcode >= ARCH_INDEPENDENT_END_opcode) {
      return false;
    }

    switch (inst.operator.opcode) {
      case INT_MOVE_opcode:
      case LONG_MOVE_opcode:
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode:
      case MUST_IMPLEMENT_INTERFACE_opcode:
      case INSTANCEOF_opcode:
      case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case PI_opcode:
      case FLOAT_MOVE_opcode:
      case DOUBLE_MOVE_opcode:
      case REF_MOVE_opcode:
      case GUARD_MOVE_opcode:
      case GUARD_COMBINE_opcode:
      case TRAP_IF_opcode:
      case REF_ADD_opcode:
      case INT_ADD_opcode:
      case LONG_ADD_opcode:
      case FLOAT_ADD_opcode:
      case DOUBLE_ADD_opcode:
      case REF_SUB_opcode:
      case INT_SUB_opcode:
      case LONG_SUB_opcode:
      case FLOAT_SUB_opcode:
      case DOUBLE_SUB_opcode:
      case INT_MUL_opcode:
      case LONG_MUL_opcode:
      case FLOAT_MUL_opcode:
      case DOUBLE_MUL_opcode:
      case INT_DIV_opcode:
      case LONG_DIV_opcode:
      case FLOAT_DIV_opcode:
      case DOUBLE_DIV_opcode:
      case INT_REM_opcode:
      case LONG_REM_opcode:
      case FLOAT_REM_opcode:
      case DOUBLE_REM_opcode:
      case INT_NEG_opcode:
      case LONG_NEG_opcode:
      case FLOAT_NEG_opcode:
      case DOUBLE_NEG_opcode:
      case REF_SHL_opcode:
      case INT_SHL_opcode:
      case LONG_SHL_opcode:
      case REF_SHR_opcode:
      case INT_SHR_opcode:
      case LONG_SHR_opcode:
      case REF_USHR_opcode:
      case INT_USHR_opcode:
      case LONG_USHR_opcode:
      case REF_AND_opcode:
      case INT_AND_opcode:
      case LONG_AND_opcode:
      case REF_OR_opcode:
      case INT_OR_opcode:
      case LONG_OR_opcode:
      case REF_XOR_opcode:
      case INT_XOR_opcode:
      case REF_NOT_opcode:
      case INT_NOT_opcode:
      case LONG_NOT_opcode:
      case LONG_XOR_opcode:
      case INT_2LONG_opcode:
      case INT_2FLOAT_opcode:
      case INT_2DOUBLE_opcode:
      case INT_2ADDRSigExt_opcode:
      case INT_2ADDRZerExt_opcode:
      case LONG_2ADDR_opcode:
      case ADDR_2INT_opcode:
      case ADDR_2LONG_opcode:
      case LONG_2INT_opcode:
      case LONG_2FLOAT_opcode:
      case LONG_2DOUBLE_opcode:
      case FLOAT_2INT_opcode:
      case FLOAT_2LONG_opcode:
      case FLOAT_2DOUBLE_opcode:
      case DOUBLE_2INT_opcode:
      case DOUBLE_2LONG_opcode:
      case DOUBLE_2FLOAT_opcode:
      case INT_2BYTE_opcode:
      case INT_2USHORT_opcode:
      case INT_2SHORT_opcode:
      case LONG_CMP_opcode:
      case FLOAT_CMPL_opcode:
      case FLOAT_CMPG_opcode:
      case DOUBLE_CMPL_opcode:
      case DOUBLE_CMPG_opcode:
      case NULL_CHECK_opcode:
      case BOUNDS_CHECK_opcode:
      case INT_ZERO_CHECK_opcode:
      case LONG_ZERO_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case BOOLEAN_NOT_opcode:
      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_ADDR_opcode:
      case FLOAT_AS_INT_BITS_opcode:
      case INT_BITS_AS_FLOAT_opcode:
      case DOUBLE_AS_LONG_BITS_opcode:
      case LONG_BITS_AS_DOUBLE_opcode:
      case ARRAYLENGTH_opcode:
      case GET_OBJ_TIB_opcode:
      case GET_CLASS_TIB_opcode:
      case GET_TYPE_FROM_TIB_opcode:
      case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
      case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
      case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
        return !(GCP.usesOrDefsPhysicalRegisterOrAddressType(inst));
    }
    return false;
  }
}
