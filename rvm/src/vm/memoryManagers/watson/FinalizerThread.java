/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Finalizer thread.
 *
 * This thread is created by VM_Scheduler.boot() at runtime startup.
 * Its "run" method does the following:
 *    1. yield to the gcwaitqueue, until scheduled by g.c.
 *    2. For all objects on finalize Q, run the finalize() method
 *    3. Go to 1
 *
 * This thread comes out of wait state via notify from the garbage collector  
 *
 * @author Dick Attanasio
 * @date 10 Nov 1999 
 */
public class FinalizerThread extends VM_Thread 
   {
   private final static boolean TRACE        = false; // emit trace messages?
   private final static boolean TRACE_DETAIL = false; // emit detail messages?
   public  static Object marker = new Object(); 


   // Run the finalizer thread (one per RVM)
   //
   public void
   run() //- overrides Thread
      {
      if (TRACE) {
	VM_Scheduler.trace("FinalizerThread ", "run routine entered");
      }

      while (true)
         {
         // suspend this thread: it will resume when the garbage collector
	 // places objects on the finalizer queue and notifies.
         //

	 VM_Scheduler.finalizerMutex.lock();
	 VM_Thread.getCurrentThread().yield(VM_Scheduler.finalizerQueue, 
	   VM_Scheduler.finalizerMutex);

          if (TRACE) 
	   {
	    VM_Scheduler.trace("FinalizerThread", "starting finalization");
	    if (TRACE_DETAIL) {
	      VM_Finalizer.dump_live();
	      VM_Finalizer.dump_finalize();
	    }
	   }

  	 synchronized (marker) {
	 Object o = VM_Finalizer.get();
	 if (TRACE_DETAIL) VM_Scheduler.trace("FinalizerThread: ", "object at ",
	   VM_Magic.objectAsAddress(o));
	 while (o != null) {
	   try {

	     VM_Method method = VM_Magic.getObjectType(o).asClass().getFinalizer();
	     if (VM.VerifyAssertions) VM.assert(method != null);
	     Object[]  none = new Object[0];
	     Object    ret = VM_Reflection.invoke(method, o, none);
	   }
	   catch (Exception e) {
	     if (TRACE) VM.sysWrite("Throwable exception caught for finalize call\n");
	   }
	   o = VM_Finalizer.get();
	   if (TRACE_DETAIL) VM_Scheduler.trace("FinalizerThread", "object at ",
	      VM_Magic.objectAsAddress(o));
	 }
         if (TRACE) VM_Scheduler.trace("FinalizerThread", "finished finalization");
	   
  	 }	 	// synchronized (marker)
	 }		// while (true)

      }  // run



}
