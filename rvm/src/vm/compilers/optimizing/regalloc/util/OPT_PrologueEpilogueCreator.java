/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class is a phase that inserts prologues and epilogues
 *
 * @author Michael Hind
 */
final class OPT_PrologueEpilogueCreator extends OPT_CompilerPhase {

  OPT_PrologueEpilogueCreator() { }

  public final String getName() { return "Insert Prologue/Epilogue"; }

  /**
   *  Insert the prologue and epilogue
   */
  public final void perform(OPT_IR ir) {
    ir.stackManager.insertPrologueAndEpilogue();
  }
}
