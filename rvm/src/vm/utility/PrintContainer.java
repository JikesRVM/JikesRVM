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
public class PrintContainer 
  extends PrintLN 
{
  // These will be implicitly initialized to null.
  private PrintWriter writer;
  private PrintStream stream;
  
  public PrintContainer(PrintWriter out) {
    writer = out;
  }
  public PrintContainer(PrintStream out) {
    stream = out;
  }

  public boolean isSystemErr() {
    return stream == System.err;
  }
  public boolean isVMSysWriteln() {
    return false;
  }
  private void inconsistentState() {
      throw new InternalError("inconsistent internal state of ibm.com.JikesRVM.PrintContainer.PrintContainer object");
  }

  public void flush() {
    if (writer != null)
      writer.flush();
    else if (stream != null)
      stream.flush();
    else
      inconsistentState();
  }

  public void println() {
    if (writer != null)
      writer.println();
    else if (stream != null)
      stream.println();
    else
      inconsistentState();
  }

  public void print(String s) {
    if (s == null)
      s = "(*null String pointer*)";

    if (writer != null)
      writer.print(s);
    else if (stream != null)
      stream.print(s);
    else
      inconsistentState();
    
  }

  public void println(String s) {
    print(s);
    if (writer != null)
      writer.println();
    else if (stream != null)
      stream.println();
    else
      inconsistentState();
  }

  
  /* Here, we are writing code to make sure that we do not rely upon any
   * external memory accesses. */
  // largest power of 10 representable as a Java integer.
  // (max int is            2147483647)
  final int max_int_pow10 = 1000000000;

  public void print(int n) {
    boolean suppress_leading_zero = true;
    if (n == 0x80000000) {
      print("-2147483648");
      return;
    } else if (n == 0) {
      print('0');
      return;
    } else if (n < 0) {
      print('-');
      n = -n;
    }     
    /* We now have a positive # of the proper range.  Will need to exit from
       the bottom of the loop. */
    for (int p = max_int_pow10; p >= 1; p /= 10) {
      int digit = n / p;
      n -= digit * p;
      if (digit == 0 && suppress_leading_zero)
	continue;
      suppress_leading_zero = false;
      char c = (char) ('0' + digit);
      print(c);
    }
  }

  public void printHex(int n) {
    print("0x");
    // print exactly 8 hexadec. digits.
    for (int i = 32 - 4; i >= 0; i -= 4) {
      int digit = (n >>> i) & 0x0000000F;		// fill with 0 bits.
      char c;

      if (digit <= 9) {
	c = (char) ('0' + digit);
      } else {
	c = (char) ('A' + (digit - 10));
      }
      print(c);
    }
  }


  /* Here we need to imitate the work that would normally be done by
   * VM_Member.toString() (which VM_Method.toString() inherits) */
  public void print(VM_Member m) {
    print(m.getDeclaringClass()); // VM_Class
    print('.');
    print(m.getName());
    print(' ');
    print(m.getDescriptor());
  }

  public void print(VM_Atom a) {
    byte[] val = a.toByteArray();
    for (int i = 0; i < val.length; ++i) {
      print((char) val[i]);
    }
  }

  public void print(char c) {
    if (writer != null)
      writer.print(c);
    else if (stream != null)
      stream.print(c);
    else
      inconsistentState();
  }

  /** This (nested) class does printing via VM.sysWriteln() */
  public static class VMSysWriteln 
    extends PrintLN
  {
    public VMSysWriteln() {}
    public boolean isSystemErr() {
      return false;
    }
    public boolean isVMSysWriteln() {
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

