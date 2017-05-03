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
package test.org.jikesrvm.jasmin_based;

public class RVM1017CounterThread extends Thread {

  private final RVM1017Counter myCounter;

  public RVM1017CounterThread(RVM1017Counter c) {
    myCounter = c;
  }

  @Override
  public void run() {
    for (int i = 0; i < 100; i++) {
      myCounter.addToCounter(i);
    }
  }

}
