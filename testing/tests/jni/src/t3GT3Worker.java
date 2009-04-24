/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
          } catch (InterruptedException e) {
          }
      }
      allocate(reps,length);
      isFinished = true;
      synchronized (t3GT3.syncher2) {
          t3GT3.syncher2.notify();
      }
  }


   public static void allocate(int reps, int length) {

       for (int i = 0; i < reps; i++) {
           char[] buf = new char[0];
           for (int j = 0; j < length; j++) {
               char[] newbuf = new char[buf.length + 1];
               System.arraycopy(buf, 0, newbuf, 0, buf.length);
               newbuf[buf.length] = 'x';
               buf = newbuf;
           }
       }
   }
}

