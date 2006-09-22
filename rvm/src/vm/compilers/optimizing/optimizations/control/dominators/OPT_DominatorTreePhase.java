/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Driver routine for dominator tree computation
 *
 * @author Michael Hind
 */
public final class OPT_DominatorTreePhase extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public final boolean shouldPerform(OPT_Options options) {
    // only perform if the dominators were successfully computed and
    // one of the following options are set.
    return  options.SSA || options.PRINT_DOMINATORS;
  }

  /**
   * Returns "Dominator Tree"
   * @return "Dominator Tree"
   */
  public final String getName() {
    return  "Dominator Tree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false.
   */
  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
  }

  /**
   * Main driver.
   *
   * @param ir the governing IR
   */
  public final void perform(OPT_IR ir) {
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
