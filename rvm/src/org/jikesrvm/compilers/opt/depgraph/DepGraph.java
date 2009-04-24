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
package org.jikesrvm.compilers.opt.depgraph;

import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.CONTROL;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.EXCEPTION_E;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.EXCEPTION_MS;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.EXCEPTION_R;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.GUARD_ANTI;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.GUARD_OUTPUT;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.GUARD_TRUE;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.MEM_ANTI;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.MEM_OUTPUT;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.MEM_READS_KILL;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.MEM_TRUE;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.REG_ANTI;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.REG_MAY_DEF;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.REG_OUTPUT;
import static org.jikesrvm.compilers.opt.depgraph.DepGraphConstants.REG_TRUE;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CAUGHT_EXCEPTION;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_TIME_BASE;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.SET_CAUGHT_EXCEPTION;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_BEGIN;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_END;

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalDefUse;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.LocationCarrier;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.liveness.LiveSet;
import org.jikesrvm.compilers.opt.util.SpaceEffGraph;

/**
 * Dependence Graph for a single basic block in the program.
 *
 * <p> June 1998 extensions by Vivek Sarkar:
 * <ul>
 * <li> 1. Fix direction of register anti dependences
 * <li> 2. Add conservative memory dependences (suitable for low opt level)
 * </ul>
 *
 * <p>Jul 1998, Harini Srinivasan:
 * made node list doubly linked list. Changes reflected in
 * depgraph construction. No calls to getDepGraphNode().
 *
 * <p> Dec 1998-March 1999, Mauricio Serrano:
 * several modifications to the memory efficiency of the graph.
 * added edges for calls.
 *
 * <p> 2000-2001, Dave Grove:
 * <ul>
 * <li> add support to handle implicit def/uses of physical registers correctly
 * <li> large scale refactor and cleanup
 * <li> more precise treatment of exceptions, control and acquire/release
 * </ul>
 */
public final class DepGraph extends SpaceEffGraph {

  /**
   * Set of variables that are live on entry to at least one catch block that
   * is reachable via a PEI in currentBlock.
   * This is an approximatation of the more precise set, but can be done in
   * linear time; doing the most precise thing (computing the set for
   * every PEI and using each individual set to create the necessary
   * dependences) is quadratic, and probably doesn't help very much anyways.
   */
  private final LiveSet handlerLiveSet;

  /**
   * The basic block we are processing
   */
  private final BasicBlock currentBlock;

  /**
   * The ir we are processing
   */
  private final IR ir;

  /**
   * Constructor (computes the dependence graph!).
   *
   * @param ir the IR to compute the dependence graph for
   * @param start instruction to start computation from
   * @param end instruction to end computation at
   * @param currentBlock the basic block that the instructions are living in
   */
  public DepGraph(IR ir, Instruction start, Instruction end, BasicBlock currentBlock) {
    this.currentBlock = currentBlock;
    this.ir = ir;
    handlerLiveSet = new LiveSet();
    computeHandlerLiveSet();
    createNodes(start, end);
    computeForwardDependences(start, end);
    computeBackwardDependences(start, end);
    computeControlAndBarrierDependences(start, end);
  }

  /**
   * Determine the set of variables live on entry to any handler
   * block that is reachable from currentBlock
   */
  private void computeHandlerLiveSet() {
    if (ir.getHandlerLivenessComputed() && currentBlock.hasExceptionHandlers()) {
      BasicBlockEnumeration e = currentBlock.getExceptionalOut();
      while (e.hasMoreElements()) {
        ExceptionHandlerBasicBlock handlerBlock = (ExceptionHandlerBasicBlock) e.next();
        handlerLiveSet.add(handlerBlock.getLiveSet());
      }
    }
  }

  /**
   * Create the dependency graph nodes for instructions start to end
   */
  private void createNodes(Instruction start, Instruction end) {
    for (Instruction p = start; ; p = p.nextInstructionInCodeOrder()) {
      DepGraphNode pnode = new DepGraphNode(p);
      addGraphNode(pnode);
      if (p == end) {
        break;
      }
    }
  }

