/*
 * (C) Copyright IBM Corp. 2001
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
 * @modified Steven Augart
 */
public class VM_StackTrace implements VM_Constants {

  static int verboseTracePeriod = 0;
  static int verboseTraceIndex = 0;

  /** How many frames are "too many" to display fully? Let's say that zero is
      undefined, any negative number means "no limit" and a positive number is
      a defined limit.   This replaces the former use of a constant.in the
      overloaded print() methods below.

      Upped the former constant 50 to 100, per a discussion with Perry Cheng.
      --Steven Augart */
  public int elideAfterThisManyFrames = 100;
  
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
  

//   /**
//    * Print stack trace.
//    * This is the "classic" routine that I'm attempting to replace --augart
//    * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
//    * deal with inlining by the opt compiler in a sensible fashion.
//    * This is identical to the method print(PrintWriter out), except for the
//    * type of the argument.@param out
//    * 
//    * @param out        stream to print on
//    */
//   public void print(PrintStream out) {
//     for (int i = 0; i<compiledMethods.length; i++) {
//       if (i == elideAfterThisManyFrames) { 
// 	// large stack - suppress excessive output
// 	int oldIndex = i;
// 	int newIndex = compiledMethods.length - 10;
// 	if (newIndex > oldIndex) {
// 	  i = newIndex;
// 	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
// 	}
//       }
//       VM_CompiledMethod cm = compiledMethods[i];
//       if (cm == null) {
// 	out.println("\tat <invisible method>");
//       } else {
// 	cm.printStackTrace(offsets.get(i), out);
//       }
//     }
//   }
   
  /** The common prelude used by all. */
  private static Method preludeMarker = getPreludeMarker();
  
  /** The method "com.ibm.JikesRVM.MainThread.run()" (with an empty parameter
     list) launches most of our programs.  That marks the prelude to calling
     main().  Most users only care about main and beyond.  So we figure out
     where the prelude is so that we can skip it.

     So, what to do if we fail to find that method?  It could be legal, since
     somebody might be experimenting with new JikesRVM features; perhaps using
     it to launch web browser applets?  (Admittedly this seems unlikely.)
     We'll just kindly return null. Better a verbose stack trace than
     generating an InternalError(). */
  private static Method getPreludeMarker() {
    try {
      Class c = Class.forName("com.ibm.JikesRVM.MainThread");
      Method m = c.getDeclaredMethod("run", new Class[0]);
      return m;
    } catch (ClassNotFoundException cnf) {
      return null;
    } catch (NoSuchMethodException nsm) {
      return null;
    } catch (SecurityException se) {
      return null;
    }
  }
  

  public void print(PrintLN out, Throwable trigger) {
    /** Where'd we find the trigger? */
    int foundTriggerAt = -1;	// -1 is a sentinel value; important in code
				// below. 
    Class triggerClass = trigger.getClass();
    // We need to get the VM_Class for trigger, not the Class.
    // arggh, triggerClass.type is only accessible from vmclass.
    // XXX Broken
    
    /* So, elide up to the triggeringMethod.  If we never find the
     * triggeringMethod, then note an error and revert to the old way of doing
     * things, by printing from the top of the stack down. */
    for (int i = 0; i < compiledMethods.length; ++i) {
      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null)
	continue;
      VM_Method m = cm.getMethod();
      /** Declaring class of the method whose call is recorded in this stack
       * frame.  */
      VM_Class frameVM_Class = m.getDeclaringClass();
      if (frameVM_Class.getClassForType() == triggerClass) {
	foundTriggerAt = i;
	break;
      }
    }
    /* foundTriggerAt should either be between 1 and compiledMethods.length
       or it should be -1. */
    /** Now we can start printing frames. */
    for (int i = foundTriggerAt + 1; i < compiledMethods.length; ++i) {
      VM_CompiledMethod cm = compiledMethods[i];

      if (cm == null) {
	out.println("\tat <invisible method>");
      // Here, I just want to get the real Method for a VM_CompiledMethod CM
      //} else if (cm.get-method-id() == preludeMarker) {
	// Notice that if preludeMarker is null, the right thing happens here
      //	return;			// gone far enough.
      } else {
	cm.printStackTrace(offsets.get(i), out);
      }
    }
  }
  

  /**
   * Print stack trace.
   * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
   * deal with inlining by the opt compiler in a sensible fashion.
   * 
   * @param out        PrintLN (always a PrintContainer for now) to print on
   */
  public void print(com.ibm.JikesRVM.PrintLN out) {
    for (int i = 0; i < compiledMethods.length; ++i) {
      if (i == elideAfterThisManyFrames) { 
	// large stack - suppress excessive output
	int oldIndex = i;
	int newIndex = compiledMethods.length - 10;
	if (newIndex > oldIndex) {
	  i = newIndex;
	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
	}
      }

      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null) {
	out.println("\tat <invisible method>");
      } else {
	cm.printStackTrace(offsets.get(i), out);
      }
    }
  }
//   /**
//    * Print stack trace.
//    * Delegate the actual printing of the stack trace to the VM_CompiledMethod's to
//    * deal with inlining by the opt compiler in a sensible fashion.
//    * This is identical to the method print(PrintStream out), except for the
//    * type of.@param out
//    * 
//    * @param out        printwriter to print on
//    */
//   public void print(PrintWriter out) {
//     for (int i = 0; i < compiledMethods.length; ++i) {
//       if (i == elideAfterThisManyFrames) { 
// 	// large stack - suppress excessive output
// 	int oldIndex = i;
// 	int newIndex = compiledMethods.length - 10;
// 	if (newIndex > oldIndex) {
// 	  i = newIndex;
// 	  out.println("\t..." + (newIndex - oldIndex) + " stackframes omitted...");
// 	}
//       }

//       VM_CompiledMethod cm = compiledMethods[i];
//       if (cm == null) {
// 	out.println("\tat <invisible method>");
//       } else {
// 	cm.printStackTrace(offsets.get(i), out);
//       }
//     }
//   }
}
