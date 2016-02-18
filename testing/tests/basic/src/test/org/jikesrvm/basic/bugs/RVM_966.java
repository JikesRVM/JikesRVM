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
package test.org.jikesrvm.basic.bugs;

/**
 * Tests that the VM terminates with an error exit
 * code when an application thread is terminated due
 * to an uncaught expection.
 */
public class RVM_966 {
  public static void main(String[] args) {
    Object obj = null;
    System.out.println(obj.hashCode());
  }
}
