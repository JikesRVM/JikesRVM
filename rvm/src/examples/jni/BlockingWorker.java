
/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Part of test of 3GT - thread management in 
 * the face of long-running native calls;
 * this thread executes a native method call
 * that sleeps for the specified time.
 * @author Stephen Smith
 * @modified by Dick Attanasio
 */

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

    for (int i=0; i < loopctr; i++) {
      t3GT3.nativeBlocking(sleepTime);
    }
    isFinished = true;
  }
}

