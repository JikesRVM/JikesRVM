/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

class TestBarrier {
  
  static Object[] array = new Object[10]; 
  static test     Test  = new test();

  public static void main(String args[]) {
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
