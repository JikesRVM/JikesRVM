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


/**
 *  Phase for expanding the calling convention
 *  @author Michael Hind
 */
final class OPT_ExpandCallingConvention extends OPT_CompilerPhase {

  public boolean printingEnabled (OPT_Options options, boolean before) {
    return  options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public final String getName() { 
    return "Expand Calling Convention"; 
  }

  public final void perform(com.ibm.JikesRVM.opt.ir.OPT_IR ir)  {
    OPT_CallingConvention.expandCallingConventions(ir);
  }
}
