/*
 * (C) Copyright IBM Corp. 2001
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
