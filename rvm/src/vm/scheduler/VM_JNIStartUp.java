/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class supports the JNI functions that originate from C programs 
 * without an existing JNI environment:
 *     JNI_CreateJavaVM
 *     JNI_DestroyJavaVM
 *     AttachCurrentThread
 *     DetachCurrentThread
 * In this mode, a C program creates the VM, then it issues JNI calls to
 * request the VM to execute Java programs, and finally it terminates
 * the VM.
 * When the VM is created, it will execute the main() method of this
 * class, which initializes the JNI support and then becomes the Java Thread
 * associated with the pthread that call JNI_CreateJavaVM
 * When 
 * 
 * Ton Ngo, Steve Smith 10/2/00
 */


// public class VM_JNIStartUp extends VM_Thread {
public class VM_JNIStartUp implements Runnable {

  static boolean trace = false;

  int argAddress; 
  int externalJNIEnvAddress;
  int pthreadID;

  /**
   * Used for AttachCurrentThread to create a java.lang.Thread and associate 
   * it with an external OS thread
   * @param argAddress  address pointing to a struct parms in C defined in libjni.c
   *                    (requestType, JNIEnv ** and pthreadID)
   * @return
   */
  public VM_JNIStartUp (int argAddress1) {
    argAddress = argAddress1;

    // obtain the JNIEnv pointer and the pthread ID
    // based on the struct parms defined in libjni.C
    externalJNIEnvAddress = VM_Magic.getMemoryWord(argAddress+4);
    pthreadID             = VM_Magic.getMemoryWord(argAddress+8);

    if (trace) 
      System.out.println("VM_JNIStartUp: " + VM.intAsHexString(argAddress) +
			 ", JNIEnvAddr=" + VM.intAsHexString(externalJNIEnvAddress) +
			 ", pid=" + pthreadID);
    
  }

  public String toString() // overrides VM_Thread
  {
    return "VM_JNIStartUp";
  }

  /******************************************************************
   *   The run method is executed as the start up Java thread by
   * AttachCurrentThread.  This sets up the necessary data structures
   * to represent the external pthread by a new native VM_Processor.
   * The sequence is similar to the one in the main method, but with
   * some differences.
   * 
   *
   */
  public void run() //- overrides VM_Thread
  {
    if (VM.TraceThreads) VM_Scheduler.trace("VM_JNIStartUp", "run");

    if (trace) {
      System.out.println("VM_JNIStartUp: Java thread " + 
			 VM.intAsHexString(VM_Magic.objectAsAddress(VM_Thread.getCurrentThread())) +
			 " attaching external pthread " + pthreadID + "\n");
      System.out.println("JNIEnv to be placed at " + VM.intAsHexString(externalJNIEnvAddress));
      
    }

    // before executing the following code, bind ourselves to the current processor
    VM_Processor currentVP = VM_Processor.getCurrentProcessor();
    VM_Thread.getCurrentThread().processorAffinity = currentVP;

    // Set up the JNICleanUpThread that will signal the external pthread
    // it will also be bound to the same processor
    VM_JNICreateVMFinishThread cleanupThread = 
      new VM_JNICreateVMFinishThread(VM_Thread.getCurrentThread(),
				      externalJNIEnvAddress,
				      currentVP);
    // get it going
    currentVP.readyQueue.enqueue(cleanupThread);

    // wait for it to come up
    if (trace) {      
      System.out.println("VM_JNIStartUp: AttachCurrentThread waiting for clean up thread"); 
    }
    while (!cleanupThread.ready) {
      VM_Thread.getCurrentThread().yield();
    }


    /***************************************************************************
     * Now we prepare the data structure to create the appearance that the Java main
     * thread has made a native method call and is executing in the native pthread
     */

    // Set up the native VM_Processor to associate with the external pthread
    VM_Processor nativeVP = VM_Processor.createNativeProcessorForExistingOSThread(VM_Thread.getCurrentThread()); 
    VM_Thread.getCurrentThread().nativeAffinity = nativeVP;

    // register the vpStatusAddress for this thread in the VM_JNIEnvironment.JNIFunctionPointers
    // this is normally done in the prolog of the native method on the Java to C transition
    VM_JNIEnvironment.JNIFunctionPointers[(VM_Thread.getCurrentThread().getIndex() * 2) + 1] = 
      nativeVP.vpStatusAddress;

    // normally done by StartupThread
    nativeVP.pthread_id = pthreadID;
    ++nativeVP.threadSwitchingEnabledCount;


    // Set up the JNI environment for this thread for the external pthread
    // to make JNI calls
    VM_JNIEnvironment myEnv = VM_Thread.getCurrentThread().getJNIEnv();
    myEnv.alwaysHasNativeFrame = true;     // indicate that no stack resize should occur
    myEnv.JNITopJavaFP = VM_Constants.STACKFRAME_SENTINAL_FP;      // indicate to GC that no Java stack frame below this point
    myEnv.savedPRreg = nativeVP;
   
    // make sure no locking is done after this point or lock info may be lost
    myEnv.savedTIreg = VM_Magic.getThreadId();  
    
    // set affinity so JNI calls will get scheduled on this VM_Processor
    VM_Thread.getCurrentThread().returnAffinity = VM_Processor.getCurrentProcessor();

    if (trace) {
      System.out.println("VM_JNIStartUp: attach done, moving thread to native VM_Processor ... ");
    }

    // take this main thread off the current VM_Processor
    // from this point, it exists on the native VM_Processor and will
    // only resume execution when the associated pthread make a JNI call

    VM_Thread.getCurrentThread().beingDispatched = true;

    cleanupThread.proceedToFinish = true;
    VM_Thread.morph();


    // Resume execution here when the associated pthread attempts to detach
    // The thread state is restored here by JNIServiceThread
    // We are now running on the original Java stack instead of the pthread stack
    if (trace) {
      System.out.println("VM_JNIStartUp: attached thread terminated, " +
			 Thread.currentThread().getName() + ", " + 
			 VM.intAsHexString(VM_Magic.objectAsAddress(VM_Thread.getCurrentThread())));
    }

    // remove the VP data structure from the list maintained for GC
    if (VM_Processor.unregisterAttachedProcessor(nativeVP)==0)
      VM.sysWrite("ERROR:  VM_Processor for JNI_CreateJavaVM was not registered\n");

    VM_Thread.terminate();

  }

 

