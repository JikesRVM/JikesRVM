/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.regalloc;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * A phase to compute register restrictions.
 */
final class RegisterRestrictionsPhase extends CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Register Restrictions";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {

    //  The registerManager has already been initialized
    GenericStackManager sm = ir.stackManager;

    // Set up register restrictions
    sm.computeRestrictions(ir);
  }
}
