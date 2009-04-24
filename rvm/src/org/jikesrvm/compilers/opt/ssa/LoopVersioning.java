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

import static org.jikesrvm.compilers.opt.driver.OptConstants.SYNTH_LOOP_VERSIONING_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SUB;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PHI;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.controlflow.AnnotatedLSTGraph;
import org.jikesrvm.compilers.opt.controlflow.AnnotatedLSTNode;
import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.controlflow.DominatorsPhase;
import org.jikesrvm.compilers.opt.controlflow.LSTGraph;
import org.jikesrvm.compilers.opt.controlflow.LTDominators;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IREnumeration;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Label;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.BasicBlockOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.util.GraphNode;

/**
 * This optimisation works from the outer most loop inward, optimising
 * loops that conform to being regular {@link
 * AnnotatedLSTNode}s. The optimisations performs the following
 * operations:
 *
 * 1) Determine the bound and null checks to be eliminated. These are
 * the ones that operate on the loop iterator. If no bound checks can
 * be eliminated, stop optimising this loop.
 *
 * 2) Determine the registers defined in the loop.
 *
 * 3) Generate phi nodes that define the original register defined by
 * the loop and use two newly created registers.
 *
 * 4) Create a version of the original loop that uses the first of the
 * newly created registers instead of the original registers.
 *
 * 5) Create a second version, this time with the result of the
 * eliminated checks set to true.
 *
 * 6) Work out what the maximum value for all the bounds checks are
 * and create branches to optimal or suboptimal loops
 *
 * 7) Fix up the phi node predecessors
 *
 * 8) Remove the unoptimized loop if its redundant
 *
 * 9) Replace register definitions in the original loop with phi
 * instructions
 *
 * 10) Compact node numbering so that CFG number of nodes reflects
 * that some basic blocks may have been deleted
 *
 *
 * Example:
 * <listing>
 *   for (int t1=0; t1 &lt; 100; t1++) {
 *      g1 = null_check   l0
 *      g2 = bounds_check l0, t1
 *      g3 = guard_combine g1,g2
 *      t2 = aload l0, t1, g3
 *      g4 = null_check   l1
 *      g5 = bounds_check l1, t1
 *      g6 = guard_combine g4,g5
 *           astore t2, l1, t1, g6
 *   }
 * </listing>
 *
 * becomes:
 *
 * <listing>
 *   goto explicit_test_block
 * successor_to_loops:
 *   g1 = phi g1_1, g1_2
 *   g2 = phi g2_1, g2_2
 *   g3 = phi g3_1, g3_2
 *   t2 = phi t2_1, t2_2
 *   g4 = phi g4_1, g4_2
 *   g5 = phi g5_1, g5_2
 *   g6 = phi g6_1, g6_2
 *   goto after_loop
 * explicit_test_block:
 *   if l0 == null (unlikely) goto sub_optimal_loop
 *   if 100 >= l0.length (unlikely) goto sub_optimal_loop
 *   if l1 == null (unlikely) goto sub_optimal_loop
 *   if 100 >= l1.length (unlikely) goto sub_optimal_loop
 *   goto optimal_loop
 * sub_optimal_loop:
 *   for (int t1_1=0; t1_1 &lt; 100; t1_1++) {
 *      g1_1 = null_check   l0
 *      g2_1 = bounds_check l0, t1_1
 *      g3_1 = guard_combine g1_1,g2_1
 *      t2_1 = aload l0, t1_1, g3_1
 *      g4_1 = null_check   l1
 *      g5_1 = bounds_check l1, t1_1
 *      g6_1 = guard_combine g4_1,g5_1
 *             astore t2_1, l1, t1_1, g6_1
 *   }
 *   goto successor_to_loops
 * optimal_loop:
 *   for (int t1_2=0; t1_2 &lt; 100; t1_2++) {
 *      g1_2 = true_guard
 *      g2_2 = true_guard
 *      g3_2 = guard_combine g1_2,g2_2
 *      t2_2 = aload l0, t1_2, g3_2
 *      g4_2 = null_check   l1
 *      g5_2 = bounds_check l1, t1_2
 *      g6_2 = guard_combine g4_2,g5_2
 *             astore t2_2, l1, t1_2, g6_2
 *   }
 *   goto successor_to_loops
 * after_loop:
 * </listing>
 *
 * The optimisation works on the Heap SSA form. A more accurate
 * example of the transformation would be:
 *
 * <listing>
 *   heap1 = ...; // previous heap state
 *   t1_1 = 0;
 *   if t1_1 &ge; 100 goto label2
 *   label1:
 *      t1_2 = phi t1_1, t1_3
 *      heap2 = phi heap1, heap3
 *      g1 = null_check   l0
 *      g2 = bounds_check l0, t1_2
 *      g3 = guard_combine g1,g2
 *      t2 = aload l0, t1_2, g3
 *      g4 = null_check   l1
 *      g5 = bounds_check l1, t1_2
 *      g6 = guard_combine g4,g5
 *      heap3 = astore t2, l1, t1_2, g6
 *      t1_3 = t1_2 + 1
 *      if t1_3 &lt; 100 label1 *   label2:
 * </listing>
 *
 * becomes:
 *
 * <listing>
 *   heap1 = ...; // previous heap state
 *   t1_1 = 0;
 *   if t1_1 &ge; 100 goto label2
 *   goto explicit_test_block
 * successor_to_loops:
 *   t1_2 = phi t1_2_1, t1_2_2
 *   heap2 = phi heap2_1, heap2_2
 *   g1 = phi g1_1, g1_2
 *   g2 = phi g2_1, g2_2
 *   g3 = phi g3_1, g3_2
 *   t2 = phi t2_1, t2_2
 *   g4 = phi g4_1, g4_2
 *   g5 = phi g5_1, g5_2
 *   g6 = phi g6_1, g6_2
 *   heap3 = phi heap3_1, heap3_2
 *   t1_3 = phi t1_3_1, t1_3_2
 *   goto after_loop
 * explicit_test_block:
 *   g1_2 = if l0 == null (unlikely) goto sub_optimal_loop
 *   g2_2 = if 100 >= l0.length (unlikely) goto sub_optimal_loop
 *   g4_2 = if l1 == null (unlikely) goto sub_optimal_loop
 *   g5_2 = if 100 >= l1.length (unlikely) goto sub_optimal_loop
 *   goto optimal_loop
 * sub_optimal_loop:
 *   label1_1:
 *      t1_2_1 = phi t1_1, t1_3_1
 *      heap2_1 = phi heap1, heap3_1
 *      g1_1 = null_check   l0
 *      g2_1 = bounds_check l0, t1_2_1
 *      g3_1 = guard_combine g1_1,g2_1
 *      t2_1 = aload l0, t1_2_1, g3_1
 *      g4_1 = null_check   l1
 *      g5_1 = bounds_check l1, t1_2_1
 *      g6_1 = guard_combine g4_1,g5_1
 *      heap3_1 = astore t2_1, l1, t1_2_1, g6_1
 *      t1_3_1 = t1_2_1 + 1
 *      if t1_3_1 &lt; 100 label1_1
 *   goto successor_to_loops
 * optimal_loop:
 *   label1_2:
 *      t1_2_2 = phi t1_1, t1_3_2
 *      heap2_2 = phi heap1, heap3_2
 *      g3_2 = guard_combine g1_2,g2_2
 *      t2_2 = aload l0, t1_2_2, g3_2
 *      g6_2 = guard_combine g4_2,g5_2
 *      heap3_2 = astore t2_2, l1, t1_2_2, g6_2
 *      t1_3_2 = t1_2_2 + 1
 *      if t1_3_2 &lt; 100 label1_2
 *   goto successor_to_loops
 * after_loop:
 * label2:
 * </listing>
 */
