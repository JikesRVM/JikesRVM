/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM;

// import java.io.PrintStream;
// import java.io.PrintWriter;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_Class;
import java.lang.reflect.Method;
import com.ibm.JikesRVM.PrintLN;
// import java.lang.Class;  // Not needed, redundant with language def.


/**
 * A list of compiled method and instructionOffset pairs that describe 
 * the state of the call stack at a particular instant.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Steven Augart
 */
public class VM_StackTrace implements VM_Constants {

  static int verboseTracePeriod = 0;
  static int verboseTraceIndex = 0;

  /** How many frames are "too many" to display fully? Let's say that zero is
      undefined, any negative number means "no limit" and a positive number is
      a defined limit.   This replaces the former use of a constant.in the
      overloaded print() methods below.  Further, it's modifiable, as a user
      preference. 

      Upped the former constant 50 to 100, per a discussion with Perry Cheng.
      --Steven Augart */
  public static int elideAfterThisManyFrames = 100;
  
  /**
   * The compiled methods that comprise the trace
   */
  private final VM_CompiledMethod[] compiledMethods;

  /**
   * The instruction offsets within those methods.
   */
  private final VM_OffsetArray offsets;
  
  /**
   * Create a trace of the current call stack
   */
  public VM_StackTrace(int skip) {
    // (1) Count the number of frames comprising the stack.
    int numFrames = walkFrames(false, skip+1);
    compiledMethods = new VM_CompiledMethod[numFrames];
    offsets = VM_OffsetArray.create(numFrames);
    walkFrames(true, skip+1);
    
    /* I have no idea why this is here. --steve augart */
    if (verboseTracePeriod > 0) {
      if ((verboseTraceIndex++ % verboseTracePeriod) == 0) {
	VM.disableGC();
	VM_Scheduler.dumpStack();
	VM.enableGC();
      }
    }
  }

  private int walkFrames(boolean record, int skip) {
    int stackFrameCount = 0;
    VM.disableGC(); // so fp & ip don't change under our feet
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address ip = VM_Magic.getReturnAddress(fp);
    for (int i=0; i<skip; i++) {
      fp = VM_Magic.getCallerFramePointer(fp);
      ip = VM_Magic.getReturnAddress(fp);
    }
    fp = VM_Magic.getCallerFramePointer(fp);
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
	VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
	if (record) compiledMethods[stackFrameCount] = compiledMethod;
	if (compiledMethod.getCompilerType() != VM_CompiledMethod.TRAP) {
	  if (record) {
	    VM_Address start = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	    offsets.set(stackFrameCount, ip.diff(start));
	  }
	  if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
	    // skip native frames, stopping at last native frame preceeding the
	    // Java To C transition frame
	    fp = VM_Runtime.unwindNativeStackFrame(fp);	 
	  }
	} 
      }
      stackFrameCount++;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    VM.enableGC();
    return stackFrameCount;
  }
  

  /*  The code below is temporarily commented out; Steve Augart's work in
      progress */
//   /** The common prelude used by all programs that JikesRVM starts.  The big
//    * idea here is to elide those last few methods. */
//   private static VM_Method runMethodMarkingPrelude = getRunMethodMarkingPrelude();
  
//   /** Get a marker for point on the stack below which we just Don't Care
//       To Look.  Two approaches we could take:

//       <ol>

//       <li> <p>The method "com.ibm.JikesRVM.MainThread.run()" (with an empty
//       parameter list) launches most of our programs.  That marks the prelude
//       to calling main().  Most users only care about main and beyond.  So we
//       figure out where the prelude is so that we can skip it.

//       <p> So, what to do if we fail to find that method?  It could be legal,
//       since somebody might be experimenting with new JikesRVM features;
//       perhaps using it to launch web browser applets?  (Admittedly this seems
//       unlikely.)  We'll just kindly return null. Better a verbose stack trace
//       than generating an InternalError().  
//       <p>
//       We will assume that no user class calls
//       com.ibm.JikesRVM.MainThread.run().  This is a pretty safe bet. 
//       </li>

//       <li>
//       <p>Just look for the invocation of <tt>main</tt>.  As the Java Language
//       Specification, Second Edition says (Section 12.1):
//       <blockquote>
//         A Java virtual machine starts execution by invoking the method
//       <tt>main</tt> of some specified class, passing it a single argument,
//       which is an array of strings.  In the examples in this specification,
//       the first class is typically called <tt>Test</tt>.  [&hellip;] 
//       <p>
//       The manner in which the initial class is specified to the Java virtual
//       machine is  beyond the scope of this specification [&hellip;]
//       </blockquote>

