/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002, 2003
 */
package java.lang;

import java.io.PrintStream;
import java.io.PrintWriter;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Options;
import org.jikesrvm.VM_StackTrace;
import org.jikesrvm.PrintLN;
import org.jikesrvm.PrintContainer;

/**
 * Jikes RVM implementation of java.lang.Throwable.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 * 
 * @author Julian Dolby
 * @author Dave Grove
 * @modified Steven Augart
 */
public class Throwable implements java.io.Serializable {

  private static final long serialVersionUID = -3042686055658047285L;
    
  /**
   * The message provided when the exception was created.
   */
  private String detailMessage;
    
  /**
   * An object which describes the walkback.
   */
  private transient VM_StackTrace stackTrace;
    
  /**
   * The cause of the throwable
   */
  private Throwable cause;
    
  public Throwable() {
    super();
    fillInStackTrace();         // fillInStackTrace() catches its own errors,
                                // and performs reporting.
  }
    
  public Throwable(String detailMessage) {
    this();
    this.detailMessage = detailMessage;
    if (stackTrace.isVerbose()) {
      VM.sysWrite("[ Throwable for VM_StackTrace # ", stackTrace.traceIndex);
      VM.sysWrite(" created w/ message:", detailMessage);
      VM.sysWriteln("]");
    }
  }
    
  public Throwable(String message, Throwable cause) {
    this(message);
    initCause(cause);
  }
    
  public Throwable(Throwable cause) {
    this(cause == null ? null : cause.toString(), cause);
  }
    
  private int numWeirdErrors = 0;
  /** just a guess.  Resettable if you really want to. */
  private int maxWeirdErrors = 4; 
  public void tallyWeirdError() {
    if (++numWeirdErrors >= maxWeirdErrors) {
      /* We exit before printing, in case we're in some weird hell where
         everything is broken, even VM.sysWriteln().. */
      VM.sysExit(VM.EXIT_STATUS_TOO_MANY_THROWABLE_ERRORS);
    }
    if (numWeirdErrors > 1 ) {
      VM.sysWriteln("I'm now ", numWeirdErrors, " levels deep in weird Errors while handling this Throwable");
    }
  }
  
  private int numOutOfMemoryErrors = 0;
  public int maxOutOfMemoryErrors = 5; /* again, a guess at a good value.
                                          Resettable if the user wants to. */
  public void tallyOutOfMemoryError() {
    if (++numOutOfMemoryErrors >= maxOutOfMemoryErrors) {
      /* We exit before printing, in case we're in some weird hell where
          everything is broken, even VM.sysWriteln().. */
      VM.sysExit(VM.EXIT_STATUS_TOO_MANY_OUT_OF_MEMORY_ERRORS);
    }
    if (numOutOfMemoryErrors > 1 ) {
      VM.sysWriteln("GC Warning: I'm now ", numOutOfMemoryErrors, " levels deep in OutOfMemoryErrors while handling this Throwable");
    }
  }
  

  public Throwable fillInStackTrace() {
    /* We collect the whole stack trace, and we strip out the cause of the
       exception later on at printing time, in printStackTrace(). */
    try {
      stackTrace = new VM_StackTrace(0);
    } catch (OutOfMemoryError t) {
      tallyOutOfMemoryError();
      stackTrace = null;
      VM.sysWriteln("Throwable.fillInStackTrace(): Cannot fill in a stack trace; out of memory!");
    } catch (Throwable t) {
      stackTrace = null;
      tallyWeirdError();
      VM.sysWrite("Throwable.fillInStackTrace(): Cannot fill in a stack trace; got a weird Throwable when I tried to:\n\t");
      t.sysWriteln();
      VM.sysWriteln("Throwable.fillInStackTrace: [ BEGIN Trying to dump THAT stack trace:");
      t.sysWriteStackTrace();
      VM.sysWriteln("Throwable.fillInStackTrace(): END of dumping recursive stack trace ]");
    }
    return this;
  }

  public Throwable getCause() {
    return cause;
  }

  public String getLocalizedMessage() {
    return getMessage();
  }
    
  public String getMessage() {
    return detailMessage;
  }
    
  /** This has a stub implementation.  How gross.  But legal. */
  public StackTraceElement[] getStackTrace() {
    return new StackTraceElement[0];
    // throw new VM_UnimplementedError();
  }