  /******************************************************************
   *   The main method is executed as the start up program by
   * the JNICreateJavaVM call.  This sets up the data structures to 
   * represent the external pthread as a native VM_Processor
   * At the end of the set up sequence, this main Java thread places
   * itself as the active thread of the new native VM_Processor and
   * calls morph() to remove itself from the Java VM_Processor. From 
   * this point on, it is no longer executed but will reappear when
   * the external pthread make a JNI call.
   * @param arg[0] a string "-jni xxxx" where xxxx is the decimal address 
   *               of the JNIEnv pointer
   * @param arg[1] a string "-pid xxxx" where xxxx is the decimal pthread ID
   * @return
   * 
   */
  public static void main(String[] args) {
    int externalJNIEnv = 0;
    int pthread_id = 0;

    if (trace) {
      System.out.println("VM_JNIStartUp: starting Jalapeno ... ");
    }

    // Mark the flag indicating Jalapeno is running as a subsystem under C
    // so the VM won't exit the process when it terminates
    VM.runningAsSubsystem = true;


    // initialize the JNI environment
    VM_JNIEnvironment.boot();

    for (int i=0; i<args.length; i++) {
      if (args[i].startsWith("-jni ")) 
	externalJNIEnv = Integer.valueOf(args[i].substring(5)).intValue();
      if (args[i].startsWith("-pid ")) 
	pthread_id = Integer.valueOf(args[i].substring(5)).intValue();
    }
    
    if (externalJNIEnv==0) {
      System.out.println("VM_JNIStartUp:  ERROR, external JNIEnv required for JNI_CreateJavaVM");
      System.exit(-1);
    } else if (pthread_id==0) {
      System.out.println("VM_JNIStartUp:  ERROR, external phtread ID required for JNI_CreateJavaVM");
      System.exit(-1);
    }      


    if (trace) {
      System.out.println("Main VM_Thread is " + 
			 VM.intAsHexString(VM_Magic.objectAsAddress(VM_Thread.getCurrentThread())));
      System.out.println("env for external pthread is " + VM.intAsHexString(externalJNIEnv));
      System.out.println("AttachRequest at " + 
			 VM.intAsHexString(VM_BootRecord.the_boot_record.attachThreadRequestedOffset + 
					   VM_Magic.getTocPointer()));
    }

    // before executing the following code, bind ourselves to the current processor
    VM_Processor currentVP = VM_Processor.getCurrentProcessor();
    VM_Thread.getCurrentThread().processorAffinity = currentVP;


    // set up the JNI service thread in VM_Scheduler
    // we need to do this in the main Java thread because new Java threads created
    // by JNIServiceThread for AttachCurrentThread needs to be from the "main" thread
    // group 
    Thread j = new JNIServiceThread();
    j.start();
    // wait for the service thread to start up and go into its attachThreadQueue
    while (VM_Scheduler.attachThreadQueue.isEmpty()) {
      VM_Thread.getCurrentThread().yield();
    }

    if (trace)
      System.out.println("VM_JNIStartUp: JNIServiceThread started");

    // Set up the JNICleanUpThread that will signal the external pthread
    // it will also be bound to the same processor
    VM_JNICreateVMFinishThread cleanupThread = 
      new VM_JNICreateVMFinishThread(VM_Thread.getCurrentThread(),
				      externalJNIEnv,
				      currentVP);
    // get it going
    currentVP.readyQueue.enqueue(cleanupThread);

    // wait for it to come up
    if (trace)
      System.out.println("VM_JNIStartUp: waiting for clean up thread"); 
    while (!cleanupThread.ready) {
      VM_Thread.getCurrentThread().yield();
    }

    /***************************************************************************
     * Now we prepare the data structure to create the appearance that the Java main
     * thread has made a native method call and is executing in the native pthread
     */

    // Set up the native VM_Processor to associate with the external pthread
    VM_Processor nativeVP = VM_Thread.getCurrentThread().nativeAffinity;
    if ( nativeVP == null) {
      nativeVP =  VM_Processor.createNativeProcessorForExistingOSThread(VM_Thread.getCurrentThread()); 
      VM_Thread.getCurrentThread().nativeAffinity = nativeVP;
      // register the vpStatusAddress for this thread in the VM_JNIEnvironment.JNIFunctionPointers
      // this is normally done in the prolog of the native method on the Java to C transition
      VM_JNIEnvironment.JNIFunctionPointers[(VM_Thread.getCurrentThread().getIndex() * 2) + 1] = 
	nativeVP.vpStatusAddress;
    }


    // normally done by StartupThread
    nativeVP.pthread_id = pthread_id;
    ++nativeVP.threadSwitchingEnabledCount;


    // Set up the JNI environment for this thread for the external pthread
    // to make JNI calls
    VM_JNIEnvironment myEnv = VM_Thread.getCurrentThread().getJNIEnv();
    myEnv.alwaysHasNativeFrame = true;     // indicate that no stack resize should occur
    myEnv.JNITopJavaFP = VM_Constants.STACKFRAME_SENTINAL_FP;    // indicate to GC that no Java stack frame below this point
    myEnv.savedPRreg = nativeVP;
   
    // make sure no locking is done after this point or lock info may be lost
    myEnv.savedTIreg = VM_Magic.getThreadId();  
    
    // set affinity so JNI calls will get scheduled on this VM_Processor
    VM_Thread.getCurrentThread().returnAffinity = VM_Processor.getCurrentProcessor();

    VM_BootRecord.the_boot_record.bootCompleted = 1;
    if (trace) 
      System.out.println("VM_JNIStartUp: Jalapeno is ready, main thread moving to native VM_Processor ... ");


    // take this main thread off the current VM_Processor
    // from this point, it exists on the native VM_Processor and will
    // only resume execution when the associated pthread make a JNI call

    VM_Thread.getCurrentThread().beingDispatched = true;

    cleanupThread.proceedToFinish = true;
    VM_Thread.morph();

    // The VM_Processor should eventually execute the cleanupThread to 
    // finish setting up this Java main thread and signal the 
    // external pthread to continue 

    // Resume execution here when the main pthread calls DestroyJavaVm
    // The thread state is restored here by JNIServiceThread
    // We are now running on the original Java stack instead of the pthread stack

    if (trace) {
      System.out.println("VM_JNIStartUp: main Java thread terminated, " +
			 Thread.currentThread().getName() + ", " + 
			 VM.intAsHexString(VM_Magic.objectAsAddress(VM_Thread.getCurrentThread())));
    }

    //  System.out.println("VM_JNIStartUp: " + VM_Scheduler.numActiveThreads + " active threads, " +
    //	      VM_Scheduler.numDaemons + " daemons.");

    // wait until the main Java thread is the last one
    while ((VM_Scheduler.numActiveThreads - VM_Scheduler.numDaemons) > 1) {
      VM_Thread.yield();
    }
    
    // remove the VP data structure from the list maintained for GC
    if (VM_Processor.unregisterAttachedProcessor(nativeVP)==0)
      VM.sysWrite("ERROR:  VM_Processor for JNI_CreateJavaVM was not registered\n");

    VM_Thread.terminate();

  }

}
