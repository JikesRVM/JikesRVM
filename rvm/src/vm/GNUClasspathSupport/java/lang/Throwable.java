/*
 * Copyright IBM Corp 2002
 */
package java.lang;

import java.io.PrintStream;
import java.io.PrintWriter;
import com.ibm.JikesRVM.librarySupport.StackTrace;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
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
    private transient StackTrace stackTrace;
    
    /**
     * The cause of the throwable
     */
    private Throwable cause = this;
    
    public Throwable () {
	super ();
	fillInStackTrace();
    }
    
    public Throwable (String detailMessage) {
	this ();
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
	stackTrace = StackTrace.create();
	return this;
    }

    public Throwable getCause() {
	return cause == this ? null : cause;
    }

    public String getMessage() {
	return detailMessage;
    }
    
    public String getLocalizedMessage() {
	return getMessage();
    }
    
    public Throwable initCause(Throwable cause) {
	if (cause == this)
	    throw new IllegalArgumentException();
	if (this.cause != this)
	    throw new IllegalStateException();
	this.cause = cause;
	return this;
    }
    
    public void printStackTrace () {
	printStackTrace(System.err);
    }
    
    public synchronized void printStackTrace (PrintStream err) {
	err.println(this);
	StackTrace.print(stackTrace, err);
    }

    public void printStackTrace(PrintWriter err) {
	err.println(this);
	StackTrace.print(stackTrace, err);
    }
    
    public String toString () {
	String msg = getMessage();
	if (msg == null)
	    return this.getClass().getName();
	else
	    return this.getClass().getName() + ": " + msg;
	
    }
    
}
