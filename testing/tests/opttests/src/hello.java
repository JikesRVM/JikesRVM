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
