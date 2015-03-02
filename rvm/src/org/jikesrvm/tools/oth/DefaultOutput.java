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

/**
 * Implements printing of output to the standard locations, i.e.
 * {@linkplain System#out} and {@linkplain System#err}.
 */
public class DefaultOutput implements OptTestHarnessOutput {

  @Override
  public void sysOutPrintln(String s) {
    System.out.println(s);
  }

  @Override
  public void sysOutPrint(String s) {
    System.out.print(s);
  }

  @Override
  public void sysErrPrintln(String s) {
    System.err.println(s);
  }

  @Override
  public PrintStream getSystemErr() {
    return System.err;
  }

}
