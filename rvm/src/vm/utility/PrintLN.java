/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;
// import com.ibm.JikesRVM.PrintLN;
import java.io.PrintWriter;
import java.io.PrintStream;

/**
 * This interface is implemented by com.ibm.JikesRVM.PrintContainer.  The
 * interfaces is used by our java.lang.Throwable to print stack traces.
 *
 * @author Steven Augart (w/ brainstorming by David Grove)
 */
public interface PrintLN {
  //  PrintLN(PrintWriter out);
  //  PrintLN(PrintStream out);
  void print(String s);
  void println(String s);
}