public final class LoopVersioning extends CompilerPhase {
  // -oO Debug variables Oo-
  /**
   * Flag to optionally print verbose debugging messages
   */
  private static final boolean DEBUG = false;
  /**
   * Flag to verify computed IR
   */
  private static final boolean VERIFY = false;

  // -oO Debug routines Oo-
  /**
   * Human readable report of what goes on
   *
   * @param s String to print
   **/
  private static void report(String s) {
    if (DEBUG) {
      VM.sysWriteln(s);
    }
  }

  /**
   * Return a string name for this phase.
   * @return "Loop Versioning"
   */
  @Override
  public String getName() {
    return "Loop Versioning";
  }

  // -oO Variables used throughout the optimisation phase Oo-
  /**
   * The phi instruction operand holding the optimized loop variable
   */
  private static final int OPTIMIZED_LOOP_OPERAND = 0;
  /**
   * The phi instruction operand holding the unoptimized loop variable
   */
  private static final int UNOPTIMIZED_LOOP_OPERAND = 1;

  /**
   * IR for optimisation
   */
  private IR ir;

  /**
   * Set used to store the loop related register
   */
  private HashSet<Register> loopRegisterSet;

  /**
   * SSA options
   */
  private SSAOptions desiredSSAOptions;
  /**
   * Compiler phases called from this one
   */
  private CompilerPhase enterSSA, leaveSSA, domPhase;
  /**
   * Run inside SSA sub-phase
   */
  private static final boolean inSSAphase = true;

