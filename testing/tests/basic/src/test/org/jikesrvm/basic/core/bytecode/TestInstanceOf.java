/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package test.org.jikesrvm.basic.core.bytecode;

import java.io.Serializable;

/**
 * @author Peter Donald
 */
class TestInstanceOf {

  static class Science {}
  static class Magic extends Science {}

  public static void main(String args[]) {

    runTest("Magic()", new Magic());
    runTest("Magic[2]", new Magic[2]);
    runTest("Object[][]{new Magic[4],new Magic[4]}", new Object[][]{new Magic[4],new Magic[4]});
    runTest("Magic[][]{new Magic[4],new Magic[4]}", new Magic[][]{new Magic[4],new Magic[4]});
    runTest("int[2]", new int[2]);
  }

  private static void runTest(final String name, final Object x3) {
    System.out.println("Testing new " + name);
    testInstanceOf(x3);
    testCasts(x3);
  }

  private static void testCasts(final Object x) {
    System.out.print("Cast to Object: ");
    try { final Object o = (Object) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Science: ");
    try { final Science o = (Science) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Magic: ");
    try { final Magic o = (Magic) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Object[]: ");
    try { final Object[] o = (Object[]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Magic[]: ");
    try { final Magic[] o = (Magic[]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Science[]: ");
    try { final Science[] o = (Science[]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Object[][]: ");
    try { final Object[][] o = (Object[][]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Magic[][]: ");
    try { final Magic[][] o = (Magic[][]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Science[][]: ");
    try { final Science[][] o = (Science[][]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to int[]: ");
    try { final int[] o = (int[]) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Serializable: ");
    try { final Serializable o = (Serializable) x; success(); }
    catch (final ClassCastException cce) { failure(); }
    System.out.print("Cast to Cloneable: ");
    try { final Cloneable o = (Cloneable) x; success(); }
    catch (final ClassCastException cce) { failure(); }
  }

  private static void failure() {System.out.println("Failed");}

  private static void success() {System.out.println("Succeeded");}

  private static void testInstanceOf(final Object x) {
    io(Object.class, (x instanceof Object));
    io(Science.class, (x instanceof Science));
    io(Magic.class, (x instanceof Magic));
    io(Object[].class, (x instanceof Object[]));
    io(Science[].class, (x instanceof Science[]));
    io(Magic[].class, (x instanceof Magic[]));
    io(Object[][].class, (x instanceof Object[][]));
    io(Science[][].class, (x instanceof Science[][]));
    io(Magic[][].class, (x instanceof Magic[][]));
    io(int[].class, (x instanceof int[]));
    io(Serializable.class, (x instanceof Serializable));
    io(Cloneable.class, (x instanceof Cloneable));
  }

  private static void io(final Class type, final boolean test) {
    System.out.println("instanceof " + type.getName() + " = " + test);
  }
}
