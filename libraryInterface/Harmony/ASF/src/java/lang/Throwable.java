/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackTrace;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.pragma.NoEscapes;

/**
 * This class must be implemented by the VM vendor, or the reference
 * implementation can be used if the documented natives are implemented.
 * 
 * This class is the superclass of all classes which can be thrown by the
 * virtual machine. The two direct subclasses represent recoverable exceptions
 * (Exception) and unrecoverable errors (Error). This class provides common
 * methods for accessing a string message which provides extra information about
 * the circumstances in which the throwable was created, and for filling in a
 * walkback (i.e. a record of the call stack at a particular point in time)
 * which can be printed later.
 * 
 * @see Error
 * @see Exception
 * @see RuntimeException
 */
public class Throwable implements java.io.Serializable {
    private static final long serialVersionUID = -3042686055658047285L;

    /** The stack trace for this throwable */
    private transient StackTrace vmStackTrace;

    /**
     * Zero length array of stack trace elements, returned when handling an OOM or
     * an unexpected error
     */
    private static final StackTraceElement[] zeroLengthStackTrace = new StackTraceElement[0];

    /**
     * The message provided when the exception was created.
     */
    private String detailMessage;

    /**
     * The cause of this Throwable. Null when there is no cause.
     */
    private Throwable cause = this;

    private StackTraceElement[] stackTrace;

    /**
     * Constructs a new instance of this class with its walkback filled in.
     */
    @NoEscapes
    public Throwable() {
        super();
        fillInStackTrace();
    }

    /**
     * Constructs a new instance of this class with its walkback and message
     * filled in.
     * 
     * @param detailMessage String The detail message for the exception.
     */
    public Throwable(String detailMessage) {
        this();
        this.detailMessage = detailMessage;
    }

    /**
     * Constructs a new instance of this class with its walkback, message and
     * cause filled in.
     * 
     * @param detailMessage String The detail message for the exception.
     * @param throwable The cause of this Throwable
     */
    public Throwable(String detailMessage, Throwable throwable) {
        this();
        this.detailMessage = detailMessage;
        cause = throwable;
    }

    /**
     * Constructs a new instance of this class with its walkback and cause
     * filled in.
     * 
     * @param throwable The cause of this Throwable
     */
    public Throwable(Throwable throwable) {
        this();
        this.detailMessage = throwable == null ? null : throwable.toString();
        cause = throwable;
    }

    /**
     * Record in the receiver a walkback from the point where this message was
     * sent. The message is public so that code which catches a throwable and
     * then <em>re-throws</em> it can adjust the walkback to represent the
     * location where the exception was re-thrown.
     * 
     * @return the receiver
     */
    public Throwable fillInStackTrace() {
      if (VM.fullyBooted) {
        if (RVMThread.getCurrentThread().getThreadForStackTrace().isGCThread()) {
          VM.sysWriteln("Exception in GC thread");
          RVMThread.dumpVirtualMachine();
        } else {
          try {
            vmStackTrace = new StackTrace();
          } catch (OutOfMemoryError oome) {
            // ignore
          } catch (Throwable t) {
            VM.sysFail("VMThrowable.fillInStackTrace(): Cannot fill in a stack trace; got a weird Throwable when I tried to");
          }
        }
      } else {
        VM.sysWriteln("Request to fill in stack trace before VM booted");
        RVMThread.dumpStack();
      }
      return this;
    }

    /**
     * Answers the extra information message which was provided when the
     * throwable was created. If no message was provided at creation time, then
     * answer null.
     * 
     * @return String The receiver's message.
     */
    public String getMessage() {
        return detailMessage;
    }

    /**
     * Answers the extra information message which was provided when the
     * throwable was created. If no message was provided at creation time, then
     * answer null. Subclasses may override this method to answer localized text
     * for the message.
     * 
     * @return String The receiver's message.
     */
    public String getLocalizedMessage() {
        return getMessage();
    }