//       Section 12.1.4 says:

//       <blockquote>
//       The method <tt>main</tt> must be declared <tt>public</tt>,
//       <tt>static</tt>, and <tt>void</tt>.  It must accept a single argument
//       that is an array of strings.
//       </blockquote>

//       We do, though, have to consider the (perhaps unlikely) possibility of a
//       recursive invocation of <tt>main</tt>.
//       </li>
//       </ol>
//   */
//   private static VM_Method getRunMethodMarkingPrelude() {
//     /* We're implementing here the first method discussed above. */
//     System.err.println("Calling getRunMethodMarkingPrelude()"); // DEBUG XXX
//     try {
//       Class c = Class.forName("com.ibm.JikesRVM.MainThread");
//       Method m = c.getDeclaredMethod("run", new Class[0]);
//       return java.lang.reflect.JikesRVMSupport.getMethodOf(m);
//     } catch (ClassNotFoundException cnf) {
//       return null;
//     } catch (NoSuchMethodException nsm) {
//       return null;
//     } catch (SecurityException se) {
//       return null;
//     }
//   }
  

  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the
   * VM_CompiledMethod; this means it will deal with inlining by the opt
   * compiler in a sensible fashion. 
   * 
   * @param out PrintLN to print on.
   * @param trigger The Throwable that caused the stack trace.
   *  Used to elide internal details from the stack trace.
   *  If null, then we print a full stack trace, without eliding the
   *  methods used internally to gather the stack trace.
   */
  public void print(PrintLN out, Throwable trigger) {
    //    out.println("Calling print(out, trigger = " + trigger.toString() + ")"); // DEBUG XXX

    /** Where'd we find the trigger? */
    int foundTriggerAt = -1;	// -1 is a sentinel value; important in code
				// below. 
    int lastFrame = compiledMethods.length - 1;
    // The last two stack frames are always:
    // --> at com.ibm.JikesRVM.MainThread.run (MainThread.java:117)
    // --> at com.ibm.JikesRVM.VM_Thread.startoff (VM_Thread.java:710)
    // so we can skip them, right?  If this was not the right thing to do,
    // please tell me. --Steve Augart
    // True for main thread, but if the program spawns other threads than
    // this isn't the case. We can always cut the VM_Thread.startoff frame
    // every thread (I think), but for threads other than the main thread, 
    // the second frame is actually interesting. --dave
    lastFrame -= 1;
    
    if (trigger != null) {
      Class triggerClass = trigger.getClass();
      /* So, elide up to the triggeringMethod.  If we never find the
	 triggeringMethod, then leave foundTriggerAt set to -1; the printing
	 code will handle that correctly.. */
      for (int i = 0; i <= lastFrame; ++i) {
	VM_CompiledMethod cm = compiledMethods[i];
	if (cm == null)
	  continue;
	VM_Method m = cm.getMethod();
	/* Declaring class of the method whose call is recorded in this stack
	 * frame.  */ 
	VM_Class frameVM_Class = m.getDeclaringClass();
	if (frameVM_Class.getClassForType() == triggerClass) {
	  foundTriggerAt = i;
	  break;
	}
      }
    }
    /* foundTriggerAt should either be between 0 and lastFrame
       or it should be -1. */

    /* Now check to see if the triggering frame is VM_Runtime.deliverHardwareException.
       If it is, then skip two more frames to avoid showing it and the
       <hardware trap> frame that called it */
    VM_CompiledMethod bottom = compiledMethods[foundTriggerAt +1];
    if (bottom.getMethod() == VM_Entrypoints.deliverHardwareExceptionMethod) {
      foundTriggerAt += 2;
    }

    /* Now we can start printing frames. */
    int nPrinted = 0;		// how many frames have we printed?
    for (int i = foundTriggerAt + 1; i <= lastFrame; ++i, ++nPrinted) {
      VM_CompiledMethod cm = compiledMethods[i];
      if (nPrinted == elideAfterThisManyFrames) {
	// large stack - suppress excessive output
	int oldIndex = i;
	int newIndex = lastFrame - 9;
	if (newIndex > oldIndex) {
	  i = newIndex;
	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	}
      }
      if (cm == null) {
	out.println("\tat <invisible method>");
	/* Commented out; Work in Progress: */
//       } else if (cm.getMethod() == runMethodMarkingPrelude) {
// 	/* cm.getMethod() yields a VM_Method. */
// 	/* Notice that if runMethodMarkingPrelude is null, the right thing
// 	   happens here. */
//       	return;			// gone far enough.
      } else {
	cm.printStackTrace(offsets.get(i), out);
      }
    }
  }
}
