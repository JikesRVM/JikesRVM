/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.lang.reflect.Method;
import java.lang.Thread;

/**
 * A thread to service JNI AttachCurrentThread request
 * This thread is normally dormant and only scheduled for execution
 * by VM_Thread.threadSwitch() when a value is stored into 
 * VM_Scheduler.attachThreadRequested by an external pthread
 * 
 * @author Ton Ngo 
 * @date October 24 2000
 */
class JNIServiceThread extends VM_Thread   {
  // constant must match #define in libjni.C
  static final int ATTACHREQUEST  = 0;     
  static final int DETACHREQUEST  = 1;
  static final int DESTROYREQUEST = 2;

  static boolean trace = false;

  public JNIServiceThread()  {
    // type cast to get around verifier problem when building the boot image
    VM_Address yy  = VM_Magic.objectAsAddress(this);
    VM_Thread  j   = (VM_Thread)VM_Magic.addressAsObject(yy);
    j.makeDaemon(true);

    //-#if RVM_WITH_OSR
    super.isSystemThread = true;
    //-#endif
  }

  public String toString() // overrides VM_Thread
  {
    return "JNIServiceThread";
  }
  
  public void run() // overrides VM_Thread
  {
    
    /* on start up, move self to the attachThreadQueue and wait there */
    if (trace) 
      VM.sysWrite("JNIServiceThread started, moving to attachThreadQueue ...\n");

    
    VM_Scheduler.attachThreadMutex.lock();
    ((VM_Thread) this).yield(VM_Scheduler.attachThreadQueue, VM_Scheduler.attachThreadMutex);
    
    /* wake up here from a nonzero value in VM_Scheduler.attachThreadRequested,
     * activated from VM_Thread.threadSwitchXS
     */
    for (;;) {

      if (trace) {
	VM.sysWrite("JNIServiceThread:  external Thread request detected ");
	VM.sysWrite(VM_Scheduler.attachThreadRequested);
	VM.sysWrite(" at 0x");
	VM.sysWrite(VM.intAsHexString(VM_Magic.getTocPointer().add(VM_BootRecord.the_boot_record.attachThreadRequestedOffset).toInt()));

	VM_Address args = VM_Scheduler.attachThreadRequested;
	VM.sysWrite("parms = " + 
		    VM.intAsHexString(VM_Magic.getMemoryInt(args)) +
		    ", " + 
		    VM.intAsHexString(VM_Magic.getMemoryInt(args.add(4))) +
		    ", " + 
		    VM.intAsHexString(VM_Magic.getMemoryInt(args.add(8))));
		    
	VM.sysWrite("\n");
      }

      /* verify that we have a good address to a struct requestParameter in C */
      if (VM_Scheduler.attachThreadRequested.isZero()) {
	if (trace)
	  VM.sysWrite("JNIServiceThread: caution, spurious request ignored. \n");
	// go back to dormant mode
	VM_Scheduler.attachThreadMutex.lock();
	((VM_Thread) this).yield(VM_Scheduler.attachThreadQueue, VM_Scheduler.attachThreadMutex);
      } 
	
      int requestType = VM_Magic.getMemoryInt(VM_Scheduler.attachThreadRequested);
      
      switch (requestType) {
      case ATTACHREQUEST:
	if (trace)
	  VM.sysWrite("JNIServiceThread:  attaching Java thread ... ");

        // Create the Java thread to attach to the external pthread
        // because the Java thread being attached needs to be a java.lang.thread
        // VM_JNIStartUp implements Runnable and then a Java thread is
        // created to run its run() method
        VM_JNIStartUp startupThread = new VM_JNIStartUp(VM_Scheduler.attachThreadRequested);
        Thread javaThread = new Thread(startupThread);   // should move this code outside lock

        javaThread.start();                              // to miminize locked time

        // notify the external pthread that a Java thread has started
        VM_Scheduler.attachThreadRequested = VM_Address.zero();   
	break;
            

      case DETACHREQUEST:
      case DESTROYREQUEST:

	if (trace) {
	  if (requestType==DETACHREQUEST)
	    VM.sysWrite("JNIServiceThread:  detach Java thread ... \n");
	  else 
	    VM.sysWrite("JNIServiceThread:  detach main Java thread and destroy VM ... \n");
	}
		
	// Detaching a Java thread that was attached to the external OS thread
	// This thread needs to run the termination code on its own stack instead
	// of the OS thread stack.  
	// It will use the stack originally installed for the Java thread

	// obtain the JNIenv handle that the OS thread uses
	VM_Address externalJNIEnvAddress = VM_Magic.getMemoryAddress(VM_Scheduler.attachThreadRequested.add(4));
	int externalJNIEnv = VM_Magic.getMemoryInt(externalJNIEnvAddress);

	// This is an address into an entry in the static array VM_JNIEnvironment.JNIFunctionPointers
	// Since the array is co-indexed with VM_Scheduler.threads, the offset is used to derive
	// the associated VM_Thread.
	// (compute with VM_Entrypoints.JNIFunctionPointersOffset because JNIFunctionPointers is private)
       	int threadOffset = externalJNIEnv - 
	  VM_Magic.getMemoryInt(VM_Magic.getTocPointer().add(VM_Entrypoints.JNIFunctionPointersField.getOffset()));
	
	if (trace)
	  VM.sysWrite("JNIServiceThread:  externalJNIEnv = " + 
		      VM.intAsHexString(externalJNIEnv) + ", threadOffset = "  + 
		      threadOffset + "\n");
	
	// NOTE:  Conversion from threadOffset to thread index needs to be changed if 
	// the number of words per thread entry change. 
	// For 3GT, there are 2 words per entry, so divide the offset by 8 to get the index
	VM_Thread attachedThread = VM_Scheduler.threads[threadOffset>>3];


	// restore the original Java context and stack to run the termination code
	// (discard the current context since this thread is exiting)
	attachedThread.contextRegisters = attachedThread.getJNIEnv().savedTerminationContext();

	// // First adjust the context of the Java thread to resume execution on this stack
	// // point the FP register into the old stack frame
	// VM_JNIEnvironment env = attachedThread.getJNIEnv();
	// VM_Registers context = attachedThread.contextRegisters;
	// int fp = env.terminationFPOffset + VM_Magic.objectAsAddress(attachedThread.stack);
	// context.gprs[FP] = fp; 
	// 
	// // recompute the next instruction address in the old stack frame in case GC has moved the code
	// int compiledMethodID = VM_Magic.getCompiledMethodID(fp);
	// VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodID);
	// int newip = env.terminationIPOffset + VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	// // TODO
	// VM_Magic.setNextInstructionAddress(fp, newip);

	// now the thread is ready to resume execution on its old stack.
	// move the thread from the native VP to the Java VP to let it execution to termination
	// remove the thread from the native VP
	attachedThread.nativeAffinity.activeThread = null;

	// schedule it to run
	attachedThread.schedule();

	if (trace)
	  VM.sysWrite("JNIServiceThread:  Java thread termination has been initiated. \n");

        // notify the external pthread that detachment has started
	// (also necessary to prevent the JNIServiceThread from being rescheduled again 
	// during threadswitch)
	VM_Scheduler.attachThreadRequested = VM_Address.zero();


	// for destroy, terminate this thread also
	// if (requestType==DESTROYREQUEST)
	//   this.exit();

	if (trace) {
	  if (requestType==DESTROYREQUEST)
	    VM.sysWrite("JNIServiceThread:  finish detaching main Java thread ... \n");
	}


	break;

      default:
	// bad requestType value from C
	VM.sysWrite("JNIServiceThread: bad request value from external pthread.\n");
	VM._assert(VM.NOT_REACHED);
      }



      // The service sequence has been initiated, put the service thread back in dormant
      // mode until the next request
      VM_Scheduler.attachThreadMutex.lock();
      ((VM_Thread) this).yield(VM_Scheduler.attachThreadQueue, VM_Scheduler.attachThreadMutex);

    }

  }


}
