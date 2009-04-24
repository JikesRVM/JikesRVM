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
class TestReturn {
  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    System.out.print("TestReturn");

    /**/                                                       void_f();
    boolean b_f = boolean_f();
    if (!b_f) {
      System.out.print("\nwant: true\n got: ");
      System.out.println(boolean_f());
      testSuccess = false;
    }

    byte by_f = byte_f();
    if (by_f != 2) {
      System.out.print("\nwant: 2\n got: ");    System.out.println(byte_f());
      testSuccess = false;
    }

    char c_f = char_f();
    if (c_f != 'A') {
      System.out.print("\nwant: A\n got: ");    System.out.println(char_f());
      testSuccess = false;
    }

    short s_f = short_f();
    if (s_f != 4) {
      System.out.print("\nwant: 4\n got: ");    System.out.println(short_f());
      testSuccess = false;
    }

    int i_f = int_f();
    if (i_f != 5) {
      System.out.print("\nwant: 5\n got: ");    System.out.println(int_f());
      testSuccess = false;
    }

    long l_f = long_f();
    if (l_f != 6L) {
      System.out.print("\nwant: 6\n got: ");    System.out.println(long_f());
      testSuccess = false;
    }

    float f_f = float_f();
    if (f_f != 7.0) {
      System.out.print("\nwant: 7.0\n got: ");  System.out.println(float_f());
      testSuccess = false;
    }

    double d_f = double_f();
    if (d_f != 8.0D) {
      System.out.print("\nwant: 8.0\n got: ");  System.out.println(double_f());
       testSuccess = false;
    }

    if (object_f() != null) {
      System.out.print("\nwant: null\n got: "); System.out.println(object_f());
      testSuccess = false;
    }

    if (primitive_array_f() != null) {
      System.out.print("\nwant: null\n got: "); System.out.println(primitive_array_f());
          testSuccess = false;
    }

    if (object_array_f() != null) {
      System.out.print("\nwant: null\n got: "); System.out.println(object_array_f());
          testSuccess = false;
    }

    if (testSuccess)
      System.out.println(" succeeded.");
    else
      System.out.println("  failed. ********************");
    return testSuccess;
  }

  static void     void_f()            {                           return  ; } // return
  static boolean  boolean_f()         { boolean x   = true;       return x; } // ireturn
  static byte     byte_f()            { byte    x   = 2;          return x; } // ireturn
  static char     char_f()            { char    x   = 0x41;       return x; } // ireturn
  static short    short_f()           { short   x   = 4;          return x; } // ireturn
  static int      int_f()             { int     x   = 5;          return x; } // ireturn
  static long     long_f()            { long    x   = 6;          return x; } // lreturn
  static float    float_f()           { float   x   = 7;          return x; } // freturn
  static double   double_f()          { double  x   = 8;          return x; } // dreturn
  static Object   object_f()          { Object  x   = null;       return x; } // areturn
  static int[]    primitive_array_f() { int[]   x = null;       return x; } // areturn
  static Object[] object_array_f()    { Object[]  x = null;       return x; } // areturn
}
