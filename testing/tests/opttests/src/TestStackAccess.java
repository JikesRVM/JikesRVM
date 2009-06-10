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
class TestStackAccess {
  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    System.out.print("TestStackAccess");

    String str;

    if (!istoreload().equals("01234")) {
      System.out.print("\nwant: 01234\n got: ");
      System.out.println(istoreload());
      testSuccess = false;
    }

    if (!fstoreload().equals("0.01.02.03.04.0")) {
      System.out.print("\nwant: 0.01.02.03.04.0\n got: ");
      System.out.println(fstoreload());
      testSuccess = false;
    }

    if (!astoreload().equals("nullnullnullnullnull")) {
      System.out.print("\nwant: nullnullnullnullnull\n got: ");
      System.out.println(astoreload());
      testSuccess = false;
    }

    str =  lstoreload0() + lstoreload1() + lstoreload2() +
             lstoreload3() + lstoreload();
    if (!str.equals("001012012301234")) {
      System.out.print("\nwant: 001012012301234\n got: ");
      System.out.println(str);
      testSuccess = false;
    }

    str = dstoreload0() + dstoreload1() + dstoreload2() +
          dstoreload3() + dstoreload();
    if (!str.equals("0.001.0012.00123.001234.0")) {
      System.out.print("\nwant: 0.001.0012.00123.001234.0\n got: ");
      System.out.println(str);
      testSuccess = false;
    }

    if (!dup().equals("12211")) {
      System.out.print("\nwant: 12211\n got: ");
      System.out.println(dup());
      testSuccess = false;
    }

    if (!swap().equals("x")) {
      System.out.print("\nwant: x\n got: ");
      System.out.println(swap());
      testSuccess = false;
    }


    if (testSuccess)
      System.out.println(" succeeded.");
    else
      System.out.println(" failed. *********************");

    return testSuccess;
  }

  static String istoreload() {
    int x0 = 0;            // istore_0
    int x1 = 1;            // istore_1
    int x2 = 2;            // istore_2
    int x3 = 3;            // istore_3
    int x4 = 4;            // istore
    /*
      System.out.print(x0);   // iload_0
      System.out.print(x1);   // iload_1
      System.out.print(x2);   // iload_2
      System.out.print(x3);   // iload_3
      System.out.print(x4);   // iload
      */
    return Integer.toString(x0) + Integer.toString(x1) +
      Integer.toString(x2) +Integer.toString(x3) +
      Integer.toString(x4) ;
  }

  static String fstoreload() {
    float x0 = 0;          // fstore_0
    float x1 = 1;          // fstore_1
    float x2 = 2;          // fstore_2
    float x3 = 3;          // fstore_3
    float x4 = 4;          // fstore

    /*
      System.out.print(x0);   // fload_0
      System.out.print(x1);   // fload_1
      System.out.print(x2);   // fload_2
      System.out.print(x3);   // fload_3
      System.out.print(x4);   // fload
      */
    return Float.toString(x0) + Float.toString(x1) +
      Float.toString(x2) +Float.toString(x3) +
      Float.toString(x4) ;
  }

  static String astoreload() {
    Object x0 = null;      // astore_0
    Object x1 = null;      // astore_1
    Object x2 = null;      // astore_2
    Object x3 = null;      // astore_3
    Object x4 = null;      // astore

    /*
      System.out.print(x0);   // aload_0
      System.out.print(x1);   // aload_1
      System.out.print(x2);   // aload_2
      System.out.print(x3);   // aload_3
      System.out.print(x4);   // aload
      */

    String str = "";
    if (x0 == null) str += "null";
    if (x1 == null) str += "null";
    if (x2 == null) str += "null";
    if (x3 == null) str += "null";
    if (x4 == null) str += "null";

    return str;
  }

   static String lstoreload0() {
      long x0 = 0;           // lstore_0

      /*
      System.out.print(x0);   // lload_0
      */

      return Long.toString(x0);
      }

   static String lstoreload1() {
      int  x0 = 0;
      long x1 = 1;           // lstore_1

      /*
      System.out.print(x0);
      System.out.print(x1);   // lload_1
      */

      return Integer.toString(x0) + Long.toString(x1);
   }

   static String lstoreload2() {
      int  x0 = 0;
      int  x1 = 1;
      long x2 = 2;           // lstore_2

      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);   // lload_2
      */

      return Integer.toString(x0) + Integer.toString(x1) + Long.toString(x2);
   }


   static String lstoreload3() {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      long x3 = 3;           // lstore_3

      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);
      System.out.print(x3);   // lload_3
      */

      return Integer.toString(x0) + Integer.toString(x1) + Integer.toString(x2) +
             Long.toString(x3);
      }

   static String lstoreload() {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      int  x3 = 3;
      long x4 = 4;           // lstore

      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);
      System.out.print(x3);
      System.out.print(x4);   // lload
      */
      return Integer.toString(x0) + Integer.toString(x1) + Integer.toString(x2) +
             Integer.toString(x3) + Long.toString(x4);
      }

   static String dstoreload0() {
      double x0 = 0;         // dstore_0
      // System.out.print(x0);   // dload_0

      return Double.toString(x0);
      }

   static String dstoreload1() {
      int  x0 = 0;
      double x1 = 1;         // dstore_1
      /*
      System.out.print(x0);
      System.out.print(x1);   // dload_1
      */

      return Integer.toString(x0) + Double.toString(x1);
      }

   static String dstoreload2() {
      int  x0 = 0;
      int  x1 = 1;
      double x2 = 2;         // dstore_2

      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);   // dload_2
      */
      return Integer.toString(x0) + Integer.toString(x1) + Double.toString(x2);
      }

   static String dstoreload3() {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      double x3 = 3;         // dstore_3
      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);
      System.out.print(x3);   // dload_3
      */

      return Integer.toString(x0) + Integer.toString(x1) +Integer.toString(x2) +
             Double.toString(x3);
      }

   static String dstoreload() {
      int  x0 = 0;
      int  x1 = 1;
      int  x2 = 2;
      int  x3 = 3;
      double x4 = 4;          // dstore

      /*
      System.out.print(x0);
      System.out.print(x1);
      System.out.print(x2);
      System.out.print(x3);
      System.out.print(x4);   // dload
      */
      return Integer.toString(x0) + Integer.toString(x1) +Integer.toString(x2) +
             Integer.toString(x3) + Double.toString(x4);
      }

   static long sa, sb;
   long         a,  b;

   int   pos   = 0;
   int[]   buf = { 1, 2 };

   int  lpos   = 0;
   long[] lbuf = { 1, 2 };

   static String dup() {
      TestStackAccess t = new TestStackAccess(); // dup

      String str = Integer.toString(t.buf[t.pos++]);
      // System.out.print(t.buf[t.pos++]);           // dup_x1

      sa = sb = 1;                               // dup2
      str += Long.toString(sa+sb);

      // System.out.print(sa + sb);

      t.a = t.b = 1;                             // dup2_x1
      str += Long.toString(t.a+t.b);
      // System.out.print(t.a + t.b);

      str += Long.toString(t.lbuf[t.lpos]++);
      //System.out.print(t.lbuf[t.lpos]++);         // dup2_x2

      int[] x = new int[1];
      switch(x[0] = 1) {                          // dup_x2
         case 1: {
           str += Integer.toString(1);
           //  System.out.print(1);
         }

         }
      return str;
   }

   static String swap() {
      String s = "";
      s += "x";             // swap

      return s;
      // System.out.println(s);
      }
   }
