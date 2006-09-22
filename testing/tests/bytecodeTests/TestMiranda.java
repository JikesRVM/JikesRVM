/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
//$Id$
/*
 * @author Ian Rogers
 */

/**
 * This class provides a test of Miranda methods. 
 */
public class TestMiranda {

  /**
   * A simple interface with a method in it
   */
  interface Interface {
    /** A method that can be implemented */
    public int someMethod (int i, int j);
  }

  /**
   * An abstract class implementing the interface, as it's abstract it
   * doesn't need to implement the method thereby creating the
   * abstract Miranda method (in this case called someMethod) that all
   * sub-classes must implement
   */
  abstract static class AbstractClass implements Interface {
    // NB someMethod isn't implemented!
    /** Constructor */
    public AbstractClass () {}
  }

  /**
   * A genuine implementation of AbstractClass that must implement
   * someMethod declared in Interface
   */
  static class ConcreteClass extends AbstractClass {
    /** Implementation of the Miranda method our test will call */
    public int someMethod (int i, int j) {
      return i*j;
    }
  }

  /**
   * Stand alone entry point
   */
  public static void main (String[] args) {
    runTest();
  }

  /**
   * Entry point from TestAll
   */
  public static void runTest() {
    AbstractClass test = new ConcreteClass();
    testSomeClass(test);
  }
  /**
   * Test calling of Miranda method
   * @param test of type AbstractClass, therefore must have
   * implementation of Interface
   */
  public static void testSomeClass (AbstractClass test) {
    SystemOut.println("TestMiranda");
    SystemOut.println("want: 42 \ngot:  " + test.someMethod(6,7));
  }
}
