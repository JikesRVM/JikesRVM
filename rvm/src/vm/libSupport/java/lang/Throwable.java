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
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_TypeReference;
import com.ibm.JikesRVM.VM_ObjectModel;



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
    fillInStackTrace();		// fillInStackTrace() catches its own errors.
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
      VM.sysExit(VM.exitStatusTooManyOutOfMemoryErrors);
    }
    if (numOutOfMemoryErrors > 1 ) {
      VM.sysWriteln("GC Warning: I'm now ", numOutOfMemoryErrors, " levels deep in OutOfMemoryErrors while handling this Throwable");
    }
  }
  

  public Throwable fillInStackTrace() {
    /* We collect the whole stack trace, and we strip out the cause of the
       exception later on at printing time, in printStackTrace(). */
    try {
      tallyOutOfMemoryError();
      stackTrace = new VM_StackTrace(0);
    } catch (OutOfMemoryError t) {
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
    
  public void sysWriteStackTrace() {
    printStackTrace(PrintContainer.readyPrinter);
  }

  public void printStackTrace () {
    // boolean useSysWrite = false;
    boolean useSysWrite = true || VM.stackTraceVMSysWrite;

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
      printStackTrace(pln);
    } else { // Not using sysWrite
      // We will catch other exceptions deeper down the call stack.
      printStackTrace(System.err);
    }
  }
    
  public synchronized void printStackTrace(PrintLN err) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    int step = 0;
    try {
      /* A routine to avoid OutOfMemoryErrors, which I think we will never see
	 anyway.  But let's encapsulate potentially memory-allocating
	 operations. */ 
      printlnJustThisThrowableNoStackTrace(err);
      ++step;
      if (stackTrace == null) {
	err.println("{ Throwable.printStackTrace(): No stack trace available to display; sorry! }");
      } else {
	stackTrace.print(err, this);
      }
      ++step;
      if (cause != null) {
	err.print("Caused by: ");
	cause.printStackTrace(err);
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
      dummy.sysWriteStackTrace();
      VM.sysWriteln(" END (possibly recursive) sysWrite() of stack trace for that unexpected Throwable ]");
    }
    if (step < 3) {
      if (err.isSysWrite()) {
	VM.sysWriteln("Throwable.printStackTrace(PrintContainer.WithSysWriteln): Can't proceed any further.");
      } else {
	VM.sysWriteln("Throwable.printStackTrace(PrintContainer): Resorting to sysWrite() methods.");
	this.sysWriteStackTrace();
      }
    }
  }


  public void printStackTrace(PrintWriter err) {
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

    // Any out-of-memory is caught deeper down the call stack.
    printStackTrace(pln);
  }


  public void printStackTrace(PrintStream err) {
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
      dummy.sysWrite();
      pln = PrintContainer.readyPrinter;
    } finally {
      if (pln == null) {
	VM.sysWriteln("\twhile trying to allocate a simple PrintContainer(PrintStream) object to print a stack trace.");
	VM.sysWriteln("\tDegrading to sysWrite()");
	pln = PrintContainer.readyPrinter;
      }
    }
    // Any out-of-memory is caught deeper down the call stack.
    printStackTrace(pln);
  }
    
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new VM_UnimplementedError(); // if we run out of memory, so be it. 
  }

  void printlnJustThisThrowableNoStackTrace(PrintLN err) {
      /** We have carefully crafted toString, at least for this exception, to
       * dump the errors properly.  But someone below us could override it.
       * If so, we'll throw a recursive OutOfMemoryException. */ 
      err.println(this.toString());
  }
  
  public void sysWrite() {
    if (false) {
      VM.sysWrite(this.toString()); // avoid toString(); no concat or funny
				    // stuff this way!
    } else {
      sysWriteClassName();
      VM.sysWrite(": ");
      VM.sysWriteln(detailMessage);
    }
  }

  public void sysWriteln() {
    sysWrite();
    VM.sysWriteln();
  }

  public void sysWriteClassName() {
    VM_Type me_type = VM_ObjectModel.getObjectType(this);
    VM_TypeReference me_tRef = me_type.getTypeRef();
    VM_Atom me_name = me_tRef.getName();
    me_name.sysWrite();
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
