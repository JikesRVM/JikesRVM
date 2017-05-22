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
package org.jikesrvm.compilers.opt.regalloc;

import static org.jikesrvm.compilers.opt.OptimizingCompilerException.opt_assert;
import static org.jikesrvm.compilers.opt.ir.Operators.NOP;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.liveness.LiveInterval;

/**
 * phase to compute linear scan intervals.
 */
public final class IntervalAnalysis extends CompilerPhase {
  /**
   * the governing ir
   */
  private IR ir;

  private RegisterAllocatorState regAllocState;

  /**
   * a list of basic blocks in topological order
   */
  private BasicBlock listOfBlocks;

  /**
   *  a reverse topological list of basic blocks
   */
  private BasicBlock reverseTopFirst;

  /**
   * Mark FMOVs that end a live range?
   */
  private static final boolean MUTATE_FMOV = VM.BuildForIA32;

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(IntervalAnalysis.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * should we perform this phase? yes.
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * a name for this phase.
   */
  @Override
  public String getName() {
    return "Interval Analysis";
  }

  /**
   * should we print the ir?
   */
  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * compute live intervals for this ir
   * the result is a sorted (by beginning point) set of compound
   * intervals, stored in the private 'intervals' field.
   *
   * @param ir the ir
   */
  @Override
  public void perform(IR ir) {
    this.ir = ir;
    this.regAllocState = ir.MIRInfo.regAllocState;

    ControlFlowGraph cfg = ir.cfg;
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    LinearScanState state = new LinearScanState();
    ir.MIRInfo.linearScanState = state;

    // create topological list and a reverse topological list
    // the results are on listOfBlocks and reverseTopFirst lists
    createTopAndReverseList(cfg);

    // give dfn values to each instruction
    assignDepthFirstNumbers(cfg);

    // initialize registers
    initializeRegisters();

    int lastBeginSeen = -1;

    // visit each basic block in the listOfBlocks list
    for (BasicBlock bb = listOfBlocks; bb != null; bb = (BasicBlock) bb.nextSorted) {

      // visit each live interval for this basic block
      LiveInterval liveIntervals = ir.getLivenessInformation();
      for (LiveIntervalElement live = liveIntervals.getFirstLiveIntervalElement(bb); live != null; live = live.getNext()) {

        // check that we process live intervals in order of increasing
        // begin.
        if (VM.VerifyAssertions) {
          int begin = regAllocState.getDfnBegin(live, bb);
          VM._assert(begin >= lastBeginSeen);
          lastBeginSeen = begin;
        }

        // skip registers which are not allocated.
        if (live.getRegister().isPhysical() && !phys.isAllocatable(live.getRegister())) {
          continue;
        }

        CompoundInterval resultingInterval = processLiveInterval(live, bb);
        if (!bb.getInfrequent() && resultingInterval != null) {
          resultingInterval.setFrequent();
        }
      }
    }

    // debug support
    if (LinearScan.VERBOSE_DEBUG) {
      VM.sysWriteln("**** start of interval dump " + ir.method + " ****");
      VM.sysWrite(ir.MIRInfo.linearScanState.intervals.toString());
      VM.sysWriteln("**** end   of interval dump ****");
    }
  }

  /**
   *  create topological list and a reverse topological list
   *  the results are on listOfBlocks and reverseTopFirst lists
   *  @param cfg the control flow graph
   */
  private void createTopAndReverseList(ControlFlowGraph cfg) {
    // dfs: create a list of nodes (basic blocks) in a topological order
    cfg.clearDFS();
    listOfBlocks = cfg.entry();
    listOfBlocks.sortDFS();

    // this loop reverses the topological list by using the sortedPrev field
    reverseTopFirst = null;
    for (BasicBlock bb = listOfBlocks; bb != null; bb = (BasicBlock) bb.nextSorted) {

      // put back pointers in the "prev" field
      // set reverseTopFirst to be the more recent node we've seen,
      // it will be the front of the list when we are done
      bb.sortedPrev = reverseTopFirst;
      reverseTopFirst = bb;
    }
  }

  /**
   *  this method processes all basic blocks, do the following to each block
   *   1) add it to the begining of the "listOfBlocks" list
   *   2) number the instructions
   *   3) process the instructions that restrict physical register
   *   assignment
   *  @param cfg the control flow graph
   */
  void assignDepthFirstNumbers(ControlFlowGraph cfg) {
    int instructionCount = ir.countInstructions();
    regAllocState.initializeDepthFirstNumbering(instructionCount);

    int curDfn = instructionCount - 1;
    listOfBlocks = null;
    for (BasicBlock bb = reverseTopFirst; bb != null; bb = (BasicBlock) bb.sortedPrev) {

      // insert bb at the front of the list
      bb.nextSorted = listOfBlocks;
      listOfBlocks = bb;

      // number the instructions last to first
      Enumeration<Instruction> e = bb.reverseInstrEnumerator();
      while (e.hasMoreElements()) {
        Instruction inst = e.nextElement();
        regAllocState.setDFN(inst, curDfn);
        curDfn--;
      }
    }

    if (LinearScan.DEBUG) {
      regAllocState.printDfns(ir);
    }
  }

  /**
   * Initialize the interval for each register to null.
   */
  private void initializeRegisters() {
    RegisterAllocatorState regAllocState = ir.MIRInfo.regAllocState;

    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      regAllocState.setInterval(reg, null);
      regAllocState.setSpill(reg, 0);
      // clear the 'long' type if it's persisted to here.
      if (VM.BuildFor32Addr && reg.isLong()) {
        if (VM.VerifyAssertions) {
          opt_assert(reg.isLong(), "Found long-typed register on 32-bit.");
        } else {
          reg.clearType();
          reg.setInteger();
        }
      }
    }
  }

