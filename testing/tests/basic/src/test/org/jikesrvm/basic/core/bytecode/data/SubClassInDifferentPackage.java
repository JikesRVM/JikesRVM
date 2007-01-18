package test.org.jikesrvm.basic.core.bytecode.data;

import test.org.jikesrvm.basic.core.bytecode.TestClassHierarchy;

/**
 * Used to test subclassing across packages - including with inner classes.
 */
public class SubClassInDifferentPackage extends TestClassHierarchy {

  static public class P_B extends A {
    public void magic() { System.out.println("invoke magic P_B"); }
  }

  static public class P_C1 extends B {
    public void magic() { System.out.println("invoke magic P_C1"); }
  }

  static public class P_C2 extends P_B {
    public void magic() { System.out.println("invoke magic P_C2"); }
  }

  static public class P_D extends A {
    public void magic() { System.out.println("invoke magic P_D"); }
  }

  public class P_E1 extends D {
    public void magic() { System.out.println("invoke magic P_E1"); }
  }

  public class P_E2 extends P_D {
    public void magic() { System.out.println("invoke magic P_E2"); }
  }

  public class P_F extends A {
    public void magic() { System.out.println("invoke magic P_F"); }
  }

  public class P_G1 extends F {
    public void magic() { System.out.println("invoke magic P_G1"); }
  }

  public class P_G2 extends P_F {
    public void magic() { System.out.println("invoke magic P_G2"); }
  }

  public class P_H implements Magic {
    public void magic() { System.out.println("invoke magic P_H"); }
  }

  public class P_I1 extends H {
    public void magic() { System.out.println("invoke magic P_I1"); }
  }

  public class P_I2 extends P_H {
    public void magic() { System.out.println("invoke magic P_I2"); }
  }

  public class P_J1 extends I {
    public void magic() { System.out.println("invoke magic P_J1"); }
  }

  public class P_J2 extends P_I1 {
    public void magic() { System.out.println("invoke magic P_J2"); }
  }

  public class P_J3 extends P_I2 {
    public void magic() { System.out.println("invoke magic P_J3"); }
  }
}
