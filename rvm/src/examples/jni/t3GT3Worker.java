//$Id$

/**
 * worker thread for thread management test.  Calls
 * t3GT3.runit the specified number of times; that
 * method is gcbench.
 *
 * @author Dick Attanasio
 */

class t3GT3Worker extends Thread {

  int        reps, length, arg3;
  boolean    isReady;
  boolean    isFinished;
  
  t3GT3Worker(int reps, int length)
  {
    this.reps = reps;
    this.length = length;
    this.isFinished = false;
    this.isReady    = false;
  }
  
  public void start() //- overrides Thread
  {
    super.start();
  }
  
  public void run()  //- overrides Thread
  {
		isReady = true;
		synchronized (t3GT3.syncher) {
		try {
		t3GT3.syncher.wait();
		}
		catch (InterruptedException e)
		{
		}
		}
		t3GTGC.runit(reps,length);
      isFinished = true;
		synchronized (t3GT3.syncher2) {
		t3GT3.syncher2.notify();
  	}
  }
}

