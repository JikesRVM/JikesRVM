class BlockingWorker extends Thread {

  static final boolean trace = false;

  int        sleepTime;
  boolean    isFinished;
  
  BlockingWorker(int time)
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

  //if (trace) VM_Scheduler.trace("Worker","hello - time",sleepTime);
    for (int i=0; i < loopctr; i++) {
 //   if (trace) VM_Scheduler.trace("Worker","calling nativeBlocking for time = ",sleepTime);
      t3GT3.nativeBlocking(sleepTime);
   // if (trace) VM_Scheduler.trace("Worker","returned from nativeBlocking for time = ",sleepTime);
    }
//  if (trace) VM_Scheduler.trace("Worker","bye - time",sleepTime);
    isFinished = true;
  }
}

