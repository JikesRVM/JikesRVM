/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Member;
import com.ibm.JikesRVM.classloader.VM_Class;

import com.ibm.JikesRVM.PrintLN;  /* This import statement isn't necessary,
                                     but is here for documentation purposes.
                                     --S. Augart */ 
import java.io.PrintWriter;
import java.io.PrintStream;
import com.ibm.JikesRVM.VM;

/**
 * This class is used by java.lang.Throwable to print stack traces; it lets
 * one use a single class to do the work for both PrintWriter and PrintStream
 * output streams.
 *
 * <p> We use it instead of PrintWriter and PrintStream so we can print stack
 * traces without having to provide two versions of each method, one for
 * PrintWriter output streams and one for PrintStream.
 *
 * @author Steven Augart (w/ brainstorming by David Grove)
 */
public class PrintContainer {
  private PrintContainer() {};  // Cannot create an instance of it.
  /** This (nested) class does printing via VM.sysWriteln() */
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
  static public PrintLN readyPrinter = new WithSysWriteln();
  
  /** This (nested) class does printing via VM.sysWriteln() */
  private static class WithSysWriteln 
    extends PrintLN
  {
    WithSysWriteln() {}
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