  /**
   * Compute flow and output dependences by doing a forward
   * traversal of the instructions from start to end.
   */
  private void computeForwardDependences(Instruction start, Instruction end) {
    boolean readsKill = ir.options.READS_KILL;
    DepGraphNode lastStoreNode = null;
    DepGraphNode lastExceptionNode = null;
    DepGraphNode lastLoadNode = null; // only used if reads kill

    clearRegisters(start, end);

    for (DepGraphNode pnode = (DepGraphNode) firstNode(); pnode != null; pnode =
        (DepGraphNode) pnode.getNext()) {
      Instruction p = pnode.instruction();

      // (1) Add edges due to registers
      int useMask = p.operator().implicitUses;
      int defMask = p.operator().implicitDefs;
      if (p.isTSPoint()) {
        useMask |= PhysicalDefUse.maskTSPUses;
        defMask |= PhysicalDefUse.maskTSPDefs;
      }
      for (OperandEnumeration uses = p.getUses(); uses.hasMoreElements();) {
        computeForwardDependencesUse(uses.next(), pnode, lastExceptionNode);
      }
      for (PhysicalDefUse.PDUEnumeration uses = PhysicalDefUse.enumerate(useMask, ir); uses.hasMoreElements();)
      {
        Register r = uses.nextElement();
        computeImplicitForwardDependencesUse(r, pnode);
      }
      for (OperandEnumeration defs = p.getDefs(); defs.hasMoreElements();) {
        computeForwardDependencesDef(defs.next(), pnode, lastExceptionNode);
      }
      for (PhysicalDefUse.PDUEnumeration defs = PhysicalDefUse.enumerate(defMask, ir); defs.hasMoreElements();)
      {
        Register r = defs.nextElement();
        computeImplicitForwardDependencesDef(r, pnode);
      }

      // (2) Add edges due to memory
      boolean isStore = p.isImplicitStore();
      boolean isLoad = p.isImplicitLoad();
      if (isStore || isLoad) {
        // If readsKill then add memory model memory dependence from prior load
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        if (readsKill && isLoad) {
          for (DepGraphNode lnode = lastLoadNode; lnode != null; lnode = (DepGraphNode) lnode.getPrev()) {
            if (lnode.instruction().isImplicitLoad() &&
                LocationOperand.mayBeAliased(getLocation(p), getLocation(lnode.instruction()))) {
              lnode.insertOutEdge(pnode, MEM_READS_KILL);
            }
          }
          lastLoadNode = pnode;
        }
        // Add output/flow memory dependence from prior potentially aliased stores.
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        for (DepGraphNode snode = lastStoreNode; snode != null; snode = (DepGraphNode) snode.getPrev()) {
          if (snode.instruction().isImplicitStore() &&
              LocationOperand.mayBeAliased(getLocation(p), getLocation(snode.instruction()))) {
            snode.insertOutEdge(pnode, isStore ? MEM_OUTPUT : MEM_TRUE);
          }
        }
        if (isStore) {
          lastStoreNode = pnode;
          if (lastExceptionNode != null) {
            lastExceptionNode.insertOutEdge(pnode, EXCEPTION_MS);
          }
        }
      }

      // (3) Add edges due to exception state/exceptional control flow.
      if (p.isPEI()) {
        if (lastExceptionNode != null) {
          lastExceptionNode.insertOutEdge(pnode, EXCEPTION_E);
        }
        lastExceptionNode = pnode;
      }
    }
  }

