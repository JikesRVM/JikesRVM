/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Part of thread management test in
 * the face of long-running native calls;
 * this thread sleeps for the specified time when called
 * repeatedly, until it receives a signal
 *
 * @author Dick Attanasio
 */

class t3GTWorker2 extends Thread {

  int        arg1;
  boolean    isReady;
  boolean    isFinished;
  
  t3GTWorker2(int arg1)
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

                isReady = true;
                while (isReady) {
    try {
      Thread.currentThread().sleep(arg1);
    }
    catch (InterruptedException e) {
                        System.out.println(" GC thread returning");
                        isFinished = true;
    }
                        if (isFinished) return;
    System.gc();
    }

  }
}

