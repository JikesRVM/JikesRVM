/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;

/**
 * Driver routine for dominator tree computation
 *
 * @author Michael Hind
 */
final class OPT_DominatorTreePhase extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  final boolean shouldPerform(OPT_Options options) {
    // only perform if the dominators were successfully computed and
    // one of the following options are set.
    return  options.SSA || options.PRINT_DOMINATORS;
  }

  /**
   * Returns "Dominator Tree"
   * @return "Dominator Tree"
   */
  final String getName() {
    return  "Dominator Tree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false.
   */
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
  }

  /**
   * Main driver.
   *
   * @param ir the governing IR
   */
  final void perform(OPT_IR ir) {
    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed)
      return;
    try {
      OPT_DominatorTree.perform(ir, true);
    } catch (OPT_OperationNotImplementedException e) {
      if (ir.options.PRINT_DOMINATORS || ir.options.PRINT_SSA) {
        OPT_Compiler.report(e.getMessage());
      }
    }
  }
}
