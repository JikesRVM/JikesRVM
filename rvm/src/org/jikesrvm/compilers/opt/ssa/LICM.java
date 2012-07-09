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
import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.DominatorInfo;
import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.controlflow.Dominators;
import org.jikesrvm.compilers.opt.controlflow.DominatorsPhase;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Label;
import org.jikesrvm.compilers.opt.ir.LocationCarrier;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.operand.BasicBlockOperand;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.util.Queue;

/**
 * This class does loop invariant code movement. It is a subphase of {@link GCP} (global code placement).
 */
public class LICM extends CompilerPhase {
  /** Generate debug output? */
  private static final boolean DEBUG = false;
  /** Generate verbose debug output? */
  private static boolean VERBOSE = false;

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor = getCompilerPhaseConstructor(LICM.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Execute loop invariant code motion on the given IR.
   */
  @Override
  public void perform(IR ir) {
    this.ir = ir;

    if (DEBUG && ir.hasReachableExceptionHandlers()) {
      VM.sysWrite("] " + ir.method + "\n");
      (new LiveAnalysis(false, false, true, false)).perform(ir);
      BasicBlockEnumeration e = ir.getBasicBlocks();
      while (e.hasMoreElements()) {
        BasicBlock b = e.next();
        if (b instanceof ExceptionHandlerBasicBlock) {
          VM.sysWrite("] " + b + ": " + ((ExceptionHandlerBasicBlock) b).getLiveSet() + "\n");
        }
      }
    }

    if (ir.hasReachableExceptionHandlers() || GCP.tooBig(ir)) {
      resetLandingPads();
      return;
    }

    VERBOSE = ir.options.DEBUG_GCP;

    if (VERBOSE && ir.options.hasMETHOD_TO_PRINT()) {
      VERBOSE = ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString());
      if (!VERBOSE) {
        resetLandingPads();
        return;
      }
    }

    if (VERBOSE) VM.sysWrite("] " + ir.method + "\n");
    initialize(ir);
    if (VERBOSE) SSA.printInstructions(ir);

    Instruction inst = ir.firstInstructionInCodeOrder();
    while (inst != null) {
      Instruction next = inst.nextInstructionInCodeOrder();
      if (DEBUG) System.out.println("scheduleEarly: " + inst);
      scheduleEarly(inst);
      inst = next;
    }

    inst = ir.lastInstructionInCodeOrder();
    while (inst != null) {
      Instruction next = inst.prevInstructionInCodeOrder();
      scheduleLate(inst);
      inst = next;
    }
    resetLandingPads();
    if (DEBUG) SSA.printInstructions(ir);
    ir.actualSSAOptions.setScalarValid(false);
  }

  /**
   * Returns the name of the phase
   */
  @Override
  public String getName() {
    return "LICM";
  }

