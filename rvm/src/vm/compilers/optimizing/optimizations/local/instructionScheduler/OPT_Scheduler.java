/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_Scheduler.java
// $Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;

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
 *
 * @author Igor Pechtchanski
 */

final class OPT_Scheduler {
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
  public static final String[] PhaseName = new String[] {
    "Invalid Phase!!!!!!!!", "pre-pass", "post-pass"
  };

  /**
   * Current phase (prepass/postpass).
   */
  private int phase;

  /**
   * Should we print the dependence graph?
   * @param options the options object
   * @return true if we should print depgraph, false otherwise
   */
  private final boolean printDepgraph(OPT_Options options) {
    return (phase == PREPASS && options.PRINT_DG_SCHED_PRE) ||
           (phase == POSTPASS && options.PRINT_DG_SCHED_POST);
  }

  /**
   * Should we produce a visualization the dependence graph?
   * @param options the options object
   * @return true if we should visualize depgraph, false otherwise
   */
  private final boolean vcgDepgraph(OPT_Options options) {
    return (phase == PREPASS && options.VCG_DG_SCHED_PRE) ||
           (phase == POSTPASS && options.VCG_DG_SCHED_POST);
  }

  /**
   * For each basic block, build the dependence graph and
   * perform instruction scheduling.
   * This is an MIR to MIR transformation.
   *
   * @param _ir the IR in question
   */
  final void perform(OPT_IR _ir) {
    // Remember the ir to schedule
    ir = _ir;
    if (verbose >= 1)
      debug("Scheduling " + ir.method.getDeclaringClass() + ' '
            + ir.method.getName()
            + ' ' + ir.method.getDescriptor());

    // Performing live analysis may reduce dependences between PEIs and stores
    if (ir.options.HANDLER_LIVENESS) {  
      new OPT_LiveAnalysis(false, false, true).perform(ir);
    }

    // Create mapping for dependence graph
    i2gn = new OPT_DepGraphNode[ir.numberInstructions()];
    // Create scheduling info for each instruction
    for (OPT_InstructionEnumeration instr = ir.forwardInstrEnumerator();
         instr.hasMoreElements();)
      OPT_SchedulingInfo.createInfo(instr.next());
    // iterate over each basic block
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
         e.hasMoreElements();)
    {
      bb = (OPT_BasicBlock)e.nextElement();
      if (bb.isEmpty())
        continue;
      // HACK: temporarily disable scheduling of unsafe basic blocks.
      // TODO: remove when UNINT_BEGIN/END are working properly.
      if (bb.isUnsafeToSchedule())
        continue;
      // Build Dependence graph
      dg = new OPT_DepGraph(ir, bb.firstInstruction(),
                            bb.lastRealInstruction(), bb);
      if (printDepgraph(ir.options)) {
        // print dependence graph.
        System.out.println("**** START OF " + PhaseName[phase].toUpperCase()
                           + " DEPENDENCE GRAPH ****");
        dg.printDepGraph();
        System.out.println("**** END   OF " + PhaseName[phase].toUpperCase()
                           + " DEPENDENCE GRAPH ****");
      }
      if (vcgDepgraph(ir.options)) {
        // output dependence graph in VCG format.
        // CAUTION: creates A LOT of files (one per BB)
        OPT_VCG.printVCG("depgraph_sched_" + PhaseName[phase] + "_" +
                         ir.method + "_" + bb + ".vcg", dg);
      }
      scheduleBasicBlock();
    }

    // Remove scheduling info for each instruction
    for (OPT_InstructionEnumeration instr = ir.forwardInstrEnumerator();
         instr.hasMoreElements();)
      OPT_SchedulingInfo.removeInfo(instr.next());
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
  OPT_Scheduler(int phase) {
    this.phase = phase;
  }

  /**
   * Debugging level.
   */
  private static final int verbose = 0;

  /**
   * Output debugging information.
   * @param s string to print
   */
  private static final void debug(String s) {
    System.err.println(s);
  }

  /**
   * A string of spaces.
   * Used for indenting.
   */
  private static String SPACES = null;

  /**
   * Output debugging information with indentation.
   * @param depth level of indenting
   * @param s string to print
   */
  private static final void debug(int depth, String s) {
    if (SPACES == null) SPACES = dup(128, ' ');
    if (SPACES.length() < depth*2) SPACES += SPACES;
    debug(SPACES.substring(0, depth*2) + s);
  }

  /**
   * Current IR.
   */
  private OPT_IR ir;

  /**
   * Current basic block.
   */
  private OPT_BasicBlock bb;

