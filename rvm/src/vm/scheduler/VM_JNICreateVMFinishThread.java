/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This class create a thread that will follow the JNIStartUp thread
 * to complete the sequence for transfering logical control from the
 * JNIStartUp thread to the external pthread.
 * The tasks include:
 *   -remove the stack allocated for the JNIStartUp thread;  from this
 *    point the thread will be running on the stack of the external pthread
 *   -store the JNIenv pointer into the address that the external pthread
 *    is expecting.  This will also signal the external pthread that
 *    the VM is ready and it can return to the caller.
 *
 * @author Ton Ngo 
 * @date 10/13/00
 */
class VM_JNICreateVMFinishThread extends VM_Thread implements VM_Constants {
  static boolean trace = false;

  VM_Address externalEnvAddress;
  VM_Thread startUpThread;

  boolean ready = false;
  boolean proceedToFinish = false;


  /**
   * Create a thread that will follow the JNIStartUp thread to perform its
   * clean up tasks before passing control to the external pthread
   * @param JNIStartUpThread the JNIStartUp thread to follow
   * @param externalEnvAddress_1 the address for the JNIEnv that the external 
   *        pthread has reserved
   * @param affinity the VM_Processor that the JNIStartUp thread is bound to
   */
  public VM_JNICreateVMFinishThread(VM_Thread JNIStartUpThread, 
				     VM_Address externalEnvAddress_1, 
				     VM_Processor affinity) {

    startUpThread = JNIStartUpThread;
    externalEnvAddress = externalEnvAddress_1;
    processorAffinity = affinity;

  }

  public String toString() // overrides VM_Thread
  {
    return "JNICreateVMFinishThread";
  }
      
  public void run() //- overrides VM_Thread
  {
    if (VM.TraceThreads) VM_Scheduler.trace("StartupThread", "run");

    // tell the JNIStartUpThread that I am ready
    ready = true;

    // spin wait for the JNIStartUp thread to signal it's ready
    while (!proceedToFinish) {
      yield();
    }


    // This thread is bound to the same VM_Processor as the JNIStartUpThread,
    // so when we get to this point in the code, the JNIStartUpThread has 
    // enqueued itself on the transfer queue of its native VM_Processor and
    // yielded from the current VM_Processor
    // It is now safe to signal to the external pthread that the VM and the 
    // Java main thread are ready


    // For the JNIStartUp thread, set its stack limit to 0: this effectively disable 
    // stack overflow checking but it's OK since this Java thread will be using 
    // the external pthread stack and cannot resize it anyway
    startUpThread.stackLimit = VM_Address.zero();

    // Don't remove the stack, save it to run the termination code 
    // Also remove its stack since it won't be using it anymore
    // startUpThread.stack = null;
   
    // Old way to save termination context
    // save the FP and IP as offset so that we can resume execution on this stack
    // in the future 
    // TODO: Should disable GC for the address calculation 
    // VM_Registers contextRegisters = startUpThread.contextRegisters;
    // int fp = contextRegisters.gprs[FP];
    // int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
    // VM_CompiledMethod     compiledMethod     = VM_ClassLoader.getCompiledMethod(compiledMethodId);
    // env.terminationFPOffset = fp - VM_Magic.objectAsAddress(startUpThread.stack);
    // int ip = VM_Magic.getNextInstructionAddress(fp);
    // env.terminationIPOffset = ip - VM_Magic.objectAsAddress(compiledMethod.getInstructions());

    // New way to save termination context
    VM_JNIEnvironment env = startUpThread.getJNIEnv();
    env.setSavedTerminationContext( startUpThread.contextRegisters );
    startUpThread.contextRegisters = new VM_Registers();
    
    //     if (trace)
    // 	 System.out.println("JNICreateVMFinishThread: fp = " + VM.intAsHexString(fp) + ", offset = " +
    // 			    env.terminationFPOffset + ", ip = " + VM.intAsHexString(ip) + ", offset = " +
    // 			    env.terminationIPOffset);
    

    // fill in the JNIEnv address for the external pthread with the address of the 
    // pointer to the JNI function table
    VM_Address envAddress = startUpThread.getJNIEnv().getJNIenvAddress();
    if (trace)
      System.out.println("JNICreateVMFinishThread: env " + VM.intAsHexString(envAddress.toInt()) +
			 " to external address " + VM.intAsHexString(externalEnvAddress.toInt()));

    VM_Magic.setMemoryAddress(externalEnvAddress, envAddress);

    if (trace)
      System.out.println("JNICreateVMFinishThread: done, exiting");

    // we've done our job, exit ourselves
    //
    if (VM.TraceThreads) VM_Scheduler.trace("StartupThread", "terminating");
  }
  
}