  /**
   * @return <code>true</code> if SSA-based global code placement is being
   *  performed
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.SSA_GCP;
  }

  //------------------------- Implementation -------------------------

  /**
   * Is it save to move the given instruction, depending on we are
   * in heapSSA form or not?
   * @param inst
   * @param ir
   */
  public static boolean shouldMove(Instruction inst, IR ir) {
    if ((inst.isAllocation()) || inst.isDynamicLinkingPoint() || inst.operator.opcode >= ARCH_INDEPENDENT_END_opcode) {
      return false;
    }

    if (ir.IRStage != IR.HIR &&
        ((inst.isPEI()) || inst.isThrow() || inst.isImplicitLoad() || inst.isImplicitStore())) {
      return false;
    }

    switch (inst.operator.opcode) {
      case INT_MOVE_opcode:
      case LONG_MOVE_opcode:
      case INT_COND_MOVE_opcode:
      case LONG_COND_MOVE_opcode:
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
      case REF_COND_MOVE_opcode:
      case PUTSTATIC_opcode:
      case PUTFIELD_opcode:
      case GETSTATIC_opcode:
      case GETFIELD_opcode:
      case INT_ALOAD_opcode:
      case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:
      case REF_ALOAD_opcode:
      case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:
      case SHORT_ALOAD_opcode:
      case USHORT_ALOAD_opcode:
      case INT_ASTORE_opcode:
      case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:
      case REF_ASTORE_opcode:
      case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
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

  /**
   * Schedule this instruction as early as possible
   * @param inst
   */
  private Instruction scheduleEarly(Instruction inst) {
    Instruction _earlyPos;

    if (getState(inst) >= early) return getEarlyPos(inst);

    setState(inst, early);
    setEarlyPos(inst, inst);

    // already on outer level?
    //if (ir.HIRInfo.LoopStructureTree.getLoopNestDepth(getBlock(inst)) == 0)
    //  return inst;

    if (ir.options.FREQ_FOCUS_EFFORT && getOrigBlock(inst).getInfrequent()) {
      return inst;
    }

    // explicitly INCLUDE instructions
    if (!shouldMove(inst, ir)) {
      return inst;
    }
    // dependencies via scalar operands
    _earlyPos = scheduleScalarDefsEarly(inst.getUses(), ir.firstInstructionInCodeOrder(), inst);
    if (VM.VerifyAssertions) VM._assert(_earlyPos != null);

    // memory dependencies
    if (ir.IRStage == IR.HIR) {
      _earlyPos = scheduleHeapDefsEarly(ssad.getHeapUses(inst), _earlyPos, inst);
      if (VM.VerifyAssertions) VM._assert(_earlyPos != null);
    }

    /* don't put memory stores or PEIs on speculative path */
    if ((inst.isPEI() && !ir.options.SSA_LICM_IGNORE_PEI) || inst.isImplicitStore()) {
      while (!postDominates(getBlock(inst), getBlock(_earlyPos))) {
        _earlyPos = dominanceSuccessor(_earlyPos, inst);
      }
    }

    setEarlyPos(inst, _earlyPos);

    if (DEBUG && getBlock(_earlyPos) != getBlock(inst)) {
      VM.sysWrite("new earlyBlock: " + getBlock(_earlyPos) + " for " + getBlock(inst) + ": " + inst + "\n");
    }

    setBlock(inst, getBlock(_earlyPos));
    return _earlyPos;
  }

  /**
   * Schedule as late as possible.
   * @param inst
   */
  BasicBlock scheduleLate(Instruction inst) {
    if (DEBUG) VM.sysWrite("Schedule Late: " + inst + "\n");

    BasicBlock lateBlock = null;
    int _state = getState(inst);
    if (_state == late || _state == done) return getBlock(inst);

    setState(inst, late);

    if (ir.options.FREQ_FOCUS_EFFORT) {
      BasicBlock _origBlock = getOrigBlock(inst);
      if (_origBlock.getInfrequent()) {
        return _origBlock;
      }
    }

    // explicitly INCLUDE instructions
    if (!shouldMove(inst, ir)) {
      return getOrigBlock(inst);
    }

    // dependencies via scalar operands
    lateBlock = scheduleScalarUsesLate(inst, lateBlock);
    if (DEBUG) VM.sysWrite("lateBlock1: " + lateBlock + " for " + inst + "\n");

    // dependencies via heap operands
    if (ir.IRStage == IR.HIR) {
      lateBlock = scheduleHeapUsesLate(inst, lateBlock);
      if (DEBUG) VM.sysWrite("lateBlock2: " + lateBlock + " for " + inst + "\n");
    }

    // if there are no uses, this instruction is dead.
    if (lateBlock == null) {
      if (VERBOSE) VM.sysWrite("deleting " + inst + "\n");
      inst.remove();
    } else {
      if (DEBUG && lateBlock != getOrigBlock(inst)) {
        VM.sysWrite("new lateBlock: " + lateBlock + " for " + getOrigBlock(inst) + ": " + inst + "\n");
      }

      BasicBlock to = upto(getEarlyPos(inst), lateBlock, inst);
      if (to == null) {
        lateBlock = getOrigBlock(inst);
      } else {
        if (VM.VerifyAssertions) VM._assert(getState(inst) != done);
        lateBlock = to;
        if (getOrigBlock(inst) != to) move(inst, to);
      }
    }
    setState(inst, done);
    setBlock(inst, lateBlock);
    return lateBlock;
  }

  /**
   * return `a's successor on the path from `a' to `b' in the dominator
   * tree. `a' must dominate `b' and `a' and `b' must belong to
   * different blocks.
   */
  private Instruction dominanceSuccessor(Instruction a, Instruction b) {
    BasicBlock aBlock = getBlock(a);
    BasicBlock bBlock = getBlock(b);

    if (VM.VerifyAssertions) {
      VM._assert(aBlock != bBlock && dominator.dominates(aBlock, bBlock));
    }

    BasicBlock last = null;

    while (bBlock != aBlock) {
      last = bBlock;
      bBlock = dominator.getParent(bBlock);
    }
    return last.firstInstruction();
  }

  /**
   * compare a and b according to their depth in the dominator tree
   * and return the one with the greatest depth.
   */
  private Instruction maxDominatorDepth(Instruction a, Instruction b) {
    BasicBlock aBlock = getBlock(a);
    BasicBlock bBlock = getBlock(b);
    int aDomDepth = dominator.depth(aBlock);
    int bDomDepth = dominator.depth(bBlock);

    if (aDomDepth > bDomDepth) return a;
    if (aDomDepth < bDomDepth) return b;

    if (VM.VerifyAssertions) VM._assert(aBlock == bBlock);

    // if an instruction depends on a branch, it can not be placed in
    // this block. Make sure we record this fact. We use this
    // information in upto()
    return a.isBranch() ? a : b;
  }

  private BasicBlock commonDominator(BasicBlock a, BasicBlock b) {
    //VM.sysWrite ("CD: "+a+", "+b);
    if (a == null) return b;
    if (b == null) return a;

    while (a != b) {
      int aDomDepth = dominator.depth(a);
      int bDomDepth = dominator.depth(b);
      if (aDomDepth >= bDomDepth) a = dominator.getParent(a);
      if (bDomDepth >= aDomDepth) b = dominator.getParent(b);
    }
    //VM.sysWrite (" = "+a+"\n");
    return a;
  }

  /**
   * Schedule me as early as possible,
   * but behind the definitions in e and behind earlyPos
   */
  private Instruction scheduleScalarDefsEarly(OperandEnumeration e, Instruction earlyPos,
                                                  Instruction inst) {
    while (e.hasMoreElements()) {
      Operand op = e.next();
      Instruction def = definingInstruction(op);

      scheduleEarly(def);

      if (def.isBranch()) def = dominanceSuccessor(def, inst);

      earlyPos = maxDominatorDepth(def, earlyPos);
    }
    return earlyPos;
  }

  /**
   * Schedule me as early as possible,
   * but behind the definitions of op[i] and behind earlyPos
   */
  Instruction scheduleHeapDefsEarly(HeapOperand<?>[] op, Instruction earlyPos, Instruction me) {
    if (op == null) return earlyPos;

    for (HeapOperand<?> anOp : op) {
      Instruction def = definingInstruction(anOp);

      //  if (me.isImplicitLoad() || me.isImplicitStore())
//      def = _getRealDef(def, me)
//        ;
//        else if (me.isPEI())
//      def = _getRealExceptionDef(def)
      //  ;

      if (VM.VerifyAssertions) VM._assert(def != null);
      earlyPos = maxDominatorDepth(scheduleEarly(def), earlyPos);
    }
    return earlyPos;
  }

  BasicBlock useBlock(Instruction use, Operand op) {
    //VM.sysWrite ("UseBlock: "+use+"\n");
    BasicBlock res = scheduleLate(use);
    if (res != null && Phi.conforms(use)) {
      int i;
      for (i = Phi.getNumberOfValues(use) - 1; i >= 0; --i) {
        if (Phi.getValue(use, i) == op) {
          res = Phi.getPred(use, i).block;
          break;
        }
      }
      if (VM.VerifyAssertions) VM._assert(i >= 0);
    }
    return res;
  }

  /**
   * Schedule me as late as possible,
   * but in front of my uses and before latePos
   */
  private BasicBlock scheduleScalarUsesLate(Instruction inst, BasicBlock lateBlock) {
    Operand resOp = getResult(inst);

    if (resOp == null || !(resOp instanceof RegisterOperand)) {
      return lateBlock;
    }

    Register res = ((RegisterOperand) resOp).getRegister();
    RegisterOperandEnumeration e = DefUse.uses(res);

    while (e.hasMoreElements()) {
      Operand op = e.next();
      Instruction use = op.instruction;
      BasicBlock _block = useBlock(use, op);
      lateBlock = commonDominator(_block, lateBlock);
    }
    return lateBlock;
  }

  /**
   * Schedule me as early as possible,
   * but behind the definitions of op[i] and behind earlyPos
   */
  BasicBlock scheduleHeapUsesLate(Instruction inst, BasicBlock lateBlock) {
    //VM.sysWrite (" scheduleHeapUsesLate\n");
    Operand[] defs = ssad.getHeapDefs(inst);
    if (defs == null) return lateBlock;

    //VM.sysWrite (" defs: "+defs.length+"\n");
    for (Operand def : defs) {
      @SuppressWarnings("unchecked") // Cast to generic HeapOperand
          HeapOperand<Object> dhop = (HeapOperand) def;
      HeapVariable<Object> H = dhop.value;
      if (DEBUG) VM.sysWrite("H: " + H + "\n");
      Iterator<HeapOperand<Object>> it = ssad.iterateHeapUses(H);
      //VM.sysWrite (" H: "+H+" ("+ssad.getNumberOfUses (H)+")\n");
      while (it.hasNext()) {
        HeapOperand<Object> uhop = it.next();
        //VM.sysWrite (" uhop: "+uhop+"\n");
        Instruction use = uhop.instruction;
        //VM.sysWrite ("use: "+use+"\n");
        BasicBlock _block = useBlock(use, uhop);
        lateBlock = commonDominator(_block, lateBlock);
      }
    }
    return lateBlock;
  }

  /**
   * Return the instruction that defines the operand.
   * @param op
   */
  Instruction definingInstruction(Operand op) {
    if (op instanceof HeapOperand) {
      @SuppressWarnings("unchecked") // Cast to generic HeapOperand
          HeapOperand<Object> hop = (HeapOperand) op;
      HeapVariable<Object> H = hop.value;
      HeapOperand<Object> defiOp = ssad.getUniqueDef(H);
      // Variable may be defined by caller, so depends on method entry
      if (defiOp == null || defiOp.instruction == null) {
        return ir.firstInstructionInCodeOrder();
      } else {
        //VM.sysWrite ("def of "+op+" is "+defiOp.instruction+"\n");
        return defiOp.instruction;
      }
    } else if (op instanceof RegisterOperand) {
      Register reg = ((RegisterOperand) op).getRegister();
      RegisterOperandEnumeration defs = DefUse.defs(reg);
      if (!defs.hasMoreElements()) {          // params have no def
        return ir.firstInstructionInCodeOrder();
      } else {
        Instruction def = defs.next().instruction;
        // we are in SSA, so there is at most one definition.
        if (VM.VerifyAssertions) VM._assert(!defs.hasMoreElements());
        //if (defs.hasMoreElements()) {
        //  VM.sysWrite("GCP: multiple defs: " + reg + "\n");
        //  return  null;
        //}
        return def;
      }
    } else {                    // some constant
      return ir.firstInstructionInCodeOrder();
    }
  }

  /**
   * Get the result operand of the instruction
   * @param inst
   */
  Operand getResult(Instruction inst) {
    if (ResultCarrier.conforms(inst)) {
      return ResultCarrier.getResult(inst);
    }
    if (GuardResultCarrier.conforms(inst)) {
      return GuardResultCarrier.getGuardResult(inst);
    }
    if (Phi.conforms(inst)) {
      return Phi.getResult(inst);
    }
    return null;
  }

  /**
   * Visit the blocks between the late and the early position along
   * their path in the dominator tree.
   * Return the block with the smallest execution costs.
   */
  BasicBlock upto(Instruction earlyPos, BasicBlock lateBlock, Instruction inst) {

    BasicBlock _origBlock = getOrigBlock(inst);
    BasicBlock actBlock = lateBlock;
    BasicBlock bestBlock = lateBlock;
    BasicBlock earlyBlock = getBlock(earlyPos);

    if (VM.VerifyAssertions) {
      if (!dominator.dominates(earlyBlock.getNumber(), _origBlock.getNumber()) ||
          !dominator.dominates(earlyBlock.getNumber(), lateBlock.getNumber())) {
        SSA.printInstructions(ir);
        VM.sysWrite("> " +
                    earlyBlock.getNumber() +
                    ", " +
                    _origBlock.getNumber() +
                    ", " +
                    lateBlock.getNumber() +
                    "\n");
        VM.sysWrite("" + inst + "\n");
      }
      VM._assert(dominator.dominates(earlyBlock.getNumber(), _origBlock.getNumber()));
      VM._assert(dominator.dominates(earlyBlock.getNumber(), lateBlock.getNumber()));
    }
    for (; ;) {
      /* is the actual block better (less frequent)
         than the so far best block? */
      if (frequency(actBlock) < frequency(bestBlock)) {
        if (DEBUG) {
          VM.sysWrite("going from " + frequency(_origBlock) + " to " + frequency(actBlock) + "\n");
        }
        bestBlock = actBlock;
      }
      /* all candidates checked? */
      if (actBlock == earlyBlock) {
        break;
      }

      /* walk up the dominator tree for next candidate*/
      actBlock = dominator.getParent(actBlock);
    }
    if (bestBlock == _origBlock) return null;
    if (DEBUG) VM.sysWrite("best Block: " + bestBlock + "\n");
    return bestBlock;
  }

  /**
   * How expensive is it to place an instruction in this block?
   */
  final float frequency(BasicBlock b) {
    return b.getExecutionFrequency();
  }

  /**
   * move `inst' behind `pred'
   */
  void move(Instruction inst, BasicBlock to) {

    BasicBlock _origBlock = getOrigBlock(inst);
    Instruction cand = null;

    /* find a position within bestBlock */
    if (dominator.dominates(_origBlock.getNumber(), to.getNumber())) {
      // moved down, so insert in from
      Instruction last = null;
      InstructionEnumeration e = to.forwardInstrEnumerator();
      while (e.hasMoreElements()) {
        cand = e.next();
        if (DEBUG) VM.sysWrite(cand.toString() + "\n");
        // skip labels, phis, and yieldpoints
        if (!Label.conforms(cand) && !cand.isYieldPoint() && !Phi.conforms(cand)) {
          break;
        }
        last = cand;
      }
      cand = last;
    } else {
      // moved up, so insert at end of block
      InstructionEnumeration e = to.reverseInstrEnumerator();
      while (e.hasMoreElements()) {
        cand = e.next();
        if (DEBUG) VM.sysWrite(cand.toString() + "\n");
        // skip branches and newly placed insts
        if (!BBend.conforms(cand) && !cand.isBranch() && !relocated.contains(cand)) {
          break;
        }
      }
      if (DEBUG) VM.sysWrite("Adding to relocated: " + inst + "\n");
      relocated.add(inst);
    }

    if (DEBUG && moved.add(inst.operator)) {
      VM.sysWrite("m(" + (ir.IRStage == IR.LIR ? "l" : "h") + ") " + inst.operator + "\n");
    }
    if (VERBOSE) {
      VM.sysWrite(ir.IRStage == IR.LIR ? "%" : "#");
      VM.sysWrite(" moving " + inst + " from " + _origBlock + " to " + to + "\n" + "behind  " + cand + "\n");

    }
    inst.remove();
    cand.insertAfter(inst);
  }

  //------------------------------------------------------------
  // some helper methods
  //------------------------------------------------------------

  /**
   * does a post dominate b?
   * @param a
   * @param b
   */
  boolean postDominates(BasicBlock a, BasicBlock b) {
    boolean res;
    if (a == b) {
      return true;
    }
    //VM.sysWrite ("does " + a + " postdominate " + b + "?: ");
    DominatorInfo info = (DominatorInfo) b.scratchObject;
    res = info.isDominatedBy(a);
    //VM.sysWrite (res ? "yes\n" : "no\n");
    return res;
  }

  /**
   * Get the basic block of an instruction
   * @param inst
   */
  BasicBlock getBlock(Instruction inst) {
    return block[inst.scratch];
  }

  /**
   * Set the basic block for an instruction
   * @param inst
   * @param b
   */
  void setBlock(Instruction inst, BasicBlock b) {
    block[inst.scratch] = b;
  }

  /**
   * Get the early position of an instruction
   * @param inst
   */
  Instruction getEarlyPos(Instruction inst) {
    return earlyPos[inst.scratch];
  }

  /**
   * Set the early position for an instruction
   * @param inst
   * @param pos
   */
  void setEarlyPos(Instruction inst, Instruction pos) {
    earlyPos[inst.scratch] = pos;
  }

  /**
   * Get the block, where the instruction was originally located
   * @param inst
   */
  BasicBlock getOrigBlock(Instruction inst) {
    return origBlock[inst.scratch];
  }

  /**
   * Set the block, where the instruction is originally located.
   * @param inst
   * @param b
   */
  void setOrigBlock(Instruction inst, BasicBlock b) {
    origBlock[inst.scratch] = b;
  }

  /**
   * In what state (initial, early, late, done) is this instruction
   * @param inst
   */
  int getState(Instruction inst) {
    return state[inst.scratch];
  }

  /**
   * Set the state (initial, early, late, done) of the instruction
   * @param inst
   * @param s
   */
  void setState(Instruction inst, int s) {
    state[inst.scratch] = s;
  }

  //------------------------------------------------------------
  // initialization
  //------------------------------------------------------------

  /**
   * initialize the state of the algorithm
   */
  void initialize(IR ir) {
    this.ir = ir;

    relocated = new HashSet<Instruction>();
    // Note: the following unfactors the CFG
    new DominatorsPhase(true).perform(ir);
    Dominators.computeApproxPostdominators(ir);
    dominator = ir.HIRInfo.dominatorTree;
    if (DEBUG) VM.sysWrite("" + dominator.toString() + "\n");
    int instructions = ir.numberInstructions();
    ssad = ir.HIRInfo.dictionary;
    DefUse.computeDU(ir);
    ssad.recomputeArrayDU();
    // also number implicit heap phis
    BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      BasicBlock b = e.next();
      Iterator<Instruction> pe = ssad.getHeapPhiInstructions(b);
      while (pe.hasNext()) {
        Instruction inst = pe.next();
        inst.scratch = instructions++;
        inst.scratchObject = null;
      }
    }
    state = new int[instructions];
    origBlock = new BasicBlock[instructions];
    block = new BasicBlock[instructions];
    earlyPos = new Instruction[instructions];
    e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      BasicBlock b = e.next();
      Enumeration<Instruction> ie = ssad.getAllInstructions(b);
      while (ie.hasMoreElements()) {
        Instruction inst = ie.nextElement();
        setBlock(inst, b);
        setOrigBlock(inst, b);
        setState(inst, initial);
      }
    }
    if (ir.IRStage == IR.HIR) {
      e = ir.getBasicBlocks();
      while (e.hasMoreElements()) {
        BasicBlock b = e.next();

        if (ir.options.FREQ_FOCUS_EFFORT && b.getInfrequent()) continue;

        Enumeration<Instruction> ie = ssad.getAllInstructions(b);
        while (ie.hasMoreElements()) {
          Instruction inst = ie.nextElement();
          while (simplify(inst, b)) ;
        }
      }
      ssad.recomputeArrayDU();
    }
  }

