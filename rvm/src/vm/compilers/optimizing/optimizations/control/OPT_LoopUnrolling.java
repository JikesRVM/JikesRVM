/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;

/*
 * Loop unrolling
 *
 * @author Martin Trapp
 */
class OPT_LoopUnrolling extends OPT_CompilerPhase
  implements OPT_Operators {


  static final boolean DEBUG = false;
  
  /**
   * Returns the name of the phase.
   */
  String getName () {
    return  "CFGTransformations";
  }

  boolean shouldPerform (OPT_Options options) {
    if (options.getOptLevel() < 2) return false;
    unrollFactor = (1 << options.UNROLL_LOG);
    return options.UNROLL_LOG >= 1;
  }
  
  private OPT_IR ir = null;
  
  /**
   * This is the method that actually does the work of the phase.
   */
  void perform (OPT_IR ir) {

    this.ir = ir;
    
    if (ir.hasReachableExceptionHandlers()) return;
    OPT_DefUse.computeDU (ir);
    new OPT_Simple().perform(ir);
    new OPT_BranchOptimizations(-1).perform(ir, true);

    new OPT_CFGTransformations().perform(ir);
    // Note: the following unfactors the CFG
    new OPT_DominatorsPhase(true).perform(ir);
    OPT_DefUse.computeDU (ir);

    ir.setInstructionScratchWord(0);
    
    unrollLoops (ir);
  }
  
  /**
   * unroll the loops.
   * still to be done
   */
  private static void unrollLoops (OPT_IR ir) {
    OPT_LSTGraph lstg = ir.HIRInfo.LoopStructureTree;
    if (lstg != null)
      unrollLoopTree((OPT_LSTNode)lstg.firstNode(), ir);
  }

  /**
   * loop unrolling on a given loop structure sub tree
   * @param t
   * @param ir
   * @return
   */
  static int unrollLoopTree (OPT_LSTNode t, OPT_IR ir) {
    int res = 0;
    Enumeration e = t.outNodes();
    if (!e.hasMoreElements()) unrollLeaf (t, ir);
    else while (e.hasMoreElements()) {
      OPT_LSTNode n = (OPT_LSTNode)e.nextElement();
      int heightOfTree = unrollLoopTree(n, ir);
      res = Math.max(res, heightOfTree) + 1;
    }
    return  res;
  }


  static final int MaxInstructions = 100;
  static int unrollFactor = 1;

  static int theCnt=0;
  
  static void unrollLeaf (OPT_LSTNode t, OPT_IR ir) {
    int instructionsInLoop = 0;
    OPT_BasicBlock exitBlock = null, succBlock = null, predBlock = null;
    OPT_BitVector nloop = t.loop;
    OPT_BasicBlock header = t.header;
    OPT_Instruction tmp;
    
    if (nloop == null) return;

    // examine what we got...
    report ("Leaf loop in " + ir.method + ": "+nloop+"\n");

    if (ir.hasReachableExceptionHandlers()){
      report ("0 IR may have exception handlers\n"); return;}

    // determine loop structure by looking at its blocks
    OPT_BasicBlockEnumeration loopBlocks = ir.getBasicBlocks(nloop);
    int blocks = 0;
    while (loopBlocks.hasMoreElements()) {
      OPT_BasicBlock b = loopBlocks.next();
      blocks++;
      // check for size
      instructionsInLoop += b.getNumberOfRealInstructions();
      if (instructionsInLoop > MaxInstructions) {
	report ("1 is too big\n"); return;}

      // look at the in edges. We want the header to be the only
      // block with out of loop incoming edges.
      OPT_BasicBlockEnumeration e = b.getIn();
      if (b != header) {
	while (e.hasMoreElements()) {
	  OPT_BasicBlock o = e.next();
	  if (!nloop.get(o.getNumber())) {
	    report ("2 interior pointers.\n"); return;}
	}
      } else {
	// check the headers predecessors: there should be
	// one out of loop input and one backedge.
	// We can extend this for loops with several backedges,
	// if they all have the same conditions.
	int inEdges = 0;
	while (e.hasMoreElements()) {
	  inEdges++;
	  OPT_BasicBlock o = e.next();
	  if (!nloop.get(o.getNumber())) {
	    if (predBlock == null) predBlock = o;
	    else {report ("3 multi entry header.\n"); return;}
	  } else {
	    if (exitBlock == null) exitBlock = o;
	    else {report ("4 multiple back edges.\n"); return;}
	  }
	}
      }      
    }
    // exitBlock must exit
    while (exitBlock.getNumberOfOut() == 1 && exitBlock.getNumberOfIn() == 1)
      exitBlock = exitBlock.getIn().next();
  
    OPT_BasicBlockEnumeration e = exitBlock.getOut();
    boolean exits = false;
    while (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      if (!nloop.get(b.getNumber())) {exits = true; break;}
    }
    if (!exits) {report ("5 exitBlock doesn't exit\n"); return;}

    if (exitBlock == header && blocks > 1) {
      report("6 while loop? ("+blocks+")\n"); return;}

    // So far, so good. Examine the exit test.
    OPT_Instruction origBranch = exitBlock.firstBranchInstruction();
    if (origBranch != exitBlock.lastRealInstruction()) {
      OPT_Instruction aGoto = origBranch.nextInstructionInCodeOrder();
      if (aGoto.operator.opcode != GOTO_opcode) {
	report ("7 too complex exit\n"); return;}
      succBlock = Label.getBlock(Goto.getTarget(aGoto).target).block;
      if (VM.VerifyAssertions)
	VM.assert (aGoto == exitBlock.lastRealInstruction());
    } else {
      succBlock = exitBlock.getFallThroughBlock();
    }
    
    if (origBranch.operator.opcode != INT_IFCMP_opcode) {
      report("8 branch isn't int_ifcmp: "+origBranch.operator+".\n"); return;}

    // examine operands:
    OPT_Operand op1 = follow(IfCmp.getVal1(origBranch));
    OPT_Operand op2 = follow(IfCmp.getVal2(origBranch));
    OPT_ConditionOperand
      cond = (OPT_ConditionOperand)IfCmp.getCond(origBranch).copy();
    OPT_RegisterOperand ifcmpGuard = IfCmp.getGuardResult(origBranch);
    double backBranchProbability
      = IfCmp.getBranchProfile (origBranch).takenProbability;
    if (!loopInvariant (op2, nloop)) {
      if (loopInvariant (op1, nloop)) {
	OPT_Operand op = op1;
	op1 = op2;
	op2 = op;
	cond.flipOperands();
      } else {
	report ("8a op1 and op2 may not be loop invariant\n"); return;}
    }
    OPT_BasicBlock
      target = Label.getBlock(IfCmp.getTarget(origBranch).target).block;

    if (!(op1 instanceof OPT_RegisterOperand)) {
      report ("9 op1 of ifcmp isn't a register\n"); return;}

    OPT_RegisterOperand rop1 = (OPT_RegisterOperand) op1;


    OPT_Register reg = rop1.register;
    if (reg.isPhysical()) {report("10 loops over physical register\n"); return;}
    if (succBlock == header && !nloop.get(target.getNumber())) {
      succBlock = target;
      target = header;
      cond.flipCode();
    }
    if (target != header) {report ("11 ifcmp doesn't jump to header\n");return;}

    OPT_Instruction iterator = null;
    OPT_OperandEnumeration defs = new RealDefs (rop1);
    while (defs.hasMoreElements()) {
      OPT_Operand def = defs.next();
      OPT_Instruction inst = def.instruction;
      OPT_BasicBlock block = inst.getBasicBlock();
      //VM.sysWrite (""+block+": "+inst+"\n"); 
      if (nloop.get(block.getNumber())) {
	if (iterator == null) {
	  iterator = inst;
	} else {report("12 iterator not unique.\n"); return;}
      }
    }
    
    if (iterator == null) {report ("15 iterator not found.\n"); return;}

      
    if (iterator.operator.opcode != INT_ADD_opcode) {
      report ("16 iterator is no addition: "+iterator.operator+"\n"); return;}

    if (!rop1.similar(follow(Binary.getVal1(iterator)))) {
      //dumpIR (ir, "malformed");
      report ("17 malformed iterator.\n"+iterator+"\n"); return;}

    OPT_Operand strideOp = follow (Binary.getVal2(iterator));
    if (!(strideOp instanceof OPT_IntConstantOperand)) {
      report ("18 stride not constant\n"); return;}

    int stride = ((OPT_IntConstantOperand)strideOp).value;
    if (stride != 1
	&& stride != -1
	) {
      report ("18b stride != +/-1 ("+stride+")\n"); return;}

    if ((   stride ==  1
	    && ((  cond.value != OPT_ConditionOperand.LESS)
		&& cond.value != OPT_ConditionOperand.LESS_EQUAL
		&& cond.value != OPT_ConditionOperand.NOT_EQUAL
		))
	|| (stride == -1
	    && ((  cond.value != OPT_ConditionOperand.GREATER)
		&& cond.value != OPT_ConditionOperand.GREATER_EQUAL
		&& cond.value != OPT_ConditionOperand.NOT_EQUAL
		))) {
      report ("19 unexpected condition: "+cond+"\n"+iterator+"\n\n"); return;}

    OPT_RegisterOperand outerGuard;
    OPT_BasicBlock outer = predBlock;
    while (outer.getNumberOfOut() == 1 && outer.getNumberOfIn() >= 1) {
      if (outer.getNumberOfIn() == 0) break;
      outer = outer.getIn().next();
    }
    if (outer.getNumberOfIn() > 0 && outer.getNumberOfOut() < 2) {
      report ("23 no suitable outer guard found.\n"); return;}

    tmp = outer.firstBranchInstruction();
    if (tmp != null && GuardResultCarrier.conforms(tmp))
      outerGuard = GuardResultCarrier.getGuardResult(tmp);
    else {
      outerGuard = ir.regpool.makeTempValidation();
    }

    ////////////
    // transfom

    // transform this:
    //
    // Orig:
    //  B
    //  if i CC b goto Orig
    //  else goto exit
    //
    // exit:
    //
    // into this:
    //
//
//  stride == 1:           common:                      stride == -1:
//--------------------------------------------------------------------------
//                          guard0:
//                           limit = b;
//   if a > b goto Orig                                  if b > a goto Orig
//                           else guard1                             
//    
// 
//                          guard 1:                             
//   remainder = b - a;                                  remainder = a - b;
// if cond == '<='                                    if cond == '>='
//   remainder++;                                         remainder++;
//                           remainder = remainder & 3                     
//   limit = a + remainder                               limit = a - remainder
// if cond == '<='                                    if cond == '>='
//   limit--;                                            limit++;
//                           if remainder == 0 goto mllp 
//                           goto Orig 
//                                                                     
//                          Orig:                                
//                           LOOP;                              
//                           if i CC limit goto Orig 
//                           else guard2  
//                                                
//                          guard2: if i CC b goto mllp 
//                           else exit
//                      
//                           mllp: // landing pad
//                           goto ml
//                
//                          ml:                                  
//                           LOOP;LOOP;LOOP;LOOP;                             
//                           if i CC b goto ml 
//                           else exit
//                                                      
//                          exit:
//--------------------------------------------------------------------------
    theCnt++;
    //if (theCnt > 0) return;
    report ("...transforming.\n");
    if (ir.options.hasMETHOD_TO_PRINT()
	&& ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()))  {
      //{report ("canceled\n"); return; }
      // dumpIR(ir, "before unroll");
    }    
    
    OPT_CFGTransformations.killFallThroughs (ir, nloop);
    OPT_BasicBlock handles[] = makeSomeCopies(unrollFactor, ir, nloop, blocks,
					      header, exitBlock, exitBlock);
    OPT_BasicBlock mainHeader = handles[0];
    OPT_BasicBlock mainExit = handles[1];

    // test block for well formed bounds
    OPT_BasicBlock guardBlock0
      = header.createSubBlock(header.firstInstruction().bcIndex, ir);
    predBlock.redirectOuts (header, guardBlock0, ir);
      
    // test block for iteration alignemnt
    OPT_BasicBlock guardBlock1
      = header.createSubBlock(header.firstInstruction().bcIndex, ir);

    OPT_BasicBlock predSucc = predBlock.nextBasicBlockInCodeOrder();
    if (predSucc != null) {
      ir.cfg.breakCodeOrder(predBlock, predSucc);
      ir.cfg.linkInCodeOrder(guardBlock1, predSucc);
    }
    ir.cfg.linkInCodeOrder(predBlock, guardBlock0);
    ir.cfg.linkInCodeOrder(guardBlock0, guardBlock1);
    
    // guard block for main loop
    OPT_BasicBlock guardBlock2
      = header.createSubBlock(header.firstInstruction().bcIndex, ir);

    // landing pad for main loop
    OPT_BasicBlock landingPad
      = header.createSubBlock(header.firstInstruction().bcIndex, ir);
    
    OPT_BasicBlock mainLoop = exitBlock.nextBasicBlockInCodeOrder();
    ir.cfg.breakCodeOrder (exitBlock,   mainLoop);
    ir.cfg.linkInCodeOrder(exitBlock,   guardBlock2);
    ir.cfg.linkInCodeOrder(guardBlock2, landingPad);
    ir.cfg.linkInCodeOrder(landingPad,  mainLoop);


    
    OPT_RegisterOperand remainder  =  ir.regpool.makeTemp(rop1.type);
    OPT_RegisterOperand limit =  ir.regpool.makeTemp(rop1.type);

    // test whether a <= b for stride == 1 and a >= b for stride == -1
    tmp = guardBlock0.lastInstruction();
    tmp.insertBefore (Move.create (INT_MOVE, limit, op2.copy()));

    OPT_ConditionOperand g0cond = OPT_ConditionOperand.GREATER();
    if   (stride == -1)  g0cond = OPT_ConditionOperand.LESS();
    
    tmp.insertBefore
      (IfCmp.create(INT_IFCMP, outerGuard.copyD2D(), rop1.copyD2U(),
		    op2.copy(), g0cond, header.makeJumpTarget(),
		    OPT_BranchProfileOperand.unlikely()));
    tmp.insertBefore(Goto.create(GOTO, guardBlock1.makeJumpTarget()));
      
    // jump over the original loop, if numbers of iteration is already aligned
    tmp = guardBlock1.lastInstruction();
    if (stride == 1) 
      tmp.insertBefore
	(Binary.create(INT_SUB, remainder, op2.copy(), rop1.copyD2U()));
    else 
      tmp.insertBefore
	(Binary.create(INT_SUB, remainder, rop1.copyD2U(), op2.copy()));

    if (cond.isGREATER_EQUAL() || cond.isLESS_EQUAL()) 
      tmp.insertBefore
	(Binary.create(INT_ADD, remainder.copyD2D(), remainder.copyD2U(),
		       new OPT_IntConstantOperand(1)));
    
    tmp.insertBefore
      (Binary.create(INT_AND, remainder.copyD2D(), remainder.copyD2U(),
		     new OPT_IntConstantOperand(unrollFactor-1)));

    if (stride == 1)
      tmp.insertBefore
	(Binary.create(INT_ADD, limit.copyD2U(), op1.copy(),
		       remainder.copyD2U()));
     else
      tmp.insertBefore
	(Binary.create(INT_SUB, limit.copyD2U(), op1.copy(),
		       remainder.copyD2U()));
    
    if (cond.isLESS_EQUAL()) 
      tmp.insertBefore
	(Binary.create(INT_ADD, limit.copyD2D(), limit.copyD2U(),
		       new OPT_IntConstantOperand(-1)));
    
    if (cond.isGREATER_EQUAL()) 
      tmp.insertBefore
	(Binary.create(INT_ADD, limit.copyD2D(), limit.copyD2U(),
		       new OPT_IntConstantOperand(1)));
    
    tmp.insertBefore
      (IfCmp.create(INT_IFCMP, outerGuard.copyD2D(), remainder.copyD2U(),
		    new OPT_IntConstantOperand(0),
		    OPT_ConditionOperand.EQUAL(),
		    landingPad.makeJumpTarget(),
		    new OPT_BranchProfileOperand(1.0/unrollFactor)));
    tmp.insertBefore(Goto.create(GOTO,header.makeJumpTarget()));

    // change the back branch in the original loop

    deleteBranches (exitBlock);
    tmp = exitBlock.lastInstruction();
    tmp.insertBefore 
      (IfCmp.create (INT_IFCMP, outerGuard.copyD2D(),
		     rop1.copyU2U(), limit.copyD2U(),
		     (OPT_ConditionOperand) cond.copy(),
		     header.makeJumpTarget(),
		     new OPT_BranchProfileOperand(1.0 - 1.0 / unrollFactor)));
    tmp.insertBefore
      (Goto.create(GOTO,guardBlock2.makeJumpTarget()));

    // jump over main loop if no iterations left
    tmp = guardBlock2.lastInstruction();
    tmp.insertBefore
      (IfCmp.create (INT_IFCMP, outerGuard.copyD2D(),
		     rop1.copyU2U(), op2.copy(),
		     (OPT_ConditionOperand) cond.copy(),
		     landingPad.makeJumpTarget(),
		     new OPT_BranchProfileOperand(1.0-backBranchProbability)));
    tmp.insertBefore (Goto.create (GOTO, succBlock.makeJumpTarget()));

    // landing pad jumps to mainHeader
    tmp = landingPad.lastInstruction();
    tmp.insertBefore (Goto.create(GOTO, mainHeader.makeJumpTarget()));
    
    // repair back edge in mainExit
    if (VM.VerifyAssertions) VM.assert (mainExit != null);
    tmp = mainExit.lastInstruction();
    if (VM.VerifyAssertions)
      VM.assert ((   mainExit.lastRealInstruction() == null)
		 || !mainExit.lastRealInstruction().isBranch());
    tmp.insertBefore
      (IfCmp.create (INT_IFCMP, ifcmpGuard.copyU2U(),
		     rop1.copyU2U(), op2.copy(),
		     (OPT_ConditionOperand) cond.copy(),
		     mainHeader.makeJumpTarget(),
		     new OPT_BranchProfileOperand(1.0
						  - (1.0-backBranchProbability)
						  * unrollFactor)));
    tmp.insertBefore
    (Goto.create (GOTO, succBlock.makeJumpTarget()));

    // recompute normal outs
    guardBlock0.recomputeNormalOut(ir);
    guardBlock1.recomputeNormalOut(ir);
    guardBlock2.recomputeNormalOut(ir);
    exitBlock.recomputeNormalOut(ir);
    landingPad.recomputeNormalOut(ir);
    mainExit.recomputeNormalOut(ir);
    if (ir.options.hasMETHOD_TO_PRINT()
	&& ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
      // dumpIR(ir, "after unroll");
    }
  }

  static void report (String s) {
    if (DEBUG) VM.sysWrite ("] "+s);
  }

  private static int theVisit = 1;

  private static OPT_Operand follow (OPT_Operand use) {
    theVisit++; return _follow(use);}
  
  private static OPT_Operand _follow (OPT_Operand use) {
    while (true) {
      if (!(use instanceof OPT_RegisterOperand)) return use;
      OPT_RegisterOperand rop = (OPT_RegisterOperand) use;
      OPT_RegisterOperandEnumeration
	defs = OPT_DefUse.defs (rop.register);
      if (!defs.hasMoreElements()) {return use;}
      OPT_Instruction def = defs.next().instruction;
      if (!Move.conforms (def)) return use;
      if (defs.hasMoreElements()) {return use;}

      if (def.scratch == theVisit) return use;
      def.scratch = theVisit;
      
      use = Move.getVal(def);
    }
  }

  private static OPT_Instruction definingInstruction (OPT_Operand op) {
    if (!(op instanceof OPT_RegisterOperand)) return op.instruction;
    OPT_RegisterOperandEnumeration
      defs = OPT_DefUse.defs (((OPT_RegisterOperand) op).register);
    if (!defs.hasMoreElements()) {return op.instruction;}
    OPT_Instruction def = defs.next().instruction;
    if (defs.hasMoreElements()) {return op.instruction;}
    return def;
  }
  
  private static boolean loopInvariant (OPT_Operand op, OPT_BitVector nloop) {
    if (op instanceof OPT_ConstantOperand) return true;
    if (op instanceof OPT_RegisterOperand) {
      boolean variant = false;
      OPT_Register reg = ((OPT_RegisterOperand)op).register;
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs(reg);
      while (defs.hasMoreElements()) {
	OPT_Instruction inst = defs.next().instruction;
	if (Move.conforms (inst))
	  inst = definingInstruction (follow (Move.getVal(inst)));
	if (nloop.get(inst.getBasicBlock().getNumber())) {
	  variant = true;
	  break;
	}
      }
      if (variant) {
	defs = OPT_DefUse.defs(reg);
	while (defs.hasMoreElements()) {
	  OPT_Instruction inst = defs.next().instruction;
	  if (Move.conforms (inst))
	    inst = definingInstruction (follow (Move.getVal(inst)));
	  //VM.sysWrite ("- "+inst.getBasicBlock()+": "+inst+"\n");
	}
      }
      return !variant;
    }
    //VM.sysWrite("other: "+op+"\n");
    return false;
  }
  
  static void linkToLST (OPT_IR ir) {
    OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      e.next().scratchObject = null;
      e.next().scratch = 0;
    }
    OPT_LSTGraph lstg = ir.HIRInfo.LoopStructureTree;
    if (lstg != null) markHeaders ((OPT_LSTNode)lstg.firstNode());
  }


  // for all loops:
  // make the header block point to the corresponding loop structure tree node.
  static private void markHeaders (OPT_LSTNode t) {
    OPT_BasicBlock header = t.header;
    header.scratchObject = t;
    Enumeration e = t.outNodes();
    while (e.hasMoreElements()) {
      OPT_LSTNode n = (OPT_LSTNode)e.nextElement();
      markHeaders (n);
    }
  }
  
  static OPT_BasicBlock[] makeSomeCopies (int unrollFactor, OPT_IR ir,
					  OPT_BitVector nloop,
					  int blocks,
					  OPT_BasicBlock header,
					  OPT_BasicBlock exitBlock,
					  OPT_BasicBlock seqStart)
  {
    // make some copies of the original loop
    OPT_BasicBlock seqEnd = seqStart.nextBasicBlockInCodeOrder();
    if (seqEnd != null) ir.cfg.breakCodeOrder(seqStart, seqEnd);
    OPT_BasicBlock seqLast = seqStart;

    OPT_BasicBlock firstHeader = null;
    OPT_BasicBlock lastHeader = null;
    OPT_BasicBlock lastExit = null;
    OPT_BasicBlock handles[] = new OPT_BasicBlock[2];
    
    OPT_BasicBlock bCopy = null;
    OPT_BitVector loop = new OPT_BitVector (nloop);
    loop.clear(header.getNumber());
    loop.clear(exitBlock.getNumber());
    
    for (int i = 0;  i < unrollFactor;  ++i) {

      // copy header
      seqLast = copyAndLinkBlock(ir, seqLast, header);
      lastHeader = seqLast;
      
      if (i == 0) {
	firstHeader = seqLast;
      } else {
	// link copies by jumps
	OPT_CFGTransformations.removeYieldPoint(seqLast);
	lastExit.lastInstruction().insertBefore
	  (Goto.create(GOTO, seqLast.makeJumpTarget()));
	lastExit.recomputeNormalOut(ir);
      }

      // copy body
      OPT_BasicBlockEnumeration bs = ir.getBasicBlocks (loop);
      while (bs.hasMoreElements()) {
	seqLast = copyAndLinkBlock (ir, seqLast, bs.next());
      }

      // copy exit block
      if (exitBlock != header) {
	seqLast = copyAndLinkBlock (ir, seqLast, exitBlock);
	lastExit = seqLast;
      } else {
	lastExit = lastHeader;
      }
      
      // delete all branches in the copies of the exit block
      deleteBranches (lastExit);
      
      // redirect internal branches
      OPT_BasicBlock cb = seqLast;
      for (int j = 0; j < blocks; ++j) {
	cb.recomputeNormalOut(ir);
	OPT_BasicBlockEnumeration be = cb.getOut();
	while (be.hasMoreElements()) {
	  OPT_BasicBlock out = be.next();
	  if (nloop.get(out.getNumber())) {
	    cb.redirectOuts (out, (OPT_BasicBlock) out.scratchObject, ir);
	  }
	}
	cb.recomputeNormalOut(ir);
	cb = cb.prevBasicBlockInCodeOrder();
      }
    }
    if (seqEnd != null) ir.cfg.linkInCodeOrder (seqLast, seqEnd);
    handles[0] = firstHeader;
    handles[1] = lastExit;
    return handles;
  }


  static OPT_BasicBlock copyAndLinkBlock (OPT_IR ir,
					  OPT_BasicBlock seqLast,
					  OPT_BasicBlock block) {
    OPT_BasicBlock copy = block.copyWithoutLinks(ir);
    ir.cfg.linkInCodeOrder (seqLast, copy);
    block.scratchObject = copy;
    return copy;
  }
  
  static void deleteBranches (OPT_BasicBlock b) {
    OPT_Instruction branch = b.lastRealInstruction();
    while (branch.isBranch()) {
      OPT_Instruction nextBranch = branch.getPrev();
      branch.remove();
      branch = nextBranch;
    }
  }

  static final class RealDefs implements OPT_OperandEnumeration {
    private OPT_RegisterOperandEnumeration defs = null;
    private OPT_Operand use;
    private RealDefs others = null;

    private void init (OPT_Operand use) {
      this.use = use;
      if (use instanceof OPT_RegisterOperand) {
	OPT_RegisterOperand rop = (OPT_RegisterOperand) use;
	defs = OPT_DefUse.defs (rop.register);
	this.use = null;
	if (!defs.hasMoreElements()) defs = null;
      }
    }
    
    public RealDefs (OPT_Operand use) {
      this.init (use);
      theVisit++;
    }

    public RealDefs (OPT_Operand use, int visit) {
      this.init (use);
      theVisit = visit;
    }

    public OPT_Operand next() {
      OPT_Operand res = use;
      if (res != null) {
	use = null;
	return res;
      }
      if (others != null && others.hasMoreElements())
	return others.next();
      
      res = defs.next();
      OPT_Instruction inst = res.instruction;
      if (!(Move.conforms (inst)) || inst.scratch == theVisit)
	return res;
      inst.scratch = theVisit;
      
      others = new RealDefs (Move.getVal(inst), theVisit);
      if (!(others.hasMoreElements())) return res;
      return others.next();
    }

    
    public boolean hasMoreElements () {
      
      return  use != null
	|| (others != null && others.hasMoreElements())
	|| (defs != null && defs.hasMoreElements());
    }

    
    public Object nextElement() {
      return next();
    }
    
    public OPT_Operand nextClear() {
      OPT_Operand res = next();
      res.instruction = null;
      return res;
    }
  }
}
