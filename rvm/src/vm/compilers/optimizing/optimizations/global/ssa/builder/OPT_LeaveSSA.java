/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;


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
class OPT_LeaveSSA extends OPT_CompilerPhase implements OPT_Operators, OPT_Constants {

  /**
   *  verbose debugging flag 
   */ 
  static final boolean DEBUG = false;

  // control bias between adding blocks or adding temporaries
  private static final boolean SplitBlockToAvoidRenaming = false;
  private static final boolean SplitBlockForLocalLive = true;
  private static final boolean SplitBlockIntoInfrequent = true;

  /**
   * The IR to manipulate
   */
  private OPT_IR ir;

  private OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1, true, true);

  private boolean splitSomeBlock = false;

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   */
  public final boolean shouldPerform(OPT_Options options) {
    return  options.SSA;
  }

  /**
   * Return a string name for this phase.
   * @return "Leave SSA"
   */
  public final String getName() {
    return  "Leave SSA";
  }

  /**
   * perform the main out-of-ssa transformation
   * @param ir the governing IR
   */
  final public void perform(OPT_IR ir) {
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
  static class VariableStacks extends HashMap {

    /**
     * Get the name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name at the top of the stack for the register
     */
    OPT_Operand peek(OPT_Register s) {
      Stack stack = (Stack)get(s);
      if (stack == null || stack.isEmpty()) return  null; 
      else return  (OPT_Operand)stack.peek();
    }

    /**
     * Pop the name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name at the top of the stack for the register
     */
    OPT_Operand pop(OPT_Register s) {
      Stack stack = (Stack)get(s);
      if (stack == null)
        throw new OPT_OptimizingCompilerException("Failure in translating out of SSA form: trying to pop operand from non-existant stack"); 
      else 
        return  (OPT_Operand)stack.pop();
    }

    /**
     * Push a name at the top of the stack for a particular register 
     * @param s the register in question
     * @return  the name to push on the stack
     */
    void push(OPT_Register s, OPT_Operand name) {
      Stack stack = (Stack)get(s);
      if (stack == null) {
        stack = new Stack();
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
    Copy(OPT_Instruction phi, int index) {
      this.phi = phi;
      destination = Phi.getResult(phi).asRegister();
      source = Phi.getValue(phi, index);
    }
  }

  /**
   * substitute variables renamed in control parents
   */
  private void performRename(OPT_BasicBlock bb, OPT_DominatorTree dom, VariableStacks s) {
    if (DEBUG) VM.sysWriteln("performRename: " + bb);

    OPT_InstructionEnumeration e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      OPT_Instruction i = e.next();
      OPT_OperandEnumeration ee = i.getUses();
      while (ee.hasMoreElements()) {
        OPT_Operand o = ee.next();      
        if (o instanceof OPT_RegisterOperand) {
          OPT_Register r1 = ((OPT_RegisterOperand)o).register;
          if (r1.isValidation()) continue;
          OPT_Operand r2 = s.peek(r1);
          if (r2 != null) {
            if (DEBUG) VM.sysWriteln("replace operand in " + i + "(" + r2 + 
                                     " for " + o);
            i.replaceOperand(o, r2.copy());
          }
        }
      }
    }

    // record renamings required in children
    e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      OPT_Instruction i = e.next();
      if (globalRenameTable.contains(i)) {
        OPT_Register original = Move.getVal(i).asRegister().register;
        OPT_RegisterOperand rename = Move.getResult(i);
        if (DEBUG) VM.sysWriteln("record rename " + rename + " for " + original);
        s.push(original, rename);
      }
    }

    // insert copies in control children
    Enumeration children = dom.getChildren(bb);
    while (children.hasMoreElements()) {
      OPT_BasicBlock c = ((OPT_DominatorTreeNode)children.nextElement()).getBlock();
      performRename(c, dom, s);
    }

    // pop renamings from this block off stack
    e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      OPT_Instruction i = e.next();
      if (globalRenameTable.contains(i)) {
        OPT_Register original = Move.getVal(i).asRegister().register;
        s.pop(original);
      }
    }
  }

  private Set globalRenameTable = new HashSet();
  private Set globalRenamePhis = new HashSet();

  private boolean usedBelowCopy(OPT_BasicBlock bb, OPT_Register r) {
    OPT_InstructionEnumeration ie = bb.reverseRealInstrEnumerator();
    while (ie.hasMoreElements() ) {
      OPT_Instruction inst = ie.next();
      if (inst.isBranch()) {
        OPT_OperandEnumeration oe = inst.getUses();
        while (oe.hasMoreElements()) {
          OPT_Operand op = oe.next();
          if (op.isRegister() && op.asRegister().register == r)
            return true;
        }
      } else
        break;
    }

    return false;
  }

  /**
   * Record pending copy operations needed to insert at the end of a basic
   * block.
   * TODO: this procedure is getting long and ugly.  Rewrite or refactor
   * it.
   * @param bb the basic block to process
   * @param live valid liveness information for the IR
   */
  private void scheduleCopies(OPT_BasicBlock bb, OPT_LiveAnalysis live) {

    if (DEBUG) VM.sysWrite("scheduleCopies: " + bb + "\n");
    
    // compute out liveness from information in LiveAnalysis
    OPT_LiveSet out = new OPT_LiveSet();
    for (Enumeration outBlocks = bb.getOut(); outBlocks.hasMoreElements(); ) {
      OPT_BasicBlock ob = (OPT_BasicBlock)outBlocks.nextElement();
      OPT_LiveAnalysis.BBLiveElement le = live.getLiveInfo(ob);
      out.add(le.in());
    }
    
    // usedByAnother represents the set of registers that appear on the
    // left-hand side of subsequent phi nodes.  This is important, since
    // we be careful to order copies if the same register appears as the 
    // source and dest of copies in the same basic block.
    HashSet usedByAnother = new HashSet(4);

    // for each basic block successor b of bb, if we make a block on the
    // critical edge bb->b, then store this critical block.
    HashMap criticalBlocks = new HashMap(4);
    
    // For each critical basic block b in which we are inserting copies: return the 
    // mapping of registers to names implied by the copies that have
    // already been inserted into b.
    HashMap currentNames = new HashMap(4);
    
    // Additionally store the current names for the current basic block bb.
    HashMap bbNames = new HashMap(4);
    
    // copySet is a linked-list of copies we need to insert in this block.
    OPT_LinkedListObjectElement copySet = null;
    OPT_LinkedListObjectElement workList = null;
   
    // collect copies required in this block.  These copies move
    // the appropriate rval into the lval of each phi node in
    // control children of the current block.
    Enumeration e = bb.getOut();
    while (e.hasMoreElements()) {
      OPT_BasicBlock bbs = (OPT_BasicBlock)e.nextElement();
      if (bbs.isExit()) continue;
      for (OPT_Instruction phi = bbs.firstInstruction(); phi != bbs.lastInstruction(); phi = phi.nextInstructionInCodeOrder()) {
        if (phi.operator() != PHI) continue;
        for (int index=0; index<Phi.getNumberOfPreds(phi); index++) {
          if (Phi.getPred(phi,index).block != bb) continue;
          OPT_Operand rval = Phi.getValue(phi, index);
          if (rval.isRegister() && Phi.getResult(phi).asRegister().register == rval.asRegister().register) {
            continue;
          }
          Copy c = new Copy(phi, index);
          copySet = OPT_LinkedListObjectElement.cons(c, copySet);
          if (c.source instanceof OPT_RegisterOperand) {
            OPT_Register r = c.source.asRegister().register;
            usedByAnother.add(r);
          }
          if (c.destination instanceof OPT_RegisterOperand) {
            OPT_Register r = c.destination.asRegister().register;
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
    OPT_LinkedListObjectElement ptr = OPT_LinkedListObjectElement.cons(null, copySet);
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
        VM_TypeReference tt = c.destination.type;
        if (VM.VerifyAssertions && tt == null) {
          tt = VM_TypeReference.Int;
          VM.sysWrite("OPT_SSA, warning: null type in " + c.destination + "\n");
        }

        OPT_Register rr = null;
        if (c.source.isRegister()) rr = c.source.asRegister().register;
        boolean shouldSplitBlock = 
          !c.phi.getBasicBlock().isExceptionHandlerBasicBlock() &&
          ((out.contains(r) && SplitBlockToAvoidRenaming) || (rr!=null && usedBelowCopy(bb, rr) && SplitBlockForLocalLive));

        if (SplitBlockIntoInfrequent) {
          if (!bb.getInfrequent() && c.phi.getBasicBlock().getInfrequent()
              && !c.phi.getBasicBlock().isExceptionHandlerBasicBlock()) 
            shouldSplitBlock = true;
        }
        
        // this check captures cases when the result of a phi
        // in a control successor is live on exit of the current
        // block.  this means it is incorrect to simply insert
        // a copy of the destination in the current block.  so
        // we rename the destination to a new temporary, and
        // record the renaming so that dominator blocks get the
        // new name.
        if (out.contains(r) && !shouldSplitBlock) {
          if (!globalRenamePhis.contains(r)) {
            OPT_Register t = ir.regpool.getReg(r);
            OPT_Instruction save = OPT_SSA.makeMoveInstruction(ir, t, r, tt);
            if (DEBUG) VM.sysWriteln("Inserting " + save + " before " +
                                     c.phi + " in " + c.phi.getBasicBlock());
            c.phi.insertFront(save);
            globalRenamePhis.add(r);
            globalRenameTable.add(save);
          }
        }
        OPT_Instruction ci = null;
        
        // insert copy operation required to remove phi
        if (c.source instanceof OPT_NullConstantOperand) {
          if (tt.isReferenceType()) {
            ci = OPT_SSA.makeMoveInstruction(ir, r, (OPT_ConstantOperand)c.source);
          }
        } 
        else if (c.source instanceof OPT_ConstantOperand) {
          if (c.source instanceof OPT_UnreachableOperand) {
            ci = null;
          } else {
            ci = OPT_SSA.makeMoveInstruction(ir, r, (OPT_ConstantOperand)c.source);
          }
        } 
        else if (c.source instanceof OPT_RegisterOperand) {
          if (shouldSplitBlock) {
            if (DEBUG) VM.sysWriteln("splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            OPT_BasicBlock criticalBlock = (OPT_BasicBlock)criticalBlocks.get(c.phi.getBasicBlock());
            if (criticalBlock == null) {
              criticalBlock = OPT_IRTools.makeBlockOnEdge(bb, c.phi.getBasicBlock(), ir);
              if (c.phi.getBasicBlock().getInfrequent()) {
                criticalBlock.setInfrequent();
              }
              splitSomeBlock = true;
              criticalBlocks.put(c.phi.getBasicBlock(),criticalBlock);
              HashMap newNames = new HashMap(4);
              currentNames.put(criticalBlock,newNames);
            }
            OPT_Register sr = c.source.asRegister().register;
            HashMap criticalBlockNames = (HashMap)currentNames.get(criticalBlock);
            OPT_Register nameForSR = (OPT_Register)criticalBlockNames.get(sr);
            if (nameForSR == null) {
              nameForSR = (OPT_Register)bbNames.get(sr);
              if (nameForSR == null) nameForSR = sr;
            }
            if (DEBUG) VM.sysWriteln("dest(r): " + r);
            if (DEBUG) VM.sysWriteln("sr: " + sr + ", nameForSR: " + nameForSR);
            ci = OPT_SSA.makeMoveInstruction(ir, r, nameForSR, tt);
            criticalBlockNames.put(sr, r);
            criticalBlock.appendInstructionRespectingTerminalBranch(ci);
          } else {
            OPT_Register sr = c.source.asRegister().register;
            OPT_Register nameForSR = (OPT_Register)bbNames.get(sr);
            if (nameForSR == null) nameForSR = sr;
            if (DEBUG) VM.sysWriteln("not splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            if (DEBUG) VM.sysWriteln("dest(r): " + r);
            if (DEBUG) VM.sysWriteln("sr: " + sr + ", nameForSR: " + nameForSR);
            ci = OPT_SSA.makeMoveInstruction(ir, r, nameForSR, tt);
            bbNames.put(sr, r);
            OPT_SSA.addAtEnd(ir, bb, ci, c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
          }
          // ugly hack: having already added ci; set ci to null to skip remaining code;
          ci = null;
        } 
        else {
          throw  new OPT_OptimizingCompilerException("Unexpected phi operand "
                                                     + c.source + " encountered during SSA teardown", true);
        }
        if (ci != null)
          if (shouldSplitBlock) {
            if (DEBUG) VM.sysWriteln("splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            OPT_BasicBlock criticalBlock = (OPT_BasicBlock)criticalBlocks.get(c.phi.getBasicBlock());
            if (criticalBlock == null) {
              criticalBlock = OPT_IRTools.makeBlockOnEdge(bb, c.phi.getBasicBlock(), ir);
              if (c.phi.getBasicBlock().getInfrequent()) {
                criticalBlock.setInfrequent();
              }
              splitSomeBlock = true;
              criticalBlocks.put(c.phi.getBasicBlock(),criticalBlock);
              HashMap newNames = new HashMap(4);
              currentNames.put(criticalBlock,newNames);
            }
            criticalBlock.appendInstructionRespectingTerminalBranch(ci);
          } else {
            OPT_SSA.addAtEnd(ir, bb, ci, c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
          }
          
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
      // an empty work list with work remaining in the copy set
      // implies a cycle in the dependencies amongst copies.  deal
      // with this: break the cycle by copying the destination 
      // of an arbitrary member of the copy set into a temporary.
      // this destination has thus been saved, and can now be
      // safely overwritten.  so, add that copy to the work list.
      if (copySet != null) {
        Copy c = (Copy)copySet.getValue();
        copySet = (OPT_LinkedListObjectElement)copySet.getNext();
        OPT_Register tt = ir.regpool.getReg(c.destination.register);
        OPT_SSA.addAtEnd(ir, bb, OPT_SSA.makeMoveInstruction(ir, tt, c.destination.register, 
                                                             c.destination.type), 
                                                             c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
        bbNames.put(c.destination.register, tt);
        workList = OPT_LinkedListObjectElement.cons(c, workList);
      }
    }
  }

  /**
   * Insert copy instructions into a basic block to safely translate out
   * of SSA form.
   *
   * @param bb the basic block
   * @param dom a valid dominator tree for the IR
   * @param live valid liveness information for the IR
   */
  private void insertCopies(OPT_BasicBlock bb, 
                            OPT_DominatorTree dom, 
                            OPT_LiveAnalysis live)
  {
    // add copies required in this block to remove phis.
    // (record renaming required by simultaneous liveness in global tables)
    scheduleCopies(bb, live);

    // insert copies in control children
    Enumeration children = dom.getChildren(bb);
    while (children.hasMoreElements()) {
      OPT_BasicBlock c = ((OPT_DominatorTreeNode)children.nextElement()).getBlock();
      insertCopies(c, dom, live);
    }
  }


  /**
   * Main driver to translate an IR out of SSA form.
   *
   * @param ir the IR in SSA form
   */
  public void translateFromSSA(OPT_IR ir) {
    // 0. Deal with guards (validation registers)
    unSSAGuards(ir);
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
    insertCopies(b, dom, live);
    // 5. If necessary, recompute dominators to account for new control flow.
    if (splitSomeBlock) {
      OPT_LTDominators.perform(ir, true, true);
      dom = new OPT_DominatorTree(ir, true);
    }
    // 6. compensate for copies required by simulataneous liveness
    performRename(b, dom, s);
    // 7. phis are now redundant
    removeAllPhis(ir);
  }

  /**
   * Remove all phi instructions from the IR.
   * 
   * @param ir the governing IR
   */
  static void removeAllPhis(OPT_IR ir) {
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(), 
         sentinel = ir.lastInstructionInCodeOrder(), 
         nextInstr = null; s != sentinel; s = nextInstr) {
      // cache because remove nulls next/prev fields
      nextInstr = s.nextInstructionInCodeOrder();
      if (Phi.conforms(s)) s.remove();
    }
  }

  /**
   * Special treatment for guard registers:
   * Remove guard-phis by evaluating operands into same register.
   * If this target register is not unique, unite the alternatives.
   */
  private void unSSAGuards(OPT_IR ir) {
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
  private void unSSAGuardsInit(OPT_IR ir) {
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
        if (!(op instanceof OPT_RegisterOperand)) {
          if (op instanceof OPT_TrueGuardOperand) {
            OPT_BasicBlock bb = Phi.getPred(inst,i).block;
            OPT_Instruction move = Move.create (GUARD_MOVE,
                                                res.asRegister().copyD2D(),
                                                new OPT_TrueGuardOperand());
            move.position = ir.gc.inlineSequence;
            move.bcIndex = SSA_SYNTH_BCI;
            bb.appendInstructionRespectingTerminalBranchOrPEI(move); 
          } else if (op instanceof OPT_UnreachableOperand) {
            // do nothing
          } else {
            if (VM.VerifyAssertions) VM._assert(false);
          }
        }
      }
    }

    // visit all guard registers, init union/find
    for (OPT_Register r=ir.regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
      if (!r.isValidation()) continue;
      r.scratch = 1;
      r.scratchObject = r;
    }
  }

  /**
   * Determine target register for guard phi operands
   */
  private void unSSAGuardsDetermineReg(OPT_IR ir) {
    OPT_Instruction inst = guardPhis;
    while (inst != null) {
      OPT_Register r = Phi.getResult(inst).asRegister().register;
      int values = Phi.getNumberOfValues(inst);
      for (int i = 0;  i < values; ++i) {
        OPT_Operand op = Phi.getValue(inst, i);
        if (op instanceof OPT_RegisterOperand) {
          guardUnion(op.asRegister().register, r);
        } else {
          if (VM.VerifyAssertions) {
            VM._assert(op instanceof OPT_TrueGuardOperand ||
                      op instanceof OPT_UnreachableOperand);
          }
        }
      }
      inst = (OPT_Instruction)inst.scratchObject;
    }
  }

  /**
   * Rename registers and delete Phis.
   */
  private void unSSAGuardsFinalize(OPT_IR ir) {
    OPT_DefUse.computeDU(ir);
    for (OPT_Register r=ir.regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
      if (!r.isValidation()) continue;
      OPT_Register nreg = guardFind(r);
      OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses(r);
      while (uses.hasMoreElements()) {
        OPT_RegisterOperand use = uses.next();
        use.register = nreg;
      }
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs(r);
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
  private OPT_Register guardUnion(OPT_Register from, OPT_Register to)
  {
    OPT_Register a = guardFind(from);
    OPT_Register b = guardFind(to);
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
  private OPT_Register guardFind(OPT_Register r)
  {
    OPT_Register start = r;
    if (VM.VerifyAssertions) VM._assert (r.scratchObject != null);
    while (r.scratchObject != r) r = (OPT_Register) r.scratchObject;
    while (start.scratchObject != r) {
      start.scratchObject = r;
      start = (OPT_Register) start.scratchObject;
    }
    return r;
  }
}
