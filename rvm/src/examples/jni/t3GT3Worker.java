class t3GT3Worker extends Thread {

  int        arg1, arg2, arg3;
  boolean    isReady;
  boolean    isFinished;
  
  t3GT3Worker(int arg1)
  {
    this.arg1 = arg1;
    this.isFinished = false;
    this.isReady    = false;
  }
  
  public void start() //- overrides Thread
  {
    super.start();
  }
  
  public void run()  //- overrides Thread
  {
//VM_Scheduler.trace(" Worker starting", " ");
		isReady = true;
		synchronized (t3GT3.syncher) {
		try {
//VM_Scheduler.trace(" Worker waiting ", " ");
		t3GT3.syncher.wait();
		}
		catch (InterruptedException e)
		{
		}
		}
//VM_Scheduler.trace("About to call doit", " ");
		for (int i = 0; i < arg1; i++) t3GTGC.runit();
//VM_Scheduler.trace("returned from doit", " ");
      isFinished = true;
		synchronized (t3GT3.syncher2) {
//VM_Scheduler.trace(" Worker notifying", " ");
		t3GT3.syncher2.notify();
  	}
  }
}

