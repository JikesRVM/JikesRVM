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
package org.jikesrvm;

import java.io.PrintStream;
import java.io.PrintWriter;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMember;

/**
 * The subclasses of PrintContainer all implement the {@link PrintLN}
 * interface.  They are used by our {@link java.lang.Throwable} to print stack
 * traces; it lets one use a single class to operate on {@link PrintWriter}
 * and {@link PrintStream} output streams and for the {@link VM#sysWrite}
 * output method.
 *
 * <p> We use it so we can print stack traces without having to provide
 * multiple versions of each method, one for each kind of output stream.
 */
public final class PrintContainer {
  /** Can not be instantiated. */
  private PrintContainer() {}

  /** Print via PrintWriter */
  private static class WithPrintWriter extends PrintLN {
    private PrintWriter out;

    WithPrintWriter(PrintWriter out) {
      this.out = out;
    }

    @Override
    public void flush() {
      out.flush();
    }

    @Override
    public void println() {
      out.println();
    }

    @Override
    public void print(String s) {
      if (s == null) {
        s = "(*null String pointer*)";
      }
      out.print(s);
    }

    @Override
    public void print(char c) {
      out.print(c);
    }
  }

  /** Print via PrintStream */
  private static class WithPrintStream extends PrintLN {
    private PrintStream out;

    WithPrintStream(PrintStream out) {
      this.out = out;
    }

    @Override
    public boolean isSystemErr() {
      return this.out == System.err;
    }

    @Override
    public void flush() {
      out.flush();
    }

    @Override
    public void println() {
      out.println();
    }

    @Override
    public void print(String s) {
      if (s == null) {
        s = "(*null String pointer*)";
      }
      out.print(s);
    }

    @Override
    public void print(char c) {
      out.print(c);
    }
  }

  public static PrintLN get(PrintStream out) {
    return new WithPrintStream(out);
  }

  public static PrintLN get(PrintWriter out) {
    return new WithPrintWriter(out);
  }

  // Keep this one ready to go at all times :)
  public static final PrintLN readyPrinter = new WithSysWrite();

  /** This (nested) class does printing via {@link VM#sysWrite} */
  private static class WithSysWrite extends PrintLN {
    /** This doesn't carry any state, but we have a constructor so that we can
     * pass an instance of this to something expecting a {@link PrintLN} . */
    WithSysWrite() {}

    @Override
    public boolean isSysWrite() {
      return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public void println() {
      VM.sysWriteln();
    }

    @Override
    public void print(String s) {
      if (s == null) {
        s = "(*null String pointer*)";
      }

      VM.sysWrite(s);
    }

    @Override
    public void println(String s) {
      print(s);
      println();
    }

    @Override
    public void print(int i) {
      VM.sysWrite(i);
    }

    @Override
    public void printHex(int i) {
      VM.sysWriteHex(i);
    }

    @Override
    public void print(char c) {
      VM.sysWrite(c);
    }

    @Override
    public void print(RVMMember m) {
      VM.sysWrite(m);
    }

    @Override
    public void print(Atom a) {
      VM.sysWrite(a);
    }
  }
}

