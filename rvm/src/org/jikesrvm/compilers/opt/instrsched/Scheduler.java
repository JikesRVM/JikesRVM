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
package org.jikesrvm.compilers.opt.instrsched;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.depgraph.DepGraph;
import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.util.GraphNodeEnumeration;

/**
 * Instruction Scheduler
 *
 * It is a simple list scheduler
 *
 * This class is declared as "final" which implies that all its methods
 * are "final" too.
 *
 * TODO:
 * - Add more priority lists
 *
 * - When scheduling an instruction, verify that its predecessors have
 *   already been scheduled.
 *
 * - Change forward propagation of earliest time to computing it from the
 *   scheduling time of predecessors + latencies.
 *
 * - Change bubble sort to insertion sort.
 */

final class Scheduler {
  /**
   * Debugging level.
   */
  private static final int VERBOSE = 0;

  /**
   * Should we print the length of the critical path for each basic block?
   */
  private static final boolean PRINT_CRITICAL_PATH_LENGTH = false;

  /**
   * A constant signifying pre-pass scheduling phase.
   */
  public static final int PREPASS = 1;

  /**
   * A constant signifying post-pass scheduling phase.
   * WARNING: POSTPASS INSTRUCTION SCHEDULING (AFTER REGISTER ALLOCATION)
   * Cannot be done safely due to failure to update GCMapping information.
   * --dave.
   */
  public static final int POSTPASS = 2;

  /**
   * Names of various scheduling phases.
   */
  public static final String[] PhaseName = new String[]{"Invalid Phase!!!!!!!!", "pre-pass", "post-pass"};

  /**
   * Current phase (prepass/postpass).
   */
  private final int phase;

  /**
   * Current IR.
   */
  private IR ir;

  /**
   * Current basic block.
   */
  private BasicBlock bb;

  /**
   * Dependence graph for current basic block.
   */
  private DepGraph dg;

  /**
   * Mapping from Instruction to DepGraphNode.
   */
  private DepGraphNode[] i2gn;

  /**
   * Should we print the dependence graph?
   * @param options the options object
   * @return true if we should print depgraph, false otherwise
   */
  private boolean printDepgraph(OptOptions options) {
    return (phase == PREPASS && options.PRINT_DG_SCHED_PRE) || (phase == POSTPASS && options.PRINT_DG_SCHED_POST);
  }

  /**
   * For each basic block, build the dependence graph and
   * perform instruction scheduling.
   * This is an MIR to MIR transformation.
   *
   * @param _ir the IR in question
   */
  void perform(IR _ir) {
    // Remember the ir to schedule
    ir = _ir;
    if (VERBOSE >= 1) {
      debug("Scheduling " +
            ir.method.getDeclaringClass() +
            ' ' +
            ir.method.getName() +
            ' ' +
            ir.method.getDescriptor());
    }

    // Performing live analysis may reduce dependences between PEIs and stores
    if (ir.options.L2M_HANDLER_LIVENESS) {
      new LiveAnalysis(false, false, true).perform(ir);
    }

    // Create mapping for dependence graph
    i2gn = new DepGraphNode[ir.numberInstructions()];
    // Create scheduling info for each instruction
    for (InstructionEnumeration instr = ir.forwardInstrEnumerator(); instr.hasMoreElements();) {
      SchedulingInfo.createInfo(instr.next());
    }
    // iterate over each basic block
    for (BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      bb = e.nextElement();
      if (bb.isEmpty()) {
        continue;
      }
      // HACK: temporarily disable scheduling of unsafe basic blocks.
      // TODO: remove when UNINT_BEGIN/END are working properly.
      if (bb.isUnsafeToSchedule()) {
        continue;
      }
      // Build Dependence graph
      dg = new DepGraph(ir, bb.firstInstruction(), bb.lastRealInstruction(), bb);
      if (printDepgraph(ir.options)) {
        // print dependence graph.
        System.out.println("**** START OF " + PhaseName[phase].toUpperCase() + " DEPENDENCE GRAPH ****");
        dg.printDepGraph();
        System.out.println("**** END   OF " + PhaseName[phase].toUpperCase() + " DEPENDENCE GRAPH ****");
      }
      scheduleBasicBlock();
    }

    // Remove scheduling info for each instruction
    for (InstructionEnumeration instr = ir.forwardInstrEnumerator(); instr.hasMoreElements();) {
      SchedulingInfo.removeInfo(instr.next());
    }
    // Remove mapping for dependence graph
    i2gn = null;
    // Clear the ir to schedule
    bb = null;
    dg = null;
    ir = null;
  }

