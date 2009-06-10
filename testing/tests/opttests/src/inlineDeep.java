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
public final class inlineDeep {

  public static void main(String[] args) {
    run();
  }

  static boolean run() {
    int i = recurs6(6, 6);
    System.out.println("inlineDeep returned: " + i);
    return true;
  }

  static int recurs6(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs5(j, n)+i;
    return k;
  }

  static int recurs5(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs4(j, n)+i;
    return k;
  }

  static int recurs4(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs3(j, n)+i;
    return k;
  }

  static int recurs3(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs2(j, n)+i;
    return k;
  }

  static int recurs2(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs1(j, n)+i;
    return k;
  }

  static int recurs1(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs0(j, n)+i;
    return k;
  }

  static int recurs0(int i, int m) {
    int k = 1;
    return k;
  }



}
