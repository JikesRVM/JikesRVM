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
package test.org.jikesrvm.opttests.bugs;

public class RVM_964_Testcase {

  public static void main(String[] args) {
    Exception ex = new Exception();
    if (ex.getMessage() == null) {
      System.out.println("Everything ok for first exception");
    }
    Exception ex2 = new Exception("Everything ok for second exception");
    System.out.println(ex2.getMessage());
    Exception ex3 = new Exception("Everything ok for third exception", ex2);
    System.out.println(ex3.getMessage());
  }

}
