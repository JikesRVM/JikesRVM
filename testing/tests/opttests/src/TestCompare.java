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
class TestCompare {
  public static void main(String[] args) {
    run();
  }
  static boolean testSuccess = true;
  public static boolean run() {
    boolean retSuccess = true;
    System.out.print("TestCompare");

    zero_cmp();
    if (!testSuccess) {
      System.out.print("\n--zero_cmp--");
      System.out.println(" failed. ***************\n\n");
     }

    retSuccess &= testSuccess;
    testSuccess = true;

    i_cmp();
    if (!testSuccess) {
      System.out.print("\n--i_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    l_cmp();
    if (!testSuccess) {
      System.out.print("\n--l_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    f_cmp();
    if (!testSuccess) {
      System.out.print("\n--f_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    d_cmp();
    if (!testSuccess) {
      System.out.print("\n--d_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    a_cmp();
    if (!testSuccess) {
      System.out.print("\n--a_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    null_cmp();
    if (!testSuccess) {
      System.out.print("\n--null_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    str_cmp();
    if (!testSuccess) {
      System.out.print("\n--str_cmp--");
      System.out.println(" failed. ***************\n\n");
    }

    if (retSuccess)
      System.out.println(" succeeded.");

    return retSuccess;

  }

   static String str = "";
   static void zero() { str += "0"; }
   static void one()  { str += "1"; }

   static void zero_cmp() {
      int i = -1;
      zero_cmp(i);
   }
   static void zero_cmp(int i) {
        // System.out.print("\nzero_cmp want: 100110\n got: ");
      str = "";
      if (i != 0) one(); else zero(); // ifeq
      if (i == 0) one(); else zero(); // ifne
      if (i >= 0) one(); else zero(); // iflt
      if (i <  0) one(); else zero(); // ifge
      if (i <= 0) one(); else zero(); // ifgt
      if (i >  0) one(); else zero(); // ifle
      if (!str.equals("100110")) {
        System.out.println("\n ****** Want 100110\n got: " + str);
        testSuccess = false;
      }
      // System.out.println();
      }

   static void i_cmp() {
      int i = -1;
      int j =  0;
      i_cmp(i,j);
   }

   static void i_cmp(int i, int j) {
      str = "";
      //   System.out.print("i_cmp want: 100110\n got: ");
      if (i != j) one(); else zero(); // if_icmpeq
      if (i == j) one(); else zero(); // if_icmpne
      if (i >= j) one(); else zero(); // if_icmplt
      if (i <  j) one(); else zero(); // if_icmpge
      if (i <= j) one(); else zero(); // if_icmpgt
      if (i >  j) one(); else zero(); // if_icmple
      if (!str.equals("100110")) {
        System.out.println("\n ****** Want 100110\n got: " + str);
        testSuccess = false;
      }

      // System.out.println();
      }

   static void l_cmp() {
      long a = 1;
      long b = 2;
      l_cmp(a,b);
   }

   static void l_cmp(long a, long b) {
        //     System.out.print("\nl_cmp want: 100010001\n got: ");
      str = "";

      if (a <  b) one(); else zero(); // lcmp(-1)
      if (a == b) one(); else zero();
      if (a >  b) one(); else zero();

      if (a <  a) one(); else zero();
      if (a == a) one(); else zero(); // lcmp(0)
      if (a >  a) one(); else zero();

      if (b <  a) one(); else zero();
      if (b == a) one(); else zero();
      if (b >  a) one(); else zero(); // lcmp(1)

      if (!str.equals("100010001")) {
        System.out.println("\n ****** Want 100010001\n got: " + str);
        testSuccess = false;
      }


      //      System.out.println();
   }

   static void f_cmp() {
      float a = 1;
      float b = 2;
      f_cmp(a,b);
   }

   static void f_cmp(float a, float b) {
        //      System.out.print("\nf_cmp want: 100010001\n got: ");
      str = "";

      if (a <  b) one(); else zero(); // fcmp[lg](-1)
      if (a == b) one(); else zero();
      if (a >  b) one(); else zero();

      if (a <  a) one(); else zero();
      if (a == a) one(); else zero(); // fcmp[lg](0)
      if (a >  a) one(); else zero();

      if (b <  a) one(); else zero();
      if (b == a) one(); else zero();
      if (b >  a) one(); else zero(); // fcmp[lg](1)

      //      System.out.println();

      if (!str.equals("100010001")) {
        System.out.println("\n ****** Want 100010001\n got: " + str);
        testSuccess = false;
      }


   }

   static void d_cmp() {
      double a = 1;
      double b = 2;
      d_cmp(a,b);
   }

   static void d_cmp(double a, double b) {
        // System.out.print("\nd_cmp want: 100010001\n got: ");
      str = "";

      if (a <  b) one(); else zero(); // dcmp[lg](-1)
      if (a == b) one(); else zero();
      if (a >  b) one(); else zero();

      if (a <  a) one(); else zero();
      if (a == a) one(); else zero(); // dcmp[lg](0)
      if (a >  a) one(); else zero();

      if (b <  a) one(); else zero();
      if (b == a) one(); else zero();
      if (b >  a) one(); else zero(); // dcmp[lg](1)

      //      System.out.println();

      if (!str.equals("100010001")) {
        System.out.println("\n ****** Want 100010001\n got: " + str);
        testSuccess = false;
      }

   }

   static void a_cmp() {
      Object a = null;
      Object b = null;
      a_cmp(a,b);
   }

   static void a_cmp(Object a, Object b) {
        //      System.out.print("\na_cmp want: 10\n got: ");
      str = "";
      if (a == b) one(); else zero(); // if_acmpne
      if (a != b) one(); else zero(); // if_acmpeq
      //      System.out.println();

     if (!str.equals("10")) {
        System.out.println("\n ****** Want 10\n got: " + str);
        testSuccess = false;
      }

   }

   static void null_cmp() {
      Object o = null;
      null_cmp(o);
   }

   static void null_cmp(Object o) {
        //      System.out.print("\nnull_cmp want: 10\n got: ");

        str = "";
        if (o == null) one(); else zero(); // ifnonnull
        if (o != null) one(); else zero(); // ifnull
      //      System.out.println();

     if (!str.equals("10")) {
        System.out.println("\n ****** Want 10\n got: " + str);
        testSuccess = false;
      }

   }

   static void str_cmp() {
      String s1 = "abc";
      String s2 = "abc";
      String s3 = "ab"; s3 = s3 + "c";
      str_cmp(s1,s2,s3);
   }

   static void str_cmp(String s1, String s2, String s3) {
        boolean strCmp = (s1 == s2);

        if (!strCmp) {
          System.out.println("\nwant: true\n got: " + (s1 == s2));
          testSuccess = false;
        }

        strCmp = (s1 == s3);
        if (strCmp) {
          System.out.println("\nwant: false\n got: " + (s1 == s3));
          testSuccess = false;
        }
   }
}
