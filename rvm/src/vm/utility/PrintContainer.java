/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;

/* Some of these import statements aren't necessary, but are here for
   documentation purposes.  --S. Augart */ 
import com.ibm.JikesRVM.PrintLN;
import com.ibm.JikesRVM.VM;

import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Member;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * The subclasses of PrintContainer all implement the {@link PrintLN}
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
public class PrintContainer {
  /** Can not be instantiated. */
  private PrintContainer() {};
  /** Print via PrintWriter */
  private static class WithPrintWriter
    extends PrintLN
  {
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
    extends PrintLN
  {
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

  static public PrintLN get(PrintStream out) {
     return new WithPrintStream(out);
  }

  static public PrintLN get(PrintWriter out) {
     return new WithPrintWriter(out);
  }

  // Keep this one ready to go at all times :)
  static public PrintLN readyPrinter = new WithSysWrite();
  
  /** This (nested) class does printing via {@link VM#sysWrite} */
  private static class WithSysWrite 
    extends PrintLN
  {
    /** This doesn't carry any state, but we have a constructor so that we can
     * pass an instance of this to something expecting a {@link PrintLN} . */
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

