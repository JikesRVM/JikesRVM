/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Convert an IR object from HIR to LIR
 *
 * @author Dave Grove
 */
final class OPT_ConvertHIRtoLIR extends OPT_CompilerPhase {

  final String getName () {
    return  "HIR Operator Expansion";
  }

  OPT_CompilerPhase newExecution (OPT_IR ir) {
    return  this;
  }             // this phase has no state.

  final void perform (OPT_IR ir) {
    computeLocalInCatch(ir);
    if (OPT_IR.SANITY_CHECK) {
      ir.verify("before conversion to LIR", true);
    }
    if (ir.options.STATIC_STATS)
      // Print summary statistics (critpath, etc.) for all basic blocks
      OPT_DepGraphStats.printBasicBlockStatistics(ir);
    // Do the conversion from HIR to LIR.
    OPT_ConvertToLowLevelIR.convert(ir, ir.options);
    // ir now contains well formed LIR.
    ir.IRStage = OPT_IR.LIR;
    ir.LIRInfo = new OPT_LIRInfo(ir);
  }

  /**
   * Compute which local variables are live on entry to a catch block.
   */
  private static void computeLocalInCatch (OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers()) {
      // Prepare for new DFS --- clear visited flags
      ir.cfg.clearDFS();
      // Compute REACHABLE_FROM_EXCEPION_HANDLER flags
      for (OPT_BasicBlockEnumeration blocks = ir.getBasicBlocks(); 
          blocks.hasMoreElements();) {
        OPT_BasicBlock bblock = blocks.next();
        if (bblock.isExceptionHandlerBasicBlock())
          dfs(bblock);
      }
      // Use REACHABLE_FROM_EXCEPION_HANDLER flags to compute isLocalInCatch
      for (OPT_BasicBlockEnumeration blocks = ir.getBasicBlocks(); 
          blocks.hasMoreElements();) {
        OPT_BasicBlock bblock = blocks.next();
        if (bblock.isReachableFromExceptionHandler()) {
          // Set isLocalInCatch = TRUE for each local used in bblock.
          // TODO: refine to upwards-exposed locals only.
          for (OPT_InstructionEnumeration instrs = 
              bblock.forwardRealInstrEnumerator(); instrs.hasMoreElements();) {
            OPT_Instruction instr = instrs.next();
            for (OPT_OperandEnumeration uses = instr.getUses(); 
                uses.hasMoreElements();) {
              OPT_Operand op = uses.next();
              if (op instanceof OPT_RegisterOperand) {
                OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
                if (rop.register.isLocal()) {
                  rop.register.setLocalInCatch();
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Depth-first search for setting REACHABLE_FROM_EXCEPTION_HANDLER flags
   */
  private static void dfs (OPT_SpaceEffGraphNode node) {
    // Mark node as visited
    node.setDfsVisited();
    // Mark node as being reachable from an exception handler
    ((OPT_BasicBlock)node).setReachableFromExceptionHandler();
    // Recurse
    for (OPT_SpaceEffGraphEdge edge = node.firstOutEdge(); edge != null; 
        edge = edge.getNextOut()) {
      OPT_SpaceEffGraphNode succ = edge.toNode();
      if (!succ.dfsVisited()) {
        dfs(succ);
      }
    }
  }
}
