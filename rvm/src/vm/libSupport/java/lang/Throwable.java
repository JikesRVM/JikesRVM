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
    fillInStackTrace();         // fillInStackTrace() catches its own errors.
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
  /** just a guess.  Resettable if you really want to. */
  public  int maxWeirdErrors = 4; 
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
    sysWriteStackTrace((Throwable) null);
  }
  public void sysWriteStackTrace(Throwable effect) {
    printStackTrace(PrintContainer.readyPrinter, effect);
  }

  public void printStackTrace () {
    // boolean useSysWrite = false;
    // My intent here in overriding this WAS to avoid stack trace hell, but
    // it only seems to have gotten worse.
    // boolean useSysWrite = true || VM.stackTraceVMSysWrite;
    boolean useSysWrite = VM.stackTraceVMSysWrite;

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
      sysWriteStackTrace();
    } else { // Not using sysWrite
      if (VM.VerifyAssertions) VM._assert(System.err != null);
      // We will catch other exceptions deeper down the call stack.
      printStackTrace(System.err);
    }
  }
    
  /** How deep into trace printing are we? Includes cascaded exceptions; the
    other tests (above) were broken.   Access to this is synchronized around
    the class Throwable. */
  static private int depth = 0;
  /** How deep can we go? */
  final static private int maxDepth = 7;
  
  public static synchronized int getDepth() {
    return depth;
  }
  public static synchronized int getMaxDepth() {
    return maxDepth;
  }

  public void printStackTrace(PrintLN err) {
    printStackTrace(err, (Throwable) null);
  }

  /** Just wraps around <code>doPrintStackTrace()</code>.  Checks for depth;
   * will give up if we go deeper than maxDepth.
   * @param err the output sink (stream) to write to.
   * @param effect <code>null</code> if this Throwable was not the
   *    <code>cause</code> of another throwable.  Any non-<code>null</code>
   *    value indicates that we have the opportunity (though not the
   *    obligation) to do some elision, just as the Sun HotSpot JVM does. 
   */
  public void printStackTrace(PrintLN err, Throwable effect) {
    // So, we will not let multiple stack traces get printed at the same
    // time, just in case!
    synchronized (Throwable.class) {
      try {
        if (++depth == maxDepth)
          VM.sysWriteln("We got ", depth, " deep in printing stack traces; trouble.  Aborting.");
        if (depth >= maxDepth)
          VM.sysExit(VM.exitStatusTooManyThrowableErrors);
        doPrintStackTrace(err, effect);
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
   */
  private void doPrintStackTrace(PrintLN err, Throwable effect) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    int step = 0;
    try {
      /* A routine to avoid OutOfMemoryErrors, which I think we will never see
         anyway.  But let's encapsulate potentially memory-allocating
         operations. */ 
      printlnMyClassAndMessage(err);
      ++step;
      if (stackTrace == null) {
        err.println("{ Throwable.printStackTrace(): No stack trace available to display; sorry! }");
      } else {
        stackTrace.print(err, this, effect);
      }
      ++step;
      if (cause != null) {
        err.print("Caused by: ");
        Throwable subEffect = this;
        cause.doPrintStackTrace(err, subEffect);
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

    // Any errors are caught deeper down the call stack.
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
    // Any errors are caught deeper down the call stack.
    printStackTrace(pln);
  }
    
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new VM_UnimplementedError(); // if we run out of memory, so be it. 
  }

  void printlnMyClassAndMessage(PrintLN out) {
    out.printClassName(classNameAsVM_Atom(this));
    /* Avoid diving into the contents of detailMessage since a subclass MIGHT
     * override getMessage(). */
    String msg = getMessage();
    if (msg != null) {
      out.print(": ");
      out.print(msg);
    }
    out.println();
  }
  
  public void sysWrite() {
    printlnMyClassAndMessage(PrintContainer.readyPrinter);
  }

  public void sysWriteln() {
    sysWrite();
    VM.sysWriteln();
  }

  public static VM_Atom classNameAsVM_Atom(Object o) {
    VM_Type me_type = VM_ObjectModel.getObjectType(o);
    VM_TypeReference me_tRef = me_type.getTypeRef();
    VM_Atom me_name = me_tRef.getName();
    return me_name;
  }

//   public void sysWriteClassName() {
//     VM_Atom me_name = classNameAsVM_Atom(this);
//     me_name.sysWrite();
//   }

  public String toString() {
    return getClass().getName() 
      + (getMessage() == null ? "" : (": " + getMessage()) );
  }

//   /* We could make this more functional in the face of running out of memory,
//    * but probably not worth the hassle. */
//   public String toString() {
//     String msg;
//     final String messageIfOutOfMemory 
//       = "<getMessage() ran out of memory; no text available>";
//     String classname;
//     final String classnameIfOutOfMemory
//       = "<getClass.getName() ran out of memory; no text available>";

//     try {
//       msg = getMessage();
//     } catch (OutOfMemoryError oom) {
//       tallyOutOfMemoryError();
//       /* This will only happen if a subclass overrides getMessage(). */
//       msg = messageIfOutOfMemory;
//     }
//     try {
//       classname = getClass().getName();
//     } catch (OutOfMemoryError oom) {
//       tallyOutOfMemoryError();
//       /* We could certainly do more to recover from this, such as dumping the
//       info via VM.sysWrite() or by getting the class's
//       name via some means that does not involve memory allocation.   But we
//       won't.  */
//       classname = classnameIfOutOfMemory;
//     }
      
//     if (msg == null || msg == messageIfOutOfMemory) {
//       return classname;
//     } else {
//       // msg, at least, must contain useful information.
//       try {
//      return classname + ": " + msg;
//       } catch (OutOfMemoryError oom) {
//      tallyOutOfMemoryError();
//      /* We could be more clever about this recovery, but it seems like too
//       * much hassle for too little gain. */
//      VM.sysWriteln("Throwable.toString(): No memory to concatenate two strings");
//      VM.sysWrite("Throwable.toString(): Will return just the message from this exception \"");
//      VM.sysWrite(msg);
//      VM.sysWriteln("\"");
//      VM.sysWrite("Throwable.toString(): without the associated classname \"");
//      VM.sysWrite(classname);
//      VM.sysWriteln("\".");
//      return msg;
//       }
//     }
//   }
}
