/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import  java.io.StringWriter;
import  java.io.PrintWriter;

/**
 * Use this exception if we encounter a runtime error in the  
 * quick compiler.  The caller can recover by calling the 
 * non-optimizing compiler instead.
 *
 * @author Chris Hoffmann
 */
public class VM_QuickCompilerException extends RuntimeException {
  /** 
   * When running in the RVM, typically quick compiler
   * exceptions are caught, optionally a message is printed, and we 
   * fallback to using the baseline compiler.  However, this
   * may not be desirable when running regression testing because
   * an quick compiler exception may be a symptom of a serious failure.
   * Thus, the code throwing the exception can use an optional boolean value
   * to indicate if the exception is "normal" or if it should be treated 
   * as a fatal failure for the purpose of regression testing. 
   */
  public boolean isFatal = true;

  public VM_QuickCompilerException () { }

  /**
   * @param   b is the exception fatal?
   */
  public VM_QuickCompilerException (boolean b) {
    isFatal = b;
  }

  /**
   * @param  err message describing reason for exception
   */
  public VM_QuickCompilerException (String err) {
    super(err);
  }

  /**
   * @param   err message descrining reason for exception 
   * @param   b is the exception fatal?
   */
  public VM_QuickCompilerException (String err, boolean b) {
    super(err);
    isFatal = b;
  }

  /** 
   * @param   module opt compiler module in which exception was raised
   * @param   err message describing reason for exception 
   */
  public VM_QuickCompilerException (String module, String err) {
    super("ERROR produced in module:" + module + "\n    " + err + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   err2 message describing reason for exception 
   */
  public VM_QuickCompilerException (String module, String err1, String err2) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " "
        + err2 + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   obj  object to print describing reason for exception
   */
  public VM_QuickCompilerException (String module, String err1, Object obj) {
    this(module, err1, obj.toString());
  }

  /** 
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   val  integer to print describing reason for exception
   */
  public VM_QuickCompilerException (String module, String err1, int val) {
    this(module, err1, Integer.toString(val));
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   err2 message describing reason for exception 
   * @param   err3 message describing reason for exception 
   */
  public VM_QuickCompilerException (String module, String err1, String err2, 
					  String err3) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " "
        + err2 + "\n" + err3 + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   err2 message describing reason for exception 
   * @param   obj  object to print describing reason for exception
   */
  public VM_QuickCompilerException (String module, String err1, String err2, 
					  Object obj) {
    this(module, err1, err2, obj.toString());
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   err2 message describing reason for exception 
   * @param   val  integer to print describing reason for exception
   */
  VM_QuickCompilerException (String module, String err1, String err2, 
				   int val) {
    this(module, err1, err2, Integer.toString(val));
  }

  /** 
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @exception VM_QuickCompilerException
   */
  public static void UNREACHABLE () throws VM_QuickCompilerException {
    throw  new VM_QuickCompilerException("Executed UNREACHABLE code");
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param module module in which exception occured
   * @exception VM_QuickCompilerException
   */
  public static void UNREACHABLE (String module) throws VM_QuickCompilerException {
    throw  new VM_QuickCompilerException(module, 
        "Executed UNREACHABLE code");
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @exception VM_QuickCompilerException
   */
  public static void UNREACHABLE (String module, String err1) throws 
      VM_QuickCompilerException {
    throw  new VM_QuickCompilerException
        (module, "Executed UNREACHABLE code", err1);
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception 
   * @param   err2 message describing reason for exception 
   * @exception VM_QuickCompilerException
   */
  public static void UNREACHABLE (String module, String err1, String err2) 
      throws VM_QuickCompilerException {
    throw  new VM_QuickCompilerException(module, 
        "Executed UNREACHABLE code", err1, err2);
  }

  /**
   * Incomplete function in IA32 port.
   * @exception VM_QuickCompilerException
   */
  public static void TODO () throws VM_QuickCompilerException {
    throw  new 
        VM_QuickCompilerException("Unsupported function in IA32 port");
  }

  /**
   * Incomplete function in IA32 port.
   * @param   module opt compiler module in which exception was raised
   * @exception VM_QuickCompilerException
   */
  public static void TODO (String module) throws VM_QuickCompilerException {
    throw  new VM_QuickCompilerException(module, 
        "Unsupported function in IA32 port");
  }

  /**
   * Return a string that is the printout of level stackframes in the stacktrace.
   * @param level the number of levels to print
   * @return n-level dump of stacktrace
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



