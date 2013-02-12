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

import static org.jikesrvm.compilers.opt.driver.OptConstants.SSA_SYNTH_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.PHI;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.controlflow.DominatorTreeNode;
import org.jikesrvm.compilers.opt.controlflow.LTDominators;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.UnreachableOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.liveness.LiveSet;
import org.jikesrvm.compilers.opt.util.TreeNode;

/**
 * This compiler phase translates out of SSA form.
 *
 * @see SSA
 * @see SSAOptions
 * @see LTDominators
 */
public class LeaveSSA extends CompilerPhase {

  /**
   *  verbose debugging flag
   */
  static final boolean DEBUG = false;

  /**
   * The IR to manipulate
   */
  private IR ir;

  private final BranchOptimizations branchOpts = new BranchOptimizations(-1, true, true);

  private boolean splitSomeBlock = false;

  private final HashSet<Instruction> globalRenameTable = new HashSet<Instruction>();

  private final HashSet<Register> globalRenamePhis = new HashSet<Register>();

  /**
   * Is SSA form enabled for the HIR?
   */
  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.SSA;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor = getCompilerPhaseConstructor(LeaveSSA.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Return a string name for this phase.
   * @return "Leave SSA"
   */
  @Override
  public final String getName() {
    return "Leave SSA";
  }

  /**
   * perform the main out-of-ssa transformation
   */
  @Override
  public final void perform(IR ir) {
    this.ir = ir;
    translateFromSSA(ir);

    // reset ir.SSADictionary
    ir.HIRInfo.dictionary = null;
    // reset ssa options
    ir.actualSSAOptions = null;

    branchOpts.perform(ir, true);

    ir.HIRInfo.dominatorsAreComputed = false;
  }

  /**
   * This class provides an abstraction over stacks of names
   * for registers.
   */
  static final class VariableStacks extends HashMap<Register, Stack<Operand>> {
    /** Support for map serialization */
    static final long serialVersionUID = -5664504465082745314L;

    /**
     * Get the name at the top of the stack for a particular register
     * @param s the register in question
     * @return the name at the top of the stack for the register
     */
    Operand peek(Register s) {
      Stack<Operand> stack = get(s);
      if (stack == null || stack.isEmpty()) {
        return null;
      } else {
        return stack.peek();
      }
    }

    /**
     * Pop the name at the top of the stack for a particular register
     * @param s the register in question
     * @return the name at the top of the stack for the register
     */
    Operand pop(Register s) {
      Stack<Operand> stack = get(s);
      if (stack == null) {
        throw new OptimizingCompilerException(
            "Failure in translating out of SSA form: trying to pop operand from non-existant stack");
      } else {
        return stack.pop();
      }
    }

    /**
     * Push a name at the top of the stack for a particular register
     * @param s the register in question
     * @param name the name to push on the stack
     */
    void push(Register s, Operand name) {
      Stack<Operand> stack = get(s);
      if (stack == null) {
        stack = new Stack<Operand>();
        put(s, stack);
      }
      stack.push(name);
    }
  }

  /**
   * An instance of this class represents a pending copy instruction
   * to be inserted.
   */
  static final class Copy {
    /**
     * The right-hand side of the copy instruction
     */
    final Operand source;
    /**
     * The left-hand side of the copy instruction
     */
    final RegisterOperand destination;
    /**
     *  The phi instruction which generated this copy instruction
     */
    final Instruction phi;

    /**
     * Create a pending copy operation for an operand of a phi instruction
     * @param     phi   the phi instruction
     * @param     index which operand of the instruction to copy
     */
    Copy(Instruction phi, int index) {
      this.phi = phi;
      destination = Phi.getResult(phi).asRegister();
      source = Phi.getValue(phi, index);
    }
  }

  /**
   * substitute variables renamed in control parents
   */
  private void performRename(BasicBlock bb, DominatorTree dom, VariableStacks s) {
    if (DEBUG) VM.sysWriteln("performRename: " + bb);

    Enumeration<Instruction> e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      Instruction i = e.nextElement();
      Enumeration<Operand> ee = i.getUses();
      while (ee.hasMoreElements()) {
        Operand o = ee.nextElement();
        if (o instanceof RegisterOperand) {
          Register r1 = ((RegisterOperand) o).getRegister();
          if (r1.isValidation()) continue;
          Operand r2 = s.peek(r1);
          if (r2 != null) {
            if (DEBUG) {
              VM.sysWriteln("replace operand in " + i + "(" + r2 + " for " + o);
            }
            i.replaceOperand(o, r2.copy());
          }
        }
      }
    }

    // record renamings required in children
    e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      Instruction i = e.nextElement();
      if (globalRenameTable.contains(i)) {
        Register original = Move.getVal(i).asRegister().getRegister();
        RegisterOperand rename = Move.getResult(i);
        if (DEBUG) VM.sysWriteln("record rename " + rename + " for " + original);
        s.push(original, rename);
      }
    }

