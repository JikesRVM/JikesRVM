/*
 * (C) Copyright IBM Corp. 2001
 */
class testRCGC {
  Object foo;
  static Object bar;

  static void testAastore(Object[] xs, Object x, int i) {
    xs[i] = x;
  }
  
  static void testNullAastore(Object[] xs, int i) {
    xs[i] = null;
  }
  
  void testPutfield(Object x) {
    foo = x;
  }

  static void testPutstatic(Object x) {
    bar = x;
  }

  static void testPutfieldUnresolved(Object x, Blat y) {
    y.squid = x;
  }

  static void testPutstaticUnresolved(Object x, Blat y) {
    y.clam = x;
  }

}


class Blat {
  Object squid;
  static Object clam;
}
