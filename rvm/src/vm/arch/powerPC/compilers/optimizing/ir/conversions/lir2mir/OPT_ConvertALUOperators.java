/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import instructionFormats.*;

/**
 * Nothing to do on PowerPC.
 *
 * @author Dave Grove
 */
final class OPT_ConvertALUOperators extends OPT_CompilerPhase 
  implements OPT_Operators {

  final String getName() { return "ConvertALUOps"; }
  final OPT_CompilerPhase newExecution(OPT_IR ir) { return this; }
  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  final void perform(OPT_IR ir) { 
    // Nothing to do on PPC
  }
}
