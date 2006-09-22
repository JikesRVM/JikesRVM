/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class hello {
  static boolean run() {
    String str = world();
    System.out.println("Hello returned: " + str);
    return true;
  }

  static final String hi = "hello world";

  static void one() {
     two(hi);
  }

  static void two(String s) {
     System.out.println(s);
  }

  static void three() {
     System.out.println();
  }

  static String world() {
    return hi;
  }
}
