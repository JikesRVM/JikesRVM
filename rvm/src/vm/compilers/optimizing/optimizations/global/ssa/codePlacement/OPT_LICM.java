/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.*;
import  com.ibm.JikesRVM.opt.ir.*;

/**
 * This class does the job. It is a subphase of OPT_GCP.
 * 
 * @author Martin Trapp
 * @modified Stephen Fink 
 */
class OPT_LICM extends OPT_CompilerPhase implements OPT_Operators {

  /**
   * Execute loop invariant code motion on the given IR.
   * @param ir
   */
  public void perform(OPT_IR ir) {
    this.ir = ir;

    if (DEBUG && ir.hasReachableExceptionHandlers()) {
      VM.sysWrite ("] "+ir.method+"\n");
      (new OPT_LiveAnalysis(false, false, true, false)).perform(ir);
      OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
      while (e.hasMoreElements()) {
        OPT_BasicBlock b = e.next();
        if (b instanceof OPT_ExceptionHandlerBasicBlock)
          VM.sysWrite ("] "+b+": "
                       +((OPT_ExceptionHandlerBasicBlock)b).getLiveSet()
                       +"\n");
      }
    }
      
    if (ir.hasReachableExceptionHandlers() || OPT_GCP.tooBig(ir)) {
      resetLandingPads();
      return;
    }
    
    verbose = ir.options.VERBOSE_GCP;
    
    if (verbose && ir.options.hasMETHOD_TO_PRINT()) {
      verbose = ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString());
      if (!verbose) {
        resetLandingPads();
        return;
      }
    }
    
    if (verbose) VM.sysWrite ("] "+ir.method+"\n");
    initialize(ir);
    if (verbose) OPT_SSA.printInstructions (ir);
    

    OPT_Instruction inst = ir.firstInstructionInCodeOrder();    
    while (inst != null) {
      OPT_Instruction next = inst.nextInstructionInCodeOrder();
      if (DEBUG) System.out.println("scheduleEarly: " + inst);
      scheduleEarly(inst);
      inst = next;
    }
    
