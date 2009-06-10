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
package test.org.jikesrvm.basic.core.bytecode.data;

import test.org.jikesrvm.basic.core.bytecode.TestClassHierarchy;

/**
 * Used to test subclassing across packages - including with inner classes.
 */
public class SubClassInDifferentPackage extends TestClassHierarchy {

  public static class P_B extends A {
    public void magic() { System.out.print("P_B"); }
  }

  public static class P_C1 extends B {
    public void magic() { System.out.print("P_C1"); }
  }

  public static class P_C2 extends P_B {
    public void magic() { System.out.print("P_C2"); }
  }

  public static class P_D extends A {
    public void magic() { System.out.print("P_D"); }
  }

  public class P_E1 extends D {
    public void magic() { System.out.print("P_E1"); }
  }

  public class P_E2 extends P_D {
    public void magic() { System.out.print("P_E2"); }
  }

  public class P_F extends A {
    public void magic() { System.out.print("P_F"); }
  }

  public class P_G1 extends F {
    public void magic() { System.out.print("P_G1"); }
  }

  public class P_G2 extends P_F {
    public void magic() { System.out.print("P_G2"); }
  }

  public class P_H implements Magic {
    public void magic() { System.out.print("P_H"); }
  }

  public class P_I1 extends H {
    public void magic() { System.out.print("P_I1"); }
  }

  public class P_I2 extends P_H {
    public void magic() { System.out.print("P_I2"); }
  }

  public class P_J1 extends I {
    public void magic() { System.out.print("P_J1"); }
  }

  public class P_J2 extends P_I1 {
    public void magic() { System.out.print("P_J2"); }
  }

  public class P_J3 extends P_I2 {
    public void magic() { System.out.print("P_J3"); }
  }
}
