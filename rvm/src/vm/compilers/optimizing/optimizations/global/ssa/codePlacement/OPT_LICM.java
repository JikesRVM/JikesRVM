/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*;
import  instructionFormats.*;

/**
 * This class does the job. It is a subphase of OPT_GCP.
 * 
 * @author Martin Trapp
 * @modified Stephen Fink */
class OPT_LICM extends OPT_CompilerPhase implements OPT_Operators {

  /**
   * Execute loop invariant code motion on the given IR.
   * @param ir
   */
  void perform (OPT_IR ir) {
    //VM.sysWrite ("in LICM for "+ir.method+"\n");
    if (ir.hasReachableExceptionHandlers() || OPT_GCP.tooBig(ir)) return;
    
    verbose = ir.options.VERBOSE_GCP;
    
    if (verbose && ir.options.hasMETHOD_TO_PRINT()) {
      verbose = ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString());
      if (!verbose) return;
    }
    
    initialize(ir);

    //if (verbose) OPT_SSA.printInstructions (ir);
    OPT_Instruction inst = ir.firstInstructionInCodeOrder();
    while (inst != null) {
      OPT_Instruction next = inst.nextInstructionInCodeOrder();
      scheduleEarly(inst);
      inst = next;
    }
    inst = ir.firstInstructionInCodeOrder();
    while (inst != null) {
      if (getState(inst) < late)
	scheduleLate(inst);
      inst = inst.nextInstructionInCodeOrder();
    }
  }

  /**
   * Returns the name of the phase
   * @return 
   */
  String getName () {
    return  "LICM";
  }
  
  /**
   * Should this phase be executed?
   * @param options
   * @return 
   */
  boolean shouldPerform (OPT_Options options) {
    return  options.GCP || options.VERBOSE_GCP;
  }
  
  /**
   * Report that we feel really sick for the given reason.
   * @param reason
   */
  static void BARF (String reason) {
    throw  new OPT_OptimizingCompilerException(reason);
  }
  
  //------------------------- Implementation -------------------------
  
  /**
   * Is it save to move the given instruction, depending on we are
   * in heapSSA form or not?
   * @param inst
   * @param heapSSA
   */
  public static boolean shouldMove (OPT_Instruction inst, OPT_IR ir) {
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
    case MATERIALIZE_CONSTANT_opcode:
      return true; // Uses JTOC (a physical register) but doesn't matter 
    case INT_MOVE_opcode:
    case LONG_MOVE_opcode:
      //  OPT_Operand ival = Move.getVal(inst);
      //if (ival instanceof OPT_ConstantOperand)
      //return  false;
      // fall through
    case PUTSTATIC_opcode:
    case PUTSTATIC_UNRESOLVED_opcode:
    case PUTFIELD_opcode:
    case PUTFIELD_UNRESOLVED_opcode:
    case GETSTATIC_UNRESOLVED_opcode:
    case GETFIELD_UNRESOLVED_opcode:
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
    case CHECKCAST_INTERFACE_NOTNULL_opcode:
    case CHECKTYPE_opcode:
    case INSTANCEOF_opcode:
    case INSTANCEOF_NOTNULL_opcode:
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
    case GET_IMPLEMENTS_TRITS_FROM_TIB_opcode:
    case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
      return !(OPT_GCP.usesOrDefsPhysicalRegister(inst));
    }
    return false;
  }

  /**
   * Schedule this instruction as early as possible
   * @param inst
   */
  void scheduleEarly (OPT_Instruction inst) {
    
    if (getState(inst) >= early) return;
    setState(inst, early);

    // already on outer level?
    if (ir.HIRInfo.LoopStructureTree.getLoopNestDepth(getBlock(inst)) == 0)
      return;
    
    // explicitely INCLUDE instructions
    if (!shouldMove(inst, ir)) return;
    
    OPT_Instruction earlyPos = ir.firstInstructionInCodeOrder();
    // dependencies via scalar operands
    earlyPos = scheduleDefEarly(inst.getUses(), earlyPos);
    // memory dependencies
    if (inst.isImplicitLoad() || inst.isImplicitStore() || inst.isPEI())
      earlyPos = scheduleDefEarly(ssad.getHeapUses(inst), earlyPos, inst);

    // move inst to its new location
    move(inst, upto(earlyPos, inst));
  }
  
  /**
   * Find the earliest position that still keeps dep after all its uses.
   * @param dep
   * @param earlyPos
   * @return 
   */
  private OPT_Instruction scheduleAfter (OPT_Instruction dep, 
					 OPT_Instruction earlyPos) {
    scheduleEarly(dep);
    int domDistance = ((dominator.depth(getBlock(dep))) - 
		       dominator.depth(getBlock(earlyPos)));
    if (domDistance > 0 || domDistance == 0
	&& seqNo(earlyPos) < seqNo(getPosition(dep))) {
      earlyPos = dep;
    }
    return  earlyPos;
  }
  
  /**
   * Schedule me as early as possible,
   * but behind the definitions in e and behind earlyPos
   */
  private OPT_Instruction scheduleDefEarly (OPT_OperandEnumeration e, 
					    OPT_Instruction earlyPos) {
    while (e.hasMoreElements()) {
      OPT_Operand op = e.next();
      OPT_Instruction def = definingInstruction(op);
      if (def != null) {
	earlyPos = scheduleAfter(def, earlyPos);
      }
    }
    return  earlyPos;
  }
  
  /**
   * Schedule me as early as possible,
   * but behind the definitions of op[i] and behind earlyPos
   */
  OPT_Instruction scheduleDefEarly (OPT_HeapOperand[] op, 
				    OPT_Instruction earlyPos, 
				    OPT_Instruction me) {
    if (op == null || earlyPos == null)
      return  earlyPos;
    for (int i = 0; i < op.length; ++i) {
      OPT_Instruction def = definingInstruction(op[i]);
      if (me.isImplicitLoad() || me.isImplicitStore())
	def = _getRealDef(def, me)
	  ;
      else if (me.isPEI()) 
	def = _getRealExceptionDef(def)
	  ;
      if (def != null) {
	earlyPos = scheduleAfter(def, earlyPos);
      }
    }
    return  earlyPos;
  }
  
  /**
   * Return the instruction that defines the operand.
   * @param op
   * @return 
   */
  OPT_Instruction definingInstruction (OPT_Operand op) {
    if (op instanceof OPT_HeapOperand) {
      OPT_HeapOperand hop = (OPT_HeapOperand)op;
      OPT_HeapVariable H = hop.value;
      OPT_HeapOperand defiOp = ssad.getUniqueDef(H);
      // Variable may be defined by caller, so depends on method entry
      if (defiOp == null || defiOp.instruction == null) {
	return  ir.firstInstructionInCodeOrder();
      } 
      else {
	return  defiOp.instruction;
      }
    } 
    else if (op instanceof OPT_RegisterOperand) {
      OPT_Register reg = ((OPT_RegisterOperand)op).register;
      OPT_RegisterOperandEnumeration defs = OPT_RegisterInfo.defs(reg);
      if (!defs.hasMoreElements()) {          // params have no def
	return  ir.firstInstructionInCodeOrder();
      } 
      else {
	OPT_Instruction def = defs.next().instruction;
	// we are in SSA, so there is at most one definition.
	if (VM.VerifyAssertions) VM.assert (!defs.hasMoreElements());
	//if (defs.hasMoreElements()) {
	//  VM.sysWrite("GCP: multiple defs: " + reg + "\n");
	//  return  null;
	//}
	return  def;
      }
    } 
    else {                    // some constant
      return  null;
    }
  }
  
  /**
   * Schedule as late as possible. Not yet implemented.
   * @param inst
   */
  void scheduleLate (OPT_Instruction inst) {}
  
  /**
   * Visit the blocks between the original and the early position along
   * their common path in the dominator tree.
   * Return the deepest block with the smallest execution costs.
   */
  OPT_Instruction upto (OPT_Instruction earlyPos, OPT_Instruction inst) {
    if (earlyPos == null)
      return  earlyPos;
    OPT_BasicBlock origBlock = getOrigBlock(inst), actBlock = origBlock, 
      bestBlock = actBlock, earlyBlock = getBlock(earlyPos);
    // should not happen. does it still?
    if (!(dominator.dominates(earlyBlock.getNumber(), 
			      origBlock.getNumber()))) {
      if (VM.VerifyAssertions)
	VM.assert(inst.operator.opcode == GUARD_COMBINE_opcode);
      return  null;
    }
    for (;;) {
      if ((true || inst.isPEI() || inst.isImplicitStore()) && 
	  !postDominates(origBlock, actBlock))
	break;
      if (frequency(actBlock) < frequency(bestBlock))
	bestBlock = actBlock;
      if (actBlock == earlyBlock)
	break;
      actBlock = dominator.getParent(actBlock);
    }
    if (bestBlock == getOrigBlock(inst))
      return  null;
    OPT_InstructionEnumeration e = bestBlock.reverseInstrEnumerator();
    while (e.hasMoreElements()) {
      inst = e.next();
      if (DEBUG)
	VM.sysWrite(inst.toString() + "\n");
      if (!BBend.conforms(inst) && !inst.isBranch())
	break;
    }
    return  inst;
  }
  
  /**
   * How expensive is it to place an instruction in this block?
   */
  final int frequency (OPT_BasicBlock b) {
    //-#if BLOCK_COUNTER_WORKS
    OPT_Instruction inst = b.firstInstruction();
    return  basicBlockCounter.getCount(inst.bcIndex, inst.position);
    //-#else
    return  ir.HIRInfo.LoopStructureTree.getLoopNestDepth(b);
    //-#endif
  }
  
  /**
   * move `inst' behind `pred'
   */
  void move (OPT_Instruction inst, OPT_Instruction pred) {
    if (pred == null)
      return;
    if (!(dominator.dominates(getBlock(inst).getNumber(), 
			      getOrigBlock(inst).getNumber())))
      return;
    if (VM.VerifyAssertions)
      VM.assert(dominator.dominates(getBlock(inst).getNumber(), 
				    getOrigBlock(inst).getNumber()));
    if (DEBUG && moved.add(inst.operator))
      VM.sysWrite("m(" + (ir.IRStage == ir.LIR ? "l" : "h") + ") " + 
		  inst.operator + "\n");
    setPosition(inst, pred);
    if (verbose) {
      VM.sysWrite(ir.IRStage == ir.LIR ? "%" : "#");
      VM.sysWrite(" moving " + inst + " from " + getOrigBlock(inst) + 
		  " to " + getBlock(inst) + "\n" + "behind  " + pred + "\n");
    }
    inst.remove();
    pred.insertAfter(inst);
  }
  
  //------------------------------------------------------------
  // some helper methods
  //------------------------------------------------------------
  
  /**
   * does a post dominate b?
   * @param a
   * @param b
   */
  boolean postDominates (OPT_BasicBlock a, OPT_BasicBlock b) {
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
   * @return 
   */
  OPT_BasicBlock getBlock (OPT_Instruction inst) {
    return  block[inst.scratch];
  }
  
  /**
   * Set the basic block for an instruction
   * @param inst
   * @param b
   */
  void setBlock (OPT_Instruction inst, OPT_BasicBlock b) {
    block[inst.scratch] = b;
  }
  
  /**
   * Get the block, where the instruction was originally located
   * @param inst
   * @return 
   */
  OPT_BasicBlock getOrigBlock (OPT_Instruction inst) {
    return  origBlock[inst.scratch];
  }
  
  /**
   * Set the block, where the instruction is originally located.
   * @param inst
   * @param b
   */
  void setOrigBlock (OPT_Instruction inst, OPT_BasicBlock b) {
    origBlock[inst.scratch] = b;
  }
  
  /**
   * Behind which instruction should this one be placed?
   * @param inst
   * @return 
   */
  OPT_Instruction getPosition (OPT_Instruction inst) {
    if (position[inst.scratch] == null)
      return  inst;
    return  position[inst.scratch];
  }
  
  /**
   * Inst should be placed behind i
   * @param inst
   * @param i
   */
  void setPosition (OPT_Instruction inst, OPT_Instruction i) {
    position[inst.scratch] = i;
    block[inst.scratch] = block[i.scratch];
  }
  
  /**
   * In what state (initial, early, late, delete) is this instruction
   * @param inst
   * @return 
   */
  int getState (OPT_Instruction inst) {
    return  state[inst.scratch];
  }
  
  /**
   * Set the state (initial, early, late, delete) of the instruction
   * @param inst
   * @param s
   */
  void setState (OPT_Instruction inst, int s) {
    state[inst.scratch] = s;
  }
  
  /**
   * Is inst on stack?1
   * @param inst
   * @return 
   */
  boolean onStack (OPT_Instruction inst) {
    return  stack[inst.scratch];
  }
  
  /**
   * Inst is on stack
   * @param inst
   */
  void markOnStack (OPT_Instruction inst) {
    stack[inst.scratch] = true;
  }
  
  /**
   * Inst is off stack
   * @param inst
   */
  void markOffStack (OPT_Instruction inst) {
    stack[inst.scratch] = false;
  }
  
  /**
   * what is the position of theis instruction in its basic block?
   * @param inst
   * @return 
   */
  int seqNo (OPT_Instruction inst) {
    return  inst.scratch;
  }
  
  //------------------------------------------------------------
  // initialization
  //------------------------------------------------------------
  
  /**
   * initialize the state of the algorithm
   */
  void initialize (OPT_IR ir) {
    this.ir = ir;

    if (ir.IRStage == OPT_IR.HIR) {
      OPT_SimpleEscape analyzer = new OPT_SimpleEscape();
      escapeSummary = analyzer.simpleEscapeAnalysis(ir);
    }
    ssad = ir.HIRInfo.SSADictionary;    
    OPT_RegisterInfo.computeRegisterList(ir);
    ssad.recomputeArrayDU();
    new OPT_DominatorsPhase().perform(ir);
    OPT_Dominators.computeApproxPostdominators(ir);
    dominator = ir.HIRInfo.dominatorTree;
    int instructions = ir.numberInstructions();
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
    position = new OPT_Instruction[instructions];
    stack = new boolean[instructions];
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
    //if (verbose) OPT_SSA.printInstructions (ir);
  }
  //------------------------------------------------------------
  // private state
  //------------------------------------------------------------
  private static final int initial = 0;
  private static final int early = 1;
  private static final int late = 2;
  private static final int delete = 3;
  private int state[];
  private OPT_BasicBlock block[];
  private OPT_BasicBlock origBlock[];
  private OPT_Instruction position[];
  private boolean stack[];
  private OPT_SSA ssa;
  private OPT_SSADictionary ssad;
  private OPT_DominatorTree dominator;
  //-#if BLOCK_COUNTER_WORKS
  private OPT_BasicBlockCounts basicBlockCounter;
  //-#endif
  private OPT_IR ir;
  private OPT_FI_EscapeSummary escapeSummary;
  private static java.util.HashSet moved = new java.util.HashSet();

  /**
   * returns the initial memory value valid before the loop is first entered,
   * iff inst is a loop header for a heap value, null otherwise.
   */
  OPT_Instruction loopHeapPhiInitialDef (OPT_Instruction inst) {
    // true if one of the inputs dominates us
    OPT_Instruction res = null;
    if (inst.operator.opcode != PHI_opcode) return null;
    Object result = Phi.getResult(inst);
    if (!(result instanceof OPT_HeapOperand)) return null;
    int n = Phi.getNumberOfValues(inst);
    for (int i = 0; i < n; ++i) {
      OPT_Operand opi = Phi.getValue(inst, i);
      OPT_Instruction insti = definingInstruction(opi);
      if (insti == null) return  null;
      if (getBlock(insti) != getBlock(inst)
	  && dominator.dominates(getBlock(insti).getNumber(), 
				 getBlock(inst).getNumber()))
	res = insti;
      else if (!dominator.dominates(getBlock(inst).getNumber(), 
				    getBlock(insti).getNumber()))
	return null;
    }
    return res;
  }
    
 
  OPT_Instruction _getRealExceptionDef (OPT_Instruction def) {
    OPT_Instruction res = getRealExceptionDef (def);
    while (res != def && Phi.conforms(res)) {
      OPT_Instruction next = getRealExceptionDef (res);
      if (next == res) break;
      res = next;
    }
    if (DEBUG && res != def)
      VM.sysWrite ("Real exception state definition of "+def+" = "+res+"\n");
    return res;
  }
  
  OPT_Instruction getRealExceptionDef (OPT_Instruction def) {
    OPT_Instruction initialDef = loopHeapPhiInitialDef (def);
    if (initialDef == null) return def;
    while (initialDef.operator.opcode == def.operator.opcode) {
      OPT_HeapOperand[] hops = ssad.getHeapUses(initialDef);
      OPT_Instruction next = null;
      for (int i = 0; i < hops.length; ++i) {
	if (hops[i].value.type == ssad.exceptionState)  
	  if (next == null) {
	    next = definingInstruction (hops[i]);
	    if (next == null) return initialDef;
	  } else {
	    return initialDef;
	  }
      }
      if (next == null) return initialDef;
      initialDef = next;
    }	
    return initialDef;
  }


  boolean threadLocal (OPT_Instruction consumer) {
    boolean res;
    OPT_Operand ref = null;

    switch (consumer.operator.opcode) {
    case GETFIELD_opcode:
    case GETFIELD_UNRESOLVED_opcode:
      ref = GetField.getRef(consumer);
      break;
    case PUTFIELD_opcode:
    case PUTFIELD_UNRESOLVED_opcode:
      ref = PutField.getRef(consumer);
      break;
    case INT_ALOAD_opcode:
    case LONG_ALOAD_opcode:
    case FLOAT_ALOAD_opcode:
    case DOUBLE_ALOAD_opcode:
    case REF_ALOAD_opcode:
    case BYTE_ALOAD_opcode:
    case UBYTE_ALOAD_opcode:
    case USHORT_ALOAD_opcode:
    case SHORT_ALOAD_opcode:
      ref = ALoad.getArray(consumer);
      break;
    case INT_ASTORE_opcode:
    case LONG_ASTORE_opcode:
    case FLOAT_ASTORE_opcode:
    case DOUBLE_ASTORE_opcode:
    case REF_ASTORE_opcode:
    case BYTE_ASTORE_opcode:
    case SHORT_ASTORE_opcode:
      ref = AStore.getArray(consumer);
      break;
    case GETSTATIC_opcode:
    case PUTSTATIC_opcode:
    case GETSTATIC_UNRESOLVED_opcode:
    case PUTSTATIC_UNRESOLVED_opcode:
      return false;
    }
    res = (ref != null && ref instanceof OPT_RegisterOperand &&
	   escapeSummary.isThreadLocal(ref.asRegister().register));
    //if (res) 
      //VM.sysWrite ("Thread local access by " + consumer + "\n");
    
    return res;
  }
  
  OPT_Instruction _getRealDef (OPT_Instruction def, OPT_Instruction consumer) {
    if (!threadLocal(consumer)) return def;
    OPT_Instruction res = getRealDef (def, consumer);
    while (res != def && Phi.conforms(res)) {
      OPT_Instruction next = getRealDef (res, consumer);
      if (next == res) break;
      res = next;
    }
    if (DEBUG && res != def)
      VM.sysWrite ("Real definition of "+def+" = "+res+"\n");
    return res;
  }
  
  OPT_Instruction getRealDef (OPT_Instruction def, OPT_Instruction consumer) {

    OPT_Instruction initialDef = loopHeapPhiInitialDef (def);
    if (initialDef == null) return def;
    
    int n = Phi.getNumberOfValues(def);
    for (int i = 0; i < n; ++i) {
      OPT_Operand opi = Phi.getValue(def, i);
      OPT_Instruction defi = definingInstruction(opi);
      if ((  defi != initialDef)
	  && defi != consumer) return def;
    }
    return initialDef;
  }
  
  private static boolean DEBUG = false;
  static boolean verbose = false;
}