  //------------------------------------------------------------
  // private state
  //------------------------------------------------------------
  private static final int initial = 0;
  private static final int early = 1;
  private static final int late = 2;
  private static final int done = 3;

  private HashSet<Instruction> relocated;

  private int[] state;
  private BasicBlock[] block;
  private BasicBlock[] origBlock;
  private Instruction[] earlyPos;
  private SSADictionary ssad;
  private DominatorTree dominator;
  private IR ir;
  private final HashSet<Operator> moved = DEBUG ? new HashSet<Operator>() : null;

  private boolean simplify(Instruction inst, BasicBlock block) {
    if (!Phi.conforms(inst)) return false;  // no phi

    //if (Phi.getNumberOfValues (inst) != 2) return false; // want exactly 2 inputs

    //VM.sysWrite ("Simplify "+inst+"\n");

    Operand resOp = Phi.getResult(inst);

    if (!(resOp instanceof HeapOperand)) {
      //VM.sysWrite (" no heap op result\n");
      return false; // scalar phi
    }

    int xidx = -1;
    Instruction x = null;

    for (int i = Phi.getNumberOfValues(inst) - 1; i >= 0; --i) {
      Instruction in = definingInstruction(Phi.getValue(inst, i));
      if (getOrigBlock(in) != getOrigBlock(inst) && dominator.dominates(getOrigBlock(in), getOrigBlock(inst))) {
        if (xidx != -1) return false;
        xidx = i;
        x = in;
      } else if (!dominator.dominates(getOrigBlock(inst), getOrigBlock(in))) {
        return false;
      }
    }
    if (x == null) return false;

    replaceUses(inst, (HeapOperand<?>) Phi.getValue(inst, xidx), Phi.getPred(inst, xidx), true);

    @SuppressWarnings("unchecked") // Cast to generic HeapOperand
        HeapOperand<Object> hop = (HeapOperand) resOp;
    if (hop.value.isExceptionHeapType()) return false;

    /* check that inside the loop, the heap variable is only used/defed
       by simple, non-volatile loads or only by stores

       if so, replace uses of inst (a memory phi) with its dominating input
    */
    int type = checkLoop(inst, hop, xidx, block);
    if (type == CL_LOADS_ONLY || type == CL_STORES_ONLY || type == CL_NONE) {
      replaceUses(inst, (HeapOperand<?>) Phi.getValue(inst, xidx), Phi.getPred(inst, xidx), false);
    }
    return false;
  }

