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
package org.jikesrvm.adaptive.recompilation;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.StringTokenizer;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;

/**
 * This class codifies the cost/benefit properties of the various compilers
 * used in the adaptive optimization system.<p>
 *
 * The DNA tells the AOS two important kinds of averages for each optimization
 * level: the cost of compiling at an optimization level (as measured in
 * bytecode/milliseconds) and the expected speedup of the resulting code
 * (relative to the first compiler).<p>
 *
 * There is an AOS command-line option to set the compiler DNA.  The method
 * {@link CompilerDNA#readDNA} contains a comment on the expected format.<p>
 *
 * This DNA was gathered on July 9, 2008 using revision r14679 + the bugfix in r14688.
 * The PowerPC data was gathered on piccolo.watson.ibm.com (JS21, machine type 8884; ppc64-aix).
 * The IA32 data was gathered on lyric.watson.ibm.com (LS41, machine type 7972; x86_64-linux).
 */
public class CompilerDNA implements Constants {

  private static final String[] compilerNames = {"Baseline", "Opt0", "Opt1", "Opt2"};
  public static final int BASELINE = 0;
  static final int OPT0 = 1;
  static final int OPT1 = 2;
  static final int OPT2 = 3;

  /**
   *  The number of compilers available
   */
  private static int numCompilers;

  /**
   * Average bytecodes compiled per millisecond.
   */
  private static final double[] compilationRates;

  static {
    if (VM.BuildForPowerPC) {
      compilationRates = new double[]{667.32,             // base
                                      26.36, 13.41, 12.73}; // opt 0...2
    } else if (VM.BuildForIA32) {
      compilationRates = new double[]{909.46,               // base
                                      39.53, 18.48, 17.28}; // opt 0...2
    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      compilationRates = null;
    }
  }

  /**
   * What is the execution rate of each compiler normalized to the 1st compiler
   */
  private static final double[] speedupRates;

  static {
    if (VM.BuildForPowerPC) {
      speedupRates = new double[]{1.00,               // base
                                  7.87, 12.23, 12.29};  // opt 0...2
    } else if (VM.BuildForIA32) {
      speedupRates = new double[]{1.00,               // base
                                  4.03, 5.88, 5.93};  // opt 0...2
    } else {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      speedupRates = null;
    }
  }

  /**
   * Benefits of moving from one compilation level to another
   * USAGE NOTE: The data is layed out in a upper triangular matrix
   */
  private static double[][] benefitRatio;

  /**
   * Compile time ratio of one compilation level to another
   * For example, if compiler1 (say OPT1) compiles at 50 bc/msec
   * and compiler2 (say OPT2) compiles at 100 bc/msec,
   *    compileTimeRatio[OPT1][OPT2] = 2
   * USAGE NOTE: The data is layed out in a upper triangular matrix
   */
  private static double[][] compileTimeRatio;

  static {
    initializeCompilerRatioArrays();
  }

  /**
   * This method returns the expected speedup from going from compiler1 to compiler2
   * @param compiler1
   * @param compiler2
   * @return the benefit ratio (speedup) of moving from compiler1 to compiler2
   */
  public static double getBenefitRatio(int compiler1, int compiler2) {
    return benefitRatio[compiler1][compiler2];
  }

  /**
   * What is the additional overhead (relative to compiler1 compile time)
   * of compile2 compile time.  For example, if compiler1 compiles at
   * 50 bc/msec and compiler2 compiles at 100 bc/msec, this method returns 2
   * @param compiler1 the compiler whose compile time we compare to
   * @param compiler2 the compiler's compile time we care about
   * @return the additional overhead (relative to compiler1 compile time)
   * of compile2 compile time
   */
  public static double getCompileTimeRatio(int compiler1, int compiler2) {
    return compileTimeRatio[compiler1][compiler2];
  }

  /**
   * Estimate how long (in milliseconds) it will/did take the
   * given compiler to compile the given method.
   *
   * @param compiler the compiler to compile meth
   * @param meth the method to be compiled
   * @return an estimate of compile time (in milliseconds)
   */
  public static double estimateCompileTime(int compiler, NormalMethod meth) {
    double bytes = (double) meth.getBytecodeLength();
    double runtimeBaselineRate = RuntimeCompiler.getBaselineRate();
    double compileTime = bytes / runtimeBaselineRate;
    if (compiler != BASELINE) {
      compileTime *= compileTimeRatio[BASELINE][compiler];
    }
    return compileTime;
  }

  /**
   * Returns the compilation rates of the baseline compiler in
   *  bytecodes/millisecond.
   * @return the compilation rates of the baseline compiler in
   *   bytecodes/millisecond
   */
  public static double getBaselineCompilationRate() {
    return compilationRates[BASELINE];
  }

