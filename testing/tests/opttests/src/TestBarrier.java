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
class TestBarrier {

  static Object[] array = new Object[10];
  static test     Test  = new test();

  public static void main(String[] args) {
      run(new Object());
  }


  static boolean run(Object o) {
     boolean result = false;
     Test.field = o;
     array[0] = o;
     if (o == null)
        result = true;
     return result;
  }


  static class test {
     Object field;
  }
}
