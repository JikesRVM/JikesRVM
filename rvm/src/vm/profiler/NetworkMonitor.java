/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Display per-thread network utilization information in real time.
 *
 * @author Derek Lieber
 * @date 10 Nov 1999 
 */
class NetworkMonitor extends Thread
   {
   // Invoked by VM_Debugger.
   //
   static void 
   main()
      {
      if (!VM.BuildForNetworkMonitoring)
         {
         VM.sysWrite("NetworkMonitor.main: vm wasn't built for network monitoring\n");
         return;
         }
      VM_Thread t = new NetworkMonitor();
      t.start();
      }

   private
   NetworkMonitor()
      {
      makeDaemon(true);
      }
      
   public void
   run() //- overrides VM_Thread
      {
      VM.sysWrite("Cpu monitor running\n");
      for (;;)
         {
         try { sleep(1000); }
         catch (InterruptedException e) {}
         
         VM_Processor.getCurrentProcessor().disableThreadSwitching();
         
         int totalNetReads  = 0;
         int totalNetWrites = 0;
         for (int threadIndex = 0, n = VM_Scheduler.threads.length; threadIndex < n; ++threadIndex)
            {
            VM_Thread t = VM_Scheduler.threads[threadIndex];
            if (t == null) continue;
            
            int netReads  = t.netReads;  t.netReads  = 0;
            int netWrites = t.netWrites; t.netWrites = 0;
            
            totalNetReads  += netReads;
            totalNetWrites += netWrites;
            
            VM.sysWrite(
                       + threadIndex
                       + ":"
                       + netReads
                       + "/"
                       + netWrites
                       + " "
                       );
            }
         
         VM.sysWrite("tot:" + totalNetReads + "/" + totalNetWrites + "\n");

         VM_Processor.getCurrentProcessor().enableThreadSwitching();
         }
      }
   }