  public Throwable initCause(Throwable cause) {
    if (cause == this)
      throw new IllegalArgumentException();
    if (this.cause != null)
      throw new IllegalStateException();
    this.cause = cause;
    return this;
  }
    
  public void sysWriteStackTrace() {
    sysWriteStackTrace(null, 0);
  }

  public void sysWriteStackTrace(int depth) {
    printStackTrace(PrintContainer.readyPrinter, null, depth);
  }

  public void sysWriteStackTrace(Throwable effect, int depth) {
    printStackTrace(PrintContainer.readyPrinter, effect, depth);
  }

  public void printStackTrace () {
    printStackTrace(0);
  }

  public void printStackTrace (int depth) {
    boolean useSysWrite = VM_Options.stackTraceVMSysWrite;

    if (!VM.fullyBooted)
      useSysWrite = true;

    if (!useSysWrite) {
      if (this instanceof OutOfMemoryError) {
        tallyOutOfMemoryError();
        VM.sysWriteln("Throwable.printStackTrace(): We are trying to dump the stack of an OutOfMemoryError.");
        VM.sysWriteln("Throwable.printStackTrace():  We'll use the VM.sysWriteln() function,");
        VM.sysWriteln("Throwable.printStackTrace():  instead of the System.err stream, to avoid trouble.");
        useSysWrite = true;
      } else if (System.err == null) {
        VM.sysWriteln("Throwable.printStackTrace(): Can't write to the uninitialized System.err stream;");
        VM.sysWriteln("Throwable.printStackTrace():  it's early in booting.  Reverting to VM.sysWriteln().");
        useSysWrite = true;
      }
    }

    if (useSysWrite) {
      // an instance of PrintContainer.WithSysWriteLn
      PrintLN pln = PrintContainer.readyPrinter;
      // We will catch any other exceptions deeper down the call stack.
      printStackTrace(pln, depth);
    } else { // Not using sysWrite
      if (VM.VerifyAssertions) VM._assert(System.err != null);
      // We will catch other exceptions deeper down the call stack.
      printStackTrace(System.err, depth);
    }
  } 
    
  public synchronized void printStackTrace(PrintLN err) {
    printStackTrace(err, null, 0);
  }

  /** How deep can we go? */
  private static final int maxDepth = 7;

  public synchronized void printStackTrace(PrintLN err, int depth) {
    printStackTrace(err, null, depth);
  }

  /** Just wraps around <code>doPrintStackTrace()</code>.  Checks for depth;
   * will give up if we go deeper than maxDepth.
   * @param err the output sink (stream) to write to.
   * @param effect <code>null</code> if this Throwable was not the
   *    <code>cause</code> of another throwable.  Any non-<code>null</code>
   *    value indicates that we have the opportunity (though not the
   *    obligation) to do some elision, just as the Sun HotSpot JVM does. 
   */
  public void printStackTrace(PrintLN err, Throwable effect, int depth) {
    // So, we will not let multiple stack traces get printed at the same
    // time, just in case!
    synchronized (Throwable.class) {
      try {
        if (++depth == maxDepth)
          VM.sysWriteln("We got ", depth, " deep in printing stack traces; trouble.  Aborting.");
        if (depth >= maxDepth)
          VM.sysExit(VM.EXIT_STATUS_TOO_MANY_THROWABLE_ERRORS);
        doPrintStackTrace(err, effect, depth);
        if (VM.VerifyAssertions) VM._assert(depth >= 1);
      } finally {
        --depth;                        // clean up
      }
    }
  }

