/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2003
 */
package org.jikesrvm;

import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Member;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * The subclasses of VM_PrintContainer all implement the {@link VM_PrintLN}
 * interface.  They are used by our {@link java.lang.Throwable} to print stack
 * traces; it lets one use a single class to operate on {@link PrintWriter}
 * and {@link PrintStream} output streams and for the {@link VM#sysWrite}
 * output method.
 *
 * <p> We use it so we can print stack traces without having to provide
 * multiple versions of each method, one for each kind of output stream.
 *
 * @author Steven Augart (w/ brainstorming by David Grove)
 */
public class VM_PrintContainer {
  /** Can not be instantiated. */
  private VM_PrintContainer() {}
  /** Print via PrintWriter */
  private static class WithPrintWriter
    extends VM_PrintLN {
    private PrintWriter out;

    WithPrintWriter(PrintWriter out) {
      this.out = out;
    }
    public void flush() {
      out.flush();
    }
    public void println() {
      out.println();
    }
    public void print(String s) {
      if (s == null)
        s = "(*null String pointer*)";
      out.print(s);
    }
    public void print(char c) {
      out.print(c);
    }
  }
  
  /** Print via PrintStream */
  private static class WithPrintStream
    extends VM_PrintLN {
    private PrintStream out;

    WithPrintStream(PrintStream out) {
      this.out = out;
    }

    public boolean isSystemErr() {
      return this.out == System.err;
    }
    public void flush() {
      out.flush();
    }
    public void println() {
      out.println();
    }
    public void print(String s) {
      if (s == null)
        s = "(*null String pointer*)";
      out.print(s);
    }
    public void print(char c) {
      out.print(c);
    }
  }

  public static VM_PrintLN get(PrintStream out) {
     return new WithPrintStream(out);
  }

  public static VM_PrintLN get(PrintWriter out) {
     return new WithPrintWriter(out);
  }

  // Keep this one ready to go at all times :)
  public static final VM_PrintLN readyPrinter = new WithSysWrite();
  
  /** This (nested) class does printing via {@link VM#sysWrite} */
  private static class WithSysWrite 
    extends VM_PrintLN {
    /** This doesn't carry any state, but we have a constructor so that we can
     * pass an instance of this to something expecting a {@link VM_PrintLN} . */
    WithSysWrite() {}

    public boolean isSysWrite() {
      return true;
    }
    public void flush() {
    }
    public void println() {
      VM.sysWriteln();
    }
    public void print(String s) {
      if (s == null)
        s = "(*null String pointer*)";
      
      VM.sysWrite(s);
    }
    public void println(String s) {
      print(s);
      println();
    }
    public void print(int i) {
      VM.sysWrite(i);
    }
    public void printHex(int i) {
      VM.sysWriteHex(i);
    }
    public void print(char c) {
      VM.sysWrite(c);
    }
    public void print(VM_Member m) {
      VM.sysWrite(m);
    }
    public void print(VM_Atom a) {
      VM.sysWrite(a);
    }
  }
}

