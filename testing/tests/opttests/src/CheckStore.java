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
class CheckStore {

  public static void main(String[] args) {
     run();
  }

  public static boolean run() {
    System.out.println("CheckStore prints: ");
    try {
      run1(new Float(0));
    } catch (ArrayStoreException e) {
      System.out.println(" so1.");
    }
    try {
      run2(1, new Integer(1));
    } catch (ArrayStoreException e) {
      System.out.println(" so2.");
    }
    return true;
  }


  static void run1(Object input) {
    Object[] n = new Integer[1];
    n[0] = new Integer(0);
    n[0] = input;

    // Unreachable but needed to prevent the opt compiler from removing
    // the access that causes the ArrayStoreException
    System.out.println(n[0]);
  }

  static Object[] global = new Object[2];

  static void run3(Object input) {
    Object[] n = new Object[1];
    n[0] = input;
    global[0] = new Integer(0);
  }


  static void run2(int a, Object elem) {
     Object[] array;
     if (a < 0)
        array = new Object[2];
     else
        array = new String[2];
     array[0] = elem;

     // Unreachable but needed to prevent the opt compiler from removing
     // the access that causes the ArrayStoreException.
     // A simple System.out.println(..) is currently not possible here
     // because it triggers a bug - see RVM-1036
     if (array[0].hashCode() % 3 == new Object().hashCode()) {
       System.out.println("Unreachable.");
     }

  }

}