  /** Do the work of printing the stack trace for this <code>Throwable</code>.
   * Does not do any depth checking.
   * 
   * @param err the output sink (stream) to write to.
   * @param effect <code>null</code> if this Throwable was not the
   *    <code>cause</code> of another throwable.  Any non-<code>null</code>
   *    value indicates that we have the opportunity (though not the
   *    obligation) to do some elision, just as the Sun HotSpot JVM does. 
   * @param depth How deep into trace printing are we?
   *    Includes cascaded exceptions.
   */
  private void doPrintStackTrace(PrintLN err, Throwable effect, int depth) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    int step = 0;
    try {
      /* A routine to avoid OutOfMemoryErrors, which I think we will never see
         anyway.  But let's encapsulate potentially memory-allocating
         operations. */ 
      printlnMyClassAndMessage(err, depth);
      ++step;
      if (stackTrace == null) {
        err.println("{ Throwable.printStackTrace(): No stack trace available to display; sorry! }");
      } else {
        stackTrace.print(err, this, effect, depth);
      }
      ++step;
      if (cause != null) {
        err.print("Caused by: ");
        Throwable subEffect = this;
        cause.doPrintStackTrace(err, subEffect, depth + 1);
      }
      ++step;
    } catch (OutOfMemoryError dummy) {
      tallyOutOfMemoryError();
      VM.sysWriteln("Throwable.printStackTrace(PrintLN) is out of memory");
    } catch (Throwable dummy) {
      tallyWeirdError();
      VM.sysWrite("Throwable.printStackTrace(PrintLN) caught an unexpected Throwable: ");
      dummy.sysWriteln();
      VM.sysWriteln("[ BEGIN (possibly recursive) sysWrite() of stack trace for that unexpected Throwable");
      dummy.doPrintStackTrace(PrintContainer.readyPrinter, effect, depth + 1);
      VM.sysWriteln(" END (possibly recursive) sysWrite() of stack trace for that unexpected Throwable ]");
    }
    if (step < 3) {
      if (err.isSysWrite()) {
        VM.sysWriteln("Throwable.printStackTrace(PrintContainer.WithSysWriteln): Can't proceed any further.");
      } else {
        VM.sysWriteln("Throwable.printStackTrace(PrintContainer): Resorting to sysWrite() methods.");
        this.doPrintStackTrace(PrintContainer.readyPrinter, effect, depth + 1);
      }
    }
  }


  public void printStackTrace(PrintWriter err) {
    int depth = 0;
    printStackTrace(err, depth);
  }

  public void printStackTrace(PrintWriter err, int depth) {
    PrintLN pln = null;
    try {
      pln = PrintContainer.get(err);
    } catch (OutOfMemoryError dummy) {
      tallyOutOfMemoryError();
      VM.sysWriteln("Throwable.printStackTrace(PrintWriter): Out of memory");
    } catch (Throwable dummy) {
      tallyWeirdError();
      VM.sysWriteln("Throwable.printStackTrace(PrintWriter) caught an unexpected Throwable");
    } finally {
      if (pln == null) {
        VM.sysWrite("\twhile allocating a simple 'PrintContainer(PrintWriter)' object to print a stack trace.");
        VM.sysWriteln("\tDegrading to sysWrite.");      
        pln = PrintContainer.readyPrinter;
      }
    }

    // Any errors are caught deeper down the call stack.
    printStackTrace(pln, depth);
  }


  public void printStackTrace(PrintStream err) {
    printStackTrace(err, 0);
  }

  public void printStackTrace(PrintStream err, int depth) {
    PrintLN pln = null;
    try {
      pln = PrintContainer.get(err);
    } catch (OutOfMemoryError dummy) {
      tallyOutOfMemoryError();
      VM.sysWriteln("Throwable.printStackTrace(PrintStream): Out of memory");
      dummy.sysWrite();
    } catch (Throwable dummy) {
      tallyWeirdError();
      VM.sysWriteln("Throwable.printStackTrace(PrintStream): Caught an unexpected Throwable");
      //      dummy.sysWrite(depth + 1);
      pln = PrintContainer.readyPrinter;
    } finally {
      if (pln == null) {
        VM.sysWriteln("\twhile trying to allocate a simple PrintContainer(PrintStream) object to print a stack trace.");
        VM.sysWriteln("\tDegrading to sysWrite()");
        pln = PrintContainer.readyPrinter;
      }
    }
    // Any errors are caught deeper down the call stack.
    printStackTrace(pln, depth);
  }
    
  /** Currently only implemented as a stub */
  public void setStackTrace(StackTraceElement[] stackTrace) {
    stackTrace = null;          // throw out the old Jikes RVM stack trace.
    
    // throw new VM_UnimplementedError(); 
  }

  void printlnMyClassAndMessage(PrintLN out, int depth) {
    out.println(toString());
  }
  
  public void sysWrite() {
    sysWrite(0);
  }

  public void sysWrite(int depth) {
      printlnMyClassAndMessage(PrintContainer.readyPrinter, depth);
  }

  public void sysWriteln() {
    sysWriteln(0);
  }

  public void sysWriteln(int depth) {
    sysWrite(depth);
    VM.sysWriteln();
  }

  public String toString() {
    return getClass().getName() 
      + (getMessage() == null ? "" : (": " + getMessage()) );
  }
}
