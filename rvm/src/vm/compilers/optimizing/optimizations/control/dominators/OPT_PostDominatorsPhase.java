/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Driver routine for post-dominator computation.  This phase invokes
 * the Lengauer-Tarjan dominator calculation.
 *
 * @author Michael Hind
 */
final class OPT_PostDominatorsPhase extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   * @return 
   */
  final boolean shouldPerform(OPT_Options options) {
    return true;
  }

  /**
   * Return a string representation of this phase
   * @return "Post-Dominators"
   */
  final String getName() {
    return  "Post-Dominators";
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
   * Main driver for the post-dominator calculation.
   */
  final void perform(OPT_IR ir) {
    try {
      // reset flags in case an exception is thrown inside "perform"
      // and it doesn't return normally
      ir.HIRInfo.postDominatorsAreComputed = false;

      // compute post-dominators, 
      // leaves info in scratch object of basic blocks
      OPT_LTDominators.perform(ir, false, false);

      // create the dominator tree, relies on dominator info being
      // in scratch object of basic blocks
      OPT_DominatorTree.perform(ir, false);

      // computation completed, so set flag
      ir.HIRInfo.postDominatorsAreComputed = true;

    } catch (OPT_OperationNotImplementedException e) {
      OPT_Options options = ir.options;
      if (options.PRINT_POST_DOMINATORS) {
        OPT_Compiler.report(e.getMessage());
      }
    }
  }
}
