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
 * Contains all the parts of the RVM1017Counter class that the compiler can
 * generate for us (i.e. the parts that don't need to need to be written by
 * hand in Java bytecode).
 */
public abstract class RVM1017AbstractCounter {

  protected long count;

  public abstract void addToCounter(int i);

  public synchronized void printCounter() {
    System.out.println("Counter: " + Long.valueOf(count));
  }

}
