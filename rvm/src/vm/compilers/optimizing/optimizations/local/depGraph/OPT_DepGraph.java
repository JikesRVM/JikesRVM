/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
import com.ibm.JikesRVM.opt.ir.*;

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
 * 
 * @author Dave Grove
 * @author Igor Pechtchanski
 * @author Vivek Sarkar
 * @author Mauricio Serrano
 * @author Harini Srinivasan
 */
final class OPT_DepGraph extends OPT_SpaceEffGraph 
  implements OPT_Operators, 
             OPT_DepGraphConstants {

  /**
   * Set of variables that are live on entry to at least one catch block that
   * is reachable via a PEI in currentBlock.
   * This is an approximatation of the more precise set, but can be done in
   * linear time; doing the most precise thing (computing the set for
   * every PEI and using each individual set to create the necessary
   * dependences) is quadratic, and probably doesn't help very much anyways.
   */
  private OPT_LiveSet handlerLiveSet;

  /**
   * The basic block we are processing
   */
  private OPT_BasicBlock currentBlock;

  /**
   * The ir we are processing
   */
  private OPT_IR ir;

  /**
   * Constructor (computes the dependence graph!).
   * 
   * @param ir the IR to compute the dependence graph for
   * @param start instruction to start computation from
   * @param end instruction to end computation at
   * @param currentBlock the basic block that the instructions are living in
   */
  OPT_DepGraph(OPT_IR ir, OPT_Instruction start, OPT_Instruction end,
               OPT_BasicBlock currentBlock) {
    this.currentBlock = currentBlock;
    this.ir = ir;

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
    if (ir.getHandlerLivenessComputed() && 
        currentBlock.hasExceptionHandlers()) {
      handlerLiveSet = new OPT_LiveSet();
      OPT_BasicBlockEnumeration e = currentBlock.getExceptionalOut();
      while (e.hasMoreElements()) {
        OPT_ExceptionHandlerBasicBlock handlerBlock =
          (OPT_ExceptionHandlerBasicBlock) e.next();
        handlerLiveSet.add(handlerBlock.getLiveSet());
      }
    }
  }

  /**
   * Create the dependency graph nodes for instructions start to end
   */
  private void createNodes(OPT_Instruction start, OPT_Instruction end) {
    for (OPT_Instruction p = start; ; p = p.nextInstructionInCodeOrder()) {
      OPT_DepGraphNode pnode = new OPT_DepGraphNode(p);
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
  private void computeForwardDependences(OPT_Instruction start,
                                         OPT_Instruction end) {
    boolean readsKill= ir.options.READS_KILL;
    OPT_DepGraphNode lastStoreNode = null;
    OPT_DepGraphNode lastExceptionNode = null;
    OPT_DepGraphNode lastLoadNode = null; // only used if reads kill

    clearRegisters(start, end);
    
    for (OPT_DepGraphNode pnode = (OPT_DepGraphNode) firstNode();
         pnode != null; 
         pnode = (OPT_DepGraphNode) pnode.getNext())  {
      OPT_Instruction p = pnode.instruction();

      // (1) Add edges due to registers
      int useMask = p.operator().implicitUses;
      int defMask = p.operator().implicitDefs;
      if (p.isTSPoint()) {
        useMask |= OPT_PhysicalDefUse.maskTSPUses;
        defMask |= OPT_PhysicalDefUse.maskTSPDefs;
      }
      for (OPT_OperandEnumeration uses = p.getUses();
           uses.hasMoreElements(); ) {
        computeForwardDependencesUse(uses.next(), pnode, lastExceptionNode);
      }
      for (Enumeration uses = OPT_PhysicalDefUse.enumerate(useMask,ir);
           uses.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)uses.nextElement(); 
        computeImplicitForwardDependencesUse(r, pnode);
      }
      for (OPT_OperandEnumeration defs = p.getDefs();
           defs.hasMoreElements(); )  {
        computeForwardDependencesDef(defs.next(), pnode, lastExceptionNode);
      }
      for (Enumeration defs = OPT_PhysicalDefUse.enumerate(defMask,ir);
           defs.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)defs.nextElement(); 
        computeImplicitForwardDependencesDef(r, pnode);
      }

      // (2) Add edges due to memory
      boolean isStore = p.isStore();
      boolean isLoad = p.isLoad();
      if (isStore || isLoad) {
        // If readsKill then add memory model memory dependence from prior load
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        if (readsKill && isLoad) {
          for (OPT_DepGraphNode lnode = lastLoadNode; 
               lnode != null;
               lnode = (OPT_DepGraphNode) lnode.getPrev()) {
            if (lnode.instruction().isLoad() &&
                OPT_LocationOperand.mayBeAliased(getLocation(p),
                                                 getLocation(lnode.instruction()))) {
              lnode.insertOutEdge(pnode, MEM_READS_KILL);
            }
          } 
          lastLoadNode = pnode;
        }
        // Add output/flow memory dependence from prior potentially aliased stores.
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        for (OPT_DepGraphNode snode = lastStoreNode; 
             snode != null;
             snode = (OPT_DepGraphNode) snode.getPrev()) {
          if (snode.instruction().isStore() && 
              OPT_LocationOperand.mayBeAliased(getLocation(p),
                                               getLocation(snode.instruction())))  {
            snode.insertOutEdge(pnode, isStore?MEM_OUTPUT:MEM_TRUE);
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
  private void computeBackwardDependences(OPT_Instruction start,
                                          OPT_Instruction end) {
    clearRegisters(start, end);

    OPT_DepGraphNode lastStoreNode = null;
    OPT_DepGraphNode lastExceptionNode = null;
    for (OPT_DepGraphNode pnode = (OPT_DepGraphNode) lastNode();
         pnode != null; 
         pnode = (OPT_DepGraphNode) pnode.getPrev())   {
      OPT_Instruction p = pnode.instruction();

      // (1) Add edges due to registers
      int useMask = p.operator().implicitUses;
      int defMask = p.operator().implicitDefs;
      if (p.isTSPoint()) {
        useMask |= OPT_PhysicalDefUse.maskTSPUses;
        defMask |= OPT_PhysicalDefUse.maskTSPDefs;
      }
      for (OPT_OperandEnumeration uses = p.getUses();
           uses.hasMoreElements(); ) {
        computeBackwardDependencesUse(uses.next(), pnode, lastExceptionNode);
      }
      for (Enumeration uses = OPT_PhysicalDefUse.enumerate(useMask,ir);
           uses.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)uses.nextElement(); 
        computeImplicitBackwardDependencesUse(r, pnode);
      }
      for (OPT_OperandEnumeration defs = p.getDefs();
           defs.hasMoreElements(); ) {
        computeBackwardDependencesDef(defs.next(), pnode, lastExceptionNode);
      }
      for (Enumeration defs = OPT_PhysicalDefUse.enumerate(defMask,ir);
           defs.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)defs.nextElement(); 
        computeImplicitBackwardDependencesDef(r, pnode);
      }

      // (2) Add edges due to memory
      boolean isStore = p.isStore();
      boolean isLoad = p.isLoad();
      if (isStore) {
        if (lastExceptionNode != null) {
          pnode.insertOutEdge(lastExceptionNode, EXCEPTION_MS);
        }
        lastStoreNode = pnode;
      } else if (isLoad) {
        // NOTE: In general alias relationships are not transitive and therefore
        //       we cannot exit this loop early.
        for (OPT_DepGraphNode snode = lastStoreNode; 
             snode != null;
             snode = (OPT_DepGraphNode)snode.getNext()) {
          if (snode.instruction().isStore() &&
              OPT_LocationOperand.mayBeAliased(getLocation(p),
                                               getLocation(snode.instruction()))) {
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
  private void computeControlAndBarrierDependences(OPT_Instruction start, 
                                                   OPT_Instruction end) {
    // (1) In a forward pass, we add the following dependences:
    //    a) No load instruction may rise above an acquire
    //    b) No instruction may rise above an UNINT_BEGIN (conservative), 
    //       a yieldpoint (we placed the yieldpoints where we wanted them),
    //       or an IR_PROLOGUE.
    //    c) No GC point may rise above an UNINT_END
    OPT_DepGraphNode lastTotalBarrier = null;
    OPT_DepGraphNode lastGCBarrier = null;
    OPT_DepGraphNode lastAcquire = null;
    for (OPT_DepGraphNode pnode = (OPT_DepGraphNode) firstNode();
         pnode != null; 
         pnode = (OPT_DepGraphNode) pnode.getNext())  {
      OPT_Instruction p = pnode.instruction();
      if (lastTotalBarrier != null) {
        lastTotalBarrier.insertOutEdge(pnode, CONTROL);
      } 
      if (lastGCBarrier != null) {
        lastGCBarrier.insertOutEdge(pnode, CONTROL);
      }
      if (lastAcquire != null && p.isLoad()) {
        lastAcquire.insertOutEdge(pnode, CONTROL);
      }
      OPT_Operator pop = p.operator();
      if (p.isYieldPoint() || pop == IR_PROLOGUE ||
          pop == UNINT_BEGIN || pop == GET_TIME_BASE) {
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
    //       a branch/return, or a yieldpoint (again want to pin yieldpoints).
    //    c) No GC point may sink below an UNINT_BEGIN
    lastTotalBarrier = null;
    lastGCBarrier = null;
    OPT_DepGraphNode lastRelease = null;
    for (OPT_DepGraphNode pnode = (OPT_DepGraphNode) lastNode();
         pnode != null; 
         pnode = (OPT_DepGraphNode) pnode.getPrev())   {
      OPT_Instruction p = pnode.instruction();
      if (lastTotalBarrier != null) {
        pnode.insertOutEdge(lastTotalBarrier, CONTROL);
      }
      if (lastGCBarrier != null) {
        pnode.insertOutEdge(lastGCBarrier, CONTROL);
      }
      if (lastRelease != null  && p.isStore()) {
        pnode.insertOutEdge(lastRelease, CONTROL);
      }
      OPT_Operator pop = p.operator();
      if (p.isBranch() || p.isReturn() || p.isYieldPoint() ||
          pop == UNINT_END || pop == GET_TIME_BASE){
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
  private void computeForwardDependencesUse(OPT_Operand op,
                                            OPT_DepGraphNode destNode,
                                            OPT_DepGraphNode lastExceptionNode) {
    if (!(op instanceof OPT_RegisterOperand)) return;
    OPT_RegisterOperand regOp = (OPT_RegisterOperand) op;
    OPT_DepGraphNode sourceNode = regOp.register.dNode();

    // if there is an element in the regTableDef[regNum] slot, set
    // the flow dependence edge.
    if (sourceNode != null) {
      if (regOp.register.isValidation()) {
        sourceNode.insertOutEdge(destNode, GUARD_TRUE);
      } else {
        for (Enumeration e = OPT_PhysicalDefUse.enumerate(OPT_PhysicalDefUse.maskTSPDefs, ir);
             e.hasMoreElements(); ) {
          OPT_Register r = (OPT_Register)e.nextElement(); 
          if (regOp.register == r) {
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
  private void computeForwardDependencesDef(OPT_Operand op,
                                            OPT_DepGraphNode destNode,
                                            OPT_DepGraphNode lastExceptionNode) {
    if (!(op instanceof OPT_RegisterOperand)) return;
    OPT_RegisterOperand regOp = (OPT_RegisterOperand) op;
    OPT_DepGraphNode sourceNode = regOp.register.dNode();

    if (sourceNode != null) {
      // create output dependence edge from sourceNode to destNode.
      int type = regOp.register.isValidation() ? GUARD_OUTPUT : REG_OUTPUT;
      sourceNode.insertOutEdge(destNode, type);

      // pin the def below the previous exception node if the register
      // being defined may be live in some reachable catch block
      if (lastExceptionNode != null && 
          regOp.register.spansBasicBlock() &&
          currentBlock.hasExceptionHandlers()) {
        if (!ir.getHandlerLivenessComputed() ||
            handlerLiveSet.contains(regOp.register)) {
          lastExceptionNode.insertOutEdge(destNode, EXCEPTION_R);
        }
      }
    }
    // update depGraphNode information in register.
    regOp.register.setdNode(destNode);
  }

  /**
   * Compute backward dependences from a given use to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param isDef does this node represent a definition?
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeBackwardDependencesUse(OPT_Operand op,
                                             OPT_DepGraphNode destNode,
                                             OPT_DepGraphNode lastExceptionNode) {
    if (!(op instanceof OPT_RegisterOperand)) return;
    OPT_RegisterOperand regOp = (OPT_RegisterOperand) op;
    OPT_DepGraphNode sourceNode = regOp.register.dNode();
    if (sourceNode != null) {
      int type = regOp.register.isValidation() ? GUARD_ANTI : REG_ANTI;
      // create antidependence edge. 
      // NOTE: sourceNode contains the def and destNode contains the use.
      destNode.insertOutEdge(sourceNode, type);
    }
  }

  /**
   * Compute backward dependences from a given def to a given node.
   * @param op source operand
   * @param destNode destination node
   * @param isDef does this node represent a definition?
   * @param lastExceptionNode node representing the last PEI
   */
  private void computeBackwardDependencesDef(OPT_Operand op,
                                             OPT_DepGraphNode destNode,
                                             OPT_DepGraphNode lastExceptionNode) {
    if (!(op instanceof OPT_RegisterOperand)) return;
    OPT_RegisterOperand regOp = (OPT_RegisterOperand) op;

    // pin the def above the next exception node if the register
    // being defined may be live in some reachable catch block
    if (lastExceptionNode != null && 
        regOp.register.spansBasicBlock() &&
        currentBlock.hasExceptionHandlers()) {
      if (!ir.getHandlerLivenessComputed() ||
          handlerLiveSet.contains(regOp.register)) {
        destNode.insertOutEdge(lastExceptionNode, EXCEPTION_R);
      }
    }
    regOp.register.setdNode(destNode);
  }

  /**
   * Compute implicit forward dependences from a given register use
   * to a given node.
   * @param r source register
   * @param destNode destination node
   * @param isDef does this node represent a definition?
   */
  private void computeImplicitForwardDependencesUse(OPT_Register r, 
                                                    OPT_DepGraphNode destNode){
    OPT_DepGraphNode sourceNode = r.dNode();
    if (sourceNode != null) {
      for (Enumeration e = OPT_PhysicalDefUse.enumerate(OPT_PhysicalDefUse.maskTSPDefs, ir);
           e.hasMoreElements(); ) {
        OPT_Register r2 = (OPT_Register)e.nextElement(); 
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
   * @param isDef does this node represent a definition?
   */
  private void computeImplicitForwardDependencesDef(OPT_Register r, 
                                                    OPT_DepGraphNode destNode){
    OPT_DepGraphNode sourceNode = r.dNode();
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
   * @param isDef does this node represent a definition?
   */
  private void computeImplicitBackwardDependencesUse(OPT_Register r, 
                                                     OPT_DepGraphNode destNode){
    OPT_DepGraphNode sourceNode = r.dNode();
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
   * @param isDef does this node represent a definition?
   */
  private void computeImplicitBackwardDependencesDef(OPT_Register r, 
                                                     OPT_DepGraphNode destNode){
    r.setdNode(destNode);
  }


  /**
   * Get the location of a given load or store instruction.
   * @param s the instruction to get the location from.
   */
  private OPT_LocationOperand getLocation(OPT_Instruction s) {
    // This extra conforms check wouldn't be necessary if the DepGraph
    // code was distinguishing between explict load/stores which have
    // locations and implicit load/stores which don't.
    return LocationCarrier.conforms(s) ? LocationCarrier.getLocation(s) : null;
  }


  /**
   * Initialize (clear) the dNode field in OPT_Register for all registers 
   * in this basic block by setting them to null.   
   * Handles both explicit and implict use/defs.
   * @param start the first opt instruction in the region
   * @param end   the last opt instruction in the region
   */
  private void clearRegisters(OPT_Instruction start, OPT_Instruction end) {
    for (OPT_Instruction p = start; ; p = p.nextInstructionInCodeOrder()) {
      for (OPT_OperandEnumeration ops = p.getOperands();
           ops.hasMoreElements(); ) {
        OPT_Operand op = ops.next();
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand rOp = (OPT_RegisterOperand)op;
          rOp.register.setdNode(null);
        }
      }
      if (p == end) break;
    }
    for (Enumeration e = OPT_PhysicalDefUse.enumerateAllImplicitDefUses(ir);
         e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement(); 
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

  /**
   * Returns a VCG descriptor for the graph which will provide VCG-relevant
   * information for the graph.
   * @return graph descriptor
   * @see OPT_VCGGraph#getVCGDescriptor
   */
  public OPT_VCGGraph.GraphDesc getVCGDescriptor() {
    return new GraphDesc() {
      public String getTitle() { return "Dependence Graph"; }
    };
  }
}