  /**
   * Initialize scheduler for a given phase.
   *
   * @param phase the scheduling phase
   */
  Scheduler(int phase) {
    this.phase = phase;
  }

  /**
   * Output debugging information.
   * @param s string to print
   */
  private static void debug(String s) {
    System.err.println(s);
  }

  /**
   * Output debugging information with indentation.
   * @param depth level of indenting
   * @param s string to print
   */
  private static void debug(int depth, String s) {
    String format = String.format("%% %ds", depth*2);
    debug(String.format(format, s));
  }

  /**
   * Set corresponding graph node for instruction.
   * @param i given instruction
   * @param n dependence graph node for instruction
   */
  private void setGraphNode(Instruction i, DepGraphNode n) {
    i2gn[i.scratch] = n;
  }

  /**
   * Return corresponding graph node for instruction.
   * @param i given instruction
   */
  private DepGraphNode getGraphNode(Instruction i) {
    return i2gn[i.scratch];
  }

  /**
   * Perform DFS to compute critical path for all instructions.
   * @param n start node
   * @param depth current DFS depth
   */
  private void computeCriticalPath(DepGraphNode n, int depth) {
    if (VERBOSE >= 5) {
      debug(depth, "Visiting " + n);
    }
    Instruction i = n.instruction();
    if (SchedulingInfo.getCriticalPath(i) != -1) {
      return;
    }
    int cp = 0;
    for (GraphNodeEnumeration succ = n.outNodes(); succ.hasMoreElements();) {
      DepGraphNode np = (DepGraphNode) succ.nextElement();
      Instruction j = np.instruction();
      computeCriticalPath(np, depth + 1);
      int d = SchedulingInfo.getCriticalPath(j);
      if (d + 1 > cp) {
        cp = d + 1;
      }
    }
    SchedulingInfo.setCriticalPath(i, cp);
  }

  /**
   * Compute earliest scheduling time for an instruction.
   * @param i given instruction
   */
  private int computeEarliestTime(Instruction i) {
    if (VERBOSE >= 5) {
      debug("Computing earliest time for " + i);
    }
    DepGraphNode n = getGraphNode(i);
    int etime = SchedulingInfo.getEarliestTime(i);
    if (etime == -1) {
      etime = 0;
    }
    OperatorClass opc = i.operator().getOpClass();
    if (VERBOSE >= 7) {
      debug("opc=" + opc);
    }
    if (opc == null) {
      throw new OptimizingCompilerException("Missing operator class " + i.operator());
    }
    for (GraphNodeEnumeration pred = n.inNodes(); pred.hasMoreElements();) {
      DepGraphNode np = (DepGraphNode) pred.next();
      Instruction j = np.instruction();
      int time = SchedulingInfo.getTime(j);
      if (VERBOSE >= 6) {
        debug("Predecessor " + j + " scheduled at " + time);
      }
      if (time == -1) {
        throw new OptimizingCompilerException("Instructions not in topological order: " + i + "; " + j);
      }
      if (VERBOSE >= 6) {
        debug("Retrieving latency from " + j);
      }
      OperatorClass joc = j.operator().getOpClass();
      if (VERBOSE >= 7) {
        debug("j's class=" + joc);
      }
      if (joc == null) {
        throw new OptimizingCompilerException("Missing operator class " + j.operator());
      }
      int lat = joc.latency(opc);
      if (time + lat > etime) {
        etime = time + lat;
      }
    }
    if (VERBOSE >= 5) {
      debug("Updating time of " + i + " to " + etime);
    }
    SchedulingInfo.setEarliestTime(i, etime);
    return etime;
  }

  /**
   * A class representing sorted list of instructions.
   * The instructions are sorted by their position on the critical path.
   */
  private static final class InstructionBucket {
    /**
     * The instruction in the current slot.
     */
    public Instruction instruction;
    /**
     * Next pointer.
     */
    public InstructionBucket next;

    /**
     * Create a list element containing the instruction.
     * @param i given instruction
     */
    private InstructionBucket(Instruction i) {
      instruction = i;
    }

