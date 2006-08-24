/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

import test.classB;
import test.interfaceB;

/**
 * @author Igor Pechtchanski
 */
public class ResolvedAccess {
  static {
    classB.load();
  }
  public static void main(String[] args) {
    // instance field access
    System.out.print("Instance field: ");
    System.out.flush();
    System.out.println(new classB().bar);
    System.out.flush();

    // static field access
    System.out.print("Static field: ");
    System.out.flush();
    System.out.println(classB.baz);
    System.out.flush();

    // instance method access
    System.out.print("Instance method: ");
    System.out.flush();
    new classB().foo();
    System.out.flush();

    // static method access
    System.out.print("Static method: ");
    System.out.flush();
    classB.fuz();
    System.out.flush();

    // interface method access
    System.out.print("Interface method: ");
    System.out.flush();
    invokeinterface(new classB());
    System.out.flush();
  }
  private static void invokeinterface(interfaceB b) {
    b.fum();
    // make big to avoid inlining!
    if (false) {
      switch (classB.baz) {
        case      1: System.out.println("1"); break;
        case     10: System.out.println("10"); break;
        case    100: System.out.println("100"); break;
        case   1000: System.out.println("1000"); break;
        case  10000: System.out.println("10000"); break;
        case 100000: System.out.println("100000"); break;
        default: break;
      }
    }
  }
}

