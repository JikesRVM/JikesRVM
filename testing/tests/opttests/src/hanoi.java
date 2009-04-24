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
public class hanoi {
  static int[] num = new int[4];
  static int cnt;

  public static void main(String[] arg) {
    int disk = 3;
    if (arg.length > 0) disk = Integer.parseInt(arg[0]);
    long start = System.currentTimeMillis();
    int n = go(disk);
    long stop = System.currentTimeMillis();
    long t = (stop - start) / 100;
    System.out.println("For " + disk + " disks, " + n + " moves");
    if (arg.length > 1)
    System.out.println("finished in " + (t / 10) + "." + (t % 10) + " seconds");
  }
  static boolean run() {
    int i = go(20);
    System.out.println("Hanoi returned: " + i);
    return true;
  }

  public static int go(int disk) {
    cnt = 0;
    num[0] = 0;
    num[1] = disk;
    moves(disk, 1, 3);
    return cnt;
  }

  public static void moves(int n, int f, int t) {
    int o;
    if(n == 1) {
      num[f]--;
      num[t]++;
      cnt++;
    } else {
      o = (6-(f+t));
      moves(n-1,f,o);
      moves(1,f,t);
      moves(n-1,o,t);
    }
  }
}
