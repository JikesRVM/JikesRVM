/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

import java.io.*;

public class virtual {
  static boolean run() {
    int i = iter(5);
    System.out.println("Virtual returned: " + i);
    return true;
  }

  static int[] num = new int[4];
  static int cnt;

  int f1, f2;

 public static int iter(int n) {
        virtual lo = new virtual(5);
        lo.f1 = lo.f2+1;
        cnt = 1000;
        lo.f1 = lo.abc(200);
         return lo.f1 + lo.f2;
  }

  virtual(int i) { f1 = i; f2= i+4; }

   int abc(int a) { 
         f1 = f1 + a; f2 = f2 + a*10;
         return virtual.cnt + f1 + f2;
   }
}

