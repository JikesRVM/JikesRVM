/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.driver;

/**
 * Class that holds miscellaneous constants used in the opt compiler
 */
public interface OptConstants {
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