  /**
   * Mutate FMOVs that end live ranges
   *
   * @param live The live interval for a basic block/reg pair
   * @param register The register for this live interval
   * @param dfnbegin The (adjusted) begin for this interval
   * @param dfnend The (adjusted) end for this interval
   * @return whether an actual change was necessary (as opposed to
   *  simple removal because the end was dead)
   */
  private boolean mutateFMOVs(LiveIntervalElement live, Register register, int dfnbegin, int dfnend) {
    Instruction end = live.getEnd();
    if (end != null && end.operator() == org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FMOV) {
      if (dfnend == dfnbegin) {
        // if end, an FMOV, both begins and ends the live range,
        // then end is dead.  Change it to a NOP and return null.
        Empty.mutate(end, NOP);
        return false;
      } else {
        if (!end.isPEI()) {
          if (VM.VerifyAssertions) {
            Operand value = org.jikesrvm.compilers.opt.ir.ia32.MIR_Move.getValue(end);
            VM._assert(value.isRegister());
            VM._assert(org.jikesrvm.compilers.opt.ir.ia32.MIR_Move.getValue(end).asRegister().getRegister() == register);
          }
          end.changeOperatorTo(org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FMOV_ENDING_LIVE_RANGE);
        }
      }
    }
    return true;
  }

  /**
   * for each live interval associated with this block
   * we either add a new interval, or extend a previous interval
   * if it is contiguous
   *
   * @param live the liveintervalelement for a basic block/reg pair
   * @param bb the basic block
   * @return the resulting CompoundInterval. null if the live interval
   * is not relevant to register allocation.
   */
  private CompoundInterval processLiveInterval(LiveIntervalElement live, BasicBlock bb) {

    // get the reg and (adjusted) begin, end pair for this interval
    Register reg = live.getRegister();
    int dfnend = regAllocState.getDfnEnd(live, bb);
    int dfnbegin = regAllocState.getDfnBegin(live, bb);

    if (MUTATE_FMOV && reg.isFloatingPoint()) {
      mutateFMOVs(live, reg, dfnbegin, dfnend);
    }

    // check for an existing live interval for this register
    CompoundInterval existingInterval = regAllocState.getInterval(reg);
    if (existingInterval == null) {
      // create a new live interval
      CompoundInterval newInterval = new CompoundInterval(dfnbegin, dfnend, reg);
      if (LinearScan.VERBOSE_DEBUG) System.out.println("created a new interval " + newInterval);

      // associate the interval with the register
      regAllocState.setInterval(reg, newInterval);

      // add the new interval to the sorted set of intervals.
      BasicInterval b = newInterval.first();
      ir.MIRInfo.linearScanState.intervals.add(b);

      return newInterval;

    } else {
      // add the new live range to the existing interval
      ArrayList<BasicInterval> intervals = ir.MIRInfo.linearScanState.intervals;
      BasicInterval added = existingInterval.addRange(regAllocState, live, bb);
      if (added != null) {
        intervals.add(added);
      }
      if (LinearScan.VERBOSE_DEBUG) System.out.println("Extended old interval " + reg);
      if (LinearScan.VERBOSE_DEBUG) System.out.println(existingInterval);

      return existingInterval;
    }
  }
}
