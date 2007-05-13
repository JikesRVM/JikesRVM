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
 * Test code demonstrating bug [ 1657236 ] Converting -0.0F to a string does not work in Opt compiler.
 * <p/>
 * Should be tested with -X:aos:initial_compiler=opt in at least one test-configuration.
 *
 */
public class R1657236 {
  public static void main(String[] args) {
    System.out.println(-0F);
  }
}
