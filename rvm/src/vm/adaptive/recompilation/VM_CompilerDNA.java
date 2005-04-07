/*
 * (C) Copyright IBM Corp. 2001, 2003, 2005
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;
import java.io.*;
import java.util.*;

/**
 * This class codifies the cost/benefit properties of the various compilers
 * used in the adaptive optimization system.
 * 
 * <p>
 * The DNA tells the AOS two important kinds of averages for each optimization
 * level: the cost of compiling at an optimization level (as measured in
 * bytecode/milliseconds) and the expected speedup of the resulting code
 * (relative to the first compiler).
 *
 * <p>There is an AOS command-line option to set the compiler DNA.  The method
 * {@link VM_CompilerDNA#readDNA} contains a comment on the expected format.
 * 
 * @author Michael Hind
 */
public class VM_CompilerDNA implements VM_Constants {

  private static final String[] compilerNames = {"Baseline", 
                                                 //-#if RVM_WITH_QUICK_COMPILER
                                                 "Quick", 
                                                 //-#endif
                                                 "Opt0", "Opt1", "Opt2"};
  final static int BASELINE = 0;
  //-#if RVM_WITH_QUICK_COMPILER
  final static int QUICK = 1;
  final static int OPT0 = 2;
  final static int OPT1 = 3;
  final static int OPT2 = 4;
  //-#else
  final static int OPT0 = 1;
  final static int OPT1 = 2;
  final static int OPT2 = 3;
  //-#endif


  /**
   *  The number of compilers available
   */
  private static int numCompilers;

  /**
   * Average bytecodes compiled per millisec
   * These numbers were from a shadow on July 22, 2004 on munchkin (AIX/PPC)
   * and Dec 2nd, 2004 on wormtongue (Linux/IA32) using unweighted compilation rate.
   */
  //-#if RVM_FOR_POWERPC
  private static final double[] compilationRates = {
    359.17,
    //-#if RVM_WITH_QUICK_COMPILER
    300, 
    //-#endif
    10.44, 4.69, 1.56};
  //-#elif RVM_FOR_IA32
  private static final double[] compilationRates = {
    696.58,
    18.19, 8.90, 3.90};
  //-#endif

  /**
   * What is the execution rate of each compiler normalized to the 1st compiler
   * These numbers were from a shadow on July 22, 2004 on munchkin (AIX/PPC)
   * and Dec 2nd, 2004 on wormtongue (Linux/IA32) using unweighted compilation rate.
   */
  //-#if RVM_FOR_POWERPC
  private static final double[] speedupRates = {
    1.00, 
    //-#if RVM_WITH_QUICK_COMPILER
    1.32, 
    //-#endif
    4.73, 6.65, 7.39};
  //-#elif RVM_FOR_IA32
  private static final double[] speedupRates = {1.00,
                                                4.56, 7.13, 7.35};
  //-#endif

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

