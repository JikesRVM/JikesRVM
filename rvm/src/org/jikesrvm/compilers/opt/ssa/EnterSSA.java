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
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.PHI;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_BEGIN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_END_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.controlflow.DominanceFrontier;
import org.jikesrvm.compilers.opt.controlflow.DominatorTreeNode;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Label;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Prepare;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.operand.BasicBlockOperand;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.UnreachableOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.liveness.LiveSet;
import org.jikesrvm.compilers.opt.util.TreeNode;
import org.jikesrvm.util.BitVector;
import org.jikesrvm.util.Pair;

/**
 * This compiler phase constructs SSA form.
 *
 * <p> This module constructs SSA according to the SSA properties defined
 * in </code> IR.desiredSSAOptions </code>.  See <code> SSAOptions
 * </code> for more details on supported options for SSA construction.
 *
 * <p>The SSA construction algorithm is the classic dominance frontier
 * based algorithm from Cytron et al.'s 1991 TOPLAS paper.
 *
 * <p> See our SAS 2000 paper
 * <a href="http://www.research.ibm.com/jalapeno/publication.html#sas00">
 *  Unified Analysis of Arrays and Object References in Strongly Typed
 *  Languages </a> for an overview of Array SSA form.  More implementation
 *  details are documented in {@link SSA <code> SSA.java</code>}.
 *
 * @see SSA
 * @see SSAOptions
 * @see org.jikesrvm.compilers.opt.controlflow.LTDominators
 */
public class EnterSSA extends CompilerPhase {
  /**
   * flag to optionally print verbose debugging messages
   */
  static final boolean DEBUG = false;

  /**
   * The governing IR
   */
  private IR ir;

  /**
   * Cached results of liveness analysis
   */
  private LiveAnalysis live;

  /**
   * A set of registers determined to span basic blocks
   */
  private HashSet<Register> nonLocalRegisters;

  /**
   * The set of scalar phi functions inserted
   */
  private final HashSet<Instruction> scalarPhis = new HashSet<Instruction>();

  /**
   * For each basic block, the number of predecessors that have been
   * processed.
   */
  private int[] numPredProcessed;

  /**
   * Should this phase be performed under a guiding set of compiler
   * options?
   *
   * @param options the controlling compiler options
   * @return true iff SSA is enabled under the options
   */
  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.SSA;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor = getCompilerPhaseConstructor(EnterSSA.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Return a string identifying this compiler phase.
   * @return "Enter SSA"
   */
  @Override
  public final String getName() {
    return "Enter SSA";
  }

  /**
   * Should the IR be printed either before or after performing this phase?
   *
   * @param options controlling compiler options
   * @param before true iff querying before the phase
   * @return true or false
   */
  @Override
  public final boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Construct SSA form to satisfy the desired options in ir.desiredSSAOptions.
   * This module is lazy; if the actual SSA options satisfy the desired options,
   * then do nothing.
   */
  @Override
  public final void perform(IR ir) {

    // Exit if we don't have to recompute SSA.
    if (ir.desiredSSAOptions.getAbort()) return;
    if (ir.actualSSAOptions != null) {
      if (ir.actualSSAOptions.satisfies(ir.desiredSSAOptions)) {
        return;
      }
    }
    this.ir = ir;
    boolean scalarsOnly = ir.desiredSSAOptions.getScalarsOnly();
    boolean backwards = ir.desiredSSAOptions.getBackwards();
    Set<Object> heapTypes = ir.desiredSSAOptions.getHeapTypes();
    boolean insertUsePhis = ir.desiredSSAOptions.getInsertUsePhis();
    boolean insertPEIDeps = ir.desiredSSAOptions.getInsertPEIDeps();
    boolean excludeGuards = ir.desiredSSAOptions.getExcludeGuards();

    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed) {
      throw new OptimizingCompilerException("Need dominators for SSA");
    }
    // reset SSA dictionary information
    ir.HIRInfo.dictionary = new SSADictionary(null, true, false, ir);
    // initialize as needed for SSA options
    prepare();
    // work around problem with PEI-generated values and handlers
    if (true /* ir.options.UNFACTOR_FOR_SSA */) {
      patchPEIgeneratedValues();
    }
    if (ir.options.PRINT_SSA) {
      SSA.printInstructions(ir);
    }
    computeSSA(ir, scalarsOnly, backwards, heapTypes, insertUsePhis, insertPEIDeps, excludeGuards);
    // reset the SSAOptions
    ir.actualSSAOptions = new SSAOptions();
    ir.actualSSAOptions.setScalarsOnly(scalarsOnly);
    ir.actualSSAOptions.setBackwards(backwards);
    ir.actualSSAOptions.setHeapTypes(heapTypes);
    ir.actualSSAOptions.setInsertUsePhis(insertUsePhis);
    ir.actualSSAOptions.setInsertPEIDeps(insertPEIDeps);
    ir.actualSSAOptions.setExcludeGuards(excludeGuards);
    ir.actualSSAOptions.setScalarValid(true);
    ir.actualSSAOptions.setHeapValid(!scalarsOnly);
  }

  /**
   * Perform some calculations to prepare for SSA construction.
   * <ul>
   * <li> If using pruned SSA, compute liveness.
   * <li> If using semi-pruned SSA, compute non-local registers
   * </ul>
   */
  private void prepare() {
    live = new LiveAnalysis(false, // don't create GC maps
                                true,  // skip (final) local propagation step
                                // of live analysis
                                false, // don't store live at handlers
                                ir.desiredSSAOptions.getExcludeGuards());
    // don't skip guards
    live.perform(ir);
  }

