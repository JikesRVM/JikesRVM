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
package test.org.jikesrvm.basic.core.reflect;

import java.lang.reflect.Constructor;

/**
 * Test of whether we enforce member access with newInstance()
 */
class tNewInstance {
  static Object o;
  static final class OnlyPrivateConstructor {
    private OnlyPrivateConstructor() {}

    static Constructor getConstructor() throws NoSuchMethodException {
      return OnlyPrivateConstructor.class.getDeclaredConstructor(new Class[] {});
    }

    static Object makeInstance() throws Exception {
      return getConstructor().newInstance(new Object[]{});
    }
  }

  public static void main(String[] args) {
    boolean fail = false;

    System.out.println("tNewInstance...");
    try {
      Class klass = Class.forName("test.org.jikesrvm.basic.core.reflect.tNewInstance$OnlyPrivateConstructor");
      Object o = klass.newInstance();
      fail = true;
     } catch (IllegalAccessException e2) {
       // Test passed.
     } catch (Exception e) {
      e.printStackTrace();
      fail = true;
    }

    try {
      Constructor cons = OnlyPrivateConstructor.getConstructor();
      cons.newInstance(new Object[] {});
      fail = true;
    } catch (IllegalAccessException e2) {
      // Test passed
    } catch (Exception e) {
      e.printStackTrace();
      fail = true;
    }

    try {
      o = OnlyPrivateConstructor.makeInstance();
    } catch (Exception e) {
      e.printStackTrace();
      fail = true;
    }

    if (fail) {
      System.out.println("Test FAILED");
    } else {
      System.out.println("Test SUCCEEDED");
    }

  }
}