    /**
     * Insert the instruction into a given slot (based on its scheduling time).
     * @param pool the bucket pool
     * @param i given instruction
     */
    public static void insert(InstructionBucket[] pool, Instruction i) {
      InstructionBucket ib = new InstructionBucket(i);
      int time = SchedulingInfo.getTime(i);
      if (pool[time] == null) {
        pool[time] = ib;
        return;
      }
      int cp = SchedulingInfo.getCriticalPath(i);
      Instruction j = pool[time].instruction;
      if (SchedulingInfo.getCriticalPath(j) < cp) {
        ib.next = pool[time];
        pool[time] = ib;
        return;
      }
      InstructionBucket p = pool[time];
      InstructionBucket t = p.next;
      while (t != null) {
        j = t.instruction;
        if (SchedulingInfo.getCriticalPath(j) < cp) {
          break;
        }
        p = t;
        t = t.next;
      }
      ib.next = t;
      p.next = ib;
    }
  }

  /**
   * Sort basic block by Scheduled Time.
   * Uses bucket sort on time, with equal times ordered by critical path.
   * @param maxtime the maximum scheduled time
   */
  private boolean sortBasicBlock(int maxtime) {
    boolean changed = false;
    InstructionBucket[] pool = new InstructionBucket[maxtime + 1];
    int num = bb.firstInstruction().scratch;
    Instruction ins;
    while ((ins = bb.firstRealInstruction()) != null) {
      InstructionBucket.insert(pool, ins);
      ins.remove();
    }
    for (int i = 0; i <= maxtime; i++) {
      for (InstructionBucket t = pool[i]; t != null; t = t.next) {
        bb.appendInstruction(t.instruction);
        changed = changed || num > t.instruction.scratch;
        num = t.instruction.scratch;
      }
    }
    return changed;
  }

  /**
   * Schedule a basic block.
   */
  private void scheduleBasicBlock() {
    if (VERBOSE >= 2) {
      debug("Scheduling " + bb);
    }
    if (VERBOSE >= 4) {
      debug("**** START OF CURRENT BB BEFORE SCHEDULING ****");
      for (InstructionEnumeration bi = bb.forwardInstrEnumerator(); bi.hasMoreElements();) {
        debug(bi.next().toString());
      }
      debug("**** END   OF CURRENT BB BEFORE SCHEDULING ****");
    }
    // Build mapping from instructions to graph nodes
    for (DepGraphNode dgn = (DepGraphNode) dg.firstNode(); dgn != null; dgn = (DepGraphNode) dgn.getNext())
    {
      setGraphNode(dgn.instruction(), dgn);
      if (VERBOSE >= 4) {
        debug("Added node for " + dgn.instruction());
      }
    }
    ResourceMap rmap = new ResourceMap();
    int bl = 0;
    Instruction fi = bb.firstInstruction();
    if (VERBOSE >= 5) {
      debug("Computing critical path for " + fi);
    }
    computeCriticalPath(getGraphNode(fi), 0);
    int cp = SchedulingInfo.getCriticalPath(fi);
    for (InstructionEnumeration ie = bb.forwardRealInstrEnumerator(); ie.hasMoreElements();) {
      Instruction i = ie.next();
      if (VERBOSE >= 5) {
        debug("Computing critical path for " + i);
      }
      computeCriticalPath(getGraphNode(i), 0);
      int d = SchedulingInfo.getCriticalPath(i);
      if (d > cp) {
        cp = d;
      }
      bl++;
    }
    cp++;
    if (PRINT_CRITICAL_PATH_LENGTH) {
      System.err.println("::: BL=" + bl + " CP=" + cp + " LOC=" + ir.method + ":" + bb);
    }
    Priority ilist = new DefaultPriority(bb);
    int maxtime = 0;
    for (ilist.reset(); ilist.hasMoreElements();) {
      Instruction i = ilist.next();
      if (VERBOSE >= 3) {
        debug("Scheduling " + i + "[" + SchedulingInfo.getInfo(i) + "]");
      }
      int time = computeEarliestTime(i);
      while (!rmap.schedule(i, time)) {
        time++;
      }
      if (VERBOSE >= 5) {
        debug("Scheduled " + i + " at time " + time);
      }
      if (time > maxtime) {
        maxtime = time;
      }
    }
    if (VERBOSE >= 2) {
      debug("Done scheduling " + bb);
    }
    if (VERBOSE >= 3) {
      debug(rmap.toString());
    }
    boolean changed = sortBasicBlock(maxtime);
    if (changed && VERBOSE >= 2) {
      debug("Basic block " + bb + " changed");
    }
    if (VERBOSE >= 4) {
      debug("**** START OF CURRENT BB AFTER SCHEDULING ****");
      for (InstructionEnumeration bi = bb.forwardInstrEnumerator(); bi.hasMoreElements();) {
        debug(bi.next().toString());
      }
      debug("**** END   OF CURRENT BB AFTER SCHEDULING ****");
    }
  }
}

