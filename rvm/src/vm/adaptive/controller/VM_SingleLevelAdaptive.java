/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A single-level adaptive model that behaves like the "SLA"
 * strategies in the OOPSLA 2000 paper.  If a method is hot enough to
 * make it to the controller (ie, get past the organizer), it is
 * recompiled at the fixed level.
 *
 * NOTE: This strategy does NOT use a cost-benefit model.
 *
 * @author Matthew arnold 
 * @author Dave Grove 
 * @author Stephen Fink
 */

class VM_SingleLevelAdaptive extends VM_RecompilationStrategy {


  /**
   * A hot method has been passed to the controller by an organizer.
   * Optimize it!  
   */
  VM_ControllerPlan considerHotMethod(VM_CompiledMethod cmpMethod,
				      VM_HotMethodEvent hme) {

    VM_Method method = cmpMethod.getMethod();

    VM_ControllerPlan plan = null;

    // Don't try to recompile if we know it won't work
    if (VM_ControllerMemory.shouldConsiderForInitialRecompilation(method)) {
      int optLevel = VM_Controller.options.DEFAULT_OPT_LEVEL;
      int prevCompiler = VM_CompilerDNA.BASELINE;
      int newCompiler = VM_CompilerDNA.getCompilerConstant(optLevel);
      double speedup = 
	VM_CompilerDNA.getBenefitRatio(prevCompiler, newCompiler);
      plan = createControllerPlan(method, optLevel,
				  null, cmpMethod.getId(),
				  speedup, 1.0);
    } else {
      if (VM.LogAOSEvents) VM_AOSLogging.oldVersionStillHot(hme);
    }
    return plan;
  } 


  /**
   * What is the maximum opt level that is vallid according to this strategy?
   */
  int getMaxOptLevel() {
    return VM_Controller.options.DEFAULT_OPT_LEVEL;
  }



}


