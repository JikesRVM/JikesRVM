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
class TestArrayAccess {
  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    boolean retSuccess = true;

    System.out.print("TestArrayAccess");

    boolean_array();
    if (!testSuccess) {
      System.out.print("\n--boolean_array--");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    byte_array();
    if (!testSuccess) {
      System.out.print("\n--byte_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    char_array();
    if (!testSuccess) {
      System.out.print("\n--char_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    short_array();
    if (!testSuccess) {
      System.out.print("\n--short_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    int_array();
    if (!testSuccess) {
      System.out.print("\n--int_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    long_array();
    if (!testSuccess) {
      System.out.print("\n--long_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    float_array();
    if (!testSuccess) {
      System.out.print("\n--float_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    double_array();
    if (!testSuccess) {
      System.out.print("\n--double_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    object_array();
    if (!testSuccess) {
      System.out.print("\n--object_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    array_array();
    if (!testSuccess) {
      System.out.print("\n--array_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    multi_int_array();
    if (!testSuccess) {
      System.out.print("\n--multi_int_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    multi_object_array();
    if (!testSuccess) {
      System.out.print("\n--multi_object_array--");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    /*
    multi_partial_array();
    if (!testSuccess) {
      System.out.print("\n--multi_partial_array--");
      System.out.println(" failed. ***************\n\n");
    }
    */

    if (retSuccess)
      System.out.println(" succeeded.");

    return retSuccess;

  }

  static void boolean_array() {
    boolean[] array = new boolean[2];    // newarray type=4 eltsize=1
    boolean x0      = false;             // iconst_0
    boolean x1      = true;              // iconst_1

    array[0] = x0;                       // bastore
    array[1] = x1;                       // bastore

    if (array[0]) {
      System.out.print("\nwant: false\n got: ");
      System.out.println(array[0]); // baload
      testSuccess = false;
    }

    if (!array[1]) {
      System.out.print("\nwant: true\n got: ");
      System.out.println(array[1]); // baload
      testSuccess = false;
    }
  }

  static void byte_array() {
    byte[] array = new byte[2];     // newarray type=8 eltsize=1
    byte x0      = 127;
    byte x1      = -1;

    array[0] = x0;                  // bastore
    array[1] = x1;                  // bastore

    if (array[0] != 127) {
      System.out.print("\nwant: 127\n got: ");
      System.out.println(array[0]);   // baload
      testSuccess = false;
    }

    if (array[1] != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(array[1]);   // baload (note sign extension)
      testSuccess = false;
    }
  }

  static void char_array() {
    char[] array = new char[2];          // newarray type=5 eltsize=2
    char x0      = 0x7f41;
    char x1      = 0xff41;

    array[0] = x0;                       // castore
    array[1] = x1;                       // castore

    String str = Integer.toHexString(array[0]); // caload
    if (!str.equals("7f41")) {
      System.out.print("\nwant: 7f41\n got: ");
      System.out.println(Integer.toHexString(array[0])); // caload
      testSuccess = false;
    }

    str = Integer.toHexString(array[1]); // caload (note zero extension)
    if (!str.equals("ff41")) {
      System.out.print("\nwant: ff41\n got: ");
      System.out.println(Integer.toHexString(array[1])); // caload (note zero extension)
      testSuccess = false;

    }
  }

  static void short_array() {
    short[] array = new short[2];        // newarray type=9 eltsize=2
    short x0      = 32767;
    short x1      = -1;

    array[0] = x0;                       // sastore
    array[1] = x1;                       // sastore

    if (array[0] != 32767) {
      System.out.print("\nwant: 32767\n got: ");
      System.out.println(array[0]); // saload
      testSuccess = false;
    }

    if (array[1] != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(array[1]); // saload (note sign extension)
      testSuccess = false;
    }
  }

  static void int_array() {
    int[] array = new int[2];          // newarray type=10 eltsize=4
    int x0      = 0;
    int x1      = 1;

    array[0] = x0;                     // iastore
    array[1] = x1;                     // iastore

    if (array[0] != 0) {
      System.out.print("\nwant: 0\n got: ");
      System.out.println(array[0]);     // iaload
      testSuccess = false;
    }

    if (array[1] != 1) {
      System.out.print("\nwant: 1\n got: ");
      System.out.println(array[1]);     // iaload
      testSuccess = false;
    }
  }

  static void long_array() {
    long[] array = new long[2];        // newarray type=11 eltsize=8
    long x0      = 0;
    long x1      = 1;

    array[0] = x0;                     // lastore
    array[1] = x1;                     // lastore

    if (array[0] != 0) {
      System.out.print("\nwant: 0\n got: ");
      System.out.println(array[0]);     // laload
      testSuccess = false;
    }

    if (array[1] != 1) {
      System.out.print("\nwant: 1\n got: ");
      System.out.println(array[1]);     // laload
      testSuccess = false;
    }
  }

  static void float_array() {
    float[] array = new float[2];     // newarray type=6 eltsize=4
    float x0      = 0;
    float x1      = 1;

    array[0] = x0;                    // fastore
    array[1] = x1;                    // fastore

    if (array[0] != 0.0) {
      System.out.print("\nwant: 0.0\n got: ");
      System.out.println(array[0]);   // faload
      testSuccess = false;
    }

    if (array[1] != 1.0) {
      System.out.print("\nwant: 1.0\n got: ");
      System.out.println(array[1]);   // faload
      testSuccess = false;
    }
  }

  static void double_array() {
    double[] array = new double[2];     // newarray type=7 eltsize=8
    double x0      = 0;
    double x1      = 1;

    array[0] = x0;                      // dastore
    array[1] = x1;                      // dastore

    if (array[0] != 0.0) {
      System.out.print("\nwant: 0.0\n got: ");
      System.out.println(array[0]);   // daload
      testSuccess = false;
    }

    if (array[1] != 1.0) {
      System.out.print("\nwant: 1.0\n got: ");
      System.out.println(array[1]);   // daload
      testSuccess = false;
    }
  }

  static void object_array() {
    Object[] array = new Object[2];   // anewarray
    Object x0      = null;
    Object x1      = null;

    array[0] = x0;                    // aastore
    array[1] = x1;                    // aastore

    if (array[0] != null) {
      System.out.print("\nwant: null\n got: ");
      System.out.println(array[0]);   // aaload
      testSuccess = false;
    }

    if (array[1] != null) {
      System.out.print("\nwant: null\n got: ");
      System.out.println(array[1]);   // aaload
      testSuccess = false;
    }
  }

  static void array_array() {
    Object[] array = new Object[2];   // anewarray
    Object[] x0    = new Object[2];   // anewarray
    Object[] x1    = null;

    array[0] = x0;                    // aastore
    array[1] = x1;                    // aastore

    String str = array[0].getClass().getName(); // aaload
    if (!str.equals("[Ljava.lang.Object;")) {
      System.out.print("\nwant: [Ljava.lang.Object;\n got: ");
      System.out.println(array[0].getClass().getName()); // aaload
      testSuccess = false;
    }

    if (array[1] != null) {
      System.out.print("\nwant: null\n got: ");
      System.out.println(array[1]);                      // aaload
      testSuccess = false;
    }
  }

  static void multi_int_array() {
    int outer  = 2;
    int middle = 3;
    int inner  = 4;

    int[][][] ary = new int[outer][middle][inner]; // multianewarray

    int n = 0;
    int m = 0;
    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k)
          ary[i][j][k] = n++;

    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k) {
          if (ary[i][j][k] != m) {
            System.out.println("\nary["+i+"]["+j+"]["+k+"]="+ary[i][j][k]);
            System.out.println("    It should be: " + m);
            testSuccess = false;
          }
          m++;

        }

    //    System.out.println();
  }

  static void multi_object_array() {
    int outer  = 2;
    int middle = 3;
    int inner  = 4;

    Integer[][][] ary = new Integer[outer][middle][inner]; // multianewarray

    int n = 0;
    int m = 0;
    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k) {
          ary[i][j][k] = new Integer(n++);
          if (ary[i][j][k].intValue() != m) {
            System.out.println("\nary["+i+"]["+j+"]["+k+"]="+ary[i][j][k]);
            System.out.println("    It should be: " + m);
            testSuccess = false;
          }
          m++;
        }

    m = 0;
    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k) {
          if (ary[i][j][k].intValue() != m) {
            System.out.println("\nary["+i+"]["+j+"]["+k+"]="+ary[i][j][k]);
            System.out.println("    It should be: " + m);
            testSuccess = false;
          }
          m++;
        }

    //    System.out.println();
  }

  static void multi_partial_array() {
    int outer  = 2;
    int middle = 3;

    int[][][] ary = new int[outer][middle][]; // multianewarray

    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        if (ary[i][j] != null) {
          System.out.println("\nary["+i+"]["+j+"]="+ary[i][j]);
          System.out.println("   It should ne: nill");
        }

    //    System.out.println();
  }
}
