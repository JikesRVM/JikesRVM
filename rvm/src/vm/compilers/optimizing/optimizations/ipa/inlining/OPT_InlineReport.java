/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.*;
import  java.io.*;

/**
 * OPT_InlineReport.java
 *
 * This class records and can synthesize information on the inlining choices
 * made during compilation.
 * 
 * @author Anne Mulhern
 *
 * @see OPT_InlineReportElement
 */
class OPT_InlineReport {
  /*
   * The PrintStream object to which the report is written.
   */
  private static PrintStream report;
  /*
   * The decision currently being made
   */
  private static OPT_InlineReportElement cur;

  /*
   * constructor
   * The constructor is private so that it can not be called from another class.
   */
  private OPT_InlineReport () {
  }

  /*
   */
  private static void report (String line) {
    // do it this way to let us put OPT_InlineReport in the bootimage
    // (can't initialize a file stream at bootimage writing time that
    //  we want to use at VM runtime).
    if (report == null) {
      try {
        report = new PrintStream((OutputStream)new FileOutputStream(
            "InlineReport.txt"));
      } catch (FileNotFoundException e) {
        System.err.println("Can't open file: InlineReport.txt");
      }
    }
    report.print(line);
  }

  /*
   * intended to be called when a new call site is reached
   * creates a new OPT_InlineReportElement object and places it on the stack.
   */
  public static void beginNewDecision () {
    if (cur == null)
      report("Starting...\n"); 
    else 
      report(cur + "\n");
    cur = new OPT_InlineReportElement();
  }

  /*
   * sets the call type of the instruction
   * @see OPT_InlineReportElement#setCallType
   */
  public static void setCallType (int type) {
    cur.setCallType(type);
  }

  /*
   * sets the values obtainable from the OPT_Compilation state object
   * 
   * @see OPT_InlineReportElement#setValues
   */
  public static void setValues (OPT_CompilationState state) {
    cur.setValues(state);
  }

  /*
   * sets the inline decision
   *
   * @see OPT_InlineReportElement#setDecision
   */
  public static void setDecision (OPT_InlineDecision d) {
    cur.setDecision(d);
  }

  /*
   * @see OPT_InlineReportElement#isPreciseType
   */
  public static void isPreciseType () {
    cur.isPreciseType();
  }

  /*
   * @see OPT_InlineReportElement#classUnresolved
   */
  public static void classUnresolved (boolean val, VM_Method method) {
    cur.classUnresolved(val, method);
  }

  /*
   * @see OPT_InlineReportElement#inliningDeferred
   */
  public static void inliningDeferred () {
    cur.inliningDeferred();
  }

  /*
   * @see OPT_InlineReportElement#unimplementedMagic
   */
  public static void unimplementedMagic (VM_Method method) {
    cur.unimplementedMagic(method);
  }

  /*
   * @see OPT_InlineReportElement#instructionNull
   */
  public static void instructionNull () {
    cur.instructionNull();
  }

  /*
   * @see OPT_InlineReportElement#isMagic
   */
  public static void isMagic (VM_Method magic) {
    cur.isMagic(magic);
  }
  public static final int INVOKE_VIRTUAL = 0;
  public static final int INVOKE_SPECIAL = 1;
  public static final int INVOKE_STATIC = 2;
  public static final int INVOKE_INTERFACE = 3;

  /*
   * encodes mapping of instruction type constant values to a string
   *
   * @param type an int representing the type of the call instruction
   *
   * @return a string representation of the type of the instruction
   */
  public static String getInstructionType (int type) {
    switch (type) {
      case INVOKE_VIRTUAL:
        return  "invokevirtual";
      case INVOKE_SPECIAL:
        return  "invokespecial";
      case INVOKE_STATIC:
        return  "invokestatic";
      case INVOKE_INTERFACE:
        return  "invokeinterface";
      default:
        return  "unknown type";
    }
  }
}



