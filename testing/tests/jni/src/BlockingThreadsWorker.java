/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
import org.jikesrvm.scheduler.VM_Scheduler;

/*
*/
class BlockingThreadsWorker extends Thread {

  static final boolean trace = false;

  int        sleepTime;
  boolean    isFinished;
  
  BlockingThreadsWorker(int time)
  {
    this.sleepTime = time;
    this.isFinished = false;
  }
  
  public void start() //- overrides Thread
  {
    super.start();
  }
  
  public void run()  //- overrides Thread
  {
    int loopctr = 5;

    if (trace) VM_Scheduler.trace("Worker","hello - time",sleepTime);
    for (int i=0; i < loopctr; i++) {
      if (trace) VM_Scheduler.trace("Worker","calling nativeBlocking for time = ",sleepTime);
      tBlockingThreads.nativeBlocking(sleepTime);
      if (trace) VM_Scheduler.trace("Worker","returned from nativeBlocking for time = ",sleepTime);
    }
    if (trace) VM_Scheduler.trace("Worker","bye - time",sleepTime);
    isFinished = true;
  }
}

