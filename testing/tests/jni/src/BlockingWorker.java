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
class BlockingWorker extends Thread {

  static final boolean trace = false;

  int        sleepTime;
  boolean    isFinished;

  BlockingWorker(int time) {
    this.sleepTime = time;
    this.isFinished = false;
  }

  public void start() {
    super.start();
  }

  public void run() {
    int loopctr = 5;

    for (int i=0; i < loopctr; i++) {
      t3GT3.nativeBlocking(sleepTime);
    }
    isFinished = true;
  }
}

