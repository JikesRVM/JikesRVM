/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * A trivial phase that can be inserted to dump the IR.
 */
public class OPT_IRPrinter extends OPT_CompilerPhase {
  protected String msg;

  /**
   * Constuct a phase to print the IR with a message.
   * @param   m the message
   */
  public OPT_IRPrinter(String m) {
    msg = m;
  }

  public final String getName() {
    return  "IR_Printer: " + msg;
  }

  /**
   * Print an IR
   * @param ir the IR to print
   */
  public final void perform(OPT_IR ir) {
    if (ir.options.getOptLevel() < ir.options.IR_PRINT_LEVEL) {
      return;
    }

    if (!ir.options.hasMETHOD_TO_PRINT() ||
        ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
      dumpIR(ir, msg);
    }
  }

  /**
   * Return this instance of this phase
   * @param ir not used
   * @return this 
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }
}