  /**
   * Pass through the IR and calculate which registers are not
   * local to a basic block.  Store the result in the <code> nonLocalRegisters
   * </code> field.
   */
  @SuppressWarnings("unused")
  private void computeNonLocals() {
    nonLocalRegisters = new HashSet<Register>(20);
    Enumeration<BasicBlock> blocks = ir.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      HashSet<Register> killed = new HashSet<Register>(5);
      BasicBlock block = blocks.nextElement();
      Enumeration<Instruction> instrs = block.forwardRealInstrEnumerator();
      while (instrs.hasMoreElements()) {
        Instruction instr = instrs.nextElement();
        Enumeration<Operand> uses = instr.getUses();
        while (uses.hasMoreElements()) {
          Operand op = uses.nextElement();
          if (op instanceof RegisterOperand) {
            if (!killed.contains(op.asRegister().getRegister())) {
              nonLocalRegisters.add(op.asRegister().getRegister());
            }
          }
        }
        Enumeration<Operand> defs = instr.getDefs();
        while (defs.hasMoreElements()) {
          Operand op = defs.nextElement();
          if (op instanceof RegisterOperand) {
            killed.add(op.asRegister().getRegister());
          }
        }
      }
    }
  }

  /**
   * Work around some problems with PEI-generated values and
   * handlers.  Namely, if a PEI has a return value, rename the
   * result register before and after the PEI in order to reflect the fact
   * that the PEI may not actually assign the result register.
   */
  private void patchPEIgeneratedValues() {
    // this only applies if there are exception handlers
    if (!ir.hasReachableExceptionHandlers()) return;

    HashSet<Pair<BasicBlock, RegisterOperand>> needed = new HashSet<Pair<BasicBlock,RegisterOperand>>(4);
    Enumeration<BasicBlock> blocks = ir.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.nextElement();
      if (block.getExceptionalOut().hasMoreElements()) {
        Instruction pei = block.lastRealInstruction();
        if (pei != null && pei.isPEI() && ResultCarrier.conforms(pei)) {
          boolean copyNeeded = false;
          RegisterOperand v = ResultCarrier.getResult(pei);
          // void calls and the like... :(
          if (v != null) {
            Register orig = v.getRegister();
            {
              Enumeration<BasicBlock> out = block.getApplicableExceptionalOut(pei);
              while (out.hasMoreElements()) {
                BasicBlock exp = out.nextElement();
                LiveSet explive = live.getLiveInfo(exp).getIn();
                if (explive.contains(orig)) {
                  copyNeeded = true;
                  break;
                }
              }
            }
            if (copyNeeded) {
              Enumeration<BasicBlock> out = block.getApplicableExceptionalOut(pei);
              while (out.hasMoreElements()) {
                BasicBlock exp = out.nextElement();
                needed.add(new Pair<BasicBlock, RegisterOperand>(exp, v));
              }
            }
          }
        }
      }
    }
    // having determine where copies should be inserted, now insert them.
    if (!needed.isEmpty()) {
      for (Pair<BasicBlock, RegisterOperand> copy : needed) {
        BasicBlock inBlock = copy.first;
        RegisterOperand registerOp = copy.second;
        TypeReference type = registerOp.getType();
        Register register = registerOp.getRegister();
        Register temp = ir.regpool.getReg(register);
        inBlock.prependInstruction(SSA.makeMoveInstruction(ir, register, temp, type));
        Enumeration<BasicBlock> outBlocks = inBlock.getIn();
        while (outBlocks.hasMoreElements()) {
          BasicBlock outBlock = outBlocks.nextElement();
          Instruction x = SSA.makeMoveInstruction(ir, temp, register, type);
          SSA.addAtEnd(ir, outBlock, x, true);
        }
      }
      // Recompute liveness information.  You might be tempted to incrementally
      // update it, but it's tricky, so resist.....do the obvious, but easy thing!
      prepare();
    }
  }

  /**
   * Calculate SSA form for an IR.  This routine holds the guts of the
   * transformation.
   *
   * @param ir the governing IR
   * @param scalarsOnly should we compute SSA only for scalar variables?
   * @param backwards If this is true, then every statement that
   * can leave the procedure is considered to <em> use </em> every heap
   * variable.  This option is useful for backwards analyses such as dead
   * store elimination.
   * @param heapTypes If this variable is non-null, then heap array SSA
   * form will restrict itself to this set of types. If this is null, build
   * heap array SSA for all types.
   * @param insertUsePhis Should we insert uphi functions for heap array
   * SSA? ie., should we create a new name for each heap array at every use
   * of the heap array? This option is useful for some analyses, such as
   * our redundant load elimination algorithm.
   * @param insertPEIDeps Should we model exceptions with an explicit
   * heap variable for exception state? This option is useful for global
   * code placement algorithms.
   * @param excludeGuards Should we exclude guard registers from SSA?
   */
  private void computeSSA(IR ir, boolean scalarsOnly, boolean backwards, Set<Object> heapTypes,
                          boolean insertUsePhis, boolean insertPEIDeps, boolean excludeGuards) {
    // if reads Kill.  model this with uphis.
    if (ir.options.READS_KILL) insertUsePhis = true;

    // reset Array SSA information
    if (!scalarsOnly) {
      ir.HIRInfo.dictionary = new SSADictionary(heapTypes, insertUsePhis, insertPEIDeps, ir);
    } else {
      ir.HIRInfo.dictionary = new SSADictionary(null, insertUsePhis, insertPEIDeps, ir);
    }
    if (DEBUG) System.out.println("Computing register lists...");

    // 1. re-compute the flow-insensitive isSSA flag for each register
    DefUse.computeDU(ir);
    DefUse.recomputeSSA(ir);

    // 2. set up a mapping from symbolic register number to the
    //  register.  !!TODO: factor this out and make it more
    //  useful.
    Register[] symbolicRegisters = getSymbolicRegisters();

    // 3. walk through the IR, and set up BitVectors representing the defs
    //    for each symbolic register (more efficient than using register
    //  lists)
    if (DEBUG) System.out.println("Find defs for each register...");
    BitVector[] defSets = getDefSets();

    // 4. Insert phi functions for scalars
    if (DEBUG) System.out.println("Insert phi functions...");
    insertPhiFunctions(ir, defSets, symbolicRegisters, excludeGuards);

    // 5. Insert heap variables into the Array SSA form
    if (!scalarsOnly) {
      insertHeapVariables(ir, backwards);
    }
    if (DEBUG) System.out.println("Before renaming...");
    if (DEBUG) SSA.printInstructions(ir);
    if (DEBUG) System.out.println("Renaming...");
    renameSymbolicRegisters(symbolicRegisters);

    if (!scalarsOnly) {
      renameHeapVariables(ir);
    }
    if (DEBUG) System.out.println("SSA done.");
    if (ir.options.PRINT_SSA) SSA.printInstructions(ir);
  }

  /**
   * Insert heap variables needed for Array SSA form.
   *
   * @param ir the governing IR
   * @param backwards if this is true, every statement that can leave the
   *                   procedure <em> uses </em> every heap variable.
   *                   This option is useful for backwards analyses
   */
  private void insertHeapVariables(IR ir, boolean backwards) {
    // insert dphi functions where needed
    registerHeapVariables(ir);

    // insert heap defs and uses for CALL instructions
    registerCalls(ir);

    // register heap uses for instructions that can leave the procedure
    if (backwards) {
      registerExits(ir);
    }

    // insert phi funcions where needed
    insertHeapPhiFunctions(ir);
  }

  /**
   * Register every instruction that can leave this method with the
   * implicit heap array SSA look aside structure.
   *
   * @param ir the governing IR
   */
  private void registerExits(IR ir) {
    SSADictionary dictionary = ir.HIRInfo.dictionary;
    for (Enumeration<BasicBlock> bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      BasicBlock b = bbe.nextElement();
      for (Enumeration<Instruction> e = b.forwardInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        // we already handled calls in a previous pass.
        if (Call.conforms(s)) {
          continue;
        }
        if (Return.conforms(s) || Athrow.conforms(s) || s.isPEI()) {
          dictionary.registerExit(s, b);
        }
      }
    }
  }

  /**
   * Register every CALL instruction in this method with the
   * implicit heap array SSA look aside structure.
   * Namely, mark that this instruction defs and uses <em> every </em>
   * type of heap variable in the IR's SSA dictionary.
   *
   * @param ir the governing IR
   */
  private void registerCalls(IR ir) {
    SSADictionary dictionary = ir.HIRInfo.dictionary;
    for (Enumeration<BasicBlock> bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      BasicBlock b = bbe.nextElement();
      for (Enumeration<Instruction> e = b.forwardInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        boolean isSynch = (s.operator() == READ_CEILING) || (s.operator() == WRITE_FLOOR) || (s.operator() == FENCE);
        if (isSynch ||
            Call.conforms(s) ||
            MonitorOp.conforms(s) ||
            Prepare.conforms(s) ||
            Attempt.conforms(s) ||
            CacheOp.conforms(s) ||
            s.isDynamicLinkingPoint()) {
          dictionary.registerUnknown(s, b);
        }
      }
    }
  }

  /**
   * Register every instruction in this method with the
   * implicit heap array SSA lookaside structure.
   *
   * @param ir the governing IR
   */
  private void registerHeapVariables(IR ir) {
    SSADictionary dictionary = ir.HIRInfo.dictionary;
    for (Enumeration<BasicBlock> bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      BasicBlock b = bbe.nextElement();
      for (Enumeration<Instruction> e = b.forwardInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        if (s.isImplicitLoad() ||
            s.isImplicitStore() ||
            s.isAllocation() ||
            Phi.conforms(s) ||
            s.isPEI() ||
            Label.conforms(s) ||
            BBend.conforms(s) ||
            s.operator.opcode == UNINT_BEGIN_opcode ||
            s.operator.opcode == UNINT_END_opcode) {
          dictionary.registerInstruction(s, b);
        }
      }
    }
  }

  /**
   * Insert phi functions for heap array SSA heap variables.
   *
   * @param ir the governing IR
   */
  private void insertHeapPhiFunctions(IR ir) {
    Iterator<HeapVariable<Object>> e = ir.HIRInfo.dictionary.getHeapVariables();
    while (e.hasNext()) {
      HeapVariable<Object> H = e.next();

      if (DEBUG) System.out.println("Inserting phis for Heap " + H);
      if (DEBUG) System.out.println("Start iterated frontier...");

      BitVector defH = H.getDefBlocks();
      if (DEBUG) System.out.println(H + " DEFINED IN " + defH);

      BitVector needsPhi = DominanceFrontier.
          getIteratedDominanceFrontier(ir, defH);
      if (DEBUG) System.out.println(H + " NEEDS PHI " + needsPhi);

      if (DEBUG) System.out.println("Done.");
      for (int b = 0; b < needsPhi.length(); b++) {
        if (needsPhi.get(b)) {
          BasicBlock bb = ir.getBasicBlock(b);
          ir.HIRInfo.dictionary.createHeapPhiInstruction(bb, H);
        }
      }
    }
  }

  /**
   * Calculate the set of blocks that contain defs for each
   *    symbolic register in an IR.  <em> Note: </em> This routine skips
   *    registers marked  already having a single static
   *    definition, physical registers, and guard registeres.
   *
   * @return an array of BitVectors, where element <em>i</em> represents the
   *    basic blocks that contain defs for symbolic register <em>i</em>
   */
  private BitVector[] getDefSets() {
    int nBlocks = ir.getMaxBasicBlockNumber();
    BitVector[] result = new BitVector[ir.getNumberOfSymbolicRegisters()];

    for (int i = 0; i < result.length; i++) {
      result[i] = new BitVector(nBlocks + 1);
    }

    // loop over each basic block
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock bb = e.nextElement();
      int bbNumber = bb.getNumber();
      // visit each instruction in the basic block
      for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
        Instruction s = ie.nextElement();
        // record each def in the instruction
        // skip SSA defs
        for (int j = 0; j < s.getNumberOfDefs(); j++) {
          Operand operand = s.getOperand(j);
          if (operand == null) continue;
          if (!operand.isRegister()) continue;
          if (operand.asRegister().getRegister().isSSA()) continue;
          if (operand.asRegister().getRegister().isPhysical()) continue;

          int reg = operand.asRegister().getRegister().getNumber();
          result[reg].set(bbNumber);
        }
      }
    }
    return result;
  }

  /**
   * Insert the necessary phi functions into an IR.
   * <p> Algorithm:
   * <p>For register r, let S be the set of all blocks that
   *    contain defs of r.  Let D be the iterated dominance frontier
   *    of S.  Each block in D needs a phi-function for r.
   *
   * <p> Special Java case: if node N dominates all defs of r, then N
   *                      does not need a phi-function for r
   *
   * @param ir the governing IR
   * @param defs defs[i] represents the basic blocks that define
   *            symbolic register i.
   * @param symbolics symbolics[i] is symbolic register number i
   */
  private void insertPhiFunctions(IR ir, BitVector[] defs, Register[] symbolics, boolean excludeGuards) {
    for (int r = 0; r < defs.length; r++) {
      if (symbolics[r] == null) continue;
      if (symbolics[r].isSSA()) continue;
      if (symbolics[r].isPhysical()) continue;
      if (excludeGuards && symbolics[r].isValidation()) continue;
      if (DEBUG) System.out.println("Inserting phis for register " + r);
      if (DEBUG) System.out.println("Start iterated frontier...");
      BitVector needsPhi = DominanceFrontier.getIteratedDominanceFrontier(ir, defs[r]);
      removePhisThatDominateAllDefs(needsPhi, ir, defs[r]);
      if (DEBUG) System.out.println("Done.");

      for (int b = 0; b < needsPhi.length(); b++) {
        if (needsPhi.get(b)) {
          BasicBlock bb = ir.getBasicBlock(b);
          if (live.getLiveInfo(bb).getIn().contains(symbolics[r])) {
            insertPhi(bb, symbolics[r]);
          }
        }
      }
    }
  }

  /**
   * If node N dominates all defs of a register r, then N does
   * not need a phi function for r; this function removes such
   * nodes N from a Bit Set.
   *
   * @param needsPhi representation of set of nodes that
   *                need phi functions for a register r
   * @param ir the governing IR
   * @param defs set of nodes that define register r
   */
  private void removePhisThatDominateAllDefs(BitVector needsPhi, IR ir, BitVector defs) {
    for (int i = 0; i < needsPhi.length(); i++) {
      if (!needsPhi.get(i)) {
        continue;
      }
      if (ir.HIRInfo.dominatorTree.dominates(i, defs)) {
        needsPhi.clear(i);
      }
    }
  }

  /**
   * Insert a phi function for a symbolic register at the head
   * of a basic block.
   *
   * @param bb the basic block
   * @param r the symbolic register that needs a phi function
   */
  private void insertPhi(BasicBlock bb, Register r) {
    Instruction s = makePhiInstruction(r, bb);
    bb.firstInstruction().insertAfter(s);
    scalarPhis.add(s);
  }

  /**
   * Create a phi-function instruction
   *
   * @param r the symbolic register
   * @param bb the basic block holding the new phi function
   * @return the instruction r = PHI null,null,..,null
   */
  private Instruction makePhiInstruction(Register r, BasicBlock bb) {
    int n = bb.getNumberOfIn();
    Enumeration<BasicBlock> in = bb.getIn();
    TypeReference type = null;
    Instruction s = Phi.create(PHI, new RegisterOperand(r, type), n);
    for (int i = 0; i < n; i++) {
      RegisterOperand junk = new RegisterOperand(r, type);
      Phi.setValue(s, i, junk);
      BasicBlock pred = in.nextElement();
      Phi.setPred(s, i, new BasicBlockOperand(pred));
    }
    s.position = ir.gc.inlineSequence;
    s.bcIndex = SSA_SYNTH_BCI;
    return s;
  }

  /**
   * Set up a mapping from symbolic register number to the register.
   * <p> TODO: put this functionality elsewhere.
   *
   * @return a mapping
   */
  private Register[] getSymbolicRegisters() {
    Register[] map = new Register[ir.getNumberOfSymbolicRegisters()];
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      int number = reg.getNumber();
      map[number] = reg;
    }
    return map;
  }

  /**
   * Rename the symbolic registers so that each register has only one
   * definition.
   *
   * <p><em> Note </em>: call this after phi functions have been inserted.
   * <p> <b> Algorithm:</b> from Cytron et. al 91
   * <pre>
   *  call search(entry)
   *
   *  search(X):
   *  for each statement A in X do
   *     if A is not-phi
   *       for each r in RHS(A) do
   *            if !r.isSSA, replace r with TOP(S(r))
   *       done
   *     fi
   *    for each r in LHS(A) do
   *            if !r.isSSA
   *                r2 = new temp register
   *                push r2 onto S(r)
   *                replace r in A by r2
   *            fi
   *    done
   *  done (end of first loop)
   *  for each Y in succ(X) do
   *      j <- whichPred(Y,X)
   *      for each phi-function F in Y do
   *       replace the j-th operand (r) in RHS(F) with TOP(S(r))
   *     done
   *  done (end of second loop)
   *  for each Y in Children(X) do
   *    call search(Y)
   *  done (end of third loop)
   *  for each assignment A in X do
   *     for each r in LHS(A) do
   *      pop(S(r))
   *   done
   *  done (end of fourth loop)
   *  end
   * <pre>
   *
   * @param symbolicRegisters mapping from integer to symbolic registers
   */
  private void renameSymbolicRegisters(Register[] symbolicRegisters) {
    int n = ir.getNumberOfSymbolicRegisters();
    @SuppressWarnings("unchecked") // the old covariant array-type problem
        Stack<RegisterOperand>[] S = new Stack[n + 1];
    for (int i = 0; i < S.length; i++) {
      S[i] = new Stack<RegisterOperand>();
      // populate the Stacks with initial names for
      // each parameter, and push "null" for other symbolic registers
      if (i >= symbolicRegisters.length) continue;
      //Register r = symbolicRegisters[i];
      // If a register's name is "null", that means the
      // register has not yet been defined.
      S[i].push(null);
    }
    BasicBlock entry = ir.cfg.entry();
    DefUse.clearDU(ir);
    numPredProcessed = new int[ir.getMaxBasicBlockNumber()];
    search(entry, S);
    DefUse.recomputeSSA(ir);
    rectifyPhiTypes();
  }

  /**
   * This routine is the guts of the SSA construction phase for scalars.  See
   * renameSymbolicRegisters for more details.
   *
   * @param X basic block to search dominator tree from
   * @param S stack of names for each register
   */
  private void search(BasicBlock X, Stack<RegisterOperand>[] S) {
    if (DEBUG) System.out.println("SEARCH " + X);
    for (Enumeration<Instruction> ie = X.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction A = ie.nextElement();
      if (A.operator() != PHI) {
        // replace each use
        for (int u = A.getNumberOfDefs(); u < A.getNumberOfOperands(); u++) {
          Operand op = A.getOperand(u);
          if (op instanceof RegisterOperand) {
            RegisterOperand rop = (RegisterOperand) op;
            Register r1 = rop.getRegister();
            if (r1.isSSA()) continue;
            if (r1.isPhysical()) continue;
            RegisterOperand r2 = S[r1.getNumber()].peek();
            if (DEBUG) System.out.println("REPLACE NORMAL USE " + r1 + " with " + r2);
            if (r2 != null) {
              rop.setRegister(r2.getRegister());
              DefUse.recordUse(rop);
            }
          }
        }
      }
      // replace each def
      for (int d = 0; d < A.getNumberOfDefs(); d++) {
        Operand op = A.getOperand(d);
        if (op instanceof RegisterOperand) {
          RegisterOperand rop = (RegisterOperand) op;
          Register r1 = rop.getRegister();
          if (r1.isSSA()) continue;
          if (r1.isPhysical()) continue;
          Register r2 = ir.regpool.getReg(r1);
          if (DEBUG) System.out.println("PUSH " + r2 + " FOR " + r1 + " BECAUSE " + A);
          S[r1.getNumber()].push(new RegisterOperand(r2, rop.getType()));
          rop.setRegister(r2);
          r2.scratchObject = r1;
        }
      }
    } // end of first loop

    if (DEBUG) System.out.println("SEARCH (second loop) " + X);
    for (Enumeration<BasicBlock> y = X.getOut(); y.hasMoreElements();) {
      BasicBlock Y = y.nextElement();
      if (DEBUG) System.out.println(" Successor: " + Y);
      int j = numPredProcessed[Y.getNumber()]++;
      if (Y.isExit()) continue;
      Instruction s = Y.firstRealInstruction();
      if (s == null) continue;
      // replace use USE in each PHI instruction
      if (DEBUG) System.out.println(" Predecessor: " + j);
      while (s.operator() == PHI) {
        Operand val = Phi.getValue(s, j);
        if (val.isRegister()) {
          Register r1 = ((RegisterOperand) Phi.getValue(s, j)).getRegister();
          // ignore registers already marked SSA by a previous pass
          if (!r1.isSSA()) {
            RegisterOperand r2 = S[r1.getNumber()].peek();
            if (r2 == null) {
              // in this case, the register is never defined along
              // this particular control flow path into the basic
              // block.
              Phi.setValue(s, j, new UnreachableOperand());
            } else {
              RegisterOperand rop = r2.copyRO();
              Phi.setValue(s, j, rop);
              DefUse.recordUse(rop);
            }
            Phi.setPred(s, j, new BasicBlockOperand(X));
          }
        }
        s = s.nextInstructionInCodeOrder();
      }
    } // end of second loop

    if (DEBUG) System.out.println("SEARCH (third loop) " + X);
    for (Enumeration<TreeNode> c = ir.HIRInfo.dominatorTree.getChildren(X); c.hasMoreElements();) {
      DominatorTreeNode v = (DominatorTreeNode) c.nextElement();
      search(v.getBlock(), S);
    } // end of third loop

    if (DEBUG) System.out.println("SEARCH (fourth loop) " + X);
    for (Enumeration<Instruction> a = X.forwardInstrEnumerator(); a.hasMoreElements();) {
      Instruction A = a.nextElement();
      // loop over each def
      for (int d = 0; d < A.getNumberOfDefs(); d++) {
        Operand newOp = A.getOperand(d);
        if (newOp == null) continue;
        if (!newOp.isRegister()) continue;
        Register newReg = newOp.asRegister().getRegister();
        if (newReg.isSSA()) continue;
        if (newReg.isPhysical()) continue;
        Register r1 = (Register) newReg.scratchObject;
        S[r1.getNumber()].pop();
        if (DEBUG) System.out.println("POP " + r1);
      }
    } // end of fourth loop
    if (DEBUG) System.out.println("FINISHED SEARCH " + X);
  }

  /**
   * Rename the implicit heap variables in the SSA form so that
   * each heap variable has only one definition.
   *
   * <p> Algorithm: Cytron et. al 91  (see renameSymbolicRegisters)
   *
   * @param ir the governing IR
   */
  private void renameHeapVariables(IR ir) {
    int n = ir.HIRInfo.dictionary.getNumberOfHeapVariables();
    if (n == 0) {
      return;
    }
    // we maintain a stack of names for each type of heap variable
    // stacks implements a mapping from type to Stack.
    // Example: to get the stack of names for HEAP<int> variables,
    // use stacks.get(ClassLoaderProxy.IntType);
    HashMap<Object, Stack<HeapOperand<Object>>> stacks = new HashMap<Object, Stack<HeapOperand<Object>>>(n);
    // populate the stacks variable with the initial heap variable
    // names, currently stored in the SSADictionary
    for (Iterator<HeapVariable<Object>> e = ir.HIRInfo.dictionary.getHeapVariables(); e.hasNext();) {
      HeapVariable<Object> H = e.next();
      Stack<HeapOperand<Object>> S = new Stack<HeapOperand<Object>>();
      S.push(new HeapOperand<Object>(H));
      Object heapType = H.getHeapType();
      stacks.put(heapType, S);
    }
    BasicBlock entry = ir.cfg.entry();
    numPredProcessed = new int[ir.getMaxBasicBlockNumber()];
    search2(entry, stacks);
    // registerRenamedHeapPhis(ir);
  }

  /**
   * This routine is the guts of the SSA construction phase for heap array
   * SSA.  The renaming algorithm is analagous to the algorithm for
   * scalars See <code> renameSymbolicRegisters </code> for more details.
   *
   * @param X the current basic block being traversed
   * @param stacks a structure holding the current names for each heap
   * variable
   * used and defined by each instruction.
   */
  private void search2(BasicBlock X, HashMap<Object, Stack<HeapOperand<Object>>> stacks) {
    if (DEBUG) System.out.println("SEARCH2 " + X);
    SSADictionary dictionary = ir.HIRInfo.dictionary;
    for (Enumeration<Instruction> ie = dictionary.getAllInstructions(X); ie.hasMoreElements();) {
      Instruction A = ie.nextElement();
      if (!dictionary.usesHeapVariable(A) && !dictionary.defsHeapVariable(A)) continue;
      if (A.operator() != PHI) {
        // replace the Heap variables USED by this instruction
        HeapOperand<Object>[] uses = dictionary.getHeapUses(A);
        if (uses != null) {
          @SuppressWarnings("unchecked")  // Generic array problem
              HeapOperand<Object>[] newUses = new HeapOperand[uses.length];
          for (int i = 0; i < uses.length; i++) {
            Stack<HeapOperand<Object>> S = stacks.get(uses[i].getHeapType());
            newUses[i] = S.peek().copy();
            if (DEBUG) {
              System.out.println("NORMAL USE PEEK " + newUses[i]);
            }
          }
          dictionary.replaceUses(A, newUses);
        }
      }
      // replace any Heap variable DEF
      if (A.operator() != PHI) {
        HeapOperand<Object>[] defs = dictionary.getHeapDefs(A);
        if (defs != null) {
          for (HeapOperand<Object> operand : dictionary.replaceDefs(A, X)) {
            Stack<HeapOperand<Object>> S = stacks.get(operand.getHeapType());
            S.push(operand);
            if (DEBUG) System.out.println("PUSH " + operand + " FOR " + operand.getHeapType());
          }
        }
      } else {
        HeapOperand<Object>[] r = dictionary.replaceDefs(A, X);
        Stack<HeapOperand<Object>> S = stacks.get(r[0].getHeapType());
        S.push(r[0]);
        if (DEBUG) System.out.println("PUSH " + r[0] + " FOR " + r[0].getHeapType());
      }
    } // end of first loop

    for (Enumeration<BasicBlock> y = X.getOut(); y.hasMoreElements();) {
      BasicBlock Y = y.nextElement();
      if (Y.isExit()) continue;
      int j = numPredProcessed[Y.getNumber()]++;
      // replace each USE in each HEAP-PHI function for Y
      for (Iterator<Instruction> hp = dictionary.getHeapPhiInstructions(Y); hp.hasNext();) {
        Instruction s = hp.next();
        @SuppressWarnings("unchecked") // Down-cast to a generic type
            HeapOperand<Object> H1 = (HeapOperand) Phi.getResult(s);
        Stack<HeapOperand<Object>> S = stacks.get(H1.getHeapType());
        HeapOperand<Object> H2 = S.peek();
        Phi.setValue(s, j, new HeapOperand<Object>(H2.getHeapVariable()));
        Phi.setPred(s, j, new BasicBlockOperand(X));
      }
    } // end of second loop

    for (Enumeration<TreeNode> c = ir.HIRInfo.dominatorTree.getChildren(X); c.hasMoreElements();) {
      DominatorTreeNode v = (DominatorTreeNode) c.nextElement();
      search2(v.getBlock(), stacks);
    } // end of third loop

    for (Enumeration<Instruction> a = dictionary.getAllInstructions(X); a.hasMoreElements();) {
      Instruction A = a.nextElement();
      if (!dictionary.usesHeapVariable(A) && !dictionary.defsHeapVariable(A)) continue;
      // retrieve the Heap Variables defined by A
      if (A.operator != PHI) {
        HeapOperand<Object>[] defs = dictionary.getHeapDefs(A);
        if (defs != null) {
          for (HeapOperand<Object> def : defs) {
            Stack<HeapOperand<Object>> S = stacks.get(def.getHeapType());
            S.pop();
            if (DEBUG) System.out.println("POP " + def.getHeapType());
          }
        }
      } else {
        @SuppressWarnings("unchecked") // Down-cast to a generic type
            HeapOperand<Object> H = (HeapOperand) Phi.getResult(A);
        Stack<HeapOperand<Object>> S = stacks.get(H.getHeapType());
        S.pop();
        if (DEBUG) System.out.println("POP " + H.getHeapType());
      }
    } // end of fourth loop
    if (DEBUG) System.out.println("END SEARCH2 " + X);
  }

  /**
   * After performing renaming on heap phi functions, this
   * routines notifies the SSA dictionary of the new names.
   * <p>
   * FIXME - this was commented out: delete it ??  RJG
   *
   * @param ir the governing IR
   */
  @SuppressWarnings({"unused", "unchecked"})
  // HeapOperand requires casts to a generic type
  private void registerRenamedHeapPhis(IR ir) {
    SSADictionary ssa = ir.HIRInfo.dictionary;
    for (Enumeration<BasicBlock> e1 = ir.getBasicBlocks(); e1.hasMoreElements();) {
      BasicBlock bb = e1.nextElement();
      for (Enumeration<Instruction> e2 = ssa.getAllInstructions(bb); e2.hasMoreElements();) {
        Instruction s = e2.nextElement();
        if (Phi.conforms(s)) {
          if (ssa.defsHeapVariable(s)) {
            int n = Phi.getNumberOfValues(s);
            HeapOperand<Object>[] uses = new HeapOperand[n];
            for (int i = 0; i < n; i++) {
              uses[i] = (HeapOperand) Phi.getValue(s, i);
            }
            ssa.replaceUses(s, uses);
          }
        }
      }
    }
  }

  /**
   * Store a copy of the Heap variables each instruction defs.
   *
   * @param ir governing IR
   * @param store place to store copies
   */
  @SuppressWarnings("unused")
  private void copyHeapDefs(IR ir, HashMap<Instruction, HeapOperand<?>[]> store) {
    SSADictionary dictionary = ir.HIRInfo.dictionary;
    for (Enumeration<BasicBlock> be = ir.forwardBlockEnumerator(); be.hasMoreElements();) {
      BasicBlock bb = be.nextElement();
      for (Enumeration<Instruction> e = dictionary.getAllInstructions(bb); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        store.put(s, ir.HIRInfo.dictionary.getHeapDefs(s));
      }
    }
  }

  /*
   * Compute type information for operands in each phi instruction.
   *
   * PRECONDITION: Def-use chains computed.
   * SIDE EFFECT: empties the scalarPhis set
   * SIDE EFFECT: bashes the Instruction scratch field.
   */
  private static final int NO_NULL_TYPE = 0;
  private static final int FOUND_NULL_TYPE = 1;

  private void rectifyPhiTypes() {
    if (DEBUG) System.out.println("Rectify phi types.");
    removeAllUnreachablePhis(scalarPhis);
    while (!scalarPhis.isEmpty()) {
      boolean didSomething = false;
      for (Iterator<Instruction> i = scalarPhis.iterator(); i.hasNext();) {
        Instruction phi = i.next();
        phi.scratch = NO_NULL_TYPE;
        if (DEBUG) System.out.println("PHI: " + phi);
        TypeReference meet = meetPhiType(phi);
        if (DEBUG) System.out.println("MEET: " + meet);
        if (meet != null) {
          didSomething = true;
          if (phi.scratch == NO_NULL_TYPE) i.remove();
          RegisterOperand result = (RegisterOperand) Phi.getResult(phi);
          result.setType(meet);
          for (Enumeration<RegisterOperand> e = DefUse.uses(result.getRegister()); e.hasMoreElements();) {
            RegisterOperand rop = e.nextElement();
            if (rop.getType() != meet) {
              rop.clearPreciseType();
              rop.setType(meet);
            }
          }
        }
      }
      if (!didSomething) {
        // iteration has bottomed out.
        return;
      }
    }
  }

  /**
   * Remove all phis that are unreachable
   */
  private void removeAllUnreachablePhis(HashSet<Instruction> scalarPhis) {
    boolean iterateAgain = false;
    do {
      iterateAgain = false;
      outer:
      for (Iterator<Instruction> i = scalarPhis.iterator(); i.hasNext();) {
        Instruction phi = i.next();
        for (int j = 0; j < Phi.getNumberOfValues(phi); j++) {
          Operand op = Phi.getValue(phi, j);
          if (!(op instanceof UnreachableOperand)) {
            continue outer;
          }
        }
        RegisterOperand result = Phi.getResult(phi).asRegister();
        i.remove();
        for (Enumeration<RegisterOperand> e = DefUse.uses(result.getRegister()); e.hasMoreElements();) {
          RegisterOperand use = e.nextElement();
          Instruction s = use.instruction;
          if (Phi.conforms(s)) {
            for (int k = 0; k < Phi.getNumberOfValues(phi); k++) {
              Operand op = Phi.getValue(phi, k);
              if (op != null && op.similar(result)) {
                Phi.setValue(phi, k, new UnreachableOperand());
                iterateAgain = true;
              }
            }
          }
        }
      }
    } while (iterateAgain);
  }

  /**
   * Remove all unreachable operands from scalar phi functions<p>
   *
   * NOT CURRENTLY USED
   */
  @SuppressWarnings("unused")
  private void removeUnreachableOperands(HashSet<Instruction> scalarPhis) {
    for (Instruction phi : scalarPhis) {
      boolean didSomething = true;
      while (didSomething) {
        didSomething = false;
        for (int j = 0; j < Phi.getNumberOfValues(phi); j++) {
          Operand v = Phi.getValue(phi, j);
          if (v instanceof UnreachableOperand) {
            // rewrite the phi instruction to remove the unreachable
            // operand
            didSomething = true;
            Instruction tmpPhi = phi.copyWithoutLinks();
            Phi.mutate(phi, PHI, Phi.getResult(tmpPhi), Phi.getNumberOfValues(phi) - 1);
            int m = 0;
            for (int k = 0; k < Phi.getNumberOfValues(phi); k++) {
              if (k == j) continue;
              Phi.setValue(phi, m, Phi.getValue(tmpPhi, k));
              Phi.setPred(phi, m, Phi.getPred(tmpPhi, k));
              m++;
            }
          }
        }
      }
    }
  }

  /**
   * Return the meet of the types on the rhs of a phi instruction
   * <p>
   * SIDE EFFECT: bashes the Instruction scratch field.
   *
   * @param s phi instruction
   */
  private static TypeReference meetPhiType(Instruction s) {

    TypeReference result = null;
    for (int i = 0; i < Phi.getNumberOfValues(s); i++) {
      Operand val = Phi.getValue(s, i);
      if (val instanceof UnreachableOperand) continue;
      TypeReference t = val.getType();
      if (t == null) {
        s.scratch = FOUND_NULL_TYPE;
      } else if (result == null) {
        result = t;
      } else {
        TypeReference meet = ClassLoaderProxy.findCommonSuperclass(result, t);
        if (meet == null) {
          // TODO: This horrific kludge should go away once we get rid of Address.toInt()
          if ((result.isIntLikeType() && (t.isReferenceType() || t.isWordLikeType())) ||
              ((result.isReferenceType() || result.isWordLikeType()) && t.isIntLikeType())) {
            meet = TypeReference.Int;
          } else if (result.isReferenceType() && t.isWordLikeType()) {
            meet = t;
          } else if (result.isWordLikeType() && t.isReferenceType()) {
            meet = result;
          }
        }
        if (VM.VerifyAssertions && meet == null) {
          String msg = result + " and " + t + " meet to null";
          VM._assert(VM.NOT_REACHED, msg);
        }
        result = meet;
      }
    }
    return result;
  }

  /**
   * Find a parameter type.
   *
   * <p> Given a register that holds a parameter, look at the register's
   * use chain to find the type of the parameter
   */
  @SuppressWarnings("unused")
  private TypeReference findParameterType(Register p) {
    RegisterOperand firstUse = p.useList;
    if (firstUse == null) {
      return null;             // parameter has no uses
    }
    return firstUse.getType();
  }
}