  // -oO Interface to the rest of the compiler Oo-

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(LoopVersioning.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Constructor
   */
  public LoopVersioning() {
    desiredSSAOptions = new SSAOptions();
    desiredSSAOptions.setScalarsOnly(true);
    if (!inSSAphase) {
      enterSSA = new EnterSSA();
      leaveSSA = new LeaveSSA();
    }
    domPhase = new DominatorsPhase(false);
  }

  /**
   * Should the optimisation be performed
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.SSA_LOOP_VERSIONING;
  }

  /**
   * The main entry point
   *
   * @param _ir the IR to process
   */
  @Override
  public void perform(IR _ir) {
    ir = _ir;

    // Create SSA
    ir.desiredSSAOptions = desiredSSAOptions;
    if (!inSSAphase) {
      enterSSA.perform(ir);
    }

    if (DEBUG) {
      SSA.printInstructions(ir);
    }

    // Perform loop annotation
    if (!ir.hasReachableExceptionHandlers()) {
      // Build LST tree and dominator info
      domPhase.perform(ir);
      DefUse.computeDU(ir);
      // Build annotated version
      ir.HIRInfo.loopStructureTree = new AnnotatedLSTGraph(ir, ir.HIRInfo.loopStructureTree);
    }
    if (VERIFY) {
      ir.verify(getName(), true);
    }

    // Check loop annotation has been performed
    if (!(ir.HIRInfo.loopStructureTree instanceof AnnotatedLSTGraph)) {
      report("Optimisation of " + ir.getMethod() + " failed as LST wasn't annotated\n");
    } else {
      loopRegisterSet = new HashSet<Register>();

      if (DEBUG) {
        VM.sysWriteln(ir.getMethod().toString());
        VM.sysWriteln(ir.HIRInfo.loopStructureTree.toString());
        SSA.printInstructions(ir);
      }

      while (findLoopToOptimise((AnnotatedLSTNode) ir.HIRInfo.loopStructureTree.getRoot())) {
        if (DEBUG) {
          VM.sysWriteln("Successful optimisation of " + ir.getMethod());
          SSA.printInstructions(ir);
          VM.sysWriteln(ir.cfg.toString());
        }
        // Get IR into shape for next pass
        DefUse.computeDU(ir);
        LTDominators.perform(ir, true, true);
        ir.HIRInfo.dominatorTree = new DominatorTree(ir, true);
        LSTGraph.perform(ir);
        AnnotatedLSTGraph.perform(ir);

        if (VERIFY) {
          ir.verify(getName(), true);
        }

        if (DEBUG) {
          VM.sysWriteln("after an optimization pass");
          VM.sysWriteln(ir.HIRInfo.loopStructureTree.toString());
          SSA.printInstructions(ir);
          VM.sysWriteln("Finish optimize: " + ir.getMethod().toString());
        }
      }
      // No longer in use
      loopRegisterSet = null;
    }

    if (!inSSAphase) {
      // Leave SSA
      leaveSSA.perform(ir);
    }

    if (VERIFY) {
      ir.verify(getName(), true);
    }
  }

  // -oO Optimisation routines Oo-

  /**
   * Find an outermost loop to optimise and optimise it. Focus on
   * annotated regular loops, LICM should handle possible
   * optimisation for the non-regular loops
   *
   * @param loop  Loop to search
   * @return was optimisation performed
   */
  private boolean findLoopToOptimise(AnnotatedLSTNode loop) {
    // Has this loop already been optimised?
    Operand carriedLoopIterator = loop.getCarriedLoopIterator();
    if ((carriedLoopIterator instanceof RegisterOperand) &&
        (isOptimizedLoop(carriedLoopIterator.asRegister().getRegister()))) {
      return false;
    }

    // Process inner loops first
    Enumeration<GraphNode> innerLoops = loop.outNodes();
    // Iterate over loops
    while (innerLoops.hasMoreElements()) {
      AnnotatedLSTNode nestedLoop = (AnnotatedLSTNode) innerLoops.nextElement();
      // Try to optimise inner loops first
      if (findLoopToOptimise(nestedLoop)) {
        // Exit early if inner loop optimisation succeeded
        return true;
      }
    }
    // Don't try to optimise irregular loops
    if (loop.isNonRegularLoop()) {
      return false;
    }
    if (DEBUG) {
      report("LoopFissionOfArrayGuards: found loop in " + ir.getMethod());
      VM.sysWriteln("dominator tree:");
      VM.sysWriteln(ir.HIRInfo.dominatorTree.toString());
    }

    // 1) Determine the bound and null checks to be eliminated. The
    // bound checks are the ones that operate on the loop iterator. If
    // no checks can be eliminated, stop optimising this loop.
    ArrayList<Instruction> checksToEliminate = new ArrayList<Instruction>();
    getListOfChecksToEliminate(loop, checksToEliminate);
    if (checksToEliminate.isEmpty()) {
      return false;
    } else {
      // We found instructions to eliminate
      if (DEBUG) {
        VM.sysWriteln("Loop being optimised:");
        VM.sysWriteln(loop.toString());
        VM.sysWriteln("Checks to eliminate:");
        for (Instruction instruction : checksToEliminate) {
          VM.sysWriteln(instruction.toString());
        }
      }
      // 2) Determine the registers defined in the loop.
      ArrayList<Register> registersDefinedInOriginalLoop = new ArrayList<Register>();
      ArrayList<TypeReference> typesOfRegistersDefinedInOriginalLoop = new ArrayList<TypeReference>();
      ArrayList<Instruction> definingInstructionsInOriginalLoop = new ArrayList<Instruction>();
      getRegistersDefinedInLoop(loop,
                                registersDefinedInOriginalLoop,
                                typesOfRegistersDefinedInOriginalLoop,
                                definingInstructionsInOriginalLoop);
      if (DEBUG) {
        VM.sysWrite("Registers in original loop:\n{");
        for (int i = 0; i < registersDefinedInOriginalLoop.size(); i++) {
          VM.sysWrite(registersDefinedInOriginalLoop.get(i).toString());
          if (definingInstructionsInOriginalLoop.get(i) != null) {
            VM.sysWrite("(escapes),");
          } else {
            VM.sysWrite(",");
          }
        }
        VM.sysWriteln("}");
      }
      // 3) Generate phi nodes that define the original register
      // defined by the loop and use two newly created registers.
      ArrayList<Instruction> phiInstructions = new ArrayList<Instruction>();
      HashMap<Register, Register> subOptimalRegMap = new HashMap<Register, Register>();
      HashMap<Register, Register> optimalRegMap = new HashMap<Register, Register>();
      generatePhiNodes(loop,
                       registersDefinedInOriginalLoop,
                       typesOfRegistersDefinedInOriginalLoop,
                       phiInstructions,
                       subOptimalRegMap,
                       optimalRegMap);
      if (DEBUG) {
        VM.sysWriteln("subOptimalRegMap");
        VM.sysWriteln(subOptimalRegMap.toString());
        VM.sysWriteln("optimalRegMap");
        VM.sysWriteln(optimalRegMap.toString());
      }

      // 4) Create a version of the original loop that uses the first of
      // the newly created registers instead of the original
      // registers.
      HashMap<Register, BasicBlock> regToUnoptimizedBlockMap = new HashMap<Register, BasicBlock>();
      HashMap<BasicBlock, BasicBlock> unoptimizedLoopMap =
          createCloneLoop(loop, subOptimalRegMap, regToUnoptimizedBlockMap);
      if (DEBUG) {
        VM.sysWriteln("subOptimalLoopMap");
        VM.sysWriteln(unoptimizedLoopMap.toString());
      }
      // 5) Create a second version, this time with the result of the
      // eliminated checks set to explicit test guards.
      HashMap<Register, BasicBlock> regToOptimizedBlockMap = new HashMap<Register, BasicBlock>();
      HashMap<BasicBlock, BasicBlock> optimizedLoopMap =
          createOptimizedLoop(loop, optimalRegMap, checksToEliminate, regToOptimizedBlockMap);
      if (DEBUG) {
        VM.sysWriteln("optimalLoopMap");
        VM.sysWriteln(optimizedLoopMap.toString());
      }
      // 6) Work out what the maximum value for all the bounds checks
      // are and create branches to optimal or suboptimal loops - with
      // the unoptimized loop possibly being unreachable
      BasicBlock firstBranchBlock = loop.header.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
      BasicBlock temp = (BasicBlock) loop.header.prev;
      ir.cfg.breakCodeOrder(temp, loop.header);
      ir.cfg.linkInCodeOrder(temp, firstBranchBlock);
      ir.cfg.linkInCodeOrder(firstBranchBlock, loop.header);
      temp.redirectOuts(loop.header, firstBranchBlock, ir);
      boolean isUnoptimizedLoopReachable =
          createBranchBlocks(loop,
                             firstBranchBlock,
                             checksToEliminate,
                             unoptimizedLoopMap.get(loop.predecessor),
                             optimizedLoopMap.get(loop.predecessor),
                             optimalRegMap);
      // 7) Fix up the phi node predecessors
      fixUpPhiPredecessors(phiInstructions,
                           isUnoptimizedLoopReachable ? unoptimizedLoopMap.get(loop.exit) : null,
                           optimizedLoopMap.get(loop.exit));
      // 8) Remove the unoptimized loop if its redundant
      if (!isUnoptimizedLoopReachable) {
        removeUnoptimizedLoop(loop, unoptimizedLoopMap);
      }

      // 9) Replace register definitions in the original
      // loop with phi instructions
      modifyOriginalLoop(loop, phiInstructions, definingInstructionsInOriginalLoop, subOptimalRegMap, optimalRegMap);
      // 10) Compact node numbering so that CFG number of nodes
      // reflects that some basic blocks may have been deleted
      ir.cfg.compactNodeNumbering();
      return true;
    }
  }

  /**
   * Create a list of instructions to be eliminated
   * @param loop the loop to examine
   * @param instrToEliminate the instructions to remove
   */
  private void getListOfChecksToEliminate(AnnotatedLSTNode loop, ArrayList<Instruction> instrToEliminate) {
    ArrayList<Instruction> nullChecks = new ArrayList<Instruction>();
    ArrayList<Instruction> oddBoundChecks = new ArrayList<Instruction>();
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, block);
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        if (NullCheck.conforms(instruction)) {
          if (loop.isInvariant(NullCheck.getRef(instruction))) {
            instrToEliminate.add(instruction);
            nullChecks.add(instruction);
          }
        } else if (loop.isMonotonic() && BoundsCheck.conforms(instruction)) {
          if (loop.isInvariant(BoundsCheck.getRef(instruction))) {
            if (loop.isRelatedToIterator(BoundsCheck.getIndex(instruction))) {
              if (loop.isInvariant(BoundsCheck.getGuard(instruction))) {
                instrToEliminate.add(instruction);
              } else {
                // Null check isn't invariant but reference was, check
                // null check will be eliminated at end of loop
                oddBoundChecks.add(instruction);
              }
            }
          }
        }
      }
    }
    // Check cases where the null check isn't loop invariant, however,
    // it will be in the optimized loop as we'll have eliminated it
    for (Instruction oddBoundCheck : oddBoundChecks) {
      Operand guard = BoundsCheck.getGuard(oddBoundCheck);
      for (Instruction nullCheck : nullChecks) {
        if (guard.similar(NullCheck.getGuardResult(nullCheck))) {
          instrToEliminate.add(oddBoundCheck);
          break;
        }
      }
    }
  }

  /**
   * Get registers defined in the given loop. As we're in SSA form
   * all register definitions must be unique.
   * @param loop - the loop to examine
   * @param registers - vector to which defined registers are added
   */
  private void getRegistersDefinedInLoop(AnnotatedLSTNode loop, ArrayList<Register> registers,
                                         ArrayList<TypeReference> types,
                                         ArrayList<Instruction> definingInstructions) {
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      // can value escape
      final boolean escapes = (block == loop.exit) || (ir.HIRInfo.dominatorTree.dominates(block, loop.exit));
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, block);
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        OperandEnumeration operands = instruction.getDefs();
        while (operands.hasMoreElements()) {
          Operand operand = operands.next();
          if (operand.isRegister()) {
            registers.add(operand.asRegister().getRegister());
            types.add(operand.asRegister().getType());
            if (escapes) {
              definingInstructions.add(instruction);
            } else {
              definingInstructions.add(null);
            }
          }
        }
      }
    }
  }

  /**
   * Generate into a new block phi nodes that define the original
   * register defined by the loop and use two newly created
   * registers.
   * @param registers - vector to which defined registers need to be
   * created registers.x used in creating phi nodes
   * @param types - vector of corresponding types of registers.
   * @param phiInstructions - created phi instructions
   * @param subOptimalRegMap - mapping of orignal destination to the
   * newly created destination for the unoptimized loop
   * @param optimalRegMap - mapping of orignal destination to the
   * newly created destination for the optimized loop
   */
  private void generatePhiNodes(AnnotatedLSTNode loop, ArrayList<Register> registers,
                                ArrayList<TypeReference> types, ArrayList<Instruction> phiInstructions,
                                HashMap<Register, Register> subOptimalRegMap,
                                HashMap<Register, Register> optimalRegMap) {
    // Get the carried loop iterator's register
    Register carriedLoopIteratorRegister = ((RegisterOperand) loop.getCarriedLoopIterator()).getRegister();
    for (int i = 0; i < registers.size(); i++) {
      Register register = registers.get(i);
      TypeReference type = types.get(i);
      Instruction phi = Phi.create(PHI, new RegisterOperand(register, type), 2);
      phi.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);

      // new operand for optimized loop
      Operand op0 = ir.regpool.makeTemp(type);
      Phi.setValue(phi, OPTIMIZED_LOOP_OPERAND, op0);
      optimalRegMap.put(register, op0.asRegister().getRegister());

      // new operand for unoptimized loop
      Operand op1 = ir.regpool.makeTemp(type);
      Phi.setValue(phi, UNOPTIMIZED_LOOP_OPERAND, op1);
      subOptimalRegMap.put(register, op1.asRegister().getRegister());

      // Add the new created carried loop iterator registers to
      // internal set to mark the optimized loops
      if (register == carriedLoopIteratorRegister) {
        setOptimizedLoop(op0.asRegister().getRegister());
        setOptimizedLoop(op1.asRegister().getRegister());
      }

      phiInstructions.add(phi);
    }
    // rename any optimized inner loops registers
    renameOptimizedLoops(subOptimalRegMap, optimalRegMap);
  }

  /**
   * Create a clone of the loop replacing definitions in the cloned
   * loop with those found in the register map
   * @param loop - loop to clone
   * @param regMap - mapping of original definition to new
   * definition
   * @return a mapping from original BBs to created BBs
   */
  private HashMap<BasicBlock, BasicBlock> createCloneLoop(AnnotatedLSTNode loop,
                                                                  HashMap<Register, Register> regMap,
                                                                  HashMap<Register, BasicBlock> regToBlockMap) {
    HashMap<BasicBlock, BasicBlock> originalToCloneBBMap = new HashMap<BasicBlock, BasicBlock>();
    // After the newly created loop goto the old loop header
    originalToCloneBBMap.put(loop.successor, loop.header);
    // Create an empty block to be the loop predecessor
    BasicBlock new_pred = loop.header.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
    ir.cfg.linkInCodeOrder(ir.cfg.lastInCodeOrder(), new_pred);
    originalToCloneBBMap.put(loop.predecessor, new_pred);
    // Create copy blocks
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      block.killFallThrough(); // get rid of fall through edges to aid recomputeNormalOuts
      // Create copy and register mapping
      BasicBlock copy = block.copyWithoutLinks(ir);
      originalToCloneBBMap.put(block, copy);
      // Link into code order
      ir.cfg.linkInCodeOrder(ir.cfg.lastInCodeOrder(), copy);
      // Alter register definitions and uses in copy
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, copy);
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        OperandEnumeration operands = instruction.getDefs();
        while (operands.hasMoreElements()) {
          Operand operand = operands.next();
          if (operand.isRegister()) {
            Register register = operand.asRegister().getRegister();
            if (regMap.containsKey(register)) {
              instruction.replaceRegister(register, regMap.get(register));
              regToBlockMap.put(regMap.get(register), copy);
            }
          }
        }
        operands = instruction.getUses();
        while (operands.hasMoreElements()) {
          Operand operand = operands.next();
          if (operand instanceof RegisterOperand) {
            Register register = operand.asRegister().getRegister();
            if (regMap.containsKey(register)) {
              instruction.replaceRegister(register, regMap.get(register));
            }
          }
        }
      }
    }
    // Fix up outs
    // loop predecessor
    new_pred.redirectOuts(loop.header, originalToCloneBBMap.get(loop.header), ir);
    // loop blocks
    blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      BasicBlock copy = originalToCloneBBMap.get(block);
      Enumeration<BasicBlock> outs = block.getOutNodes();
      while (outs.hasMoreElements()) {
        BasicBlock out = outs.nextElement();
        if (originalToCloneBBMap.containsKey(out)) {
          copy.redirectOuts(out, originalToCloneBBMap.get(out), ir);
        }
      }
    }
    // Fix up phis
    blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      BasicBlock copy = originalToCloneBBMap.get(block);
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, copy);
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        if (Phi.conforms(instruction)) {
          for (int i = 0; i < Phi.getNumberOfValues(instruction); i++) {
            BasicBlock phi_predecessor = Phi.getPred(instruction, i).block;
            if (originalToCloneBBMap.containsKey(phi_predecessor)) {
              Phi.setPred(instruction, i, new BasicBlockOperand(originalToCloneBBMap.get(phi_predecessor)));
            } else {
              dumpIR(ir, "Error when optimising" + ir.getMethod());
              throw new Error("There's > 1 route to this phi node " +
                              instruction +
                              " from outside the loop: " +
                              phi_predecessor);
            }
          }
        }
      }
    }
    return originalToCloneBBMap;
  }

  /**
   * Create a clone of the loop replacing definitions in the cloned
   * loop with those found in the register map and eliminate
   * unnecessary bound checks
   * @param loop - loop to clone
   * @param regMap - mapping of original definition to new
   * definition
   * @param instrToEliminate - instructions to eliminate
   * @param regToBlockMap - mapping of a register to its defining BB
   * @return a mapping from original BBs to created BBs
   */
  private HashMap<BasicBlock, BasicBlock> createOptimizedLoop(AnnotatedLSTNode loop,
                                                                      HashMap<Register, Register> regMap,
                                                                      ArrayList<Instruction> instrToEliminate,
                                                                      HashMap<Register, BasicBlock> regToBlockMap) {
    HashMap<BasicBlock, BasicBlock> originalToCloneBBMap = new HashMap<BasicBlock, BasicBlock>();
    // After the newly created loop goto the old loop header
    originalToCloneBBMap.put(loop.successor, loop.header);
    // Create an empty block to be the loop predecessor
    BasicBlock new_pred = loop.header.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
    ir.cfg.linkInCodeOrder(ir.cfg.lastInCodeOrder(), new_pred);
    originalToCloneBBMap.put(loop.predecessor, new_pred);

    // Create copy blocks
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      // N.B. fall through will have been killed by unoptimized loop
      // Create copy and register mapping
      BasicBlock copy = block.copyWithoutLinks(ir);
      originalToCloneBBMap.put(block, copy);
      // Link into code order
      ir.cfg.linkInCodeOrder(ir.cfg.lastInCodeOrder(), copy);

      // Alter register definitions in copy
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, copy);
      loop_over_created_instructions:
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        if (BoundsCheck.conforms(instruction)) {
          for (Instruction anInstrToEliminate : instrToEliminate) {
            if (instruction.similar(anInstrToEliminate)) {
              instruction.remove();
              continue loop_over_created_instructions;
            }
          }
        } else if (NullCheck.conforms(instruction)) {
          for (Instruction anInstrToEliminate : instrToEliminate) {
            if (instruction.similar(anInstrToEliminate)) {
              instruction.remove();
              continue loop_over_created_instructions;
            }
          }
        }
        OperandEnumeration operands = instruction.getDefs();
        while (operands.hasMoreElements()) {
          Operand operand = operands.next();
          if (operand instanceof RegisterOperand) {
            Register register = operand.asRegister().getRegister();
            if (regMap.containsKey(register)) {
              instruction.replaceRegister(register, regMap.get(register));
              regToBlockMap.put(regMap.get(register), copy);
            }
          }
        }
        operands = instruction.getUses();
        while (operands.hasMoreElements()) {
          Operand operand = operands.next();
          if (operand.isRegister()) {
            Register register = operand.asRegister().getRegister();
            if (regMap.containsKey(register)) {
              instruction.replaceRegister(register, regMap.get(register));
            }
          }
        }
      }
    }
    // Fix up outs
    // loop predecessor
    new_pred.redirectOuts(loop.header, originalToCloneBBMap.get(loop.header), ir);
    blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      BasicBlock copy = originalToCloneBBMap.get(block);
      Enumeration<BasicBlock> outs = block.getOutNodes();
      while (outs.hasMoreElements()) {
        BasicBlock out = outs.nextElement();
        if (originalToCloneBBMap.containsKey(out)) {
          copy.redirectOuts(out, originalToCloneBBMap.get(out), ir);
        }
      }
    }
    // Fix up phis
    blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      BasicBlock copy = originalToCloneBBMap.get(block);
      IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, copy);
      while (instructions.hasMoreElements()) {
        Instruction instruction = instructions.next();
        if (Phi.conforms(instruction)) {
          for (int i = 0; i < Phi.getNumberOfValues(instruction); i++) {
            BasicBlock phi_predecessor = Phi.getPred(instruction, i).block;
            if (originalToCloneBBMap.containsKey(phi_predecessor)) {
              Phi.setPred(instruction, i, new BasicBlockOperand(originalToCloneBBMap.get(phi_predecessor)));
            } else {
              throw new Error("There's > 1 route to this phi node from outside the loop: " + phi_predecessor);
            }
          }
        }
      }
    }
    return originalToCloneBBMap;
  }

  /**
   * When phi nodes were generated the basic blocks weren't known for
   * the predecessors, fix this up now. (It may also not be possible
   * to reach the unoptimized loop any more)
   */
  private void fixUpPhiPredecessors(ArrayList<Instruction> phiInstructions, BasicBlock unoptimizedLoopExit,
                                    BasicBlock optimizedLoopExit) {
    if (unoptimizedLoopExit != null) {
      for (Instruction instruction : phiInstructions) {
        Phi.setPred(instruction, OPTIMIZED_LOOP_OPERAND, new BasicBlockOperand(optimizedLoopExit));
        Phi.setPred(instruction, UNOPTIMIZED_LOOP_OPERAND, new BasicBlockOperand(unoptimizedLoopExit));
      }
    } else {
      for (Instruction instruction : phiInstructions) {
        Operand operand = Phi.getValue(instruction, OPTIMIZED_LOOP_OPERAND);
        Phi.resizeNumberOfPreds(instruction, 1);
        Phi.resizeNumberOfValues(instruction, 1);
        Phi.setValue(instruction, OPTIMIZED_LOOP_OPERAND, operand);
        Phi.setPred(instruction, OPTIMIZED_LOOP_OPERAND, new BasicBlockOperand(optimizedLoopExit));
      }
    }
  }

  /**
   * Create the block containing explict branches to either the
   * optimized or unoptimized loops
   * @param optimalRegMap - mapping used to map eliminated bound and
   * null check guards to
   */
  private boolean createBranchBlocks(AnnotatedLSTNode loop, BasicBlock block,
                                     ArrayList<Instruction> checksToEliminate, BasicBlock unoptimizedLoopEntry,
                                     BasicBlock optimizedLoopEntry,
                                     HashMap<Register, Register> optimalRegMap) {
    BasicBlock blockOnEntry = block;
    // 1) generate null check guards
    block = generateNullCheckBranchBlocks(loop, checksToEliminate, optimalRegMap, block, unoptimizedLoopEntry);

    // 2) generate bound check guards
    if (loop.isMonotonic()) {
      // create new operands for values beyond initial and terminal iterator values
      Operand terminal;
      Operand terminalLessStrideOnce;
      Operand terminalPlusStrideOnce;

      // NB. precomputing these makes life easier and the code easier to read,
      //     it does create dead code though
      if (loop.terminalIteratorValue.isIntConstant()) {
        terminal = loop.terminalIteratorValue;
        int terminalAsInt = terminal.asIntConstant().value;
        int stride = loop.strideValue.asIntConstant().value;
        terminalLessStrideOnce = new IntConstantOperand(terminalAsInt - stride);
        terminalPlusStrideOnce = new IntConstantOperand(terminalAsInt + stride);
      } else {
        Instruction tempInstr;
        terminal = loop.generateLoopInvariantOperand(block, loop.terminalIteratorValue);
        terminalLessStrideOnce = ir.regpool.makeTempInt();
        terminalPlusStrideOnce = ir.regpool.makeTempInt();
        tempInstr =
            Binary.create(INT_SUB, terminalLessStrideOnce.asRegister(), terminal.copy(), loop.strideValue.copy());
        tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
        block.appendInstruction(tempInstr);
        DefUse.updateDUForNewInstruction(tempInstr);

        tempInstr =
            Binary.create(INT_ADD, terminalPlusStrideOnce.asRegister(), terminal.copy(), loop.strideValue.copy());
        tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
        block.appendInstruction(tempInstr);
        DefUse.updateDUForNewInstruction(tempInstr);
      }

      // Determine maximum and minimum index values for different loop types
      Operand phiMinIndexValue;
      Operand phiMaxIndexValue;

      if (loop.isMonotonicIncreasing()) {
        phiMinIndexValue = loop.initialIteratorValue;
        if ((loop.condition.isLESS() ||
             loop.condition.isLOWER() ||
             loop.condition.isNOT_EQUAL())) {
          phiMaxIndexValue = terminal;
        } else if ((loop.condition.isLESS_EQUAL() ||
                    loop.condition.isLOWER_EQUAL() ||
                    loop.condition.isEQUAL())) {
          phiMaxIndexValue = terminalPlusStrideOnce;
        } else {
          throw new Error("Unrecognised loop for fission " + loop);
        }
      } else if (loop.isMonotonicDecreasing()) {
        phiMaxIndexValue = loop.initialIteratorValue;
        if ((loop.condition.isGREATER() ||
             loop.condition.isHIGHER() ||
             loop.condition.isNOT_EQUAL())) {
          phiMinIndexValue = terminalPlusStrideOnce;
        } else if ((loop.condition.isGREATER_EQUAL() ||
                    loop.condition.isHIGHER_EQUAL() ||
                    loop.condition.isEQUAL())) {
          phiMinIndexValue = terminalLessStrideOnce;
        } else {
          throw new Error("Unrecognised loop for fission " + loop);
        }
      } else {
        throw new Error("Unrecognised loop for fission " + loop);
      }
      // Generate tests
      for (int i = 0; i < checksToEliminate.size(); i++) {
        Instruction instr = checksToEliminate.get(i);
        if (BoundsCheck.conforms(instr)) {
          // Have we already generated these tests?
          boolean alreadyChecked = false;
          for (int j = 0; j < i; j++) {
            Instruction old_instr = checksToEliminate.get(j);
            if (BoundsCheck.conforms(old_instr) &&
                (BoundsCheck.getRef(old_instr).similar(BoundsCheck.getRef(instr))) &&
                (BoundsCheck.getIndex(old_instr).similar(BoundsCheck.getIndex(instr)))) {
              // yes - just create a guard move
              alreadyChecked = true;
              RegisterOperand guardResult = BoundsCheck.getGuardResult(instr).copyRO();
              guardResult.setRegister(optimalRegMap.get(guardResult.getRegister()));
              RegisterOperand guardSource = BoundsCheck.getGuardResult(old_instr).copyRO();
              guardSource.setRegister(optimalRegMap.get(guardSource.getRegister()));
              Instruction tempInstr = Move.create(GUARD_MOVE, guardResult, guardSource);
              tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
              block.appendInstruction(tempInstr);
              break;
            }
          }
          if (!alreadyChecked) {
            // no - generate tests
            Operand index = BoundsCheck.getIndex(instr);
            int distance = loop.getFixedDistanceFromPhiIterator(index);
            if (distance == 0) {
              block =
                  generateExplicitBoundCheck(instr,
                                             phiMinIndexValue,
                                             phiMaxIndexValue,
                                             optimalRegMap,
                                             block,
                                             unoptimizedLoopEntry);
            } else {
              Instruction tempInstr;
              RegisterOperand minIndex = ir.regpool.makeTempInt();
              RegisterOperand maxIndex = ir.regpool.makeTempInt();

              tempInstr =
                  Binary.create(INT_ADD, minIndex, phiMinIndexValue.copy(), new IntConstantOperand(distance));
              tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
              block.appendInstruction(tempInstr);
              DefUse.updateDUForNewInstruction(tempInstr);

              tempInstr =
                  Binary.create(INT_ADD, maxIndex, phiMaxIndexValue.copy(), new IntConstantOperand(distance));
              tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
              block.appendInstruction(tempInstr);
              DefUse.updateDUForNewInstruction(tempInstr);

              block = generateExplicitBoundCheck(instr, minIndex, maxIndex, optimalRegMap, block, unoptimizedLoopEntry);
            }
          }
        }
      }
    }
    // Have we had to create a new basic block since entry => we
    // generated a branch to the unoptimized loop
    boolean isUnoptimizedLoopReachable = (blockOnEntry != block);
    // 3) Finish up with goto and generate true guard value
    {
      Instruction branch; // the generated branch instruction
      branch = Goto.create(GOTO, optimizedLoopEntry.makeJumpTarget());
      branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
      block.appendInstruction(branch);
      block.deleteNormalOut();
      block.insertOut(optimizedLoopEntry);
    }
    return isUnoptimizedLoopReachable;
  }

  /**
   * Generate null check branch blocks
   *
   * @param loop the current loop where checks are being eliminated
   * @param checksToEliminate all of the checks that are being eliminated in the pass
   * @param optimalRegMap a map from original register to the register used in the optimal loop
   * @param block the block to generate code into
   * @param unoptimizedLoopEntry entry to the unoptimized loop for if the check fails
   * @return the new block to generate code into
   */
  private BasicBlock generateNullCheckBranchBlocks(AnnotatedLSTNode loop,
                                                       ArrayList<Instruction> checksToEliminate,
                                                       HashMap<Register, Register> optimalRegMap,
                                                       BasicBlock block, BasicBlock unoptimizedLoopEntry) {
    // Map of already generated null check references to their
    // corresponding guard result
    HashMap<Register, Operand> refToGuardMap = new HashMap<Register, Operand>();
    // Iterate over checks
    for (Instruction instr : checksToEliminate) {
      // Is this a null check
      if (NullCheck.conforms(instr)) {
        // the generated branch instruction
        Instruction branch;
        // the reference to compare
        Operand ref = AnnotatedLSTNode.follow(NullCheck.getRef(instr));
        // the guard result to define
        RegisterOperand guardResult = NullCheck.getGuardResult(instr).copyRO();
        guardResult.setRegister(optimalRegMap.get(guardResult.getRegister()));
        // check if we've generated this test already
        if (ref.isRegister() && refToGuardMap.containsKey(ref.asRegister().getRegister())) {
          // yes - generate just a guard move
          branch = Move.create(GUARD_MOVE, guardResult, refToGuardMap.get(ref.asRegister().getRegister()).copy());
          branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
          block.appendInstruction(branch);
        } else {
          // check if we can just move a guard from the loop predecessors
          RegisterOperand guard = nullCheckPerformedInLoopPredecessors(loop.header, instr);
          if (guard != null) {
            // yes - generate just a guard move
            branch = Move.create(GUARD_MOVE, guardResult, guard.copyRO());
            branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
            block.appendInstruction(branch);
          } else {
            // generate explicit null test
            branch =
                IfCmp.create(REF_IFCMP,
                             guardResult,
                             ref.copy(),
                             new NullConstantOperand(),
                             ConditionOperand.EQUAL(),
                             unoptimizedLoopEntry.makeJumpTarget(),
                             BranchProfileOperand.unlikely());
            if (ref.isRegister()) {
              refToGuardMap.put(ref.asRegister().getRegister(), guardResult);
            }
            branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
            block.appendInstruction(branch);
            // Adjust block
            block.insertOut(unoptimizedLoopEntry);
            BasicBlock new_block = block.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
            BasicBlock temp = (BasicBlock) block.next;
            ir.cfg.breakCodeOrder(block, temp);
            ir.cfg.linkInCodeOrder(block, new_block);
            ir.cfg.linkInCodeOrder(new_block, temp);
            block.insertOut(new_block);
            block = new_block;
          }
        }
      }
    }
    return block;
  }

  /**
   * Generate bound check branch blocks
   *
   * @param boundCheckInstr the bound check instruction in question
   * @param minIndexValue the min value for an iterator a loop will generate
   * @param maxIndexValue the max value for an iterator a loop will generate
   * @param optimalRegMap a map from original register to the register used in the optimal loop
   * @param block the block to generate code into
   * @param unoptimizedLoopEntry entry to the unoptimized loop for if the check fails
   * @return the new block to generate code into
   */
  private BasicBlock generateExplicitBoundCheck(Instruction boundCheckInstr, Operand minIndexValue,
                                                    Operand maxIndexValue,
                                                    HashMap<Register, Register> optimalRegMap,
                                                    BasicBlock block, BasicBlock unoptimizedLoopEntry) {
    // 1) Work out what tests are necessary. NB we don't optimise for
    // the case when exceptions will always be generated
    boolean lowerBoundTestRedundant;
    boolean upperBoundTestRedundant;
    {
      // as array lengths must be >= 0 the lower bound test is not
      // necessary if:
      // (minIndexValue >= 0) or ((arraylength A) + zeroOrPositiveConstant)
      lowerBoundTestRedundant =
          ((minIndexValue.isIntConstant() && (minIndexValue.asIntConstant().value >= 0)) ||
           ((getConstantAdjustedArrayLengthRef(minIndexValue) != null) &&
            (getConstantAdjustedArrayLengthDistance(minIndexValue) >= 0)));
      // as the upper bound must be <= arraylength the test is not
      // necessary if:
      // maxIndexValue = (arraylength A) - zeroOrPositiveConstant
      Operand maxIndexArrayLengthRef = getConstantAdjustedArrayLengthRef(maxIndexValue);
      upperBoundTestRedundant =
          ((maxIndexArrayLengthRef != null) &&
           maxIndexArrayLengthRef.similar(BoundsCheck.getRef(boundCheckInstr)) &&
           (getConstantAdjustedArrayLengthDistance(maxIndexValue) <= 0));
    }

    // 2) Create explicit bound check

    // register to hold result (NB it's a guard for the optimal loop)
    RegisterOperand guardResult = BoundsCheck.getGuardResult(boundCheckInstr).copyRO();
    guardResult.setRegister(optimalRegMap.get(guardResult.getRegister()));

    // the guard on the bound check (mapped from the optimal loop as
    // it should already have been generated or may already be out of
    // the loop)
    Operand origGuard = BoundsCheck.getGuard(boundCheckInstr);
    Operand guard = origGuard.copy();
    if (origGuard.isRegister() && optimalRegMap.containsKey(origGuard.asRegister().getRegister())) {
      guard.asRegister().setRegister(optimalRegMap.get(origGuard.asRegister().getRegister()));
    }

    if (lowerBoundTestRedundant && upperBoundTestRedundant) {
      // both tests redundant so just generate a guard move of the
      // bound check guard
      Instruction move = Move.create(GUARD_MOVE, guardResult, guard);
      move.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
      block.appendInstruction(move);
    } else {
      // 2.1) Create array length
      RegisterOperand array_length = ir.regpool.makeTempInt();
      Instruction array_length_instr =
          GuardedUnary.create(ARRAYLENGTH,
                              array_length,
                              AnnotatedLSTNode.follow(BoundsCheck.getRef(boundCheckInstr)).copy(),
                              guard);
      array_length_instr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
      block.appendInstruction(array_length_instr);

      // 2.2) Create minimum index test unless test is redundant
      if (!lowerBoundTestRedundant) {
        RegisterOperand lowerBoundGuard = upperBoundTestRedundant ? guardResult : ir.regpool.makeTempValidation();
        // Generate bound check
        Instruction branch =
            IfCmp.create(INT_IFCMP,
                         lowerBoundGuard,
                         minIndexValue.copy(),
                         array_length.copyRO(),
                         ConditionOperand.LESS(),
                         unoptimizedLoopEntry.makeJumpTarget(),
                         BranchProfileOperand.unlikely());
        branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
        block.appendInstruction(branch);
        // Adjust block
        block.insertOut(unoptimizedLoopEntry);
        BasicBlock new_block = block.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
        BasicBlock temp = (BasicBlock) block.next;
        ir.cfg.breakCodeOrder(block, temp);
        ir.cfg.linkInCodeOrder(block, new_block);
        ir.cfg.linkInCodeOrder(new_block, temp);
        block.insertOut(new_block);
        block = new_block;
      }
      // 2.3) Create maximum index test
      if (!upperBoundTestRedundant) {
        Instruction branch =
            IfCmp.create(INT_IFCMP,
                         guardResult,
                         maxIndexValue.copy(),
                         array_length.copyRO(),
                         ConditionOperand.GREATER(),
                         unoptimizedLoopEntry.makeJumpTarget(),
                         BranchProfileOperand.unlikely());
        branch.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
        block.appendInstruction(branch);
        // Adjust block
        block.insertOut(unoptimizedLoopEntry);
        BasicBlock new_block = block.createSubBlock(SYNTH_LOOP_VERSIONING_BCI, ir);
        BasicBlock temp = (BasicBlock) block.next;
        ir.cfg.breakCodeOrder(block, temp);
        ir.cfg.linkInCodeOrder(block, new_block);
        ir.cfg.linkInCodeOrder(new_block, temp);
        block.insertOut(new_block);
        block = new_block;
      }
    }
    return block;
  }

  /**
   * Can we eliminate a null check as it has lready been performed?
   * NB SSA guarantees that if a value is null it must always be null
   *
   * @param instr null check instruction
   */
  private RegisterOperand nullCheckPerformedInLoopPredecessors(BasicBlock header, Instruction instr) {
    if (VM.VerifyAssertions) VM._assert(NullCheck.conforms(instr));
    BasicBlock block = header;
    do {
      block = ir.HIRInfo.dominatorTree.getParent(block);
      Instruction lastInst = block.lastInstruction();
      for (Instruction itrInst = block.firstInstruction(); itrInst != lastInst; itrInst =
          itrInst.nextInstructionInCodeOrder()) {
        if (NullCheck.conforms(itrInst) && NullCheck.getRef(itrInst).similar(NullCheck.getRef(instr))) {
          return NullCheck.getGuardResult(itrInst);
        }
      }
    } while (block != ir.cfg.entry());
    return null;
  }

  /**
   * Get the array length reference ignoring instructions that adjust
   * its result by a fixed amount
   *
   * @param op operand to chase arraylength opcode to
   * constant value from an array length
   */
  private Operand getConstantAdjustedArrayLengthRef(Operand op) {
    Operand result = null;
    if (op.isRegister()) {
      Instruction opInstr = AnnotatedLSTNode.definingInstruction(op);
      if (opInstr.operator.opcode == ARRAYLENGTH_opcode) {
        result = GuardedUnary.getVal(opInstr);
      } else if ((opInstr.operator.opcode == INT_ADD_opcode) || (opInstr.operator.opcode == INT_SUB_opcode)) {
        Operand val1 = Binary.getVal1(opInstr);
        Operand val2 = Binary.getVal2(opInstr);
        if (val1.isConstant()) {
          result = getConstantAdjustedArrayLengthRef(val2);
        } else if (val2.isConstant()) {
          result = getConstantAdjustedArrayLengthRef(val1);
        }
      }
    }
    return result;
  }

  /**
   * Get the distance from an array length by addding up instructions
   * that adjust the array length result by a constant amount
   *
   * @param op operand to chase arraylength opcode to
   */
  private int getConstantAdjustedArrayLengthDistance(Operand op) {
    Instruction opInstr = AnnotatedLSTNode.definingInstruction(op);
    if (opInstr.operator.opcode == ARRAYLENGTH_opcode) {
      return 0;
    } else if (opInstr.operator.opcode == INT_ADD_opcode) {
      Operand val1 = Binary.getVal1(opInstr);
      Operand val2 = Binary.getVal2(opInstr);
      if (val1.isConstant()) {
        return val1.asIntConstant().value + getConstantAdjustedArrayLengthDistance(val2);
      } else {
        VM._assert(val2.isConstant());
        return getConstantAdjustedArrayLengthDistance(val1) + val2.asIntConstant().value;
      }
    } else if (opInstr.operator.opcode == INT_SUB_opcode) {
      Operand val1 = Binary.getVal1(opInstr);
      Operand val2 = Binary.getVal2(opInstr);
      if (val1.isConstant()) {
        return val1.asIntConstant().value - getConstantAdjustedArrayLengthDistance(val2);
      } else {
        VM._assert(val2.isConstant());
        return getConstantAdjustedArrayLengthDistance(val1) - val2.asIntConstant().value;
      }
    } else {
      throw new Error("Unexpected opcode when computing distance " + op);
    }
  }

  /**
   * Remove loop and replace register definitions in the original loop
   * with phi instructions
   */
  private void modifyOriginalLoop(AnnotatedLSTNode loop, ArrayList<Instruction> phiInstructions,
                                  ArrayList<Instruction> definingInstrInOriginalLoop,
                                  HashMap<Register, Register> subOptimalRegMap,
                                  HashMap<Register, Register> optimalRegMap) {
    // Remove instructions from loop header and exit, remove other
    // loop body blocks
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.next();
      if ((block == loop.header) || (block == loop.exit)) {
        IREnumeration.AllInstructionsEnum instructions = new IREnumeration.AllInstructionsEnum(ir, block);
        while (instructions.hasMoreElements()) {
          Instruction instruction = instructions.next();
          if (!BBend.conforms(instruction) && !Label.conforms(instruction)) {
            instruction.remove();
          }
        }
      } else {
        ir.cfg.removeFromCFGAndCodeOrder(block);
      }
    }

    // Place phi instructions in loop header
    for (int i = 0; i < phiInstructions.size(); i++) {
      Instruction origInstr = definingInstrInOriginalLoop.get(i);
      // Did the original instructions value escape the loop?
      if (origInstr != null) {
        // Was this a phi of a phi?
        if (Phi.conforms(origInstr)) {
          Instruction phi = phiInstructions.get(i);
          boolean phiHasUnoptimizedArg = Phi.getNumberOfValues(phi) == 2;
          // Phi of a phi - so make sure that we get the value to escape the loop, not the value at the loop header
          boolean fixed = false;
          for (int index = 0; index < Phi.getNumberOfPreds(origInstr); index++) {
            BasicBlockOperand predOp = Phi.getPred(origInstr, index);
            // Only worry about values who are on the backward branch
            if (predOp.block == loop.exit) {
              if (fixed) { // We've tried to do 2 replaces => something wrong
                SSA.printInstructions(ir);
                OptimizingCompilerException.UNREACHABLE("LoopVersioning",
                                                            "Phi node in loop header with multiple in loop predecessors");
              }
              Operand rval = Phi.getValue(origInstr, index);
              if (rval.isRegister()) {
                // Sort out registers
                Register origRegPhiRval = rval.asRegister().getRegister();
                Register subOptRegPhiRval;
                Register optRegPhiRval;
                if (!subOptimalRegMap.containsKey(origRegPhiRval)) {
                  // Register comes from loop exit but it wasn't defined in the loop
                  subOptRegPhiRval = origRegPhiRval;
                  optRegPhiRval = origRegPhiRval;
                } else {
                  subOptRegPhiRval = subOptimalRegMap.get(origRegPhiRval);
                  optRegPhiRval = optimalRegMap.get(origRegPhiRval);
                }
                if (phiHasUnoptimizedArg) {
                  Phi.getValue(phi, UNOPTIMIZED_LOOP_OPERAND).asRegister().setRegister(subOptRegPhiRval);
                }
                Phi.getValue(phi, OPTIMIZED_LOOP_OPERAND).asRegister().setRegister(optRegPhiRval);
              } else if (rval.isConstant()) {
                // Sort out constants
                if (phiHasUnoptimizedArg) {
                  Phi.setValue(phi, UNOPTIMIZED_LOOP_OPERAND, rval.copy());
                }
                Phi.setValue(phi, OPTIMIZED_LOOP_OPERAND, rval.copy());
              } else if (rval instanceof HeapOperand) {
                // Sort out heap variables
                @SuppressWarnings("unchecked") // Cast to generic type
                    HeapVariable<Object> origPhiRval = ((HeapOperand) rval).value;
                HeapVariable<Object> subOptPhiRval;
                HeapVariable<Object> optPhiRval;
                if (true /*subOptimalRegMap.containsKey(origPhiRval) == false*/) {
                  // currently we only expect to optimise scalar SSA
                  // form
                  subOptPhiRval = origPhiRval;
                  optPhiRval = origPhiRval;
                } else {
                  /*
                  subOptPhiRval   = (HeapVariable)subOptimalRegMap.get(origPhiRval);
                  optPhiRval      = (HeapVariable)optimalRegMap.get(origPhiRval);
                  */
                }
                if (phiHasUnoptimizedArg) {
                  Phi.setValue(phi, UNOPTIMIZED_LOOP_OPERAND, new HeapOperand<Object>(subOptPhiRval));
                }
                Phi.setValue(phi, OPTIMIZED_LOOP_OPERAND, new HeapOperand<Object>(optPhiRval));
              } else {
                OptimizingCompilerException.UNREACHABLE("LoopVersioning",
                                                            "Unknown operand type",
                                                            rval.toString());
              }
              fixed = true;
            }
          }
        }
        // Add back to loop
        loop.header.appendInstruction(phiInstructions.get(i));
      }
    }
    // Remove original loop and branch to loop successor
    Instruction tempInstr;
    if (loop.header != loop.exit) {
      tempInstr = Goto.create(GOTO, loop.exit.makeJumpTarget());
      tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
      loop.header.appendInstruction(tempInstr);
      loop.header.deleteNormalOut();
      loop.header.insertOut(loop.exit);

    }
    tempInstr = Goto.create(GOTO, loop.successor.makeJumpTarget());
    tempInstr.setBytecodeIndex(SYNTH_LOOP_VERSIONING_BCI);
    loop.exit.appendInstruction(tempInstr);
    loop.exit.deleteNormalOut();
    loop.exit.insertOut(loop.successor);
  }

  /**
   * Remove unreachable unoptimized loop
   */
  private void removeUnoptimizedLoop(AnnotatedLSTNode loop,
                                     HashMap<BasicBlock, BasicBlock> unoptimizedLoopMap) {
    BasicBlockEnumeration blocks = loop.getBasicBlocks();
    report("removing unoptimized loop");
    BasicBlock block = unoptimizedLoopMap.get(loop.predecessor);
    report("removing block " + block);
    ir.cfg.removeFromCFGAndCodeOrder(block);
    while (blocks.hasMoreElements()) {
      block = unoptimizedLoopMap.get(blocks.next());
      if (!loop.contains(block)) {
        report("removing block " + block);
        ir.cfg.removeFromCFGAndCodeOrder(block);
      } else {
        report("not removing block that's in the original loop" + block);
      }
    }
  }

  /**
   * Put the optimized loop's iterator register into the hash set
   *
   * @param reg register
   */
  private void setOptimizedLoop(Register reg) {
    loopRegisterSet.add(reg);
  }

  /**
   * Check whether the loop that contain such iterator register had
   * been optimized
   *
   * @param reg register
   * @return the test result
   */
  private boolean isOptimizedLoop(Register reg) {
    return loopRegisterSet.contains(reg);
  }

  /**
   * Rename the iterators for optimized loops so we can tell they are still optimized
   */
  private void renameOptimizedLoops(HashMap<Register, Register> subOptimalRegMap,
                                    HashMap<Register, Register> optimalRegMap) {
    Iterator<Register> itr = loopRegisterSet.iterator();
    while (itr.hasNext()) {
      Register reg = itr.next();
      if (subOptimalRegMap.containsKey(reg)) {
        loopRegisterSet.remove(reg);
        loopRegisterSet.add(subOptimalRegMap.get(reg));
        loopRegisterSet.add(optimalRegMap.get(reg));
        itr = loopRegisterSet.iterator();
      }
    }
  }
}
