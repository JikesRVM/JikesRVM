/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.io.PrintStream;
import java.io.PrintWriter;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_StackTrace;
import com.ibm.JikesRVM.VM_UnimplementedError;
import com.ibm.JikesRVM.PrintLN;
import com.ibm.JikesRVM.PrintContainer;


/**
 * Jikes RVM implementation of java.lang.Throwable.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 * 
 * @author Julian Dolby
 * @author Dave Grove
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
    
  public Throwable () {
    super();
    fillInStackTrace();
  }
    
  public Throwable (String detailMessage) {
    this();
    this.detailMessage = detailMessage;
  }
    
  public Throwable(String message, Throwable cause) {
    this(message);
    initCause(cause);
  }
    
  public Throwable(Throwable cause) {
    this(cause == null ? null : cause.toString(), cause);
  }
    
  int numWeirdErrors = 0;
  final int maxWeirdErrors = 4;	// just a guess.
  
  public Throwable fillInStackTrace() {
    /* We collect the whole stack trace, and we strip out the cause of the
       exception later on at printing time, in printStackTrace(). */
    stackTrace = new VM_StackTrace(0);
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
    
  public StackTraceElement[] getStackTrace() {
    throw new VM_UnimplementedError();
  }

  public Throwable initCause(Throwable cause) {
    if (cause == this)
      throw new IllegalArgumentException();
    if (this.cause != null)
      throw new IllegalStateException();
    this.cause = cause;
    return this;
  }
    
  public void printStackTrace () {
    //    static boolean ranOut = false;
    boolean useSysWrite = false;
    if (this instanceof OutOfMemoryError) {
      VM.sysWriteln("Throwable.printStackTrace(): We are trying to dump the stack of an OutOfMemoryError in the default fashion.");
      VM.sysWriteln("Throwable.printStackTrace(): We'll use the VM.sysWriteln() function instead of the System.err stream, to avoid trouble");
      useSysWrite = true;
    } else if (System.err == null) {
      VM.sysWriteln("Throwable.printStackTrace() can't write to the uninitialized System.err stream; it's early in booting.  Trying VM.sysWriteln()");
      useSysWrite = true;
    }

    if (useSysWrite) {
      PrintLN pln;
      try {
	pln = new PrintContainer.VMSysWriteln();
      } catch (OutOfMemoryError oom) {
	VM.sysWriteln("Throwable.printStackTrace(): Out of memory while trying to allocate a simple 'PrintContainer.VMSysWriteln' object to print the trace.  Giving up.");
	return;
      } catch (Throwable dummy) {
	if (++numWeirdErrors >= maxWeirdErrors) {
	  VM.sysExit(99);
	}
	VM.sysWriteln("Throwable.printStackTrace(PrintLN) caught an unexpected exception while allocating a simple 'PrintContainer.VMSysWriteln' object to print a stack trace.  Giving up.");
	return;
      }
      // We catch exceptions deeper down the call stack.
      printStackTrace(pln);
    } else {
      // We catch exceptions deeper down the call stack.
      printStackTrace(System.err);
    }
  }
    
  public synchronized void printStackTrace (PrintLN err) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    try {
      /** We have carefully crafted toString to dump the errors properly. */
      err.println(this.toString());
      /* Old call.  This won't elide stack frames as nicely, but otherwise will
	 work fine. */
      // stackTrace.print(err);
      /* new call: */
      stackTrace.print(err, this);
      if (cause != null) {
	err.print("Caused by: ");
	cause.printStackTrace(err);
      }
    } catch (OutOfMemoryError dummy) {
      VM.sysWriteln("Throwable.printStackTrace(PrintLN) is out of memory, in an unexpected way.  Giving up on the stack trace printing.");
    } catch (Throwable dummy) {
      if (++numWeirdErrors >= maxWeirdErrors) {
	VM.sysExit(99);
      }
      VM.sysWriteln("Throwable.printStackTrace(PrintLN) caught an unexpected exception while printing a stack trace.  It won't print any more of the stack trace.");
    }
  }


  public void printStackTrace(PrintWriter err) {
    PrintLN pln;
    try {
      pln = new PrintContainer(err);
    } catch (OutOfMemoryError dummy) {
      VM.sysWriteln("printStackTrace: Out of memory while trying to allocate a simple stupid PrintContainer(PrintWriter) to print the trace.  I give up.");
      return;
    } catch (Throwable dummy) {
      if (++numWeirdErrors >= maxWeirdErrors) {
	VM.sysExit(99);
      }
      VM.sysWriteln("Throwable.printStackTrace(PrintWriter) caught an unexpected Throwable while allocating a simple 'PrintContainer(PrintWriter)' object to print a stack trace.  Giving up.");
      return;
    }

    // Any out-of-memory is caught deeper down the call stack.
    printStackTrace(pln);
  }


  public void printStackTrace(PrintStream err) {
    PrintLN pln;
    try {
      pln = new PrintContainer(err);
    } catch (OutOfMemoryError dummy) {
      VM.sysWriteln("printStackTrace: Out of memory while trying to allocate a simple stupid PrintContainer(PrintStream) to print the trace.  I give up.");
      return;
    } catch (Throwable dummy) {
      if (++numWeirdErrors >= maxWeirdErrors) {
	VM.sysExit(99);
      }
      VM.sysWriteln("Throwable.printStackTrace(PrintWriter) caught an unexpected Throwable while allocating a simple 'PrintContainer(PrintStream)' object to print a stack trace.  Giving up.");
      return;
    }
    // Any out-of-memory is caught deeper down the call stack.
    printStackTrace(pln);
  }
    
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new VM_UnimplementedError();
  }

  public String toString() {
    String msg;
    String classname;
    try {
      msg = getMessage();
    } catch (OutOfMemoryError oom) {
      msg = "<getMessage() ran out of memory; no text available>";
    }
    try {
      classname = getClass().getName();
    } catch (OutOfMemoryError oom) {
      classname = "<getClass.getName() ran out of memory; no text available>";
    }
      
    if (msg == null) {
      return classname;
    } else {
      try {
	return classname + ": " + msg;
      } catch (OutOfMemoryError oom) {
	VM.sysWriteln("Throwable.toString(): No memory to concatenate two strings");
	VM.sysWrite("Throwable.toString(): Will return just the class name \"");
	VM.sysWrite(classname);
	VM.sysWrite("\"\nThrowable.toString(): without the associated message \"");
	VM.sysWrite(msg);
	VM.sysWriteln("\".");
	return classname;
      }
    }
  }
}
