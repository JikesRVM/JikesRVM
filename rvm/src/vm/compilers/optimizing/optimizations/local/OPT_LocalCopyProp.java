/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id: 

import instructionFormats.*;

/**
 * Perform local copy propagation for a factored basic block.
 * Orthogonal to the copy propagation performed in OPT_Simple
 * since here we use flow-sensitive analysis within a basic block.
 * 
 * TODO: factor out common functionality in the various local propagation
 * phases?
 *
 * @author Dave Grove
 * @author Stephen Fink
 */
public class OPT_LocalCopyProp extends OPT_CompilerPhase implements OPT_Operators {

  final boolean shouldPerform (OPT_Options options) {
    return options.LOCAL_COPY_PROP;
  }

  final String getName () {
    return "Local CopyProp";
  }

  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * Perform Local Constant propagation for a method.
   * 
   * @param ir the IR to optimize
   */
  public void perform (OPT_IR ir) {
    // info is a mapping from OPT_Register to OPT_Register
    java.util.HashMap info = new java.util.HashMap();
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
	 bb != null; 
	 bb = bb.nextBasicBlockInCodeOrder()) {
      if (!bb.isEmpty()) {
	// iterate over all instructions in the basic block
	for (OPT_Instruction s = bb.firstRealInstruction(), 
	       sentinel = bb.lastInstruction();
	     s != sentinel; 
	     s = s.nextInstructionInCodeOrder()) {

	  if (!info.isEmpty()) {
	    // PROPAGATE COPIES
	    int numUses = s.getNumberOfUses();
	    if (numUses > 0) {
	      boolean didSomething = false;
	      int numDefs = s.getNumberOfDefs();
	      for (int idx = numDefs; idx < numUses + numDefs; idx++) {
		OPT_Operand use = s.getOperand(idx);
		if (use instanceof OPT_RegisterOperand) {
		  OPT_RegisterOperand rUse = (OPT_RegisterOperand)use;
		  OPT_Operand value = (OPT_Operand)info.get(rUse.register);
		  if (value != null) {
		    didSomething = true;
		    s.putOperand(idx, value.copy());
		  }
		}
	      }
	      if (didSomething) OPT_Simplifier.simplify(s);
	    }
	    // KILL
	    for (OPT_OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
              OPT_Operand def = e.next();
              if (def != null && def.isRegister()) {
                OPT_Register r = def.asRegister().register;
                info.remove(r);
                // also must kill any registers mapped to r
                // TODO: use a better data structure for efficiency.
                // I'm being lazy for now in the name of avoiding
                // premature optimization.
                java.util.HashSet toRemove = new java.util.HashSet();
                for (java.util.Iterator i = info.entrySet().iterator();
                     i.hasNext(); ) {
                  java.util.Map.Entry entry = (java.util.Map.Entry)i.next();
                  OPT_Register eR =
                    ((OPT_RegisterOperand)entry.getValue()).
                    asRegister().register;
                  if (eR == r) {
                    // delay the removal to avoid ConcurrentModification
                    // with iterator.
                    toRemove.add(entry.getKey());
                  }
                }
                // Now perform the removals.
                for (java.util.Iterator i = toRemove.iterator(); i.hasNext();) {
                  info.remove(i.next());
                }
              }
            }
	  }
	  // GEN
	  if (Move.conforms(s)) {
	    OPT_Operand val = Move.getVal(s);
	    if (val.isRegister() && !val.asRegister().register.isPhysical()) {
	      info.put(Move.getResult(s).register, val);
	    } 
	  }
	}
	info.clear();
      }
    }
  }
}



