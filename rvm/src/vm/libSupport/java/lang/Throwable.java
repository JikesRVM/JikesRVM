 /*
 * (C) Copyright IBM Corp 2002, 2003
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
    
  public Throwable () {
    super();
    try {
      fillInStackTrace();
    } catch (OutOfMemoryError e) {
      tallyOutOfMemoryError();
      VM.sysWriteln("Cannot fill in a stack trace; out of memory!\n");
	
    } catch (Throwable t) {
      tallyWeirdError();
      VM.sysWriteln("Cannot fill in a stack trace; got a weird Throwable\n");
    }
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
    
  private int numWeirdErrors = 0;
  public int maxWeirdErrors = 4;	/* just a guess.  Resettable if you
					   really want to. */
  public void tallyWeirdError() {
    if (++numWeirdErrors >= maxWeirdErrors) {
      /* We exit before printing, in case we're in some weird hell where
	 everything is broken, even VM.sysWriteln().. */
      VM.sysExit(VM.exitStatusTooManyThrowableErrors);
    }
  }
  
  private int numOutOfMemoryErrors = 0;
  public int maxOutOfMemoryErrors = 5; /* again, a guess at a good value.
					  Resettable if the user wants to. */
  public void tallyOutOfMemoryError() {
    if (++numOutOfMemoryErrors >= maxOutOfMemoryErrors) {
      /* We exit before printing, in case we're in some weird hell where
	 everything is broken, even VM.sysWriteln().. */
      VM.sysExit(VM.exitStatusTooManyOutOfMemoryErrors);
    }
  }
  

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

    if (VM.stackTraceVMSysWrite) {
      useSysWrite = true;
    } else if (this instanceof OutOfMemoryError) {
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

    if (useSysWrite) {
      PrintLN pln;
      try {
	pln = new PrintContainer.VMSysWriteln();
      } catch (OutOfMemoryError oom) {
	tallyOutOfMemoryError();
	VM.sysWriteln("Throwable.printStackTrace(): Out of memory while trying to allocate a simple 'PrintContainer.VMSysWriteln' object to print the trace.  Giving up.");
	return;
      } catch (Throwable dummy) {
	this.tallyWeirdError();
	VM.sysWriteln("Throwable.printStackTrace(PrintLN) caught an unexpected exception while allocating a simple 'PrintContainer.VMSysWriteln' object to print a stack trace.  Giving up.");
	return;
      }
      // We will catch any other exceptions deeper down the call stack.
      printStackTrace(pln);
    } else { // Not using sysWrite
      // We will catch other exceptions deeper down the call stack.
      printStackTrace(System.err);
    }
  }
    
  public synchronized void printStackTrace (PrintLN err) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    try {
      /* A routine to avoid OutOfMemoryErrors, which I think we will never see
	 anyway.  But let's encapsulate potentially memory-gathering operations. */
      printJustThisThrowableNoStackTrace(err);
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
      tallyOutOfMemoryError();
      VM.sysWriteln("Throwable.printStackTrace(PrintLN) is out of memory, in an unexpected way.  Giving up on the stack trace printing.");
    } catch (Throwable dummy) {
      tallyWeirdError();
      VM.sysWriteln("Throwable.printStackTrace(PrintLN) caught an unexpected exception while printing a stack trace.  It won't print any more of the stack trace.");
    }
  }


  public void printStackTrace(PrintWriter err) {
    PrintLN pln;
    try {
      pln = new PrintContainer(err);
    } catch (OutOfMemoryError dummy) {
      tallyOutOfMemoryError();
      VM.sysWriteln("printStackTrace: Out of memory while trying to allocate a simple stupid PrintContainer(PrintWriter) to print the trace.  I give up.");
      return;
    } catch (Throwable dummy) {
      tallyWeirdError();
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
      tallyOutOfMemoryError();
      VM.sysWriteln("printStackTrace: Out of memory while trying to allocate a simple stupid PrintContainer(PrintStream) to print the trace.  I give up.");
      return;
    } catch (Throwable dummy) {
      tallyWeirdError();
      VM.sysWriteln("Throwable.printStackTrace(PrintWriter) caught an unexpected Throwable while allocating a simple 'PrintContainer(PrintStream)' object to print a stack trace.  Giving up.");
      return;
    }
    // Any out-of-memory is caught deeper down the call stack.
    printStackTrace(pln);
  }
    
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new VM_UnimplementedError(); // if we run out of memory, so be it. 
  }

  void printJustThisThrowableNoStackTrace(PrintLN err) {
      /** We have carefully crafted toString, at least for this exception, to
       * dump the errors properly.  But someone below us could override it.
       * If so, we'll throw a recursive OutOfMemoryException. */ 
      err.println(this.toString());
  }
  
  /* We could make this more functional in the face of running out of memory,
   * but probably not worth the hassle. */
  public String toString() {

    String msg;
    final String messageIfOutOfMemory 
      = "<getMessage() ran out of memory; no text available>";
    String classname;
    final String classnameIfOutOfMemory
      = "<getClass.getName() ran out of memory; no text available>";

    try {
      msg = getMessage();
    } catch (OutOfMemoryError oom) {
      tallyOutOfMemoryError();
      /* This will only happen if a subclass overrides getMessage(). */
      msg = messageIfOutOfMemory;
    }
    try {
      classname = getClass().getName();
    } catch (OutOfMemoryError oom) {
      tallyOutOfMemoryError();
      /* We could certainly do more to recover from this, such as dumping the
	 info via VM.sysWrite() or by getting the class's
	 name via some means that does not involve memory allocation.   But we
	 won't.  */
      classname = classnameIfOutOfMemory;
    }
      
    if (msg == null || msg == messageIfOutOfMemory) {
      return classname;
    } else {
      // msg, at least, must contain useful information.
      try {
	return classname + ": " + msg;
      } catch (OutOfMemoryError oom) {
	tallyOutOfMemoryError();
	/* We could be more clever about this recovery, but it seems like too
	 * much hassle for too little gain. */
	VM.sysWriteln("Throwable.toString(): No memory to concatenate two strings");
	VM.sysWrite("Throwable.toString(): Will return just the message from this exception \"");
	VM.sysWrite(msg);
	VM.sysWriteln("\"");
	VM.sysWrite("Throwable.toString(): without the associated classname \"");
	VM.sysWrite(classname);
	VM.sysWriteln("\".");
	return msg;
      }
    }
  }
}
