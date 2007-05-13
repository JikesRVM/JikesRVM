
/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

/**
 * Part of test of 3GT - thread management in 
 * the face of long-running native calls;
 * this thread executes a native method call
 * that sleeps for the specified time.
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

