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
package test.org.jikesrvm.basic.core.bytecode;

import java.io.Serializable;

class TestInstanceOf {

  static class Science {}
  static class Magic extends Science {}

  public static void main(String[] args) {
    runTest("Magic()", new Magic());
    runTest("Magic[2]", new Magic[2]);
    runTest("Object[][]{new Magic[4],new Magic[4]}", new Object[][]{new Magic[4], new Magic[4]});
    runTest("Magic[][]{new Magic[4],new Magic[4]}", new Magic[][]{new Magic[4], new Magic[4]});
    runTest("int[2]", new int[2]);
  }

  private static void runTest(final String name, final Object x3) {
    System.out.println("Testing " + name + " - instanceof: ");
    testInstanceOf(x3);
    System.out.println(" casts: ");
    testCasts(x3);
    System.out.println();
  }

  @SuppressWarnings({"UnusedDeclaration", "RedundantCast"})
  private static void testCasts(final Object x) {
    try { final Object o = (Object) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Science o = (Science) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Magic o = (Magic) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Object[] o = (Object[]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Magic[] o = (Magic[]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Science[] o = (Science[]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Object[][] o = (Object[][]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Magic[][] o = (Magic[][]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Science[][] o = (Science[][]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final int[] o = (int[]) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Serializable o = (Serializable) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final Cloneable o = (Cloneable) x; success(); } catch (final ClassCastException cce) { failure(); }
  }

  private static void failure() {System.out.print("0");}

  private static void success() {System.out.print("1");}

  @SuppressWarnings({"ConstantConditions"})
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

  @SuppressWarnings({"UnusedDeclaration"})
  private static void io(final Class type, final boolean test) {
    System.out.print(test ? "1" : "0");
  }
}
