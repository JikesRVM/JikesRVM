/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 * @author Ton Ngo
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

