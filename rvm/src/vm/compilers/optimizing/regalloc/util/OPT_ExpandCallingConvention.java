/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;


/**
 *  Phase for expanding the calling convention
 *  @author Michael Hind
 */

final class OPT_ExpandCallingConvention extends OPT_CompilerPhase {

  boolean printingEnabled (OPT_Options options, boolean before) {
    return  options.PRINT_CALLING_CONVENTIONS && !before;
  }

  final boolean shouldPerform(OPT_Options options) { 
    return true; 
  }

  final String getName() { 
    return "Expand Calling Convention"; 
  }

  final void perform(OPT_IR ir)  {
    OPT_CallingConvention.expandCallingConventions(ir);
  }
}
