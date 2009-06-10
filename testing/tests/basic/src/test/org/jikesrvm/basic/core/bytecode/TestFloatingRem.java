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
package test.org.jikesrvm.basic.core.bytecode;

class TestFloatingRem {
   public static void main(String[] args) {
      System.out.println("\n-- frem --");
      // easy cases
      testFREM(5f,    3f);
      testFREM(-5f,    3f);
      testFREM(5f,   -3f);
      testFREM(-5f,   -3f);
      // harder cases
      testFREM(3f,    2f);
      testFREM(-3f,    2f);
      testFREM(3f,   -2f);
      testFREM(-3f,   -2f);
      // fringe cases
      float Inff = 1f/0f, NaNf = 0f/0f;
      testFREM(5.6f,    0f, NaNf);
      testFREM(7f,    0f, NaNf);
      testFREM(0f,  5.6f,   0f);
      testFREM(-0f,  5.6f,  -0f);
      testFREM(0f,    7f,   0f);
      testFREM(-0f,    7f,  -0f);
      testFREM(NaNf,  NaNf, NaNf);
      testFREM(NaNf,    1f, NaNf);
      testFREM(1f,  NaNf, NaNf);
      testFREM(Inff,  Inff, NaNf);
      testFREM(Inff, -Inff, NaNf);
      testFREM(-Inff,  Inff, NaNf);
      testFREM(-Inff, -Inff, NaNf);
      testFREM(Inff,    1f, NaNf);
      testFREM(1f,  Inff,   1f);
      testFREM(-Inff,    1f, NaNf);
      testFREM(1f, -Inff,   1f);

      System.out.println("\n-- drem --");
      // easy cases
      testDREM(5,    3);
      testDREM(-5,    3);
      testDREM(5,   -3);
      testDREM(-5,   -3);
      // harder cases
      testDREM(3,    2);
      testDREM(-3,    2);
      testDREM(3,   -2);
      testDREM(-3,   -2);
      // fringe cases
      double Inf = 1.0/0.0, NaN = 0.0/0.0;
      testDREM(5.6,    0, NaN);
      testDREM(7,    0, NaN);
      testDREM(0,  5.6,   0);
      testDREM(-0,  5.6,  -0);
      testDREM(0,    7,   0);
      testDREM(-0,    7,  -0);
      testDREM(NaN,  NaN, NaN);
      testDREM(NaN,    1, NaN);
      testDREM(1,  NaN, NaN);
      testDREM(Inf,  Inf, NaN);
      testDREM(Inf, -Inf, NaN);
      testDREM(-Inf,  Inf, NaN);
      testDREM(-Inf, -Inf, NaN);
      testDREM(Inf,    1, NaN);
      testDREM(1,  Inf,   1);
      testDREM(-Inf,    1, NaN);
      testDREM(1, -Inf,   1);
      System.out.println();
  }

   private static void testFREM(float a, float b) {
      float apb = a % b;
      float adb = a / b;
      int q = (int) adb;
      float res = a - b*q;
      System.out.println("   a: "+a+"; b: "+b+"; (a/b)="+q+"; Expected: "+res+"; Actual: "+apb);
   }

   private static void testFREM(float a, float b, float res) {
      float apb = a % b;
      float adb = a / b;
      System.out.println("   a: "+a+"; b: "+b+"; (a/b)="+adb+"; Expected: "+res+"; Actual: "+apb);
   }

   private static void testDREM(double a, double b) {
      double apb = a % b;
      double adb = a / b;
      int q = (int) adb;
      double res = a - b*q;
      System.out.println("   a: "+a+"; b: "+b+"; (a/b)="+q+"; Expected: "+res+"; Actual: "+apb);
   }

   private static void testDREM(double a, double b, double res) {
      double apb = a % b;
      double adb = a / b;
      System.out.println("   a: "+a+"; b: "+b+"; (a/b)="+adb+"; Expected: "+res+"; Actual: "+apb);
   }
}
