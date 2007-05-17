/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.core.reflect;

/**
 * Test of whether we enforce member access with newInstance()
 */
class tNewInstance {
  static class OnlyPrivateConstructor {
    private OnlyPrivateConstructor() {}
  }

  public static void main(String[] args) {
    System.out.println("tNewInstance...");
    try {
      Class klass = Class.forName("test.org.jikesrvm.basic.core.reflect.tNewInstance$OnlyPrivateConstructor");
      Object o = klass.newInstance();
      System.out.println("Test FAILED");
    } catch (IllegalAccessException e2) {
      System.out.println("Test SUCCEEDED");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}


