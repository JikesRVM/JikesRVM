/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Driver routine for dominator computation.  This phase invokes
 * the Lengauer-Tarjan dominator calculation.
 *
 * @author Michael Hind
 */
final class OPT_DominatorsPhase extends OPT_CompilerPhase {

  /**
   * Should we unfactor the CFG? 
   */
  private boolean unfactor = false;

  /**
   * @param unfactor Should we unfactor the CFG before computing
   * dominators?
   */
  OPT_DominatorsPhase(boolean unfactor) {
    this.unfactor = unfactor;
  }

  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   */
  final boolean shouldPerform (OPT_Options options) {
    return true;
  }
  /**
   * Return a string representation of this phase
   * @return "Dominators + LpStrTree"
   */
  final String getName() {
    return  "Dominators + LpStrTree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false
   */
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
  }

  /**
   * Main driver for the dominator calculation.
   */
  final void perform(OPT_IR ir) {
    try {
      // reset flags in case an exception is thrown inside "perform"
      // and it doesn't return normally
      ir.HIRInfo.dominatorsAreComputed = false;

      // compute (forward) dominators, 
      // leaves info in scratch object of basic blocks
      OPT_LTDominators.perform(ir, true, unfactor);

      // create the dominator tree, relies on dominator info being
      // in scratch object of basic blocks
      OPT_DominatorTree.perform(ir, true);

      // create the loop structured tree (LST)
      OPT_LSTGraph.perform(ir);

      // computation completed, so set flag
      ir.HIRInfo.dominatorsAreComputed = true;
    } catch (OPT_OperationNotImplementedException e) {
      OPT_Options options = ir.options;
        OPT_Compiler.report(e.getMessage());
    }
  }
}