  /**
   * Dependence graph for current basic block.
   */
  private OPT_DepGraph dg;

  /**
   * Mapping from OPT_Instruction to OPT_DepGraphNode.
   */
  private OPT_DepGraphNode[] i2gn;

  /**
   * Set corresponding graph node for instruction.
   * @param i given instruction
   * @param n dependence graph node for instruction
   */
  private final void setGraphNode(OPT_Instruction i, OPT_DepGraphNode n) {
    i2gn[i.scratch] = n;
  }

  /**
   * Return corresponding graph node for instruction.
   * @param i given instruction
   */
  private final OPT_DepGraphNode getGraphNode(OPT_Instruction i) {
    return i2gn[i.scratch];
  }

  /**
   * Perform DFS to compute critical path for all instructions.
   * @param n start node
   * @param depth current DFS depth
   */
  private final void computeCriticalPath(OPT_DepGraphNode n, int depth) {
    if (verbose >= 5)
      debug(depth, "Visiting " + n);
    OPT_Instruction i = n.instruction();
    if (OPT_SchedulingInfo.getCriticalPath(i) != -1)
      return;
    int cp = 0;
    for (Enumeration succ = n.outNodes(); succ.hasMoreElements();) {
      OPT_DepGraphNode np = (OPT_DepGraphNode)succ.nextElement();
      OPT_Instruction j = np.instruction();
      computeCriticalPath(np, depth + 1);
      int d = OPT_SchedulingInfo.getCriticalPath(j);
      if (d + 1 > cp)
        cp = d + 1;
    }
    OPT_SchedulingInfo.setCriticalPath(i, cp);
  }

  /**
   * Compute earliest scheduling time for an instruction.
   * @param i given instruction
   */
  private final int computeEarliestTime(OPT_Instruction i) {
    if (verbose >= 5)
      debug("Computing earliest time for " + i);
    OPT_DepGraphNode n = getGraphNode(i);
    int etime = OPT_SchedulingInfo.getEarliestTime(i);
    if (etime == -1)
      etime = 0;
    OPT_OperatorClass opc = i.operator().getOpClass();
    if (verbose >= 7)
      debug("opc=" + opc);
    if (opc == null)
      throw new OPT_OptimizingCompilerException("Missing operator class "
                                                + i.operator());
    for (OPT_GraphNodeEnumeration pred = n.inNodes();
         pred.hasMoreElements(); ) {
      OPT_DepGraphNode np = (OPT_DepGraphNode)pred.next();
      OPT_Instruction j = np.instruction();
      int time = OPT_SchedulingInfo.getTime(j);
      if (verbose >= 6)
        debug("Predecessor " + j + " scheduled at " + time);
      if (time == -1)
        throw new OPT_OptimizingCompilerException(
                     "Instructions not in topological order: " + i + "; " + j);
      if (verbose >= 6)
        debug("Retrieving latency from " + j);
      OPT_OperatorClass joc = j.operator().getOpClass();
      if (verbose >= 7)
        debug("j's class=" + joc);
      if (joc == null)
        throw new OPT_OptimizingCompilerException("Missing operator class " +
                                                  j.operator());
      int lat = joc.latency(opc);
      if (time + lat > etime)
        etime = time + lat;
    }
    if (verbose >= 5)
      debug("Updating time of " + i + " to " + etime);
    OPT_SchedulingInfo.setEarliestTime(i, etime);
    return etime;
  }

  /**
   * A class representing sorted list of instructions.
   * The instructions are sorted by their position on the critical path.
   */
  private static class InstructionBucket {
    /**
     * The instruction in the current slot.
     */
    public OPT_Instruction instruction;
    /**
     * Next pointer.
     */
    public InstructionBucket next;

    /**
     * Create a list element containing the instruction.
     * @param i given instruction
     */
    private InstructionBucket(OPT_Instruction i) {
      instruction = i;
    }

