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

/**
 * Test class for RVM-1017 (Implicit unlock in synchronized method relies on local variable 0).
 * <p>
 * The RVM1017CounterThread instances call a method in RVM1017Counter that reproduces the bug.
 * We use this setup to ensure synchronization removal can't remove the synchronization from a
 * the failing method by turning the call into a thread-local one.
 */
public class RVM1017 {

  public static void main(String[] args) {
    // counter starts at 0
    RVM1017Counter c = new RVM1017Counter();
    // each counter thread adds 5050 to the counter during its lifetime
    RVM1017CounterThread t1 = new RVM1017CounterThread(c);
    RVM1017CounterThread t2 = new RVM1017CounterThread(c);
    try {
      t1.start();
      t2.start();
      t1.join();
      t2.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }
    c.printCounter();
  }

}
