/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.core.bytecode;

/**
 * @author unascribed
 */
class TestReturn {
  public static void main(String args[]) {
    System.out.println(boolean_f());
    System.out.println(byte_f());
    System.out.println(char_f());
    System.out.println(short_f());
    System.out.println(int_f());
    System.out.println(long_f());
    System.out.println(float_f());
    System.out.println(double_f());
    System.out.println(object_f());
    System.out.println(primitive_array_f());
    System.out.println(object_array_f());
  }

  private static boolean boolean_f() { return true; }

  private static byte byte_f() { return 2; }

  private static char char_f() { return 'a'; }

  private static short short_f() { return 4; }

  private static int int_f() { return 5; }

  private static long long_f() { return 6L; }

  private static float float_f() { return 7.0F; }

  private static double double_f() { return 8.0; }

  private static Object object_f() { return null; }

  private static int[] primitive_array_f() { return null; }

  private static Object[] object_array_f() { return null; }
}
