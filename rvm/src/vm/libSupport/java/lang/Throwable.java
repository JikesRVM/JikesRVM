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
    if (System.err == null) {
      VM.sysWriteln("Throwable.printStackTrace() can't write to null System.err stream; it's early in booting.  Trying VM.sysWriteln()");
      printStackTrace(new PrintContainer.VMSysWriteln());
      return;
    }
    printStackTrace(System.err);
  }
    
  public synchronized void printStackTrace (PrintLN err) {
    //    err.println("This is a call to printStackTrace()"); // DEBUG
    err.println(this.toString()); 
    stackTrace.print(err, this);
    /* Old call.  This won't elide stack frames as nicely, but otherwise will
       work fine. */
    // stackTrace.print(err);
    if (cause != null) {
      err.print("Caused by: ");
      cause.printStackTrace(err);
    }
  }

  public void printStackTrace(PrintWriter err) {
    printStackTrace(new PrintContainer(err));
  }

  public void printStackTrace(PrintStream err) {
    printStackTrace(new PrintContainer(err));
  }
    
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new VM_UnimplementedError();
  }

  public String toString() {
    String msg = getMessage();
    if (msg == null)
      return getClass().getName();
    else
      return getClass().getName() + ": " + msg;
  }

}
