/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/reflect/src/tNewInstance.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
package test.org.jikesrvm.basic.core.reflect;

/**
 * Test of whether we enforce member access with newInstance()
 *
 * @author Stephen Fink
 * @author Eugene Gluzberg
 */
class tNewInstance {
  static class OnlyPrivateConstructor {
    private OnlyPrivateConstructor() {}
  }

  public static void main(String args[]) {
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


