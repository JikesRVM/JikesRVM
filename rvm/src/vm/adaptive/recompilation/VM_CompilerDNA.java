/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_Constants;
import java.io.*;
import java.util.*;

/**
 * This class codifies the cost/benefit properties of the various compilers
 * used in the adaptive optimization system.
 *
 * @author: Michael Hind
 */
class VM_CompilerDNA implements VM_Constants {

  private static final String[] compilerNames = {"Baseline", "Opt0", "Opt1", "Opt2"};
  final static int BASELINE = 0;
  final static int OPT0 = 1;
  final static int OPT1 = 2;
  final static int OPT2 = 3;

  /**
   *  The number of compilers available
   */
  private static int numCompilers;

  /**
   *  Average bytecodes compiled per millisec
   */
  //-#if RVM_FOR_AIX
  /*
   *  These numbers were from a shadow on June 28, 2002 on AIX/PPC (munchkin)
   */
  private static final double[] compilationRates = {431.78, 9.72, 4.04, 1.44};
  //-#else
  /*
   *  These numbers were from a shadow on July 7, 2002 on Linux/IA32 (turangalila)
   */
  private static final double[] compilationRates = {742.78, 15.68, 6.25, 2.52};
  //-#endif

  /**
   * What is the execution rate of each compiler normalized to the 1st compiler
   */
  //-#if RVM_FOR_AIX
  /*
   *  These numbers were from a shadow on June 28, 2002 on AIX/PPC (munchkin)
   */
  private static final double[] speedupRates = {1.00, 4.09, 5.27, 5.66};
  //-#else
  /*
   *  These numbers were from a shadow on July 7, 2002 on Linux/IA32 (turangalila)
   */
  private static final double[] speedupRates = {1.00, 3.55, 4.68, 4.75};
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
   * Returns the compilation rates of the baseline compiler in 
   *  bytecodes/millisecond
   * @return the compilation rates of the baseline compiler in 
   *   bytecodes/millisecond
   */
  static public double getBaselineCompilationRate() {
    return compilationRates[BASELINE];
  }

  /**
   * initialize static fields
   */
  static void init()  { 
    // check to see if the raw rates are specified during boot time
    if (VM_Controller.options.USE_COMPILER_DNA_FILE) {
      //  Read the DNA values from disk
      readDNA();
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
  }


  /** 
   * Read a serialized representation of the DNA info
   */
  static private void readDNA() {
    try {

      LineNumberReader in =
	new LineNumberReader(new FileReader(VM_Controller.options.COMPILER_DNA_FILE_NAME));

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
