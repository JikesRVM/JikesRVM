/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;
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
  implements PrintLN 
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

  public void print(String s) {
    if (writer != null)
      writer.print(s);
    else if (stream != null)
      stream.print(s);
    else
      throw new InternalError("inconsistent internal state of ibm.com.JikesRVM.PrintContainer.PrintContainer object");
  }

  public void println(String s) {
    if (writer != null)
      writer.println(s);
    else if (stream != null)
      stream.println(s);
    else
      throw new InternalError("inconsistent internal state of ibm.com.JikesRVM.PrintContainer.PrintContainer object");
  }

  /** This class does printing via VM.sysWriteln() */
  public static class VMSysWriteln 
    implements PrintLN
  {
    public VMSysWriteln() {}
    public void println(String s) {
      VM.sysWriteln(s);
    }
    public void print(String s) {
      VM.sysWrite(s);
    }
  }
}

