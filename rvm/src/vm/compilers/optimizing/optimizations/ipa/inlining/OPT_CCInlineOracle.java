/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.io.*;
import  java.util.*;

/**
 * This object implements the OPT_InlineOracle interface to get at
 * the information stored in an OPT_CCInlinePlan.
 *
 * <p> This class also knows how to populate the OPT_InlineOracleDictionary
 * from a stream.
 *
 * @author Stephen Fink
 *
 */

class OPT_CCInlineOracle extends OPT_GenericInlineOracle
    implements OPT_InlineOracleDictionaryPopulator {

  /** 
   * Populate the inline oracle dictionary with OPT_CCInlineOracles.
   * Not currently implemented.
   * @param in the input stream holding an ASCII representation of the
   * 	      context free inlining plan
   */
  public void populateInlineOracleDictionary (LineNumberReader in) 
      throws OPT_OptimizingCompilerException {
    // read in the inline plan information
    VM.sysFail("Not currently implemented.");    /*
     OPT_CCInlinePlan plan = new OPT_CCInlinePlan();
     try {
     plan.readObject(in);
     } catch (IOException e) {
     e.printStackTrace();
     throw new OPT_OptimizingCompilerException("could not read plan");
     } 
     // create a new oracle based on this plan
     OPT_CCInlineOracle oracle = new OPT_CCInlineOracle(plan);
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
    // consult the inlining plan
    VM_Method[] targets = plan.getInlineTargets(state.getSequence(), 
        state.getBytecodeIndex());
    if (targets == null)
      return  OPT_InlineDecision.NO("not in the plan");
    for (int i = 0; i < targets.length; i++) {
      if (!legalToInline(caller, targets[i])) {
        return  OPT_InlineDecision.NO("illegal inline target");
      }
    }
    if ((state.getComputedTarget() == null) && needsGuard(callee)) {
      return  OPT_InlineDecision.unsafeYES(targets, "virtual methods in plan");
    } 
    else {
      if (targets.length != 1)
        throw  new OPT_OptimizingCompilerException(
            "OPT_CCInlineOracle: logic error");
      return  OPT_InlineDecision.YES(targets[0], "method in plan");
    }
  }

  /**
   * repository for inlining decision information
   */
  OPT_CCInlinePlan plan;        

  /** 
   * Construct an oracle that interfaces to a plan 
   */
  OPT_CCInlineOracle (OPT_CCInlinePlan plan) {
    this.plan = plan;
  }

  // TODO: eliminate the need for the following, which is needed by
  // OPT_InlineOracleDictionary
  public OPT_CCInlineOracle () {
    this.plan = null;
  }
}