    inst = ir.lastInstructionInCodeOrder();
    while (inst != null) {
      OPT_Instruction next = inst.prevInstructionInCodeOrder();
      scheduleLate (inst);
      inst = next;
    }
    resetLandingPads();
    if (DEBUG) OPT_SSA.printInstructions (ir);
  }

  /**
   * Returns the name of the phase
   */
  public String getName() {
    return  "LICM";
  }
  
  /**
   * Should this phase be executed?
   * @param options
   */
  public boolean shouldPerform(OPT_Options options) {
    return  options.GCP || options.VERBOSE_GCP;
  }
  
  /**
   * Report that we feel really sick for the given reason.
   * @param reason
   */
  static void BARF(String reason) {
    throw  new OPT_OptimizingCompilerException(reason);
  }
  
  //------------------------- Implementation -------------------------
  
  /**
   * Is it save to move the given instruction, depending on we are
   * in heapSSA form or not?
   * @param inst
   * @param ir
   */
  public static boolean shouldMove(OPT_Instruction inst, OPT_IR ir)
  {    
    if ((  inst.isAllocation())
        || inst.isDynamicLinkingPoint()
        || inst.operator.opcode >= ARCH_INDEPENDENT_END_opcode)
      return false;
    
    if (ir.IRStage != ir.HIR
        && ((  inst.isPEI())
            || inst.isThrow()
            || inst.isLoad()
            || inst.isStore()))
        return false;

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
    case GET_CLASS_OBJECT_opcode:
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
    case INT_ADD_opcode:
    case LONG_ADD_opcode:
    case FLOAT_ADD_opcode:
    case DOUBLE_ADD_opcode:
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
    case INT_SHL_opcode:
    case LONG_SHL_opcode:
    case INT_SHR_opcode:
    case LONG_SHR_opcode:
    case INT_USHR_opcode:
    case LONG_USHR_opcode:
    case INT_AND_opcode:
    case LONG_AND_opcode:
    case INT_OR_opcode:
    case LONG_OR_opcode:
    case INT_XOR_opcode:
    case INT_NOT_opcode:
    case LONG_NOT_opcode:
    case LONG_XOR_opcode:
    case INT_2LONG_opcode:
    case INT_2FLOAT_opcode:
    case INT_2DOUBLE_opcode:
    case INT_2ADDRSigExt_opcode:
    case INT_2ADDRZerExt_opcode:
  //-#if RVM_FOR_64_ADDR
    case LONG_2ADDR_opcode:
  //-#endif
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
    case BOOLEAN_CMP_opcode:
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
      return !(OPT_GCP.usesOrDefsPhysicalRegisterOrAddressType(inst));
    }
    return false;
  }

  /**
   * Schedule this instruction as early as possible
   * @param inst
   */
  private OPT_Instruction scheduleEarly(OPT_Instruction inst)
  {
    OPT_Instruction earlyPos;

    if (getState(inst) >= early) return getEarlyPos (inst);
    
    setState (inst, early);
    setEarlyPos (inst, inst);
    
    // already on outer level?
    //if (ir.HIRInfo.LoopStructureTree.getLoopNestDepth(getBlock(inst)) == 0)
    //  return inst;

    if (ir.options.FREQ_FOCUS_EFFORT && getOrigBlock (inst).getInfrequent())
      return inst;
    
    // explicitly INCLUDE instructions
    if (!shouldMove(inst, ir)) {
      return inst;
    }
    // dependencies via scalar operands
    earlyPos = scheduleScalarDefsEarly(inst.getUses(),
                                       ir.firstInstructionInCodeOrder(), inst);
    if (VM.VerifyAssertions) VM._assert (earlyPos != null);
    
    // memory dependencies
    if (ir.IRStage == ir.HIR) {
      earlyPos = scheduleHeapDefsEarly(ssad.getHeapUses(inst), earlyPos, inst);
      if (VM.VerifyAssertions) VM._assert (earlyPos != null);
    }

    /* don't put memory stores or PEIs on speculative path */
    if ((inst.isPEI() && !ir.options.LICM_IGNORE_PEI)
        || inst.isImplicitStore())
      while (!postDominates (getBlock (inst), getBlock (earlyPos)))
        earlyPos = dominanceSuccessor (earlyPos, inst);

    setEarlyPos (inst, earlyPos);

    if (DEBUG && getBlock (earlyPos) != getBlock (inst)) {
      VM.sysWrite ("new earlyBlock: "+ getBlock (earlyPos) +" for "
                   + getBlock (inst)+": "+inst+"\n");
    }
    
    setBlock (inst, getBlock (earlyPos));
    return earlyPos;    
  }

  
  /**
   * Schedule as late as possible.
   * @param inst
   */
  OPT_BasicBlock scheduleLate(OPT_Instruction inst)
  {
    if (DEBUG) VM.sysWrite ("Schedule Late: "+inst+"\n");
    
    OPT_BasicBlock lateBlock = null;
    int state = getState (inst);
    if (state == late || state == done) return getBlock (inst);

    setState (inst, late);

    if (ir.options.FREQ_FOCUS_EFFORT) {
      OPT_BasicBlock origBlock = getOrigBlock (inst);
      if (origBlock.getInfrequent())
        return origBlock;
    }
    
    // explicitly INCLUDE instructions
    if (!shouldMove(inst, ir)) {
      return getOrigBlock (inst);
    }

    // dependencies via scalar operands
    lateBlock = scheduleScalarUsesLate (inst, lateBlock);
    if (DEBUG) VM.sysWrite ("lateBlock1: "+ lateBlock +" for "+inst+"\n");
    
    // dependencies via heap operands
    if (ir.IRStage == ir.HIR) {
      lateBlock = scheduleHeapUsesLate (inst, lateBlock);
      if (DEBUG) VM.sysWrite ("lateBlock2: "+ lateBlock +" for "+inst+"\n");
    }

    
    // if there are no uses, this instruction is dead.
    if (lateBlock == null) {
      if (verbose) VM.sysWrite ("deleting "+inst+"\n");
      inst.remove();      
    } else {
      if (DEBUG && lateBlock != getOrigBlock (inst)) {
        VM.sysWrite ("new lateBlock: "+ lateBlock +" for "
                     + getOrigBlock (inst)+": "+inst+"\n");
      }
      
      OPT_BasicBlock to = upto (getEarlyPos (inst), lateBlock, inst);
      if (to == null) {
        lateBlock = getOrigBlock (inst);
      } else {
        if (VM.VerifyAssertions) VM._assert (getState (inst) != done);
        lateBlock = to;
        if (getOrigBlock (inst) != to) move (inst, to);
      }
    }
    setState (inst, done);
    setBlock (inst, lateBlock);
    return lateBlock;
  }



  /**
   * return `a's successor on the path from `a' to `b' in the dominator
   * tree. `a' must dominate `b' and `a' and `b' must belong to
   * different blocks.
   */
  private OPT_Instruction dominanceSuccessor(OPT_Instruction a, OPT_Instruction b)
  {
    OPT_BasicBlock aBlock = getBlock (a);
    OPT_BasicBlock bBlock = getBlock (b);

    if (VM.VerifyAssertions)
      VM._assert (aBlock != bBlock
                 && dominator.dominates (aBlock, bBlock));
    
    OPT_BasicBlock last = null;
    
    while (bBlock != aBlock) {
      last = bBlock;
      bBlock = dominator.getParent (bBlock);
    }
    return last.firstInstruction();
  }

  /**
   * compare a and b according to their depth in the dominator tree
   * and return the one with the greatest depth.
   */
  private OPT_Instruction maxDominatorDepth(OPT_Instruction a, OPT_Instruction b)
  {
    OPT_BasicBlock aBlock = getBlock(a);
    OPT_BasicBlock bBlock = getBlock(b);
    int aDomDepth = dominator.depth(aBlock);
    int bDomDepth = dominator.depth(bBlock);

    if (aDomDepth > bDomDepth) return a;
    if (aDomDepth < bDomDepth) return b;

    if (VM.VerifyAssertions) VM._assert (aBlock == bBlock);
    
    // if an instruction depends on a branch, it can not be placed in
    // this block. Make sure we record this fact. We use this
    // information in upto()
    return a.isBranch() ? a : b;
  }

  
  private OPT_BasicBlock commonDominator(OPT_BasicBlock a, OPT_BasicBlock b) {
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
  private OPT_Instruction scheduleScalarDefsEarly(OPT_OperandEnumeration e, 
                                                   OPT_Instruction earlyPos,
                                                   OPT_Instruction inst) {
    while (e.hasMoreElements()) {
      OPT_Operand op = e.next();
      OPT_Instruction def = definingInstruction(op);

      scheduleEarly (def);

      if (def.isBranch()) def = dominanceSuccessor(def, inst);
        
      earlyPos = maxDominatorDepth (def, earlyPos);
    }
    return  earlyPos;
  }


  /**
   * Schedule me as early as possible,
   * but behind the definitions of op[i] and behind earlyPos
   */
  OPT_Instruction scheduleHeapDefsEarly(OPT_HeapOperand[] op, OPT_Instruction earlyPos, OPT_Instruction me) {
    if (op == null) return  earlyPos;
    
    for (int i = 0; i < op.length; ++i) {
      OPT_Instruction def = definingInstruction(op[i]);

      //  if (me.isImplicitLoad() || me.isImplicitStore())
//      def = _getRealDef(def, me)
//        ;
//        else if (me.isPEI()) 
//      def = _getRealExceptionDef(def)
      //  ;
      
      if (VM.VerifyAssertions) VM._assert (def != null);
      earlyPos = maxDominatorDepth (scheduleEarly (def), earlyPos);      
    }
    return  earlyPos;
  }

  
  OPT_BasicBlock useBlock(OPT_Instruction use, OPT_Operand op) {
    //VM.sysWrite ("UseBlock: "+use+"\n");
    OPT_BasicBlock res = scheduleLate (use);
    if (res != null && Phi.conforms (use)) {
      int i;
      for (i = Phi.getNumberOfValues (use) - 1;  i >= 0;  --i) {
        if (Phi.getValue (use, i) == op) {
          res = Phi.getPred (use, i).block;
          break;
        }
      }
      if (VM.VerifyAssertions) VM._assert (i >= 0);
    }
    return res;
  }

  /**
   * Schedule me as late as possible,
   * but in front of my uses and before latePos
   */
  private OPT_BasicBlock scheduleScalarUsesLate(OPT_Instruction inst, OPT_BasicBlock lateBlock) {
    OPT_Operand resOp = getResult (inst);

    if (resOp == null || !(resOp instanceof OPT_RegisterOperand))
      return lateBlock;

    OPT_Register res = ((OPT_RegisterOperand) resOp).register;
    OPT_RegisterOperandEnumeration e = OPT_DefUse.uses (res);
    
    while (e.hasMoreElements()) {
      OPT_Operand op = e.next();
      OPT_Instruction use = op.instruction;
      OPT_BasicBlock block = useBlock (use, op);
      lateBlock = commonDominator (block, lateBlock);
    }
    return  lateBlock;
  }
  
  /**
   * Schedule me as early as possible,
   * but behind the definitions of op[i] and behind earlyPos
   */
  OPT_BasicBlock scheduleHeapUsesLate(OPT_Instruction inst, OPT_BasicBlock lateBlock) {
    //VM.sysWrite (" scheduleHeapUsesLate\n");
    OPT_Operand[] defs = ssad.getHeapDefs(inst);
    if (defs == null) return  lateBlock;
    
    //VM.sysWrite (" defs: "+defs.length+"\n");
    for (int i = 0; i < defs.length; ++i) {
      OPT_HeapOperand dhop = (OPT_HeapOperand) defs[i];
      OPT_HeapVariable H = dhop.value;
      if (DEBUG) VM.sysWrite ("H: "+H+"\n");
      Iterator it = ssad.iterateHeapUses (H);
      //VM.sysWrite (" H: "+H+" ("+ssad.getNumberOfUses (H)+")\n");
      while (it.hasNext()) {
        OPT_HeapOperand uhop = (OPT_HeapOperand) it.next();
        //VM.sysWrite (" uhop: "+uhop+"\n");
        OPT_Instruction use = (OPT_Instruction) uhop.instruction;
        //VM.sysWrite ("use: "+use+"\n");
        OPT_BasicBlock block = useBlock (use, uhop);
        lateBlock = commonDominator (block, lateBlock);
      }
    }
    return  lateBlock;
  }
  

  /**
   * Return the instruction that defines the operand.
   * @param op
   */
  OPT_Instruction definingInstruction(OPT_Operand op) {
    if (op instanceof OPT_HeapOperand) {
      OPT_HeapOperand hop = (OPT_HeapOperand)op;
      OPT_HeapVariable H = hop.value;
      OPT_HeapOperand defiOp = ssad.getUniqueDef(H);
      // Variable may be defined by caller, so depends on method entry
      if (defiOp == null || defiOp.instruction == null) {
        return  ir.firstInstructionInCodeOrder();
      } 
      else {
        //VM.sysWrite ("def of "+op+" is "+defiOp.instruction+"\n");
        return  defiOp.instruction;
      }
    } 
    else if (op instanceof OPT_RegisterOperand) {
      OPT_Register reg = ((OPT_RegisterOperand)op).register;
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs(reg);
      if (!defs.hasMoreElements()) {          // params have no def
        return  ir.firstInstructionInCodeOrder();
      } 
      else {
        OPT_Instruction def = defs.next().instruction;
        // we are in SSA, so there is at most one definition.
        if (VM.VerifyAssertions) VM._assert (!defs.hasMoreElements());
        //if (defs.hasMoreElements()) {
        //  VM.sysWrite("GCP: multiple defs: " + reg + "\n");
        //  return  null;
        //}
        return  def;
      }
    } 
    else {                    // some constant
      return  ir.firstInstructionInCodeOrder();
    }
  }
  
  /**
   * Get the result operand of the instruction
   * @param inst
   */
  OPT_Operand getResult(OPT_Instruction inst) {
    if (ResultCarrier.conforms(inst))
      return  ResultCarrier.getResult(inst);
    if (GuardResultCarrier.conforms(inst))
      return  GuardResultCarrier.getGuardResult(inst);
    if (Phi.conforms (inst))
      return Phi.getResult (inst);
    return  null;
  }
  
  /**
   * Visit the blocks between the late and the early position along
   * their path in the dominator tree.
   * Return the block with the smallest execution costs.
   */
  OPT_BasicBlock upto(OPT_Instruction earlyPos, OPT_BasicBlock lateBlock,
                      OPT_Instruction inst) {
    
    OPT_BasicBlock origBlock = getOrigBlock(inst);
    OPT_BasicBlock actBlock = lateBlock;
    OPT_BasicBlock bestBlock = lateBlock;
    OPT_BasicBlock earlyBlock = getBlock(earlyPos);

    if (VM.VerifyAssertions) {
      if (!dominator.dominates (earlyBlock.getNumber(), 
                                origBlock.getNumber())
          || !dominator.dominates (earlyBlock.getNumber(), 
                                   lateBlock.getNumber())) {
        OPT_SSA.printInstructions (ir);
        VM.sysWrite ("> "+earlyBlock.getNumber()+", "+origBlock.getNumber()
                     + ", "+lateBlock.getNumber()+"\n");
        VM.sysWrite (""+inst+"\n");
      }
      VM._assert (dominator.dominates (earlyBlock.getNumber(), 
                                      origBlock.getNumber()));
      VM._assert (dominator.dominates (earlyBlock.getNumber(), 
                                      lateBlock.getNumber()));
    }
    for (;;) {
      /* is the actual block better (less frequent)
         than the so far best block? */
      if (frequency (actBlock) < frequency (bestBlock)) {
        if (DEBUG) VM.sysWrite ("going from "+frequency (origBlock)+" to "
                                +frequency (actBlock)+"\n");
        bestBlock = actBlock;
      }
      /* all candidates checked? */
      if (actBlock == earlyBlock)
        break;
      
      /* walk up the dominator tree for next candidate*/
      actBlock = dominator.getParent(actBlock);
    }
    if (bestBlock == origBlock) return  null;
    if (DEBUG) VM.sysWrite ("best Block: "+bestBlock+"\n");
    return bestBlock;
  }
  
  /**
   * How expensive is it to place an instruction in this block?
   */
  final float frequency (OPT_BasicBlock b) {
    return b.getExecutionFrequency();
  }
  
  /**
   * move `inst' behind `pred'
   */
  void move(OPT_Instruction inst, OPT_BasicBlock to) {

    OPT_BasicBlock origBlock = getOrigBlock (inst);
    OPT_Instruction cand = null;
    
    /* find a position within bestBlock */
    if (dominator.dominates (origBlock.getNumber(), to.getNumber())) {
      // moved down, so insert in from
      OPT_Instruction last = null;
      OPT_InstructionEnumeration e = to.forwardInstrEnumerator();
      while (e.hasMoreElements()) {
        cand = e.next();
        if (DEBUG) VM.sysWrite(cand.toString() + "\n");
        if ((  !Label.conforms (cand)) // skip labels, phis, and yieldpoints
            && !cand.isYieldPoint()
            && !Phi.conforms(cand)) break; 
        last = cand;
      }
      cand = last;
    } else {
      // moved up, so insert at end of block
      OPT_InstructionEnumeration e = to.reverseInstrEnumerator();
      while (e.hasMoreElements()) {
        cand = e.next();
        if (DEBUG) VM.sysWrite(cand.toString() + "\n");
        if ((  !BBend.conforms(cand))
            && !cand.isBranch() // skip branches and newly placed insts
            && !relocated.contains (cand)) break; 
      }
      if (DEBUG) VM.sysWrite ("Adding to relocated: "+inst+"\n");
      relocated.add (inst);
    }
    
    if (DEBUG && moved.add(inst.operator))
      VM.sysWrite("m(" + (ir.IRStage == ir.LIR ? "l" : "h") + ") " + 
                  inst.operator + "\n");
    if (verbose) {
      VM.sysWrite(ir.IRStage == ir.LIR ? "%" : "#");
      VM.sysWrite(" moving " + inst + " from " + origBlock + 
                  " to " + to + "\n" + "behind  " + cand + "\n");
      
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
  boolean postDominates(OPT_BasicBlock a, OPT_BasicBlock b) {
    boolean res;
    if (a == b)
      return  true;
    //VM.sysWrite ("does " + a + " postdominate " + b + "?: ");
    OPT_DominatorInfo info = (OPT_DominatorInfo)b.scratchObject;
    res = info.isDominatedBy(a);
    //VM.sysWrite (res ? "yes\n" : "no\n");
    return  res;
  }
  
  /**
   * Get the basic block of an instruction
   * @param inst
   */
  OPT_BasicBlock getBlock(OPT_Instruction inst) {
    return  block[inst.scratch];
  }
  
  /**
   * Set the basic block for an instruction
   * @param inst
   * @param b
   */
  void setBlock(OPT_Instruction inst, OPT_BasicBlock b) {
    block[inst.scratch] = b;
  }
  
  /**
   * Get the early position of an instruction
   * @param inst
   */
  OPT_Instruction getEarlyPos(OPT_Instruction inst) {
    return  earlyPos[inst.scratch];
  }
  
  /**
   * Set the early position for an instruction
   * @param inst
   * @param pos
   */
  void setEarlyPos(OPT_Instruction inst, OPT_Instruction pos) {
    earlyPos[inst.scratch] = pos;
  }
  
  /**
   * Get the block, where the instruction was originally located
   * @param inst
   */
  OPT_BasicBlock getOrigBlock(OPT_Instruction inst) {
    return  origBlock[inst.scratch];
  }
  
  /**
   * Set the block, where the instruction is originally located.
   * @param inst
   * @param b
   */
  void setOrigBlock(OPT_Instruction inst, OPT_BasicBlock b) {
    origBlock[inst.scratch] = b;
  }
  
  /**
   * In what state (initial, early, late, done) is this instruction
   * @param inst
   */
  int getState(OPT_Instruction inst) {
    return  state[inst.scratch];
  }
  
  /**
   * Set the state (initial, early, late, done) of the instruction
   * @param inst
   * @param s
   */
  void setState(OPT_Instruction inst, int s) {
    state[inst.scratch] = s;
  }

  //------------------------------------------------------------
  // initialization
  //------------------------------------------------------------
  
  /**
   * initialize the state of the algorithm
   */
  void initialize(OPT_IR ir) {
    this.ir = ir;

    relocated = new HashSet();
    if (ir.IRStage == OPT_IR.HIR) {
      OPT_SimpleEscape analyzer = new OPT_SimpleEscape();
      escapeSummary = analyzer.simpleEscapeAnalysis(ir);
    }
    // Note: the following unfactors the CFG
    new OPT_DominatorsPhase(true).perform(ir);
    OPT_Dominators.computeApproxPostdominators(ir);
    dominator = ir.HIRInfo.dominatorTree;
    if (DEBUG) VM.sysWrite (""+dominator.toString()+"\n");
    int instructions = ir.numberInstructions();
    ssad = ir.HIRInfo.SSADictionary;    
    OPT_DefUse.computeDU(ir);
    ssad.recomputeArrayDU();
    // also number implicit heap phis
    OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      Enumeration pe = ssad.getHeapPhiInstructions(b);
      while (pe.hasMoreElements()) {
        OPT_Instruction inst = (OPT_Instruction)pe.nextElement();
        inst.scratch = instructions++;
        inst.scratchObject = null;
      }
    }
    state = new int[instructions];
    origBlock = new OPT_BasicBlock[instructions];
    block = new OPT_BasicBlock[instructions];
    earlyPos = new OPT_Instruction[instructions];
    e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      Enumeration ie = ssad.getAllInstructions(b);
      while (ie.hasMoreElements()) {
        OPT_Instruction inst = (OPT_Instruction)ie.nextElement();
        setBlock(inst, b);
        setOrigBlock(inst, b);
        setState(inst, initial);
      }
    }
    if (ir.IRStage == OPT_IR.HIR) {
      e = ir.getBasicBlocks();
      while (e.hasMoreElements()) {
        OPT_BasicBlock b = e.next();
        
        if (ir.options.FREQ_FOCUS_EFFORT && b.getInfrequent()) continue;

        Enumeration ie = ssad.getAllInstructions(b);
        while (ie.hasMoreElements()) {
          OPT_Instruction inst = (OPT_Instruction)ie.nextElement();
          while (simplify (inst, b));
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

  private HashSet relocated;
  
  private int state[];
  private OPT_BasicBlock block[];
  private OPT_BasicBlock origBlock[];
  private OPT_Instruction earlyPos[];
  private OPT_SSA ssa;
  private OPT_SSADictionary ssad;
  private OPT_DominatorTree dominator;
  private OPT_IR ir;
  private OPT_FI_EscapeSummary escapeSummary;
  private static java.util.HashSet moved = new java.util.HashSet();
  

  private boolean simplify(OPT_Instruction inst, OPT_BasicBlock block)
  {
    if (!Phi.conforms (inst)) return false;  // no phi

    //if (Phi.getNumberOfValues (inst) != 2) return false; // want exactly 2 inputs
    
    //VM.sysWrite ("Simplify "+inst+"\n");
    
    OPT_Operand resOp = Phi.getResult (inst);
    
    if (! (resOp instanceof OPT_HeapOperand)) {
      //VM.sysWrite (" no heap op result\n");      
      return false; // scalar phi
    }
    
    int xidx = -1;
    OPT_Instruction x = null;
    
    for (int i = Phi.getNumberOfValues (inst) - 1;  i >= 0;  --i) {
      OPT_Instruction in = definingInstruction (Phi.getValue (inst, i));
      if (getOrigBlock (in) != getOrigBlock (inst) 
          && dominator.dominates (getOrigBlock (in), getOrigBlock (inst))) {
        if (xidx != -1) return false;
        xidx = i;
        x = in;
      } else
        if (!dominator.dominates (getOrigBlock (inst), getOrigBlock (in)))
          return false;
    }
    if (x == null) return false;

    replaceUses (inst, (OPT_HeapOperand) Phi.getValue (inst, xidx),
                                         Phi.getPred  (inst, xidx), true);

    OPT_HeapOperand hop = (OPT_HeapOperand) resOp;
    if (hop.value.type == ssad.exceptionState) return false;

    /* check that inside the loop, the heap variable is only used/defed
       by simple, non-volatile loads or only by stores

       if so, replace uses of inst (a memory phi) with its dominating input
    */
    int type = checkLoop (inst, hop, xidx, block);
    if (type == CL_LOADS_ONLY || type == CL_STORES_ONLY || type == CL_NONE) {
      replaceUses (inst, (OPT_HeapOperand) Phi.getValue (inst, xidx),
                                           Phi.getPred  (inst, xidx), false);
    }
    return false;
  }

  static final int CL_NONE=0;
  static final int CL_LOADS_ONLY=1;
  static final int CL_STORES_ONLY=2;
  static final int CL_LOADS_AND_STORES=3;
  static final int CL_COMPLEX=4;
  
  /**
   * check that inside the loop, the heap variable is only used/defed
   * by simple, non-volatile loads/stores
   *
   * returns one of:
   * CL_LOADS_ONLY, CL_STORES_ONLY, CL_LOADS_AND_STORES, CL_COMPLEX
   */
  private int _checkLoop (OPT_Instruction inst, OPT_HeapOperand hop, int xidx)
  {
    for (int i = Phi.getNumberOfValues (inst) - 1;  i >= 0;  --i) {
      if (i == xidx) continue;
      OPT_Instruction y = definingInstruction (Phi.getValue (inst, i));
      while (y != inst) {
        //VM.sysWrite (" y: "+y+"\n");
        if (y.isImplicitStore() || y.isPEI() || !LocationCarrier.conforms (y))
          return CL_COMPLEX;
        
        // check for access to volatile field
        OPT_LocationOperand loc = LocationCarrier.getLocation (y);
        if (loc == null || loc.mayBeVolatile()) {
          //VM.sysWrite (" no loc or volatile field\n");          
          return CL_COMPLEX;
        }
        OPT_HeapOperand[] ops = ssad.getHeapUses (y);
        for (int j = 0;  j < ops.length;  ++j) {
          if (ops[j].value.type == ssad.exceptionState) continue;
          if (ops[j].value.type != hop.value.type) return CL_COMPLEX;
          y = definingInstruction (ops[j]);
        }
      }
    }
    return CL_LOADS_ONLY;
  }


  /**
   * check that inside the loop, the heap variable is only used/defed
   * by simple, non-volatile loads/stores
   *
   * returns one of:
   * CL_LOADS_ONLY, CL_STORES_ONLY, CL_LOADS_AND_STORES, CL_COMPLEX
   */
  private int checkLoop (OPT_Instruction inst, OPT_HeapOperand hop, int xidx,
                         OPT_BasicBlock block)
  {
    HashSet seen = new HashSet();
    OPT_Queue workList = new OPT_Queue();
    int state = CL_NONE;
    int instUses = 0;
    
    seen.add (inst);
    for (int i = Phi.getNumberOfValues (inst) - 1;  i >= 0;  --i) {
      if (i == xidx) continue;
      OPT_Instruction y = definingInstruction (Phi.getValue (inst, i));
      if (y == inst) instUses++;
      if (!(seen.contains (y))) {
        seen.add (y);
        workList.insert (y);
      }
    }
    
    while (!(workList.isEmpty())) {
      OPT_Instruction y = (OPT_Instruction) workList.remove();
      if (Phi.conforms (y)) {
        for (int i = Phi.getNumberOfValues (y) - 1;  i >= 0;  --i) {
          OPT_Instruction z = definingInstruction (Phi.getValue (y, i));
          if (z == inst) instUses++;
          if (!(seen.contains (z))) {
            seen.add (z);
            workList.insert (z);
          }
        }
      } else if ((  y.isPEI())
                 || !LocationCarrier.conforms (y)
                 || y.operator.isAcquire()
                 || y.operator.isRelease()) {
        return CL_COMPLEX;
      } else {
        // check for access to volatile field
        OPT_LocationOperand loc = LocationCarrier.getLocation (y);
        if (loc == null || loc.mayBeVolatile()) {
          //VM.sysWrite (" no loc or volatile field\n");          
          return CL_COMPLEX;
        }
        if (y.isImplicitStore()) {
          // only accept loop-invariant stores
          // conservatively estimate loop-invariance by header domination
          if (!inVariantLocation (y, block)) return CL_COMPLEX;
          state |= CL_STORES_ONLY;
        } else {
          state |= CL_LOADS_ONLY;
        }
        OPT_HeapOperand[] ops = ssad.getHeapUses (y);
        for (int j = 0;  j < ops.length;  ++j) {
          if (ops[j].value.type == ssad.exceptionState) continue;
          if (ops[j].value.type != hop.value.type) return CL_COMPLEX;
          y = definingInstruction (ops[j]);
          if (y == inst) instUses++;
          if (!(seen.contains (y))) {
            seen.add (y);
            workList.insert (y);
          }
        }
      }
    }
    if (state == CL_STORES_ONLY
        && ssad.getNumberOfUses (hop.value) != instUses)
      return CL_COMPLEX;
    
    return state;
  }


  private boolean inVariantLocation (OPT_Instruction inst,
                                     OPT_BasicBlock block)
  {
    if (PutStatic.conforms (inst)) return true;
    if (PutField.conforms (inst))
      return useDominates (PutField.getRef (inst), block);
    if (AStore.conforms (inst))
      return ((  useDominates (AStore.getArray (inst), block))
              && useDominates (AStore.getIndex (inst), block));
    if (VM.VerifyAssertions) {
      VM._assert(false, "inst: " + inst);
    }
    return false;
  }

  private boolean useDominates (OPT_Operand op, OPT_BasicBlock block)
  {
    if (! (op instanceof OPT_RegisterOperand)) return true;
    OPT_Instruction inst = definingInstruction (op);
    OPT_BasicBlock b = getOrigBlock (inst);
    return b != block && dominator.dominates (b, block);
  }

  /**
   * In the consumers of `inst', replace uses of `inst's result
   * with uses of `replacement' 
   */
  private boolean replaceUses (OPT_Instruction inst,
                               OPT_HeapOperand replacement,
                               OPT_BasicBlockOperand replacementBlock, 
                               boolean onlyPEIs)
  {
    if (VM.VerifyAssertions) VM._assert (Phi.conforms (inst));
    
    boolean changed = false;
    OPT_HeapOperand hop = (OPT_HeapOperand) Phi.getResult (inst);
    OPT_HeapVariable H = hop.value;
    Iterator it = ssad.iterateHeapUses (H);
    while (it.hasNext()) {
      hop = (OPT_HeapOperand) it.next();
      OPT_Instruction user = hop.instruction;
      if (onlyPEIs && !user.isPEI()) continue;
      
      if (Phi.conforms (user)) {
        for (int i = 0; i < Phi.getNumberOfValues (user); i++) {
          if (Phi.getValue (user, i) == hop) {
            Phi.setValue (user, i, replacement.copy());
            Phi.setPred  (user, i,
                          (OPT_BasicBlockOperand) replacementBlock.copy());
          }
        }
        changed |= replacement.value != H;
      } else {
        OPT_HeapOperand[] uses = ssad.getHeapUses (user);
        for (int i = uses.length - 1;  i >= 0;  --i) {
          if (uses[i].value == H) {
            changed |= replacement.value != H;
            uses[i] = (OPT_HeapOperand) (replacement.copy());
            uses[i].setInstruction (user);
          }
        }
      }
      if (DEBUG && changed) {
        VM.sysWrite (" changing dependency of "+user+"\n"
                     +"from "+ H +" to "+ replacement + "\n");
      }
    }
    if (!onlyPEIs) {
      for (int i = Phi.getNumberOfValues(inst) - 1;  i >= 0;  --i) {
        Phi.setValue (inst, i, replacement.copy());
      }
    }
    return changed;
  }

  private void resetLandingPads()
  {
    OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) e.next().clearLandingPad();
  }
  
  private static boolean DEBUG = false;
  static boolean verbose = false;
}
