/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.io.*;
import  java.util.*;

/**
 * This object implements the OPT_InlineOracle interface to get at
 * the information stored in an OPT_ContextFreeInlinePlan.
 *
 * <p> This class also knows how to populate the OPT_InlineOracleDictionary
 * from a stream.
 *
 * @author Stephen Fink
 */

class OPT_ContextFreeInlineOracle extends OPT_GenericInlineOracle
    implements OPT_InlineOracleDictionaryPopulator {

  /** 
   * Populate the inline oracle dictionary with
   * OPT_ContextFreeInlineOracles.  Not currently implemented.
   * @param in the input stream holding an ASCII representation of the
   * 	      context free inlining plan
   */
  public void populateInlineOracleDictionary (LineNumberReader in) {
    VM.sysFail("Not implemented");    /*
     // read in the inline plan information
     OPT_ContextFreeInlinePlan plan = new OPT_ContextFreeInlinePlan();
     try {
     plan.readObject(in);
     } catch (IOException e) {
     e.printStackTrace();
     throw new OPT_OptimizingCompilerException("could not read plan");
     } 
     // create a new oracle based on this plan
     OPT_ContextFreeInlineOracle oracle = new OPT_ContextFreeInlineOracle(plan);
     // register this oracle as the default oracle for ALL methods
     OPT_InlineOracleDictionary.registerDefault(oracle);
     */

  }

  /**
   * The main routine whereby this oracle makes decisions whether or not
   * to inline.
   * @param caller the calling method
   * @param callee the callee method
   * @param state miscellaneous state of the compilation
   * @param inlinedSizeEstimate estimate of the size of callee if inlined
   * @return an inlining decisions
   */
  protected OPT_InlineDecision shouldInlineInternal (VM_Method caller, 
      VM_Method callee, OPT_CompilationState state, int inlinedSizeEstimate) {
    VM.sysFail("Not implemented");
    return  OPT_InlineDecision.NO("not implemented");    /*
     // consult the inlining plan
     boolean present = plan.containsRule(caller,callee);
     if (!present) return OPT_InlineDecision.NO("not in the plan");
     OPT_InlineSequence seq = state.getSequence();
     if (seq.containsMethod(callee)) {
     return OPT_InlineDecision.NO("recursive call");
     }
     if ((state.getComputedTarget() == null) && needsGuard(callee)) {
     return OPT_InlineDecision.unsafeYES(callee,"virtual method in plan");
     } else {
     return OPT_InlineDecision.YES(callee,"method in plan");
     }
     */

  }

  /**
   * repository for inlining decision information
   */
  OPT_ContextFreeInlinePlan plan;   

  /** 
   * Construct an oracle that interfaces to a plan 
   */
  OPT_ContextFreeInlineOracle (OPT_ContextFreeInlinePlan plan) {
    this.plan = plan;
  }

  // TODO: eliminate the need for the following, which is needed by
  // OPT_InlineOracleDictionary
  public OPT_ContextFreeInlineOracle () {
    this.plan = null;
  }
}



