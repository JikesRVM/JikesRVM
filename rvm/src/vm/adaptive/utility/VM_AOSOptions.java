/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

/**
 * Additional option values that are computed internally are defined
 * here.  Commnad line options are inherited from VM_AOSExternalOptions
 * which is machine generated from the various .dat files.
 * @author Dave Grove
 */
public final class VM_AOSOptions extends VM_AOSExternalOptions {
  public int MAX_OPT_LEVEL;

  public int FILTER_OPT_LEVEL;

}
