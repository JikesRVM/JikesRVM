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

/**
 * Class that holds miscellaneous constants used in the opt compiler
 *
 * @author Stephen Fink
 */
public interface OPT_Constants {
  // the following constants are dummy bytecode indices,
  // used to mark IR instructions that do not correspond
  // to any original bytecode
  int UNKNOWN_BCI = -1;
  int PROLOGUE_BCI = -2;
  int EPILOGUE_BCI = -3;
  int RECTIFY_BCI = -4;
  int SYNTH_CATCH_BCI = -5;
  int SYNCHRONIZED_MONITORENTER_BCI = -6;
  int SYNCHRONIZED_MONITOREXIT_BCI = -7;
  int METHOD_COUNTER_BCI = -8;
  int SSA_SYNTH_BCI = -9;
  int INSTRUMENTATION_BCI = -10;
  int RUNTIME_SERVICES_BCI = -11;
  int EXTANT_ANALYSIS_BCI = -12;
  int PROLOGUE_BLOCK_BCI = -13;
  int EPILOGUE_BLOCK_BCI = -14;
  int OSR_PROLOGUE = -15;
  int SYNTH_LOOP_VERSIONING_BCI = -16;

  // The following are used as trinary return values in OptCompiler code
  byte NO = 0;
  byte YES = 1;
  byte MAYBE = 2;
}
