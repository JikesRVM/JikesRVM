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
package org.jikesrvm.tools.oth;

import java.io.PrintStream;

import org.jikesrvm.tests.util.StringBuilderOutputStream;

public class TestOutput implements OptTestHarnessOutput {

  private final StringBuilderOutputStream out;
  private final StringBuilderOutputStream err;

  private final PrintStream sysOut;
  private final PrintStream sysErr;

  TestOutput() {
  out = new StringBuilderOutputStream();
  sysOut = new PrintStream(out);

  err = new StringBuilderOutputStream();
  sysErr = new PrintStream(err);
  }

  @Override
  public void sysOutPrintln(String s) {
    sysOut.println(s);
  }

  @Override
  public void sysOutPrint(String s) {
    sysOut.print(s);
  }

  @Override
  public void sysErrPrintln(String s) {
    sysErr.println(s);
  }

  @Override
  public PrintStream getSystemErr() {
    return sysErr;
  }

  public PrintStream getSystemOut() {
    return sysOut;
  }

  public StringBuilder getStandardOutput() {
    return out.getOutput();
  }

  public StringBuilder getStandardError() {
    return err.getOutput();
  }

}
