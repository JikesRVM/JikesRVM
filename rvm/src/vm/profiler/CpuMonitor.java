/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Display per-thread cpu utilization information in real time.
 * @authorDerek Lieber
 * @date 09 Nov 1999 
 */
class CpuMonitor extends VM_Thread {
  // Invoked by VM_Debugger.
  //
  static void main() {
    VM.EnableCPUMonitoring = true;
    VM_Thread t = new CpuMonitor();
    t.start();
  }

  private CpuMonitor() {
    makeDaemon(true);
  }
      
  public void run() {
    VM.sysWrite("Cpu monitor running\n");
    for (;;) {
      try { sleep(1000); }
      catch (InterruptedException e) {}
         
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
         
      // pass 1: add up cpu times
      //
      double totalCpuTime = 0;
      for (int threadIndex = 0, n = VM_Scheduler.threads.length; threadIndex < n; ++threadIndex) {
	VM_Thread t = VM_Scheduler.threads[threadIndex];
	if (t == null) continue;
	totalCpuTime += t.cpuTotalTime;
      }

      // pass 1: print and reset cpu times
      //
      VM.sysWrite("\033[H\033[2J");
      for (int threadIndex = 0, n = VM_Scheduler.threads.length; threadIndex < n; ++threadIndex) {
	VM_Thread t = VM_Scheduler.threads[threadIndex];
	if (t == null) continue;
            
	int cpu = (int)(t.cpuTotalTime / totalCpuTime * 100);
	t.cpuTotalTime = 0;
            
	char cpu0 = (char)('0' + cpu / 10);
	char cpu1 = (char)('0' + cpu % 10);
            
	VM.sysWrite(
		    + threadIndex
		    + "("
		    + cpu0 + cpu1                         // percent cpu time
		    + (  t.isIdleThread ?     "i"         // idle daemon
			 : t.isGCThread   ?     "g"         // gc daemon
			 : t.isDaemon     ?     "d"         // user daemon
			 :                      "" )
		    + (!t.isAlive     ?     "!" : "")     // dead/alive
		    + (t.cpuStartTime > 0 ? "+" : "-")    // running/stopped
		    + ") "
		    );
	VM.sysWrite("\n");
      }
         
      VM.sysWrite((int)(totalCpuTime * 1000) + "ms");
      VM.sysWrite("\n");
      
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }
  }
}