  /**
   * Compute anti dependences by doing a backwards
   * traversal of the instructions from start to end.
   */
  private void computeBackwardDependences(Instruction start, Instruction end) {
    clearRegisters(start, end);

    DepGraphNode lastStoreNode = null;
    DepGraphNode lastExceptionNode = null;
    for (DepGraphNode pnode = (DepGraphNode) lastNode(); pnode != null; pnode =
        (DepGraphNode) pnode.getPrev()) {
      Instruction p = pnode.instruction();

      // (1) Add edges due to registers
      int useMask = p.operator().implicitUses;
      int defMask = p.operator().implicitDefs;
      if (p.isTSPoint()) {
        useMask |= PhysicalDefUse.maskTSPUses;
        defMask |= PhysicalDefUse.maskTSPDefs;
      }
      for (OperandEnumeration uses = p.getUses(); uses.hasMoreElements();) {
        computeBackwardDependencesUse(uses.next(), pnode, lastExceptionNode);
      }
      for (PhysicalDefUse.PDUEnumeration uses = PhysicalDefUse.enumerate(useMask, ir); uses.hasMoreElements();)
      {
        Register r = uses.nextElement();
        computeImplicitBackwardDependencesUse(r, pnode);
      }
      for (OperandEnumeration defs = p.getDefs(); defs.hasMoreElements();) {
        computeBackwardDependencesDef(defs.next(), pnode, lastExceptionNode);
      }
      for (PhysicalDefUse.PDUEnumeration defs = PhysicalDefUse.enumerate(defMask, ir); defs.hasMoreElements();)
      {
        Register r = defs.nextElement();
        computeImplicitBackwardDependencesDef(r, pnode);
      }

      // (2) Add edges due to memory
      boolean isStore = p.isImplicitStore();
      boolean isLoad = p.isImplicitLoad();
      if (isStore) {
        if (lastExceptionNode != null) {
          pnode.insertOutEdge(lastExceptionNode, EXCEPTION_MS);
        }
        lastStoreNode = pnode;
      } else if (isLoad) {
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        for (DepGraphNode snode = lastStoreNode; snode != null; snode = (DepGraphNode) snode.getNext()) {
          if (snode.instruction().isImplicitStore() &&
              LocationOperand.mayBeAliased(getLocation(p), getLocation(snode.instruction()))) {
            pnode.insertOutEdge(snode, MEM_ANTI);
          }
        }
      }

      if (p.isPEI()) {
        lastExceptionNode = pnode;
      }
    }
  }

  /**
   * Compute control and barrier (acquire/release) dependences
   * in two passes (one forward, one reverse over the instructions
   * from start to end.
   */
  private void computeControlAndBarrierDependences(Instruction start, Instruction end) {
    // (1) In a forward pass, we add the following dependences:
    //    a) No load instruction may rise above an acquire
    //    b) No instruction may rise above an UNINT_BEGIN (conservative),
    //       a yieldpoint (we placed the yieldpoints where we wanted them),
    //       a GET_CAUGHT_EXCEPTION, or an IR_PROLOGUE.
    //    c) No GC point may rise above an UNINT_END
    DepGraphNode lastTotalBarrier = null;
    DepGraphNode lastGCBarrier = null;
    DepGraphNode lastAcquire = null;
    for (DepGraphNode pnode = (DepGraphNode) firstNode(); pnode != null; pnode =
        (DepGraphNode) pnode.getNext()) {
      Instruction p = pnode.instruction();
      if (lastTotalBarrier != null) {
        lastTotalBarrier.insertOutEdge(pnode, CONTROL);
      }
      if (lastGCBarrier != null) {
        lastGCBarrier.insertOutEdge(pnode, CONTROL);
      }
      if (lastAcquire != null && p.isImplicitLoad()) {
        lastAcquire.insertOutEdge(pnode, CONTROL);
      }
      Operator pop = p.operator();
      if (p.isYieldPoint() || pop == IR_PROLOGUE || pop == UNINT_BEGIN || pop == GET_TIME_BASE || pop == GET_CAUGHT_EXCEPTION) {
        lastTotalBarrier = pnode;
      }
      if (pop == UNINT_END) {
        lastGCBarrier = pnode;
      }
      if (p.isAcquire() || p.isDynamicLinkingPoint()) {
        lastAcquire = pnode;
      }
    }

    // (2) In a backward pass we add the following dependences:
    //    a) No store instruction may sink below a release.
    //    b) No instruction may sink below an UNINT_END (conservative),
    //       a branch/return, a SET_CAUGHT_EXCEPTION, or a yieldpoint
    //       (again want to pin yieldpoints).
    //    c) No GC point may sink below an UNINT_BEGIN
    lastTotalBarrier = null;
    lastGCBarrier = null;
    DepGraphNode lastRelease = null;
    for (DepGraphNode pnode = (DepGraphNode) lastNode(); pnode != null; pnode =
        (DepGraphNode) pnode.getPrev()) {
      Instruction p = pnode.instruction();
      if (lastTotalBarrier != null) {
        pnode.insertOutEdge(lastTotalBarrier, CONTROL);
      }
      if (lastGCBarrier != null) {
        pnode.insertOutEdge(lastGCBarrier, CONTROL);
      }
      if (lastRelease != null && p.isImplicitStore()) {
        pnode.insertOutEdge(lastRelease, CONTROL);
      }
      Operator pop = p.operator();
      if (p.isBranch() || p.isReturn() || p.isYieldPoint() || pop == UNINT_END || pop == GET_TIME_BASE || pop == SET_CAUGHT_EXCEPTION) {
        lastTotalBarrier = pnode;
      }
      if (pop == UNINT_BEGIN) {
        lastGCBarrier = pnode;
      }
      if (p.isRelease() || p.isDynamicLinkingPoint()) {
        lastRelease = pnode;
      }
    }
  }

