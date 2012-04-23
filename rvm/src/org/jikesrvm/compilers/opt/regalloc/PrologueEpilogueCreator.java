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

import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * This class is a phase that inserts prologues and epilogues
 */
public final class PrologueEpilogueCreator extends CompilerPhase {

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
  public String getName() { return "Insert Prologue/Epilogue"; }

  /**
   *  Insert the prologue and epilogue
   */
  @Override
  public void perform(IR ir) {
    ir.stackManager.insertPrologueAndEpilogue();
  }
}
