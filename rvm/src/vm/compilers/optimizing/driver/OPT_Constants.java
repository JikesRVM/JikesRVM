/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Class that holds miscellaneous constants used in the opt compiler
 *
 * @author Stephen Fink
 */
public interface OPT_Constants {
  // the following constants are dummy bytecode indices,
  // used to mark IR instructions that do not correspond
  // to any original bytecode
  final int UNKNOWN_BCI = -1;
  final int PROLOGUE_BCI = -2;
  final int EPILOGUE_BCI = -3;
  final int RECTIFY_BCI = -4;
  final int SYNTH_CATCH_BCI = -5;
  final int SYNCHRONIZED_MONITORENTER_BCI = -6;
  final int SYNCHRONIZED_MONITOREXIT_BCI = -7;
  final int METHOD_COUNTER_BCI = -8;
  final int SSA_SYNTH_BCI = -9;
  final int INSTRUMENTATION_BCI = -10;
  final int RUNTIME_SERVICES_BCI = -11;
  final int EXTANT_ANALYSIS_BCI = -12;
  final int PROLOGUE_BLOCK_BCI = -13;
  final int EPILOGUE_BLOCK_BCI = -14;

  // The following are used as trinary return values in OptCompiler code
  public final byte NO = 0;
  public final byte YES = 1;
  public final byte MAYBE = 2;
}
