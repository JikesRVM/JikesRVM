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
class TypeCheck {

  public static void main(String[] args) {
    PRINT = true;
    num_cc1 = num_cc2 = num_cc3 = num_cc4 = num_cc5 = num_io1 = num_io2 = num_io3 =
      num_cs = num_cc_cs = num_io_cc_cs = 100000;
    run();
  }

  static int num_cc1 = 10000;
  static int num_cc2 = 10000;
  static int num_cc3 = 10000;
  static int num_cc4 = 10000;
  static int num_cc5 = 10000;
  static int num_io1 = 10000;
  static int num_io2 = 10000;
  static int num_io3 = 10000;
  static int num_io4 = 10000;
  static int num_cs = 10000;
  static int num_cc_cs = 10000;
  static int num_io_cc_cs = 10000;
  static volatile Integer field1;
  static volatile Object field2;
  static volatile Throwable field3;
  static volatile java.io.Serializable field4;
  static Integer[] a1;
  static Object[] a2;
  static Throwable[] a3;

  static long t1;

  static boolean testSuccess = true;

  static boolean run() {

    // TEST 1
    // checkcast
    Object obj = new Integer(1);
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc1; ++i) {
      field1 = (Integer)obj;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 2
    field2 = new Integer(2);
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc2; ++i) {
      field1 = (Integer)field2;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 3
    obj = new NoSuchMethodError();
    field3 = (Throwable)obj;
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc3; ++i) {
      field3 = (Throwable)obj;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 4
    field2 = new NoSuchMethodError();
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc4; ++i) {
      field3 = (Throwable)field2;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 5
    Object old1 = field2;
 ///!!TODO: oti library doesn't have rmi package. is io package just as good here? [--DL]
 ///field2 = field3 = new java.rmi.server.SocketSecurityException("");
    field2 = field3 = new java.io.IOException("");
    field4 = (java.io.Serializable)field2;
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc5; ++i) {
      field4 = (java.io.Serializable)field2;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);
    field2 = old1;

    // TEST 6
    // instanceof
    int sum = 0;
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_io1; ++i) {
      if (field2 instanceof NoSuchMethodError) ++sum;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 7
    field2 = new Integer(sum);
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_io2; ++i) {
      if (field2 instanceof Integer) ++sum;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 8
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_io3; ++i) {
      if (field2 instanceof NoSuchMethodError) ++sum;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 9
    // checkstore
    field2 = new Integer(sum);
    a1 = new Integer[num_cs];
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cs; ++i) {
      a1[i] = field1;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 10
    Object[] b = a1;
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cs; ++i) {
      b[i] = field2;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 11
    a2 = new Object[num_cs];
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cs; ++i) {
      a2[i] = field3;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 12
    a3 = new Exception[num_cs];
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cs; ++i) {
      a3[i] = field3;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 13
    // checkcast + checkstore
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_cc_cs; ++i) {
      a1[i] = (Integer)field2;
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 14
    // instanceof + checkcast + checkstore
    a3[0] = (Exception)a2[0];
    t1 = System.currentTimeMillis();
    for (int i=0; i<num_io_cc_cs; ++i) {
      Object foo = a2[i];
      if (foo instanceof Integer) {
        //      System.err.println("bug in type checking");
        return false;
      }
      if (foo instanceof java.io.IOException) {
        a3[i] = (Exception)foo;
      }
      if (foo instanceof Throwable) {
        a2[i] = (Throwable)foo;
      }
    }
    t1 = System.currentTimeMillis() - t1;
    report(t1);

    // TEST 15
    // checkcast + nullcheck
    Object x;
    if (t1 > 0)
        x  = new Integer(5);
    else if (t1 < 0)
        x = new Float(6.7);
    else
        x = null;
    if (t1 > 0 && ((Integer) x).intValue() != 5)
        return false;

    // TEST 16
    // checkcast + if null
    if (t1 > 0)
        x  = new Integer(5);
    else if (t1 < 0)
        x = new Float(6.7);
    else
        x = null;
    Integer i = (Integer) x;
    if (i == null && t1 > 0)
        return false;

    // TEST 17
    // checkcast + if null
    if (t1 > 0)
        x  = new Integer(5);
    else if (t1 < 0)
        x = new Float(6.7);
    else
        x = null;
    i = (Integer) x;
    if (i != null)
        if (t1 > 0 && i.intValue() != 5)
            return false;

    return testSuccess;
  }

  static int testNum = 0;

  static void report(long val) {

    if (PRINT)
      System.out.println(" Type Test #"+(++testNum)+"\tTime in ms: "+val);
  }
  static boolean PRINT = false;

}
