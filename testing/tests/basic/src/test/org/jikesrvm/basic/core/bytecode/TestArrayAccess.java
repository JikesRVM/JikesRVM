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

class TestArrayAccess {
  public static void main(String[] args) {
    boolean_array();
    byte_array();
    char_array();
    short_array();
    int_array();
    long_array();
    float_array();
    double_array();
    object_array();
    array_array();
    multi_int_array();
    multi_object_array();
    multi_partial_array();
  }

  private static void boolean_array() {
    final boolean[] array = new boolean[]{false, true};
    System.out.print("Boolean Array Expected: false,true  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void byte_array() {
    final byte[] array = new byte[]{127, -1};
    System.out.print("Byte Array Expected: 127,-1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void char_array() {
    final char[] array = new char[]{'c', '$'};
    System.out.print("Char Array Expected: c,$  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void short_array() {
    final short[] array = new short[]{32767, -1};
    System.out.print("Short Array Expected: 32767,-1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void int_array() {
    final int[] array = new int[]{0, 1};
    System.out.print("Int Array Expected: 0,1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void long_array() {
    final long[] array = new long[]{0, 1};
    System.out.print("Long Array Expected: 0,1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void float_array() {
    final float[] array = new float[]{0, 1};
    System.out.print("Float Array Expected: 0,1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void double_array() {
    final double[] array = new double[]{0, 1};
    System.out.print("Double Array Expected: 0,1  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void object_array() {
    final Object[] array = new Object[]{null, "s"};
    System.out.print("Double Array Expected: null,s  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1]);
    System.out.println();
  }

  private static void array_array() {
    final Object[] array = new Object[]{null, new Object[2]};
    System.out.print("Double Array Expected: null,[Ljava.lang.Object;  Actual: ");
    System.out.print(array[0]);
    System.out.print(",");
    System.out.print(array[1].getClass().getName());
    System.out.println();
  }

  private static void multi_int_array() {
    final int outer = 2;
    final int middle = 3;
    final int inner = 4;

    final int[][][] ary = new int[outer][middle][inner]; // multianewarray

    int n = 0;
    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k)
          ary[i][j][k] = n++;

    System.out.println("Multi-dimensional Int Array");
    for (int i = 0; i < outer; ++i) {
      for (int j = 0; j < middle; ++j) {
        for (int k = 0; k < inner; ++k) {
          System.out.printf(" %4d", ary[i][j][k]);
        }
        System.out.println();
      }
      System.out.println();
    }

    System.out.println();
  }

  private static void multi_object_array() {
    final int outer = 2;
    final int middle = 3;
    final int inner = 4;

    final Integer[][][] ary = new Integer[outer][middle][inner]; // multianewarray

    int n = 0;
    for (int i = 0; i < outer; ++i)
      for (int j = 0; j < middle; ++j)
        for (int k = 0; k < inner; ++k)
          ary[i][j][k] = n++;

    System.out.println("Multi-dimensional Object Array");
    for (int i = 0; i < outer; ++i) {
      for (int j = 0; j < middle; ++j) {
        for (int k = 0; k < inner; ++k) {
          System.out.printf(" %4d", ary[i][j][k]);
        }
        System.out.println();
      }
      System.out.println();
    }

    System.out.println();
  }

  private static void multi_partial_array() {
    final int outer = 2;
    final int middle = 3;

    final int[][][] ary = new int[outer][middle][]; // multianewarray

    System.out.println("Partial Multi-dimensional int Array");
    for (int i = 0; i < outer; ++i) {
      for (int j = 0; j < middle; ++j) {
        System.out.printf(" %5s", String.valueOf(ary[i][j]));
      }
      System.out.println();
    }
  }
}