  /**
   * Compute forward dependences from a given use to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeForwardDependencesUse(Operand op, DepGraphNode destNode,
                                            DepGraphNode lastExceptionNode) {
    if (!(op instanceof RegisterOperand)) return;
    RegisterOperand regOp = (RegisterOperand) op;
    DepGraphNode sourceNode = regOp.getRegister().dNode();

    // if there is an element in the regTableDef[regNum] slot, set
    // the flow dependence edge.
    if (sourceNode != null) {
      if (regOp.getRegister().isValidation()) {
        sourceNode.insertOutEdge(destNode, GUARD_TRUE);
      } else {
        for (PhysicalDefUse.PDUEnumeration e =
            PhysicalDefUse.enumerate(PhysicalDefUse.maskTSPDefs, ir); e.hasMoreElements();) {
          Register r = e.nextElement();
          if (regOp.getRegister() == r) {
            sourceNode.insertOutEdge(destNode, REG_MAY_DEF);
            return;
          }
        }
        sourceNode.insertRegTrueOutEdge(destNode, regOp);
      }
    }
  }

  /**
   * Compute forward dependences from a given def to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeForwardDependencesDef(Operand op, DepGraphNode destNode,
                                            DepGraphNode lastExceptionNode) {
    if (!(op instanceof RegisterOperand)) return;
    RegisterOperand regOp = (RegisterOperand)op;
    DepGraphNode sourceNode = regOp.getRegister().dNode();

    if (sourceNode != null) {
      // create output dependence edge from sourceNode to destNode.
      int type = regOp.getRegister().isValidation() ? GUARD_OUTPUT : REG_OUTPUT;
      sourceNode.insertOutEdge(destNode, type);
    }

    // pin the def below the previous exception node if the register
    // being defined may be live in some reachable catch block
    if (lastExceptionNode != null && regOp.getRegister().spansBasicBlock() && currentBlock.hasExceptionHandlers()) {
      if (!ir.getHandlerLivenessComputed() || handlerLiveSet.contains(regOp.getRegister())) {
        lastExceptionNode.insertOutEdge(destNode, EXCEPTION_R);
      }
    }

    // update depGraphNode information in register.
    regOp.getRegister().setdNode(destNode);
  }

  /**
   * Compute backward dependences from a given use to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeBackwardDependencesUse(Operand op, DepGraphNode destNode,
                                             DepGraphNode lastExceptionNode) {
    if (!(op instanceof RegisterOperand)) return;
    RegisterOperand regOp = (RegisterOperand) op;
    DepGraphNode sourceNode = regOp.getRegister().dNode();
    if (sourceNode != null) {
      int type = regOp.getRegister().isValidation() ? GUARD_ANTI : REG_ANTI;
      // create antidependence edge.
      // NOTE: sourceNode contains the def and destNode contains the use.
      destNode.insertOutEdge(sourceNode, type);
    }
  }

  /**
   * Compute backward dependences from a given def to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeBackwardDependencesDef(Operand op, DepGraphNode destNode,
                                             DepGraphNode lastExceptionNode) {
    if (!(op instanceof RegisterOperand)) return;
    RegisterOperand regOp = (RegisterOperand) op;

    // pin the def above the next exception node if the register
    // being defined may be live in some reachable catch block
    if (lastExceptionNode != null && regOp.getRegister().spansBasicBlock() && currentBlock.hasExceptionHandlers()) {
      if (!ir.getHandlerLivenessComputed() || handlerLiveSet.contains(regOp.getRegister())) {
        destNode.insertOutEdge(lastExceptionNode, EXCEPTION_R);
      }
    }
    regOp.getRegister().setdNode(destNode);
  }

  /**
   * Compute implicit forward dependences from a given register use
   * to a given node.
   * @param r source register
   * @param destNode destination node
   */
  private void computeImplicitForwardDependencesUse(Register r, DepGraphNode destNode) {
    DepGraphNode sourceNode = r.dNode();
    if (sourceNode != null) {
      for (PhysicalDefUse.PDUEnumeration e =
          PhysicalDefUse.enumerate(PhysicalDefUse.maskTSPDefs, ir); e.hasMoreElements();) {
        Register r2 = e.nextElement();
        if (r == r2) {
          sourceNode.insertOutEdge(destNode, REG_MAY_DEF);
          return;
        }
      }
      sourceNode.insertOutEdge(destNode, REG_TRUE);
    }
  }

