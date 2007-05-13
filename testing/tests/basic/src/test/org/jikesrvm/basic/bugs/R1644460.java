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
 * Test code demonstrating bug [ 1644460 ] DaCapo lusearch fails when opt compiled.
 *
 * Should be tested with -X:aos:initial_compiler=opt in at least one test-configuration. 
 *
 */
public class R1644460 {
  public static float b = 0.009765625f;
  public static float a = 10.57379f;

  public static void main(String[] args) {
    final float c = a * b;
    System.out.println(c == 0.10325967f);
  }
}