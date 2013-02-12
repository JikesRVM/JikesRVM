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
package org.jikesrvm.compilers.opt.driver;

import org.jikesrvm.compilers.opt.ir.IR;

/**
 * A trivial phase that can be inserted to dump the IR.
 */
public class IRPrinter extends CompilerPhase {
  protected final String msg;

  /**
   * Constuct a phase to print the IR with a message.
   * @param   m the message
   */
  public IRPrinter(String m) {
    msg = m;
  }

  @Override
  public final String getName() {
    return "IR_Printer: " + msg;
  }

  /**
   * Print an IR
   * @param ir the IR to print
   */
  @Override
  public final void perform(IR ir) {
    if (ir.options.getOptLevel() < ir.options.PRINT_IR_LEVEL) {
      return;
    }

    if (!ir.options.hasMETHOD_TO_PRINT() || ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
      dumpIR(ir, msg);
    }
  }

  /**
   * Return this instance of this phase
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }
}