  /**
   * initialize static fields
   */
  public static void init() {
    // check to see if the raw rates are specified during boot time
    if (Controller.options.COMPILER_DNA_FILE_NAME.length() != 0) {
      //  Read the DNA values from disk
      readDNA(Controller.options.COMPILER_DNA_FILE_NAME);
      initializeCompilerRatioArrays();
    }

    for (int i = 0; i < compilationRates.length; i++) {
      AOSLogging.logger.reportCompilationRate(i, compilationRates[i]);
    }
    for (int i = 0; i < speedupRates.length; i++) {
      AOSLogging.logger.reportSpeedupRate(i, speedupRates[i]);
    }

    // Compute MAX_OPT_LEVEL
    int maxProfitableCompiler = 0;
    for (int compiler = 1; compiler < numCompilers; compiler++) {
      if (compilationRates[compiler] > compilationRates[compiler - 1] ||
          speedupRates[compiler] > speedupRates[compiler - 1]) {
        maxProfitableCompiler = compiler;
      }
    }
    int maxOptLevel = getOptLevel(maxProfitableCompiler);
    Controller.options.DERIVED_MAX_OPT_LEVEL = Math.min(maxOptLevel,Controller.options.MAX_OPT_LEVEL);
    Controller.options.DERIVED_FILTER_OPT_LEVEL = Controller.options.DERIVED_MAX_OPT_LEVEL;
  }

  private static void initializeCompilerRatioArrays() {
    numCompilers = compilerNames.length;
    benefitRatio = new double[numCompilers][numCompilers];
    compileTimeRatio = new double[numCompilers][numCompilers];

    // fill in the upper triangular matrices
    for (int prevCompiler = 0; prevCompiler < numCompilers; prevCompiler++) {

      benefitRatio[prevCompiler][prevCompiler] = 1.0;
      compileTimeRatio[prevCompiler][prevCompiler] = 1.0;

      for (int nextCompiler = prevCompiler + 1; nextCompiler < numCompilers; nextCompiler++) {
        benefitRatio[prevCompiler][nextCompiler] = speedupRates[nextCompiler] / speedupRates[prevCompiler];

        // Since compilation rates are not relative to the 1st compiler
        //  we invert the division.
        compileTimeRatio[prevCompiler][nextCompiler] = compilationRates[prevCompiler] / compilationRates[nextCompiler];
        AOSLogging.logger.reportBenefitRatio(prevCompiler, nextCompiler, benefitRatio[prevCompiler][nextCompiler]);

        AOSLogging.logger.reportCompileTimeRatio(prevCompiler, nextCompiler, compileTimeRatio[prevCompiler][nextCompiler]);
      }
    }
  }

  /**
   * Read a serialized representation of the DNA info
   * @param filename DNA filename
   */
  private static void readDNA(String filename) {
    try {

      LineNumberReader in = new LineNumberReader(new FileReader(filename));

      // Expected Format
      //   CompilationRates  aaa.a  bbbb.b cccc.c dddd.d ....
      //   SpeedupRates      aaa.a  bbbb.b cccc.c dddd.d ....
      processOneLine(in, "CompilationRates", compilationRates);
      processOneLine(in, "SpeedupRates", speedupRates);
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to open controller DNA file");
    }
  }

  /**
   *  Helper method to read one line of the DNA file
   *  @param in the LineNumberReader object
   *  @param title the title string to look for
   *  @param valueHolder the array to hold the read values
   */
  private static void processOneLine(LineNumberReader in, String title, double[] valueHolder) throws IOException {

    String s = in.readLine();
    if (VM.VerifyAssertions) VM._assert(s != null);

    // parse the string
    StringTokenizer parser = new StringTokenizer(s);

    // make sure the title matches
    String token = parser.nextToken();
    if (VM.VerifyAssertions) VM._assert(token.equals(title));

    // walk through the array, making sure we still have tokens
    for (int i = 0; parser.hasMoreTokens() && i < valueHolder.length; i++) {

      // get the available token
      token = parser.nextToken();

      // convert token to a double
      valueHolder[i] = Double.valueOf(token);
    }
  }

  /**
   * returns the number of compilers
   * @return the number of compilers
   */
  public static int getNumberOfCompilers() {
    return numCompilers;
  }

  /**
   * A mapping from an Opt compiler number to the corresponding Opt level
   * @param compiler the compiler constant of interest
   * @return the Opt level that corresponds to the Opt compiler constant passed
   */
  public static int getOptLevel(int compiler) {
    switch (compiler) {
      case BASELINE:
        return -1;
      case OPT0:
        return 0;
      case OPT1:
        return 1;
      case OPT2:
        return 2;
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED, "Unknown compiler constant\n");
        return -99;
    }
  }

  /**
   * maps a compiler constant to a string
   * @param compiler
   * @return the string that represents the passed compiler constant
   */
  public static String getCompilerString(int compiler) {
    return compilerNames[compiler];
  }

  /**
   * maps opt levels to the compiler
   * @param optLevel opt level
   * @return the opt level that corresponds to the passed compiler constant
   */
  public static int getCompilerConstant(int optLevel) {
    switch (optLevel) {
      case 0:
        return OPT0;
      case 1:
        return OPT1;
      case 2:
        return OPT2;
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED, "Unknown Opt Level\n");
        return -99;
    }
  }
}