  static final int CL_NONE = 0;
  static final int CL_LOADS_ONLY = 1;
  static final int CL_STORES_ONLY = 2;
  static final int CL_LOADS_AND_STORES = 3;
  static final int CL_COMPLEX = 4;

  /**
   * check that inside the loop, the heap variable is only used/defed
   * by simple, non-volatile loads/stores<p>
   *
   * returns one of:
   * CL_LOADS_ONLY, CL_STORES_ONLY, CL_LOADS_AND_STORES, CL_COMPLEX
   */
  @SuppressWarnings("unused")
  // useful for debugging
  private int _checkLoop(Instruction inst, HeapOperand<?> hop, int xidx) {
    for (int i = Phi.getNumberOfValues(inst) - 1; i >= 0; --i) {
      if (i == xidx) continue;
      Instruction y = definingInstruction(Phi.getValue(inst, i));
      while (y != inst) {
        //VM.sysWrite (" y: "+y+"\n");
        if (y.isImplicitStore() || y.isPEI() || !LocationCarrier.conforms(y)) {
          return CL_COMPLEX;
        }

        // check for access to volatile field
        LocationOperand loc = LocationCarrier.getLocation(y);
        if (loc == null || loc.mayBeVolatile()) {
          //VM.sysWrite (" no loc or volatile field\n");
          return CL_COMPLEX;
        }
        for (HeapOperand<?> op : ssad.getHeapUses(y)) {
          if (op.value.isExceptionHeapType()) continue;
          if (op.getHeapType() != hop.getHeapType()) return CL_COMPLEX;
          y = definingInstruction(op);
        }
      }
    }
    return CL_LOADS_ONLY;
  }

