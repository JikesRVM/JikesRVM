/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Thread in which a processor ("virtual cpu") begins its work.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_StartupThread extends VM_Thread
   {
   VM_StartupThread(byte[] stack)
      {
      super(stack);
      makeDaemon(true);
      }

   public String
   toString() // overrides VM_Thread
      {
      return "VM_StartupThread";
      }
      
   public void
   run() //- overrides VM_Thread
      {
      if (VM.TraceThreads) VM_Scheduler.trace("VM_StartupThread", "run");
      
      // bind our execution to a physical cpu
      //
      if (VM_Scheduler.cpuAffinity != VM_Scheduler.NO_CPU_AFFINITY)
	VM_SysCall.sysVirtualProcessorBind(VM_Scheduler.cpuAffinity + VM_Processor.getCurrentProcessorId() - 1);
     
      // get pthread_id from AIX and store into vm_processor field
      // 
      VM_Processor.getCurrentProcessor().pthread_id = 
	VM_SysCall.sysPthreadSelf();

      if (VM.TraceThreads) VM_Scheduler.trace("VM_StartupThread", "pthread_id =",
					      VM_Processor.getCurrentProcessor().pthread_id);

      //
      // tell VM_Scheduler.boot() that we've left the C startup
      // code/stack and are now running vm code/stack
      //
      VM_Processor.getCurrentProcessor().isInitialized = true;
      //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
      VM_SysCall.sysWaitForVirtualProcessorInitialization();
      //-#endif

      // enable multiprocessing
      //
      VM_Processor.getCurrentProcessor().enableThreadSwitching();

      // wait for all other processors to do likewise
      //
      //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
      VM_SysCall.sysWaitForMultithreadingStart();
      //-#endif

      //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
      // Store VM_Processor in pthread
      VM_Processor.getCurrentProcessor().stashProcessorInPthread();
      //-#endif
      
      // we've done our job
      //
      if (VM.TraceThreads) VM_Scheduler.trace("VM_StartupThread", "terminating");
      }
   }
