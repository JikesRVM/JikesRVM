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
package org.jikesrvm.compilers.opt;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.jikesrvm.classloader.TypeReference;

/**
 * Use this exception if we encounter a runtime error in the dynamic
 * optimizing compiler.  The caller can recover by calling the
 * non-optimizing compiler instead (or by reverting to the previous
 * version of compiled code).
 */
public class OptimizingCompilerException extends RuntimeException {
  /** Support for exception serialization */
  static final long serialVersionUID = -868535710873341956L;

  /**
   * Capture illegal upcasts from magic types to java.lang.Object
   */
  public static final class IllegalUpcast extends RuntimeException {
    /** Support for exception serialization */
    static final long serialVersionUID = -847866659938089530L;
    /** Unboxed type that was attempted to convert to an Object */
    final transient TypeReference magicType;

    public IllegalUpcast(TypeReference type) {
      super("Illegal upcast from " + type + " to java.lang.Object");
      magicType = type;
    }
  }

  /**
   * When running in the RVM, typically optimizing compiler
   * exceptions are caught, optionally a message is printed, and we
   * fallback to using the baseline compiler.  However, this
   * may not be desirable when running regression testing because
   * an optimizing compiler exception may be a symptom of a serious failure.
   * Thus, the code throwing the exception can use an optional boolean value
   * to indicate if the exception is "normal" or if it should be treated
   * as a fatal failure for the purpose of regression testing.
   */
  public boolean isFatal = true;

  public OptimizingCompilerException() { }

  /**
   * @param   b is the exception fatal?
   */
  public OptimizingCompilerException(boolean b) {
    isFatal = b;
  }

  /**
   * @param  err message describing reason for exception
   */
  public OptimizingCompilerException(String err) {
    super(err);
  }

  /**
   * @param   err message descrining reason for exception
   * @param   b is the exception fatal?
   */
  public OptimizingCompilerException(String err, boolean b) {
    super(err);
    isFatal = b;
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err message describing reason for exception
   */
  public OptimizingCompilerException(String module, String err) {
    super("ERROR produced in module:" + module + "\n    " + err + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   err2 message describing reason for exception
   */
  public OptimizingCompilerException(String module, String err1, String err2) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " " + err2 + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   obj  object to print describing reason for exception
   */
  public OptimizingCompilerException(String module, String err1, Object obj) {
    this(module, err1, obj.toString());
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   val  integer to print describing reason for exception
   */
  public OptimizingCompilerException(String module, String err1, int val) {
    this(module, err1, Integer.toString(val));
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   err2 message describing reason for exception
   * @param   err3 message describing reason for exception
   */
  public OptimizingCompilerException(String module, String err1, String err2, String err3) {
    super("ERROR produced in module:" + module + "\n    " + err1 + " " + err2 + "\n" + err3 + "\n");
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   err2 message describing reason for exception
   * @param   obj  object to print describing reason for exception
   */
  public OptimizingCompilerException(String module, String err1, String err2, Object obj) {
    this(module, err1, err2, obj.toString());
  }

  /**
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   err2 message describing reason for exception
   * @param   val  integer to print describing reason for exception
   */
  OptimizingCompilerException(String module, String err1, String err2, int val) {
    this(module, err1, err2, Integer.toString(val));
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @exception OptimizingCompilerException
   */
  public static void UNREACHABLE() throws OptimizingCompilerException {
    throw new OptimizingCompilerException("Executed UNREACHABLE code");
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param module module in which exception occured
   * @exception OptimizingCompilerException
   */
  public static void UNREACHABLE(String module) throws OptimizingCompilerException {
    throw new OptimizingCompilerException(module, "Executed UNREACHABLE code");
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @exception OptimizingCompilerException
   */
  public static void UNREACHABLE(String module, String err1) throws OptimizingCompilerException {
    throw new OptimizingCompilerException(module, "Executed UNREACHABLE code", err1);
  }

  /**
   * Use the UNREACHABLE methods to mark code that should never execute
   * eg, unexpected cases of switch statments and nested if/then/else
   * @param   module opt compiler module in which exception was raised
   * @param   err1 message describing reason for exception
   * @param   err2 message describing reason for exception
   * @exception OptimizingCompilerException
   */
  public static void UNREACHABLE(String module, String err1, String err2) throws OptimizingCompilerException {
    throw new OptimizingCompilerException(module, "Executed UNREACHABLE code", err1, err2);
  }

  /**
   * Incomplete function in IA32 port.
   * @exception OptimizingCompilerException
   */
  public static void TODO() throws OptimizingCompilerException {
    throw new OptimizingCompilerException("Unsupported function in IA32 port");
  }

  /**
   * Incomplete function in IA32 port.
   * @param   module opt compiler module in which exception was raised
   * @exception OptimizingCompilerException
   */
  public static void TODO(String module) throws OptimizingCompilerException {
    throw new OptimizingCompilerException(module, "Unsupported function in IA32 port");
  }

  /**
   * Return a string that is the printout of level stackframes in the stacktrace.
   * @param level the number of levels to print
   * @return n-level dump of stacktrace
   */
  public String trace(int level) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    printStackTrace(pw);
    int count = 0, i = 0;
    StringBuffer sb = sw.getBuffer();
    for (; i < sb.length() && count < level + 1; i++) {
      if (sb.charAt(i) == '\n') {
        count++;
      }
    }
    sb.setLength(i);
    return sb.toString();
  }
}