  /**
   * check that inside the loop, the heap variable is only used/defed
   * by simple, non-volatile loads/stores<p>
   *
   * returns one of:
   * CL_LOADS_ONLY, CL_STORES_ONLY, CL_LOADS_AND_STORES, CL_COMPLEX
   */
  private int checkLoop(Instruction inst, HeapOperand<Object> hop, int xidx, BasicBlock block) {
    HashSet<Instruction> seen = new HashSet<Instruction>();
    Queue<Instruction> workList = new Queue<Instruction>();
    int _state = CL_NONE;
    int instUses = 0;

    seen.add(inst);
    for (int i = Phi.getNumberOfValues(inst) - 1; i >= 0; --i) {
      if (i == xidx) continue;
      Instruction y = definingInstruction(Phi.getValue(inst, i));
      if (y == inst) instUses++;
      if (!(seen.contains(y))) {
        seen.add(y);
        workList.insert(y);
      }
    }

    while (!(workList.isEmpty())) {
      Instruction y = workList.remove();
      if (Phi.conforms(y)) {
        for (int i = Phi.getNumberOfValues(y) - 1; i >= 0; --i) {
          Instruction z = definingInstruction(Phi.getValue(y, i));
          if (z == inst) instUses++;
          if (!(seen.contains(z))) {
            seen.add(z);
            workList.insert(z);
          }
        }
      } else if ((y.isPEI()) || !LocationCarrier.conforms(y) || y.operator.isAcquire() || y.operator.isRelease()) {
        return CL_COMPLEX;
      } else {
        // check for access to volatile field
        LocationOperand loc = LocationCarrier.getLocation(y);
        if (loc == null || loc.mayBeVolatile()) {
          //VM.sysWrite (" no loc or volatile field\n");
          return CL_COMPLEX;
        }
        if (y.isImplicitStore()) {
          // only accept loop-invariant stores
          // conservatively estimate loop-invariance by header domination
          if (!inVariantLocation(y, block)) return CL_COMPLEX;
          _state |= CL_STORES_ONLY;
        } else {
          _state |= CL_LOADS_ONLY;
        }
        for (HeapOperand<?> op : ssad.getHeapUses(y)) {
          if (op.value.isExceptionHeapType()) continue;
          if (op.getHeapType() != hop.getHeapType()) return CL_COMPLEX;
          y = definingInstruction(op);
          if (y == inst) instUses++;
          if (!(seen.contains(y))) {
            seen.add(y);
            workList.insert(y);
          }
        }
      }
    }
    if (_state == CL_STORES_ONLY && ssad.getNumberOfUses(hop.value) != instUses) {
      return CL_COMPLEX;
    }

    return _state;
  }

