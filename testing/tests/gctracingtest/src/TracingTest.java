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
public class TracingTest {
  public static Integer[] arr;

  public static void main(String[] args) {
    arr = new Integer[20];
    for (int i = 0; i < 20; i++) {
      arr[i] = i;
      System.out.println(arr[i].toString());
    }
    System.gc();
    for (int i = 0; i < 20; i++) {
      arr[i] = i * 2;
      if (i != 0)
        System.out.println(arr[i-1].toString());
    }
    System.gc();
    arr = new Integer[10];
    for (int i = 0; i < 10; i+=2) {
      arr[i] = i >> 1;
      if (i != 0)
        System.out.println(arr[i-2].toString());
    }
    System.out.println("TracingTest Finished");
  }
}
