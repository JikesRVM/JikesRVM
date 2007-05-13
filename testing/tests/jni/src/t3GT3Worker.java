/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

/**
 * worker thread for thread management test.  Calls
 * t3GT3.runit the specified number of times; that
 * method is gcbench.
 */

class t3GT3Worker extends Thread {

  int        reps, length;
  boolean    isReady = false;
  boolean    isFinished = false;
  Object     syncher;

  t3GT3Worker(int reps, int length, Object o) {
    this.reps = reps;
    this.length = length;
    syncher = o;
  }
  
  public void start() { //- overrides Thread
    super.start();
  }
  
  public void run() { //- overrides Thread
      isReady = true;
      synchronized (syncher) {
          try {
              syncher.wait();
          }
          catch (InterruptedException e) {
          }
      }
      allocate(reps,length);
      isFinished = true;
      synchronized (t3GT3.syncher2) {
          t3GT3.syncher2.notify();
      }
  }


   public static void allocate (int reps, int length) {

       for (int i = 0; i < reps; i++) {
           char buf [] = new char[0];
           for (int j = 0; j < length; j++) {
               char newbuf [] = new char[buf.length + 1];
               System.arraycopy(buf, 0, newbuf, 0, buf.length);
               newbuf[buf.length] = 'x';
               buf = newbuf;
           }
       }
   }
}

