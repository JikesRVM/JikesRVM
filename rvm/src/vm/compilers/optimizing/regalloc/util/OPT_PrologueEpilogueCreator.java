/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import instructionFormats.*;

/**
 * This class is a phase that inserts prologues and epilogues
 *
 * @author Michael Hind
 */
final class OPT_PrologueEpilogueCreator extends OPT_CompilerPhase {

  OPT_PrologueEpilogueCreator() {
  }

  final boolean shouldPerform(OPT_Options options) { return true; }
  final String getName() { return "Insert Prologue/Epilogue"; }
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   *  Insert the prologue and epilogue
   */
  public final void perform(OPT_IR ir) {
    ir.stackManager.insertPrologueAndEpilogue();
  }
}