    /**
     * Insert the instruction into a given slot (based on its scheduling time).
     * @param pool the bucket pool
     * @param i given instruction
     */
    public static void insert(InstructionBucket[] pool, OPT_Instruction i) {
      InstructionBucket ib = new InstructionBucket(i);
      int time = OPT_SchedulingInfo.getTime(i);
      if (pool[time] == null) {
        pool[time] = ib;
        return;
      }
      int cp = OPT_SchedulingInfo.getCriticalPath(i);
      OPT_Instruction j = pool[time].instruction;
      if (OPT_SchedulingInfo.getCriticalPath(j) < cp) {
        ib.next = pool[time];
        pool[time] = ib;
        return;
      }
      InstructionBucket p = pool[time];
      InstructionBucket t = p.next;
      while (t != null) {
        j = t.instruction;
        if (OPT_SchedulingInfo.getCriticalPath(j) < cp)
          break;
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
  private final boolean sortBasicBlock(int maxtime) {
    boolean changed = false;
    InstructionBucket[] pool = new InstructionBucket[maxtime + 1];
    int num = bb.firstInstruction().scratch;
    OPT_Instruction ins;
    while ((ins = bb.firstRealInstruction()) != null) {
      InstructionBucket.insert(pool, ins);
      ins.remove();
    }
    for (int i = 0; i <= maxtime; i++)
      for (InstructionBucket t = pool[i]; t != null; t = t.next) {
        bb.appendInstruction(t.instruction);
        changed = changed || num > t.instruction.scratch;
        num = t.instruction.scratch;
      }
    return changed;
  }

  /**
   * Schedule a basic block.
   */
  private final void scheduleBasicBlock() {
    if (verbose >= 2)
      debug("Scheduling " + bb);
    if (verbose >= 4) {
      debug("**** START OF CURRENT BB BEFORE SCHEDULING ****");
      for (OPT_InstructionEnumeration bi = bb.forwardInstrEnumerator();
           bi.hasMoreElements();)
        debug(bi.next().toString());
      debug("**** END   OF CURRENT BB BEFORE SCHEDULING ****");
    }
    // Build mapping from instructions to graph nodes
    for (OPT_DepGraphNode dgn = (OPT_DepGraphNode) dg.firstNode();
         dgn != null; dgn = (OPT_DepGraphNode)dgn.getNext())
    {
      setGraphNode(dgn.instruction(), dgn);
      if (verbose >= 4)
        debug("Added node for " + dgn.instruction());
    }
    OPT_ResourceMap rmap = new OPT_ResourceMap();
    int bl = 0;
    OPT_Instruction fi = bb.firstInstruction();
    if (verbose >= 5)
      debug("Computing critical path for " + fi);
    computeCriticalPath(getGraphNode(fi), 0);
    int cp = OPT_SchedulingInfo.getCriticalPath(fi);
    for (OPT_InstructionEnumeration ie = bb.forwardRealInstrEnumerator();
         ie.hasMoreElements();)
    {
      OPT_Instruction i = ie.next();
      if (verbose >= 5)
        debug("Computing critical path for " + i);
      computeCriticalPath(getGraphNode(i), 0);
      int d = OPT_SchedulingInfo.getCriticalPath(i);
      if (d > cp)
        cp = d;
      bl++;
    }
    cp++;
    if (PRINT_CRITICAL_PATH_LENGTH)
      System.err.println("::: BL=" + bl + " CP=" + cp + " LOC=" + ir.method
                         + ":" + bb);
    OPT_Priority ilist = new OPT_DefaultPriority(ir, bb);
    int maxtime = 0;
    for (ilist.reset(); ilist.hasMoreElements(); ) {
      OPT_Instruction i = ilist.next();
      if (verbose >= 3)
        debug("Scheduling " + i + "[" + OPT_SchedulingInfo.getInfo(i) + "]");
      int time = computeEarliestTime(i);
      while (!rmap.schedule(i, time))
        time++;
      if (verbose >= 5)
        debug("Scheduled " + i + " at time " + time);
      if (time > maxtime)
        maxtime = time;
    }
    if (verbose >= 2)
      debug("Done scheduling " + bb);
    if (verbose >= 3)
      debug(rmap.toString());
    boolean changed = sortBasicBlock(maxtime);
    if (changed && verbose >= 2)
      debug("Basic block " + bb + " changed");
    if (verbose >= 4) {
      debug("**** START OF CURRENT BB AFTER SCHEDULING ****");
      for (OPT_InstructionEnumeration bi = bb.forwardInstrEnumerator();
           bi.hasMoreElements();)
        debug(bi.next().toString());
      debug("**** END   OF CURRENT BB AFTER SCHEDULING ****");
    }
  }

  /**
   * Generates a string of a given length filled by a given character.
   * @param len the length to generate
   * @param c the character to fill the string with
   */
  private static final String dup(int len, char c) {
    StringBuffer ret = new StringBuffer();
    StringBuffer sp2 = new StringBuffer(c);
    int p2 = 1;
    for (int i = 0; i < 32; i++) {
      if ((len & p2) != 0)
        ret.append(sp2);
      sp2.append(sp2);
      p2 <<= 1;
    }
    return ret.toString();
  }
}