    // insert copies in control children
    Enumeration<TreeNode> children = dom.getChildren(bb);
    while (children.hasMoreElements()) {
      BasicBlock c = ((DominatorTreeNode) children.nextElement()).getBlock();
      performRename(c, dom, s);
    }

    // pop renamings from this block off stack
    e = bb.forwardRealInstrEnumerator();
    while (e.hasMoreElements()) {
      Instruction i = e.nextElement();
      if (globalRenameTable.contains(i)) {
        Register original = Move.getVal(i).asRegister().getRegister();
        s.pop(original);
      }
    }
  }

  private boolean usedBelowCopy(BasicBlock bb, Register r) {
    Enumeration<Instruction> ie = bb.reverseRealInstrEnumerator();
    while (ie.hasMoreElements()) {
      Instruction inst = ie.nextElement();
      if (inst.isBranch()) {
        Enumeration<Operand> oe = inst.getUses();
        while (oe.hasMoreElements()) {
          Operand op = oe.nextElement();
          if (op.isRegister() && op.asRegister().getRegister() == r) {
            return true;
          }
        }
      } else {
        break;
      }
    }

    return false;
  }

  /**
   * Record pending copy operations needed to insert at the end of a basic
   * block.<p>
   *
   * TODO: this procedure is getting long and ugly.  Rewrite or refactor
   * it.
   * @param bb the basic block to process
   * @param live valid liveness information for the IR
   */
  private void scheduleCopies(BasicBlock bb, LiveAnalysis live) {

    if (DEBUG) VM.sysWrite("scheduleCopies: " + bb + "\n");

    // compute out liveness from information in LiveAnalysis
    LiveSet out = new LiveSet();
    for (Enumeration<BasicBlock> outBlocks = bb.getOut(); outBlocks.hasMoreElements();) {
      BasicBlock ob = outBlocks.nextElement();
      LiveAnalysis.BBLiveElement le = live.getLiveInfo(ob);
      out.add(le.getIn());
    }

    // usedByAnother represents the set of registers that appear on the
    // left-hand side of subsequent phi nodes.  This is important, since
    // we be careful to order copies if the same register appears as the
    // source and dest of copies in the same basic block.
    HashSet<Register> usedByAnother = new HashSet<Register>(4);

    // for each basic block successor b of bb, if we make a block on the
    // critical edge bb->b, then store this critical block.
    HashMap<BasicBlock, BasicBlock> criticalBlocks = new HashMap<BasicBlock, BasicBlock>(4);

    // For each critical basic block b in which we are inserting copies: return the
    // mapping of registers to names implied by the copies that have
    // already been inserted into b.
    HashMap<BasicBlock, HashMap<Register, Register>> currentNames =
        new HashMap<BasicBlock, HashMap<Register, Register>>(4);

    // Additionally store the current names for the current basic block bb.
    HashMap<Register, Register> bbNames = new HashMap<Register, Register>(4);

    // copySet is a linked-list of copies we need to insert in this block.
    final LinkedList<Copy> copySet = new LinkedList<Copy>();

    /* Worklist is actually used like a stack - should we make this an Stack ?? */
    final LinkedList<Copy> workList = new LinkedList<Copy>();

    // collect copies required in this block.  These copies move
    // the appropriate rval into the lval of each phi node in
    // control children of the current block.
    Enumeration<BasicBlock> e = bb.getOut();
    while (e.hasMoreElements()) {
      BasicBlock bbs = e.nextElement();
      if (bbs.isExit()) continue;
      for (Instruction phi = bbs.firstInstruction(); phi != bbs.lastInstruction(); phi =
          phi.nextInstructionInCodeOrder()) {
        if (phi.operator() != PHI) continue;
        for (int index = 0; index < Phi.getNumberOfPreds(phi); index++) {
          if (Phi.getPred(phi, index).block != bb) continue;
          Operand rval = Phi.getValue(phi, index);
          if (rval.isRegister() && Phi.getResult(phi).asRegister().getRegister() == rval.asRegister().getRegister()) {
            continue;
          }
          Copy c = new Copy(phi, index);
          copySet.add(0, c);
          if (c.source instanceof RegisterOperand) {
            Register r = c.source.asRegister().getRegister();
            usedByAnother.add(r);
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
    for (Iterator<Copy> copySetIter = copySet.iterator(); copySetIter.hasNext();) {
      Copy c = copySetIter.next();
      if (!usedByAnother.contains(c.destination.getRegister())) {
        workList.add(0, c);
        copySetIter.remove();
      }
    }
    // while there is any more work to do.
    while (!workList.isEmpty() || !copySet.isEmpty()) {
      // while there are copies that can be correctly inserted.
      while (!workList.isEmpty()) {
        Copy c = workList.remove(0);
        Register r = c.destination.getRegister();
        TypeReference tt = c.destination.getType();
        if (VM.VerifyAssertions && tt == null) {
          tt = TypeReference.Int;
          VM.sysWrite("SSA, warning: null type in " + c.destination + "\n");
        }

        Register rr = null;
        if (c.source.isRegister()) rr = c.source.asRegister().getRegister();
        boolean shouldSplitBlock =
            !c.phi.getBasicBlock().isExceptionHandlerBasicBlock() &&
            ((ir.options.SSA_SPLITBLOCK_TO_AVOID_RENAME && out.contains(r)) ||
             (rr != null && ir.options.SSA_SPLITBLOCK_FOR_LOCAL_LIVE && usedBelowCopy(bb, rr)));

        if (ir.options.SSA_SPLITBLOCK_INTO_INFREQUENT) {
          if (!bb.getInfrequent() &&
              c.phi.getBasicBlock().getInfrequent() &&
              !c.phi.getBasicBlock().isExceptionHandlerBasicBlock()) {
            shouldSplitBlock = true;
          }
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
            Register t = ir.regpool.getReg(r);
            Instruction save = SSA.makeMoveInstruction(ir, t, r, tt);
            if (DEBUG) {
              VM.sysWriteln("Inserting " + save + " before " + c.phi + " in " + c.phi.getBasicBlock());
            }
            c.phi.insertAfter(save);
            globalRenamePhis.add(r);
            globalRenameTable.add(save);
          }
        }
        Instruction ci = null;

        // insert copy operation required to remove phi
        if (c.source instanceof ConstantOperand) {
          if (c.source instanceof UnreachableOperand) {
            ci = null;
          } else {
            ci = SSA.makeMoveInstruction(ir, r, (ConstantOperand) c.source);
          }
        } else if (c.source instanceof RegisterOperand) {
          if (shouldSplitBlock) {
            if (DEBUG) VM.sysWriteln("splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            BasicBlock criticalBlock = criticalBlocks.get(c.phi.getBasicBlock());
            if (criticalBlock == null) {
              criticalBlock = IRTools.makeBlockOnEdge(bb, c.phi.getBasicBlock(), ir);
              if (c.phi.getBasicBlock().getInfrequent()) {
                criticalBlock.setInfrequent();
              }
              splitSomeBlock = true;
              criticalBlocks.put(c.phi.getBasicBlock(), criticalBlock);
              HashMap<Register, Register> newNames = new HashMap<Register, Register>(4);
              currentNames.put(criticalBlock, newNames);
            }
            Register sr = c.source.asRegister().getRegister();
            HashMap<Register, Register> criticalBlockNames = currentNames.get(criticalBlock);
            Register nameForSR = criticalBlockNames.get(sr);
            if (nameForSR == null) {
              nameForSR = bbNames.get(sr);
              if (nameForSR == null) nameForSR = sr;
            }
            if (DEBUG) VM.sysWriteln("dest(r): " + r);
            if (DEBUG) VM.sysWriteln("sr: " + sr + ", nameForSR: " + nameForSR);
            ci = SSA.makeMoveInstruction(ir, r, nameForSR, tt);
            criticalBlockNames.put(sr, r);
            criticalBlock.appendInstructionRespectingTerminalBranch(ci);
          } else {
            Register sr = c.source.asRegister().getRegister();
            Register nameForSR = bbNames.get(sr);
            if (nameForSR == null) nameForSR = sr;
            if (DEBUG) VM.sysWriteln("not splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            if (DEBUG) VM.sysWriteln("dest(r): " + r);
            if (DEBUG) VM.sysWriteln("sr: " + sr + ", nameForSR: " + nameForSR);
            ci = SSA.makeMoveInstruction(ir, r, nameForSR, tt);
            bbNames.put(sr, r);
            SSA.addAtEnd(ir, bb, ci, c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
          }
          // ugly hack: having already added ci; set ci to null to skip remaining code;
          ci = null;
        } else {
          throw new OptimizingCompilerException("Unexpected phi operand " +
                                                    c
                                                        .source +
                                                                " encountered during SSA teardown", true);
        }
        if (ci != null) {
          if (shouldSplitBlock) {
            if (DEBUG) VM.sysWriteln("splitting edge: " + bb + "->" + c.phi.getBasicBlock());
            BasicBlock criticalBlock = criticalBlocks.get(c.phi.getBasicBlock());
            if (criticalBlock == null) {
              criticalBlock = IRTools.makeBlockOnEdge(bb, c.phi.getBasicBlock(), ir);
              if (c.phi.getBasicBlock().getInfrequent()) {
                criticalBlock.setInfrequent();
              }
              splitSomeBlock = true;
              criticalBlocks.put(c.phi.getBasicBlock(), criticalBlock);
              HashMap<Register, Register> newNames = new HashMap<Register, Register>(4);
              currentNames.put(criticalBlock, newNames);
            }
            criticalBlock.appendInstructionRespectingTerminalBranch(ci);
          } else {
            SSA.addAtEnd(ir, bb, ci, c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
          }
        }

        // source has been copied and so can now be overwritten
        // safely.  so now add any copies _to_ the source of the
        // current copy to the work list.
        if (c.source instanceof RegisterOperand) {
          Register saved = c.source.asRegister().getRegister();
          Iterator<Copy> copySetIter = copySet.iterator();
          while (copySetIter.hasNext()) {
            Copy cc = copySetIter.next();
            if (cc.destination.asRegister().getRegister() == saved) {
              workList.add(0, cc);
              copySetIter.remove();
            }
          }
        }
      }
      // an empty work list with work remaining in the copy set
      // implies a cycle in the dependencies amongst copies.  deal
      // with this: break the cycle by copying the destination
      // of an arbitrary member of the copy set into a temporary.
      // this destination has thus been saved, and can now be
      // safely overwritten.  so, add that copy to the work list.
      if (!copySet.isEmpty()) {
        Copy c = copySet.remove(0);
        Register tt = ir.regpool.getReg(c.destination.getRegister());
        SSA.addAtEnd(ir,
                         bb,
                         SSA.makeMoveInstruction(ir, tt, c.destination.getRegister(), c.destination.getType()),
                         c.phi.getBasicBlock().isExceptionHandlerBasicBlock());
        bbNames.put(c.destination.getRegister(), tt);
        workList.add(0, c);
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
  private void insertCopies(BasicBlock bb, DominatorTree dom, LiveAnalysis live) {
    // add copies required in this block to remove phis.
    // (record renaming required by simultaneous liveness in global tables)
    scheduleCopies(bb, live);

    // insert copies in control children
    Enumeration<TreeNode> children = dom.getChildren(bb);
    while (children.hasMoreElements()) {
      BasicBlock c = ((DominatorTreeNode) children.nextElement()).getBlock();
      insertCopies(c, dom, live);
    }
  }

  /**
   * Main driver to translate an IR out of SSA form.
   *
   * @param ir the IR in SSA form
   */
  public void translateFromSSA(IR ir) {
    // 0. Deal with guards (validation registers)
    unSSAGuards(ir);

    // 1. re-compute dominator tree in case of control flow changes
    LTDominators.perform(ir, true, true);
    DominatorTree dom = new DominatorTree(ir, true);

    // 1.5 Perform Sreedhar's naive translation from TSSA to CSSA
    //if (ir.options.UNROLL_LOG == 0) normalizeSSA(ir);

    // 2. compute liveness
    LiveAnalysis live = new LiveAnalysis(false,  // don't create GC maps
                                                 true,   // skip (final) local propagation step
                                                 // of live analysis
                                                 false,  // don't store information at handlers
                                                 false); // don't skip guards

    live.perform(ir);
    // 3. initialization
    VariableStacks s = new VariableStacks();
    // 4. convert phi nodes into copies
    BasicBlock b = ((DominatorTreeNode) dom.getRoot()).getBlock();
    insertCopies(b, dom, live);
    // 5. If necessary, recompute dominators to account for new control flow.
    if (splitSomeBlock) {
      LTDominators.perform(ir, true, true);
      dom = new DominatorTree(ir, true);
    }
    // 6. compensate for copies required by simultaneous liveness
    performRename(b, dom, s);
    // 7. phis are now redundant
    removeAllPhis(ir);
  }

  /**
   * Remove all phi instructions from the IR.
   *
   * @param ir the governing IR
   */
  static void removeAllPhis(IR ir) {
    for (Instruction s = ir.firstInstructionInCodeOrder(),
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
  private void unSSAGuards(IR ir) {
    // 0. initialization
    unSSAGuardsInit(ir);
    // 1. Determine target registers
    unSSAGuardsDetermineReg(ir);
    // 2. Rename targets and remove Phis
    unSSAGuardsFinalize(ir);
  }

  Instruction guardPhis = null;

  /**
   * Initialization for removal of guard phis.
   */
  private void unSSAGuardsInit(IR ir) {
    guardPhis = null;
    Enumeration<Instruction> e = ir.forwardInstrEnumerator();

    // visit all instructions, looking for guard phis

    while (e.hasMoreElements()) {
      Instruction inst = e.nextElement();
      if (!Phi.conforms(inst)) continue;
      Operand res = Phi.getResult(inst);
      if (!(res instanceof RegisterOperand)) continue;
      Register r = res.asRegister().getRegister();
      if (!r.isValidation()) continue;

      // force all operands of Phis into registers.

      inst.scratchObject = guardPhis;
      guardPhis = inst;

      int values = Phi.getNumberOfValues(inst);
      for (int i = 0; i < values; ++i) {
        Operand op = Phi.getValue(inst, i);
        if (!(op instanceof RegisterOperand)) {
          if (op instanceof TrueGuardOperand) {
            BasicBlock bb = Phi.getPred(inst, i).block;
            Instruction move = Move.create(GUARD_MOVE, res.asRegister().copyD2D(), new TrueGuardOperand());
            move.position = ir.gc.inlineSequence;
            move.bcIndex = SSA_SYNTH_BCI;
            bb.appendInstructionRespectingTerminalBranchOrPEI(move);
          } else if (op instanceof UnreachableOperand) {
            // do nothing
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          }
        }
      }
    }

    // visit all guard registers, init union/find
    for (Register r = ir.regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
      if (!r.isValidation()) continue;
      r.scratch = 1;
      r.scratchObject = r;
    }
  }

  /**
   * Determine target register for guard phi operands
   */
  private void unSSAGuardsDetermineReg(IR ir) {
    Instruction inst = guardPhis;
    while (inst != null) {
      Register r = Phi.getResult(inst).asRegister().getRegister();
      int values = Phi.getNumberOfValues(inst);
      for (int i = 0; i < values; ++i) {
        Operand op = Phi.getValue(inst, i);
        if (op instanceof RegisterOperand) {
          guardUnion(op.asRegister().getRegister(), r);
        } else {
          if (VM.VerifyAssertions) {
            VM._assert(op instanceof TrueGuardOperand || op instanceof UnreachableOperand);
          }
        }
      }
      inst = (Instruction) inst.scratchObject;
    }
  }

  /**
   * Rename registers and delete Phis.
   */
  private void unSSAGuardsFinalize(IR ir) {
    DefUse.computeDU(ir);
    for (Register r = ir.regpool.getFirstSymbolicRegister(); r != null; r = r.getNext()) {
      if (!r.isValidation()) continue;
      Register nreg = guardFind(r);
      Enumeration<RegisterOperand> uses = DefUse.uses(r);
      while (uses.hasMoreElements()) {
        RegisterOperand use = uses.nextElement();
        use.setRegister(nreg);
      }
      Enumeration<RegisterOperand> defs = DefUse.defs(r);
      while (defs.hasMoreElements()) {
        RegisterOperand def = defs.nextElement();
        def.setRegister(nreg);
      }
    }
    Instruction inst = guardPhis;
    while (inst != null) {
      inst.remove();
      inst = (Instruction) inst.scratchObject;
    }
  }

  /**
   * union step of union/find for guard registers during unSSA
   */
  private Register guardUnion(Register from, Register to) {
    Register a = guardFind(from);
    Register b = guardFind(to);
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
  private Register guardFind(Register r) {
    Register start = r;
    if (VM.VerifyAssertions) VM._assert(r.scratchObject != null);
    while (r.scratchObject != r) r = (Register) r.scratchObject;
    while (start.scratchObject != r) {
      start.scratchObject = r;
      start = (Register) start.scratchObject;
    }
    return r;
  }

  /**
   * Avoid potential lost copy and other associated problems by
   * Sreedhar's naive translation from TSSA to CSSA. Guards are rather
   * trivial to un-SSA so they have already been removed from the IR.
   * This algorithm is very wasteful of registers so needs good
   * coalescing.
   * @param ir the IR to work upon
   */
  @SuppressWarnings("unused") // NB this was an aborted attempt to fix a bug in leave SSA
  private static void normalizeSSA(IR ir) {
    for (Instruction s = ir.firstInstructionInCodeOrder(),
        sentinel = ir.lastInstructionInCodeOrder(),
        nextInstr = null; s != sentinel; s = nextInstr) {
      // cache so we don't process inserted instructions
      nextInstr = s.nextInstructionInCodeOrder();
      if (Phi.conforms(s) && !s.getBasicBlock().isExceptionHandlerBasicBlock()) {
        // We ignore exception handler BBs as they cause problems when inserting copies
        if (DEBUG) VM.sysWriteln("Processing " + s + " of basic block " + s.getBasicBlock());
        // Does the phi instruction have an unreachable operand?
        boolean hasUnreachable = false;
        // 1. Naively copy source operands into predecessor blocks
        for (int index = 0; index < Phi.getNumberOfPreds(s); index++) {
          Operand op = Phi.getValue(s, index);
          if (op.isRegister()) {
            // Get rval
            Register rval = op.asRegister().getRegister();
            if (rval.isValidation()) {
              continue; // ignore guards
            } else {
              // Make rval'
              Register rvalPrime = ir.regpool.getReg(rval);
              // Make copy instruction
              Instruction copy = SSA.makeMoveInstruction(ir, rvalPrime, rval, op.getType());
              // Insert a copy of rval to rval' in predBlock
              BasicBlock pred = Phi.getPred(s, index).block;
              pred.appendInstructionRespectingTerminalBranch(copy);
              if (DEBUG) VM.sysWriteln("Inserted rval copy of " + copy + " into basic block " + pred);
              // Rename rval to rval' in phi instruction
              op.asRegister().setRegister(rvalPrime);
            }
          } else if (op instanceof UnreachableOperand) {
            hasUnreachable = true;
          }
        }
        // 2. Naively copy the result if there were no unreachable operands
        if (!hasUnreachable) {
          Operand op = Phi.getResult(s);
          if (!op.isRegister()) {
            // ignore heap operands
          } else {
            // Get lval
            Register lval = op.asRegister().getRegister();
            // Make lval'
            Register lvalPrime = ir.regpool.getReg(lval);
            // Make copy instruction
            Instruction copy = SSA.makeMoveInstruction(ir, lval, lvalPrime, op.getType());
            // Insert a copy of lval' to lval after phi instruction
            s.insertAfter(copy);
            // Rename lval to lval' in phi instruction
            op.asRegister().setRegister(lvalPrime);
            if (DEBUG) VM.sysWriteln("Inserted lval copy of " + copy + " after " + s);
          }
        }
      }
    }
  }
}
