/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Nothing to do on PowerPC.
 *
 * @author Dave Grove
 */
final class OPT_ConvertALUOperators extends OPT_CompilerPhase 
  implements OPT_Operators {

  public final String getName() { return "ConvertALUOps"; }
  public final OPT_CompilerPhase newExecution(OPT_IR ir) { return this; }
  public final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  public final void perform(OPT_IR ir) { 
    // Nothing to do on PPC
  }
}