  /**
   * Compute implicit forward dependences from a given register def
   * to a given node.
   * @param r source register
   * @param destNode destination node
   */
  private void computeImplicitForwardDependencesDef(Register r, DepGraphNode destNode) {
    DepGraphNode sourceNode = r.dNode();
    if (sourceNode != null) {
      sourceNode.insertOutEdge(destNode, REG_OUTPUT);
    }
    r.setdNode(destNode);
  }

  /**
   * Compute implicit backward dependences from a given register use
   * to a given node.
   * @param r source register
   * @param destNode destination node
   */
  private void computeImplicitBackwardDependencesUse(Register r, DepGraphNode destNode) {
    DepGraphNode sourceNode = r.dNode();
    if (sourceNode != null) {
      // create antidependence edge.
      // NOTE: sourceNode contains the def and destNode contains the use.
      destNode.insertOutEdge(sourceNode, REG_ANTI);
    }
  }

  /**
   * Compute implicit backward dependences from a given register def
   * to a given node.
   * @param r source register
   * @param destNode destination node
   */
  private void computeImplicitBackwardDependencesDef(Register r, DepGraphNode destNode) {
    r.setdNode(destNode);
  }

  /**
   * Get the location of a given load or store instruction.
   * @param s the instruction to get the location from.
   */
  private LocationOperand getLocation(Instruction s) {
    // This extra conforms check wouldn't be necessary if the DepGraph
    // code was distinguishing between explict load/stores which have
    // locations and implicit load/stores which don't.
    return LocationCarrier.conforms(s) ? LocationCarrier.getLocation(s) : null;
  }

  /**
   * Initialize (clear) the dNode field in Register for all registers
   * in this basic block by setting them to null.
   * Handles both explicit and implict use/defs.
   * @param start the first opt instruction in the region
   * @param end   the last opt instruction in the region
   */
  private void clearRegisters(Instruction start, Instruction end) {
    for (Instruction p = start; ; p = p.nextInstructionInCodeOrder()) {
      for (OperandEnumeration ops = p.getOperands(); ops.hasMoreElements();) {
        Operand op = ops.next();
        if (op instanceof RegisterOperand) {
          RegisterOperand rOp = (RegisterOperand) op;
          rOp.getRegister().setdNode(null);
        }
      }
      if (p == end) break;
    }
    for (PhysicalDefUse.PDUEnumeration e = PhysicalDefUse.enumerateAllImplicitDefUses(ir); e.hasMoreElements();)
    {
      Register r = e.nextElement();
      r.setdNode(null);
    }
  }

  /**
   * Print the dependence graph to standard out.
   */
  public void printDepGraph() {
    System.out.println(toString());
    System.out.println("-----------------------------------");
  }
}
