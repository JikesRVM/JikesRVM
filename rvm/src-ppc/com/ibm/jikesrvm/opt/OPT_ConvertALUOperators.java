/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.opt.ir.*;

/**
 * Nothing to do on PowerPC.
 *
 * @author Dave Grove
 */
public abstract class OPT_ConvertALUOperators extends OPT_CompilerPhase 
  implements OPT_Operators {
 
  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this 
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  public final String getName() { return "ConvertALUOps"; }
  public final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  public final void perform(OPT_IR ir) { 
    // Nothing to do on PPC
  }
}