    /**
     * This native must be implemented to use the reference implementation of
     * this class. The result of this native is cloned, and returned from the
     * public API getStackTrace().
     * 
     * Answers an array of StackTraceElement. Each StackTraceElement represents
     * a entry on the stack.
     * 
     * @return an array of StackTraceElement representing the stack
     */
    private StackTraceElement[] getStackTraceImpl() {
	if (vmStackTrace == null) {
	    return zeroLengthStackTrace;
	} else if (RVMThread.getCurrentThread().getThreadForStackTrace().isGCThread()) {
	    VM.sysWriteln("Throwable.getStackTrace called from GC thread: dumping stack using scheduler");
	    RVMThread.dumpStack();
	    return zeroLengthStackTrace;
	}

	StackTrace.Element[] vmElements;
	try {
	    vmElements = vmStackTrace.getStackTrace(this);
	} catch (Throwable t) {
	    VM.sysWriteln("Error calling StackTrace.getStackTrace: dumping stack using scheduler");
	    RVMThread.dumpStack();
	    return zeroLengthStackTrace;
	}
	if (vmElements == null) {
	    VM.sysWriteln("Error calling StackTrace.getStackTrace returned null");
	    RVMThread.dumpStack();
	    return zeroLengthStackTrace;
	}
	if (VM.fullyBooted) {
	    try {
		StackTraceElement[] elements = new StackTraceElement[vmElements.length];
		for (int i=0; i < vmElements.length; i++) {
		    StackTrace.Element vmElement = vmElements[i];
		    String fileName = vmElement.getFileName();
		    int lineNumber = vmElement.getLineNumber();
		    String className = vmElement.getClassName();
		    if (className == null) className = "";
		    String methodName = vmElement.getMethodName();
		    if (methodName == null) methodName = "";
		    boolean isNative = vmElement.isNative();
		    elements[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
		}
		return elements;
	    } catch (Throwable t) {
		VM.sysWriteln("Error constructing StackTraceElements: dumping stack");
	    }
	} else {
	    VM.sysWriteln("Dumping stack using sysWrite in not fullyBooted VM");
	}
	for (StackTrace.Element vmElement : vmElements) {
	    if (vmElement == null) {
		VM.sysWriteln("Error stack trace with null entry");
		RVMThread.dumpStack();
		return zeroLengthStackTrace;
	    }
	    String fileName = vmElement.getFileName();
	    int lineNumber = vmElement.getLineNumber();
	    String className = vmElement.getClassName();
	    String methodName = vmElement.getMethodName();
	    VM.sysWrite("   at ");
	    if (className != "") {
		VM.sysWrite(className);
		VM.sysWrite(".");
	    }
	    VM.sysWrite(methodName);
	    if (fileName != null) {
		VM.sysWrite("(");
		VM.sysWrite(fileName);
		if (lineNumber > 0) {
		    VM.sysWrite(":");
		    VM.sysWrite(vmElement.getLineNumber());
		}
		VM.sysWrite(")");
	    }
	    VM.sysWriteln();
	}
	return zeroLengthStackTrace;
    }

    /**
     * Answers an array of StackTraceElement. Each StackTraceElement represents
     * a entry on the stack.
     * 
     * @return an array of StackTraceElement representing the stack
     */
    public StackTraceElement[] getStackTrace() {
        return getInternalStackTrace().clone();
    }

    /**
     * Sets the array of StackTraceElements. Each StackTraceElement represents a
     * entry on the stack. A copy of this array will be returned by
     * getStackTrace() and printed by printStackTrace().
     * 
     * @param trace The array of StackTraceElement
     */
    public void setStackTrace(StackTraceElement[] trace) {
        StackTraceElement[] newTrace = trace.clone();
        for (java.lang.StackTraceElement element : newTrace) {
            if (element == null) {
                throw new NullPointerException();
            }
        }
        stackTrace = newTrace;
    }

    /**
     * Outputs a printable representation of the receiver's walkback on the
     * System.err stream.
     */
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    /**
     * Count the number of duplicate stack frames, starting from the end of the
     * stack.
     * 
     * @param currentStack a stack to compare
     * @param parentStack a stack to compare
     * 
     * @return the number of duplicate stack frames.
     */
    private static int countDuplicates(StackTraceElement[] currentStack,
            StackTraceElement[] parentStack) {
        int duplicates = 0;
        int parentIndex = parentStack.length;
        for (int i = currentStack.length; --i >= 0 && --parentIndex >= 0;) {
            StackTraceElement parentFrame = parentStack[parentIndex];
            if (parentFrame.equals(currentStack[i])) {
                duplicates++;
            } else {
                break;
            }
        }
        return duplicates;
    }

    /**
     * Answers an array of StackTraceElement. Each StackTraceElement represents
     * a entry on the stack. Cache the stack trace in the stackTrace field,
     * returning the cached field when it has already been initialized.
     * 
     * @return an array of StackTraceElement representing the stack
     */
    private StackTraceElement[] getInternalStackTrace() {
        if (stackTrace == null) {
            stackTrace = getStackTraceImpl();
        }
        return stackTrace;
    }

    /**
     * Outputs a printable representation of the receiver's walkback on the
     * stream specified by the argument.
     * 
     * @param err PrintStream The stream to write the walkback on.
     */
    public void printStackTrace(PrintStream err) {
        err.println(toString());
        // Don't use getStackTrace() as it calls clone()
        // Get stackTrace, in case stackTrace is reassigned
        StackTraceElement[] stack = getInternalStackTrace();
        for (java.lang.StackTraceElement element : stack) {
            err.println("\tat " + element);
        }

        StackTraceElement[] parentStack = stack;
        Throwable throwable = getCause();
        while (throwable != null) {
            err.print("Caused by: ");
            err.println(throwable);
            StackTraceElement[] currentStack = throwable.getInternalStackTrace();
            int duplicates = countDuplicates(currentStack, parentStack);
            for (int i = 0; i < currentStack.length - duplicates; i++) {
                err.println("\tat " + currentStack[i]);
            }
            if (duplicates > 0) {
                err.println("\t... " + duplicates + " more");
            }
            parentStack = currentStack;
            throwable = throwable.getCause();
        }
    }

    /**
     * Outputs a printable representation of the receiver's walkback on the
     * writer specified by the argument.
     * 
     * @param err PrintWriter The writer to write the walkback on.
     */
    public void printStackTrace(PrintWriter err) {
        err.println(toString());
        // Don't use getStackTrace() as it calls clone()
        // Get stackTrace, in case stackTrace is reassigned
        StackTraceElement[] stack = getInternalStackTrace();
        for (java.lang.StackTraceElement element : stack) {
            err.println("\tat " + element);
        }

        StackTraceElement[] parentStack = stack;
        Throwable throwable = getCause();
        while (throwable != null) {
            err.print("Caused by: ");
            err.println(throwable);
            StackTraceElement[] currentStack = throwable.getInternalStackTrace();
            int duplicates = countDuplicates(currentStack, parentStack);
            for (int i = 0; i < currentStack.length - duplicates; i++) {
                err.println("\tat " + currentStack[i]);
            }
            if (duplicates > 0) {
                err.println("\t... " + duplicates + " more");
            }
            parentStack = currentStack;
            throwable = throwable.getCause();
        }
    }

    /**
     * Answers a string containing a concise, human-readable description of the
     * receiver.
     * 
     * @return String a printable representation for the receiver.
     */
    @Override
    public String toString() {
        String msg = getLocalizedMessage();
        String name = getClass().getName();
        if (msg == null) {
            return name;
        }
        return new StringBuffer(name.length() + 2 + msg.length()).append(name).append(": ")
                .append(msg).toString();
    }

    /**
     * Initialize the cause of the receiver. The cause cannot be reassigned.
     * 
     * @param throwable The cause of this Throwable
     * 
     * @exception IllegalArgumentException when the cause is the receiver
     * @exception IllegalStateException when the cause has already been
     *            initialized
     * 
     * @return the receiver.
     */
    public synchronized Throwable initCause(Throwable throwable) {
        if (cause == this) {
            if (throwable != this) {
                cause = throwable;
                return this;
            }
            throw new IllegalArgumentException("Cause cannot be the receiver");
        }
        throw new IllegalStateException("Cause already initialized");
    }

    /**
     * Answers the cause of this Throwable, or null if there is no cause.
     * 
     * @return Throwable The receiver's cause.
     */
    public Throwable getCause() {
        if (cause == this) {
            return null;
        }
        return cause;
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        // ensure the stackTrace field is initialized
        getInternalStackTrace();
        s.defaultWriteObject();
    }
}