  private boolean inVariantLocation(Instruction inst, BasicBlock block) {
    if (PutStatic.conforms(inst)) return true;
    if (PutField.conforms(inst)) {
      return useDominates(PutField.getRef(inst), block);
    }
    if (AStore.conforms(inst)) {
      return ((useDominates(AStore.getArray(inst), block)) && useDominates(AStore.getIndex(inst), block));
    }
    if (VM.VerifyAssertions) {
      VM._assert(false, "inst: " + inst);
    }
    return false;
  }

  private boolean useDominates(Operand op, BasicBlock block) {
    if (!(op instanceof RegisterOperand)) return true;
    Instruction inst = definingInstruction(op);
    BasicBlock b = getOrigBlock(inst);
    return b != block && dominator.dominates(b, block);
  }

  /**
   * In the consumers of `inst', replace uses of `inst's result
   * with uses of `replacement'
   */
  private boolean replaceUses(Instruction inst, HeapOperand<?> replacement,
                              BasicBlockOperand replacementBlock, boolean onlyPEIs) {
    if (VM.VerifyAssertions) VM._assert(Phi.conforms(inst));

    boolean changed = false;
    @SuppressWarnings("unchecked") // Cast to generic HeapOperand
        HeapOperand<Object> hop = (HeapOperand) Phi.getResult(inst);
    HeapVariable<Object> H = hop.value;
    Iterator<HeapOperand<Object>> it = ssad.iterateHeapUses(H);
    while (it.hasNext()) {
      hop = it.next();
      Instruction user = hop.instruction;
      if (onlyPEIs && !user.isPEI()) continue;

      if (Phi.conforms(user)) {
        for (int i = 0; i < Phi.getNumberOfValues(user); i++) {
          if (Phi.getValue(user, i) == hop) {
            Phi.setValue(user, i, replacement.copy());
            Phi.setPred(user, i, (BasicBlockOperand) replacementBlock.copy());
          }
        }
        changed |= replacement.value != H;
      } else {
        HeapOperand<?>[] uses = ssad.getHeapUses(user);
        for (int i = uses.length - 1; i >= 0; --i) {
          if (uses[i].value == H) {
            changed |= replacement.value != H;
            uses[i] = replacement.copy();
            uses[i].setInstruction(user);
          }
        }
      }
      if (DEBUG && changed) {
        VM.sysWrite(" changing dependency of " + user + "\n" + "from " + H + " to " + replacement + "\n");
      }
    }
    if (!onlyPEIs) {
      for (int i = Phi.getNumberOfValues(inst) - 1; i >= 0; --i) {
        Phi.setValue(inst, i, replacement.copy());
      }
    }
    return changed;
  }

  private void resetLandingPads() {
    BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) e.next().clearLandingPad();
  }
}
