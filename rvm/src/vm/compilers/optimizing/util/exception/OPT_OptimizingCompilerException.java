/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.io.StringWriter;
import  java.io.PrintWriter;

/**
 * Use this exception if we encounter a runtime error in the dynamic 
 * optimizing compiler.  The caller can recover by calling the 
 * non-optimizing compiler instead (or by reverting to the previous
 * version of compiled code).
 *
 * @author Vivek Sarkar
 */
public class OPT_OptimizingCompilerException extends RuntimeException {
  // When running in Jalapeno, typically optimizing compiler
  // exceptions are caught, optionally a message is printed, and we 
  // fallback to using the baseline compiler.  However, this
  // may not be desirable when running regression testing because
  // an optimizing compiler exception may be a symptom of a serious failure.
  // Thus, the code throwing the exception can use an optional boolean value
  // to indicate if the exception is "normal" or if it should be treated 
  // as a fatal failure for the purpose of regression testing. 
  public boolean isFatal = true;

  /**
   * put your documentation comment here
   */
  public OPT_OptimizingCompilerException () {
  }

  /**
   * put your documentation comment here
   * @param   boolean b
   */
  public OPT_OptimizingCompilerException (boolean b) {
    isFatal = b;
  }

  /**
   * put your documentation comment here
   * @param   String err
   */
  public OPT_OptimizingCompilerException (String err) {
    super(err);
  }

  /**
   * put your documentation comment here
   * @param   String err
   * @param   boolean b
   */
  public OPT_OptimizingCompilerException (String err, boolean b) {
    super(err);
    isFatal = b;
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err
   */
  public OPT_OptimizingCompilerException (String module, String err) {
    super("ERROR produced in module:" + module + "\n    " + err + "\n");
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   String err2
   */
  public OPT_OptimizingCompilerException (String module, String err1, String err2) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " "
        + err2 + "\n");
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   Object obj
   */
  public OPT_OptimizingCompilerException (String module, String err1, Object obj) {
    this(module, err1, obj.toString());
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   int val
   */
  public OPT_OptimizingCompilerException (String module, String err1, int val) {
    this(module, err1, Integer.toString(val));
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   String err2
   * @param   String err3
   */
  public OPT_OptimizingCompilerException (String module, String err1, String err2, 
      String err3) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " "
        + err2 + "\n" + err3 + "\n");
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   String err2
   * @param   Object obj
   */
  public OPT_OptimizingCompilerException (String module, String err1, String err2, 
      Object obj) {
    this(module, err1, err2, obj.toString());
  }

  /**
   * put your documentation comment here
   * @param   String module
   * @param   String err1
   * @param   String err2
   * @param   int val
   */
  OPT_OptimizingCompilerException (String module, String err1, String err2, 
      int val) {
    this(module, err1, err2, Integer.toString(val));
  }

  /** 
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   */
  public static void UNREACHABLE () throws OPT_OptimizingCompilerException {
    throw  new OPT_OptimizingCompilerException("Executed UNREACHABLE code");
  }

  /**
   * put your documentation comment here
   * @param module
   * @exception OPT_OptimizingCompilerException
   */
  public static void UNREACHABLE (String module) throws OPT_OptimizingCompilerException {
    throw  new OPT_OptimizingCompilerException(module, 
        "Executed UNREACHABLE code");
  }

  /**
   * put your documentation comment here
   * @param module
   * @param err1
   * @exception OPT_OptimizingCompilerException
   */
  public static void UNREACHABLE (String module, String err1) throws 
      OPT_OptimizingCompilerException {
    throw  new OPT_OptimizingCompilerException
        (module, "Executed UNREACHABLE code", err1);
  }

  /**
   * put your documentation comment here
   * @param module
   * @param err1
   * @param err2
   * @exception OPT_OptimizingCompilerException
   */
  public static void UNREACHABLE (String module, String err1, String err2) 
      throws OPT_OptimizingCompilerException {
    throw  new OPT_OptimizingCompilerException(module, 
        "Executed UNREACHABLE code", err1, err2);
  }

  /**
   * put your documentation comment here
   * @exception OPT_OptimizingCompilerException
   */
  public static void TODO () throws OPT_OptimizingCompilerException {
    throw  new 
        OPT_OptimizingCompilerException("Unsupported function in IA32 port");
  }

  /**
   * put your documentation comment here
   * @param module
   * @exception OPT_OptimizingCompilerException
   */
  public static void TODO (String module) throws OPT_OptimizingCompilerException {
    throw  new OPT_OptimizingCompilerException(module, 
        "Unsupported function in IA32 port");
  }

  /**
   * put your documentation comment here
   * @param level
   * @return 
   */
  public String trace (int level) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    printStackTrace(pw);
    int count = 0, i = 0;
    StringBuffer sb = sw.getBuffer();
    for (; i < sb.length() && count < level + 1; i++)
      if (sb.charAt(i) == '\n')
        count++;
    sb.setLength(i);
    return  sb.toString();
  }
}



