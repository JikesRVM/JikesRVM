/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;


/**
 * This compiler phase translates out of SSA form.  
 *
 * @see OPT_SSA
 * @see OPT_SSAOptions
 * @see OPT_LTDominators
 *
 * @author Stephen Fink
 * @author Julian Dolby
 * @author Martin Trapp
 */
class OPT_LeaveSSA extends OPT_CompilerPhase
  implements OPT_Operators, OPT_Constants {
  
  /**
   *  verbose debugging flag 
   */ 
  static final boolean DEBUG = false;

  /**
   * The IR to manipulate
   */
  private OPT_IR ir;

  private OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1);

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   * @return 
   */
  final boolean shouldPerform (OPT_Options options) {
    return  options.SSA;
  }

  /**
   * Return a string name for this phase.
   * @return "Leave SSA"
   */
  final String getName () {
    return  "Leave SSA";
  }

  /**
   * Should we print the IR before or after performing this phase?
   * @param options controlling compiler options
   * @param before query before if true, after if false.
   * @return 
   */
  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * perform the main out-of-ssa transformation
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
    this.ir = ir;
    translateFromSSA(ir);
    branchOpts.perform(ir, true);
    // reset ir.SSADictionary 
    ir.HIRInfo.SSADictionary = null;
    // reset ssa options
    ir.actualSSAOptions = null;
  }

  /**
   * This class provides an abstraction over stacks of names
   * for registers.
   */
  static class VariableStacks extends java.util.HashMap {

    /**
     * Get the name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name at the top of the stack for the register
     */
    OPT_Operand peek (OPT_Register s) {
      java.util.Stack stack = (java.util.Stack)get(s);
      if (stack == null || stack.isEmpty())
        return  null; 
      else 
        return  (OPT_Operand)stack.peek();
    }

    /**
     * Pop the name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name at the top of the stack for the register
     */
    OPT_Operand pop (OPT_Register s) {
      java.util.Stack stack = (java.util.Stack)get(s);
      if (stack == null)
        throw  new OPT_OptimizingCompilerException("Failure in translating out of SSA form: trying to pop operand from non-existant stack"); 
      else 
        return  (OPT_Operand)stack.pop();
    }

    /**
     * Push a name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name to push on the stack
     */
    void push (OPT_Register s, OPT_Operand name) {
      java.util.Stack stack = (java.util.Stack)get(s);
      if (stack == null) {
        stack = new java.util.Stack();
        put(s, stack);
      }
      stack.push(name);
    }
  }

  /**
   * An instance of this class represents a pending copy instruction
   * to be inserted.
   */
  static class Copy {
    /**
     * The right-hand side of the copy instruction
     */
    OPT_Operand source;
    /**
     * The left-hand side of the copy instruction
     */
    OPT_RegisterOperand destination;
    /**
     *  The phi instruction which generated this copy instruction
     */
    OPT_Instruction phi;

    /**
     * Create a pending copy operation for an operand of a phi instruction
     * @param     phi   the phi instruction
     * @param     index which operand of the instruction to copy
     */
    Copy (OPT_Instruction phi, int index) {
      this.phi = phi;
      destination = Phi.getResult(phi).asRegister();
      source = Phi.getValue(phi, index);
    }
  }

  /**
   * Record pending copy operations needed to insert at the end of a basic
   * block.
   * @param bb the basic block to process
   * @param live valid liveness information for the IR
   * @param s structure holding stacks of names for each symbolic register
   */
  private java.util.Set scheduleCopies (OPT_BasicBlock bb, OPT_LiveAnalysis live, 
                                   VariableStacks s) {
    // pushed records variables renamed in this block
    java.util.Set pushed = new java.util.HashSet(4);

    // compute out liveness from information in LiveAnalysis
    OPT_LiveSet out = new OPT_LiveSet();
    for (Enumeration outBlocks = bb.getOut(); outBlocks.hasMoreElements(); ) {
      OPT_BasicBlock ob = (OPT_BasicBlock)outBlocks.nextElement();
      OPT_LiveAnalysis.BBLiveElement le = live.getLiveInfo(ob);
      out.add(le.in());
    }

    // initialization
    java.util.HashMap map = new java.util.HashMap(4);
    java.util.HashSet usedByAnother = new java.util.HashSet(4);
    OPT_LinkedListObjectElement copySet = null;
    OPT_LinkedListObjectElement workList = null;
    // collect copies required in this block.  These copies move
    // the appropriate rval into the lval of each phi node in
    // control children of the current block.
    Enumeration e = bb.getOut();
    while (e.hasMoreElements()) {
      OPT_BasicBlock bbs = (OPT_BasicBlock)e.nextElement();
      if (bbs.isExit()) continue;
      for (OPT_Instruction phi = bbs.firstInstruction(); 
          phi != bbs.lastInstruction(); 
          phi = phi.nextInstructionInCodeOrder()) {
        if (phi.operator() != PHI) continue;
        for (int index=0; index<Phi.getNumberOfPreds(phi); index++) {
          if (Phi.getPred(phi,index).block != bb) continue;
          OPT_Operand rval = Phi.getValue(phi, index);
          if (rval.isRegister() && Phi.getResult(phi).asRegister().register
              == rval.asRegister().register)
             continue;
          Copy c = new Copy(phi, index);
          copySet = OPT_LinkedListObjectElement.cons(c, copySet);
          if (c.source instanceof OPT_RegisterOperand) {
            OPT_Register r = c.source.asRegister().register;
            map.put(r, r);
            usedByAnother.add(r);
          }
          if (c.destination instanceof OPT_RegisterOperand) {
            OPT_Register r = c.destination.asRegister().register;
            map.put(r, r);
          }
        }
      }
    }
    //  the copies that need to be added to this block are processed
    //  in a worklist that ensures that copies are inserted only
    //  after the destination register has been read by any other copy
    //  that needs it.
    //
    // initialize work list with all copies whose destination is not
    // the source for any other copy, and delete such copies from
    // the set of needed copies.
    OPT_LinkedListObjectElement ptr = OPT_LinkedListObjectElement.cons(null, 
        copySet);
    OPT_LinkedListObjectElement head = ptr;
    while (ptr.getNext() != null) {
      Copy c = (Copy)((OPT_LinkedListObjectElement)ptr.getNext()).getValue();
      if (!usedByAnother.contains(c.destination.register)) {
        workList = OPT_LinkedListObjectElement.cons(c, workList);
        ptr.setNext(ptr.getNext().getNext());
      } 
      else 
        ptr = (OPT_LinkedListObjectElement)ptr.getNext();
    }
    copySet = (OPT_LinkedListObjectElement)head.getNext();
    // while there is any more work to do.
    while (workList != null || copySet != null) {
      // while there are copies that can be correctly inserted.
      while (workList != null) {
        Copy c = (Copy)workList.getValue();
        workList = (OPT_LinkedListObjectElement)workList.getNext();
        OPT_Register r = c.destination.register;
        VM_Type tt = c.destination.type;
        if (tt == null) {
          tt = VM_Type.IntType;
          VM.sysWrite("OPT_SSA, warning: null type in " + c.destination
              + "\n");
        }
        // this check captures cases when the result of a phi
        // in a control successor is live on exit of the current
        // block.  this means it is incorrect to simply insert
        // a copy of the destination in the current block.  so
        // we rename the destination to a new temporary, and
        // record the renaming so that dominator blocks get the
        // new name.
        if (out.contains(r)) {
          OPT_Register t = ir.regpool.getReg(r);
          OPT_Instruction save = OPT_SSA.makeMoveInstruction(ir, t, r, 
              tt);
          c.phi.insertFront(save);
          s.push(r, new OPT_RegisterOperand(t, tt));
          pushed.add(r);
        }
        OPT_Instruction ci = null;
        // insert copy operation required to remove phi
        if (c.source instanceof OPT_NullConstantOperand) {
          if (tt.isReferenceType())
            ci = OPT_SSA.makeMoveInstruction(ir, 
                r, (OPT_ConstantOperand)c.source);
        } 
        else if (c.source instanceof OPT_ConstantOperand) {
          ci = OPT_SSA.makeMoveInstruction(ir, r, 
              (OPT_ConstantOperand)c.source);
        } 
        else if (c.source instanceof OPT_RegisterOperand) {
          OPT_Register sr = c.source.asRegister().register;
          ci = OPT_SSA.makeMoveInstruction(ir, r, (OPT_Register)map.get(sr), 
              tt);
          map.put(sr, r);
        } 
        else {
          throw  new OPT_OptimizingCompilerException("Unexpected phi operand "
              + c.source + " encountered during SSA teardown", true);
        }
        if (ci != null)
          OPT_SSA.addAtEnd(ir, bb, ci, 
              c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
        // source has been copied and so can now be overwritten
        // safely.  so now add any copies _to_ the source of the
        // current copy to the work list.
        if (c.source instanceof OPT_RegisterOperand) {
          OPT_Register saved = c.source.asRegister().register;
          OPT_LinkedListObjectElement ptr1 = OPT_LinkedListObjectElement.cons(
              null, copySet);
          OPT_LinkedListObjectElement head1 = ptr1;
          while (ptr1.getNext() != null) {
            Copy cc = (Copy)((OPT_LinkedListObjectElement)ptr1.getNext()).
                getValue();
            if (cc.destination.asRegister().register == saved) {
              workList = OPT_LinkedListObjectElement.cons(cc, workList);
              ptr1.setNext(ptr1.getNext().getNext());
            } 
            else 
              ptr1 = (OPT_LinkedListObjectElement)ptr1.getNext();
          }
          copySet = (OPT_LinkedListObjectElement)head1.getNext();
        }
      }
      // and empty work list with work remaining in the copy set
      // implies a cycle in the dependencies amongst copies.  deal
      // with this: break the cycle by copying the destination 
      // of an arbitrary member of the copy set into a temporary.
      // this destination has thus been saved, and can now be
      // safely overwritten.  so, add that copy to the work list.
      if (copySet != null) {
        Copy c = (Copy)copySet.getValue();
        copySet = (OPT_LinkedListObjectElement)copySet.getNext();
        OPT_Register tt = ir.regpool.getReg(c.destination.register);
        OPT_SSA.addAtEnd(ir, bb, OPT_SSA.makeMoveInstruction(
            ir, tt, c.destination.register, 
            c.destination.type), 
            c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
        map.put(c.destination.register, tt);
        workList = OPT_LinkedListObjectElement.cons(c, workList);
      }
    }
    return  pushed;
  }

  /**
   * Insert copy instructions into a basic block to safely translate out
   * of SSA form.
   *
   * @param bb the basic block
   * @param dom a valid dominator tree for the IR
   * @param live valid liveness information for the IR
   * @param s a structure holding stacks of names for symbolic registers
   */
  private void insertCopies (OPT_BasicBlock bb, OPT_DominatorTree dom, 
      OPT_LiveAnalysis live, VariableStacks s) {
    // substitute variables renamed in control parents
    OPT_InstructionEnumeration e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      OPT_Instruction i = e.next();
      OPT_OperandEnumeration ee = i.getOperands();
      while (ee.hasMoreElements()) {
        OPT_Operand o = ee.next();
        if (o instanceof OPT_RegisterOperand) {
          OPT_Register r1 = ((OPT_RegisterOperand)o).register;
	  if (r1.isValidation()) continue;
          OPT_Operand r2 = s.peek(r1);
          if (r2 != null)
            i.replaceOperand(o, r2);
        }
      }
    }
    // add copies required in this block to remove phis,
    // recording needed renamings in s and pushed
    java.util.Set pushed = scheduleCopies(bb, live, s);
    // insert copies in control children
    Enumeration children = dom.getChildren(bb);
    while (children.hasMoreElements()) {
      OPT_BasicBlock c = ((OPT_DominatorTreeNode)children.nextElement()).
          getBlock();
      insertCopies(c, dom, live, s);
    }
    // pop renamings from this block off stack
    java.util.Iterator p = pushed.iterator();
    while (p.hasNext())
      s.pop((OPT_Register)p.next());
  }

  /**
   * Main driver to translate an IR out of SSA form.
   *
   * @param ir the IR in SSA form
   */
  public void translateFromSSA (OPT_IR ir) {
    // 0. Deal with guards (validation registers)
    unSSAGuards (ir);
    // 1. re-compute dominator tree in case of control flow changes
    OPT_LTDominators.perform(ir, true, true);
    OPT_DominatorTree dom = new OPT_DominatorTree(ir, true);
    // 2. compute liveness
    OPT_LiveAnalysis live = 
      new OPT_LiveAnalysis(false,  // don't create GC maps
			   true,   // skip (final) local propagation step
			           // of live analysis
			   false,  // don't store information at handlers
			   false); // dont skip guards
                                   
    live.perform(ir);
    // 3. initialization
    VariableStacks s = new VariableStacks();
    // 4. convert phi nodes into copies
    OPT_BasicBlock b = ((OPT_DominatorTreeNode)dom.getRoot()).getBlock();
    insertCopies(b, dom, live, s);
    // 5. phis are now redundant
    removeAllPhis(ir);
  }

  /**
   * Remove all phi instructions from the IR.
   * 
   * @param ir the governing IR
   */
  static void removeAllPhis (OPT_IR ir) {
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(), 
        sentinel = ir.lastInstructionInCodeOrder(), 
        nextInstr = null; s != sentinel; s = nextInstr) {
      // cache because remove nulls next/prev fields
      nextInstr = s.nextInstructionInCodeOrder();
      if (Phi.conforms(s))
        s.remove();
    }
  }

  /**
   * Special treatment for guard registers:
   * Remove guard-phis by evaluating operands into same register.
   * If this target register is not unique, unite the alternatives.
   */
  private void unSSAGuards (OPT_IR ir) {
    // 0. initialization
    unSSAGuardsInit (ir);
    // 1. Determine target registers
    unSSAGuardsDetermineReg (ir);
    // 2. Rename targets and remove Phis
    unSSAGuardsFinalize (ir);
  }

  OPT_Instruction guardPhis = null;
  
  /**
   * Initialization for removal of guard phis.
   */
  private void unSSAGuardsInit (OPT_IR ir) {
    guardPhis = null;
    OPT_InstructionEnumeration e = ir.forwardInstrEnumerator();

    // visit all instructions, looking for guard phis
    
    while (e.hasMoreElements()) {
      OPT_Instruction inst = e.next();
      if (! Phi.conforms (inst)) continue;
      OPT_Operand res = Phi.getResult(inst);
      if (! (res instanceof OPT_RegisterOperand)) continue;
      OPT_Register r = res.asRegister().register;
      if (!r.isValidation()) continue;

      // force all operands of Phis into registers.

      inst.scratchObject = guardPhis;
      guardPhis = inst;
      
      int values = Phi.getNumberOfValues (inst);
      for (int i = 0;  i < values;  ++i) {
	OPT_Operand op = Phi.getValue (inst, i);
	if (! (op instanceof OPT_RegisterOperand)) {
	  if (op instanceof OPT_TrueGuardOperand) {
	    OPT_BasicBlock bb = Phi.getPred (inst, i).block;
	    OPT_Instruction move = Move.create (GUARD_MOVE,
						res.asRegister().copyD2D(),
						new OPT_TrueGuardOperand());
	    move.position = ir.gc.inlineSequence;
	    move.bcIndex = SSA_SYNTH_BCI;
	    bb.appendInstructionRespectingTerminalBranchOrPEI (move); 
	  } else {
	    if (VM.VerifyAssertions) VM.assert (false);
	  }
	}
      }
    }

    // visit all guard registers, init union/find
    
    for (OPT_Register r=ir.regpool.getFirstRegister(); r != null; r = r.next) {
      if (!r.isValidation()) continue;
      r.scratch = 1;
      r.scratchObject = r;
    }
  }

  /**
   * Determine target register for guard phi operands
   */
  private void unSSAGuardsDetermineReg (OPT_IR ir) {
    OPT_Instruction inst = guardPhis;
    while (inst != null) {
      OPT_Register r = Phi.getResult (inst).asRegister().register;
      int values = Phi.getNumberOfValues (inst);
      for (int i = 0;  i < values;  ++i) {
	OPT_Operand op = Phi.getValue (inst, i);
	if (op instanceof OPT_RegisterOperand) {
	  guardUnion (op.asRegister().register, r);
	} else {
	  if (VM.VerifyAssertions)
	    VM.assert (op instanceof OPT_TrueGuardOperand);
	}
      }
      inst = (OPT_Instruction) inst.scratchObject;
    }
  }

  /**
   * Rename registers and delete Phis.
   */
  private void unSSAGuardsFinalize (OPT_IR ir) {
    OPT_DefUse.computeDU (ir);
    for (OPT_Register r=ir.regpool.getFirstRegister(); r != null; r = r.next) {
      if (!r.isValidation()) continue;
      OPT_Register nreg = guardFind (r);
      OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses (r);
      while (uses.hasMoreElements()) {
	OPT_RegisterOperand use = uses.next();
	use.register = nreg;
      }
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs (r);
      while (defs.hasMoreElements()) {
	OPT_RegisterOperand def = defs.next();
	def.register = nreg;
      }
    }
    OPT_Instruction inst = guardPhis;
    while (inst != null) {
      inst.remove();
      inst = (OPT_Instruction) inst.scratchObject;
    }
  }

  /**
   * union step of union/find for guard registers during unSSA
   */
  private OPT_Register guardUnion (OPT_Register from, OPT_Register to)
  {
    OPT_Register a = guardFind (from);
    OPT_Register b = guardFind (to);
    if (a == b) return a;
    if (a.scratch == b.scratch) {
      a.scratch++;
      b.scratchObject = a;
      return a;
    }
    if (a.scratch > b.scratch) {
      b.scratchObject = a;
      return a;
    }
    a.scratchObject = b;
    return b;
  }
  
  /**
   * find step of union/find for guard registers during unSSA
   */
  private OPT_Register guardFind (OPT_Register r)
  {
    OPT_Register start = r;
    if (VM.VerifyAssertions) VM.assert (r.scratchObject != null);
    while (r.scratchObject != r) r = (OPT_Register) r.scratchObject;
    while (start.scratchObject != r) {
      start.scratchObject = r;
      start = (OPT_Register) start.scratchObject;
    }
    return r;
  }
}



