/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
      Thread.sleep(arg1);
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

