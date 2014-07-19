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
public final class OptConstants {
  // the following constants are dummy bytecode indices,
  // used to mark IR instructions that do not correspond
  // to any original bytecode
  public static final int UNKNOWN_BCI = -1;
  public static final int PROLOGUE_BCI = -2;
  public static final int EPILOGUE_BCI = -3;
  public static final int RECTIFY_BCI = -4;
  public static final int SYNTH_CATCH_BCI = -5;
  public static final int SYNCHRONIZED_MONITORENTER_BCI = -6;
  public static final int SYNCHRONIZED_MONITOREXIT_BCI = -7;
  public static final int METHOD_COUNTER_BCI = -8;
  public static final int SSA_SYNTH_BCI = -9;
  public static final int INSTRUMENTATION_BCI = -10;
  public static final int RUNTIME_SERVICES_BCI = -11;
  public static final int EXTANT_ANALYSIS_BCI = -12;
  public static final int PROLOGUE_BLOCK_BCI = -13;
  public static final int EPILOGUE_BLOCK_BCI = -14;
  public static final int OSR_PROLOGUE = -15;
  public static final int SYNTH_LOOP_VERSIONING_BCI = -16;

  // The following are used as trinary return values in OptCompiler code
  public static final byte NO = 0;
  public static final byte YES = 1;
  public static final byte MAYBE = 2;

  private OptConstants() {
    // prevent instantiation
  }
}
