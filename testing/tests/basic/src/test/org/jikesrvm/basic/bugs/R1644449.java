/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package test.org.jikesrvm.basic.bugs;

/**
 * [ 1644449 ] Floating point display breaks on IA32.
 *
 */
public class R1644449 {
  public static float b = 0.009765625f;
  public static float a = 10.57379f;

  public static void main(String[] args) {
    System.out.println(a * b);
  }
}