  /**
   * This method returns the expected speedup from going from compiler1 to compiler2
   * @param compiler1
   * @param compiler2
   * @return the benefit ratio (speedup) of moving from compiler1 to compiler2
   */
  static public double getBenefitRatio(int compiler1, int compiler2) {
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
  static public double getCompileTimeRatio(int compiler1, int compiler2) {
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
  static double estimateCompileTime(int compiler, VM_NormalMethod meth) {
    double bytes = (double)meth.getBytecodeLength();
    double runtimeBaselineRate = VM_RuntimeCompiler.getBaselineRate();
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
  static void init()  { 
    // check to see if the raw rates are specified during boot time
    if (!VM_Controller.options.COMPILER_DNA_FILE_NAME.equals("")) {
      //  Read the DNA values from disk
      readDNA(VM_Controller.options.COMPILER_DNA_FILE_NAME);
    }

    numCompilers = compilerNames.length;

    benefitRatio = new double[numCompilers][numCompilers];
    compileTimeRatio = new double[numCompilers][numCompilers];

    if (VM.LogAOSEvents) {
      for (int i=0; i < compilationRates.length; i++) {
        VM_AOSLogging.reportCompilationRate(i, compilationRates[i]);
      }
      for (int i=0; i < speedupRates.length; i++) {
        VM_AOSLogging.reportSpeedupRate(i, speedupRates[i]);
      }
    }

    // fill in the upper triangular matrices
    for (int prevCompiler = 0; 
         prevCompiler < numCompilers; 
         prevCompiler++) {

      benefitRatio[prevCompiler][prevCompiler] = 1.0;
      compileTimeRatio[prevCompiler][prevCompiler] = 1.0;

      for (int nextCompiler = prevCompiler+1; 
           nextCompiler < numCompilers; 
           nextCompiler++) {

        benefitRatio[prevCompiler][nextCompiler] = 
          speedupRates[nextCompiler] / speedupRates[prevCompiler];

        // Since compilation rates are not relative to the 1st compiler
        //  we invert the division.
        compileTimeRatio[prevCompiler][nextCompiler] = 
          compilationRates[prevCompiler] / compilationRates[nextCompiler];  

        if (VM.LogAOSEvents) {
          VM_AOSLogging.reportBenefitRatio(
                           prevCompiler, nextCompiler,
                           benefitRatio[prevCompiler][nextCompiler]);

          VM_AOSLogging.reportCompileTimeRatio(
                           prevCompiler, nextCompiler,
                           compileTimeRatio[prevCompiler][nextCompiler]);
        }
        
      }
    }

    // Compute MAX_OPT_LEVEL
    int maxProfitableCompiler = 0;
    for (int compiler = 1; compiler < numCompilers; compiler++) {
      if (compilationRates[compiler] > compilationRates[compiler-1] ||
          speedupRates[compiler] > speedupRates[compiler-1]) {
        maxProfitableCompiler = compiler;
      }
    }
    int maxOptLevel = getOptLevel(maxProfitableCompiler);
    VM_Controller.options.MAX_OPT_LEVEL = maxOptLevel;
    VM_Controller.options.FILTER_OPT_LEVEL = maxOptLevel;
  }


  /** 
   * Read a serialized representation of the DNA info
   * @param filename DNA filename
   */
  static private void readDNA(String filename) {
    try {

      LineNumberReader in =
        new LineNumberReader(new FileReader(filename));

      // Expected Format
      //   CompilationRates  aaa.a  bbbb.b cccc.c dddd.d ....
      //   SpeedupRates      aaa.a  bbbb.b cccc.c dddd.d ....
      processOneLine(in, "CompilationRates", compilationRates);
      processOneLine(in, "SpeedupRates", speedupRates);
    }
    catch (Exception e) {
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
  static private void processOneLine(LineNumberReader in, String title,
                                     double[] valueHolder) throws IOException {

    String s = in.readLine();
    if (VM.VerifyAssertions) VM._assert(s != null);
    
    // parse the string
    StringTokenizer parser = new StringTokenizer(s);
    
    // make sure the title matches
    String token = parser.nextToken();
    if (VM.VerifyAssertions) VM._assert(token.equals(title));
    
    // walk through the array, making sure we still have tokens
    for (int i=0;
         parser.hasMoreTokens() && i < valueHolder.length;
         i++) {

      // get the available token
      token = parser.nextToken();
      
      // convert token to a double
      valueHolder[i] = Double.valueOf(token).doubleValue();
    }
  }

  /**
   * returns the number of compilers 
   * @return the number of compilers 
   */
  static public int getNumberOfCompilers() {
    return numCompilers;
  }


  /**
   * A mapping from an Opt compiler number to the corresponding Opt level
   * @param compiler the compiler constant of interest
   * @return the Opt level that corresponds to the Opt compiler constant passed
   */
  public static int getOptLevel(int compiler) {
    switch (compiler) {
      case BASELINE: return -1;
      //-#if RVM_WITH_QUICK_COMPILER
      case QUICK: return -1;
      //-#endif
      case OPT0: return 0;
      case OPT1: return 1;
      case OPT2: return 2;
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
      case 0: return OPT0;
      case 1: return OPT1;
      case 2: return OPT2;
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED, "Unknown Opt Level\n");
        return -99;
    }
  }
}
