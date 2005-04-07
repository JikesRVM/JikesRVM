/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.VM_Method;

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
 * @modified Perry Cheng
 * @date 10 Nov 1999 
 */
public class FinalizerThread extends VM_Thread {

   private final static int verbose = 0; // currently goes up to 2

   public FinalizerThread() {
     super(null);
   }

   public String toString() {
     return "FinalizerThread";
   }

   // Run the finalizer thread (one per RVM)
   //
   public void run() {

     if (verbose >= 1)
       VM_Scheduler.trace("FinalizerThread ", "run routine entered");
     
     try {
       while (true) {
         
         // suspend this thread: it will resume when the garbage collector
         // places objects on the finalizer queue and notifies.
         
         VM_Scheduler.finalizerMutex.lock();
         VM_Thread.yield(VM_Scheduler.finalizerQueue, 
                         VM_Scheduler.finalizerMutex);
         
         if (verbose >= 1)
           VM.sysWriteln("FinalizerThread starting finalization");
         
         while (true) {
           Object o = MM_Interface.getFinalizedObject();
           if (o == null) break;
           if (verbose >= 2) {
             VM.sysWrite("FinalizerThread finalizing object at ", VM_Magic.objectAsAddress(o));
             VM.sysWrite("of type ");
             VM.sysWrite(VM_Magic.getObjectType(o).getDescriptor());
             VM.sysWriteln();
           }
           try {
             VM_Method method = VM_Magic.getObjectType(o).asClass().getFinalizer();
             if (VM.VerifyAssertions) VM._assert(method != null);
             Object[]  none = new Object[0];
             Object    ret = VM_Reflection.invoke(method, o, none);
           }
           catch (Exception e) {
               if (verbose >= 1) VM.sysWriteln("Throwable exception caught for finalize call");
           }
           if (verbose >= 2) VM.sysWriteln("FinalizerThread done with object at ",
                                           VM_Magic.objectAsAddress(o));
         }
         if (verbose >= 1) VM.sysWriteln("FinalizerThread finished finalization");
         
       }          // while (true)
     }
     catch (Exception e) {
       VM.sysWriteln("Unexpected exception thrown in finalizer thread: ", e.toString());
       e.printStackTrace();
     }
     
   }  // run
  
  

}
