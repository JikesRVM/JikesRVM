/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Splits a large basic block into smaller ones with size <= MAX_NUM_INSTRUCTIONS.
 * 
 * @author Jong Choi
 */
final class OPT_SplitBasicBlock extends OPT_CompilerPhase {

  private static final int MAX_NUM_INSTRUCTIONS = 300;

  final String getName() { return "SplitBasicBlock"; }
  final OPT_CompilerPhase newExecution() { return this; }
  
  final void perform(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock) e.nextElement();
      
      if (!bb.isEmpty()) {
	while (bb != null) 
	  bb = splitEachBlock(bb, ir);
      }
    }
  }

  // Splits bb. 
  // Returns null if no splitting is done.
  // returns the second block if splitting is done.
  final OPT_BasicBlock splitEachBlock(OPT_BasicBlock bb, OPT_IR ir) {

    int instCount = MAX_NUM_INSTRUCTIONS;
    for (OPT_Instruction inst = bb.start; 
	 inst != bb.end; inst = inst.getNext()) 
      {
	if ((--instCount) == 0) {
	  if (inst.isBranch())
	    return null; // no need to split because all the rests are just branches
          if (inst.isMove()) {  // why do we need this?? --dave
             OPT_Instruction next = inst.getNext();
             if (next != bb.end && next.isImplicitLoad()) 
                inst = next;
          }
	  // Now, split!
	  return bb.splitNodeWithLinksAt(inst, ir);
	}
      }

    return null; // no splitting happened
  }

}



