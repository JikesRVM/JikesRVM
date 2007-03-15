/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Canisius College. 2006
 */


/**
 * @author <a href="http://cs.canisius.edu/~hertzm">Matthew Hertz</a>
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
