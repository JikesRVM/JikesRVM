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
package org.jikesrvm.tests.util;

import org.jikesrvm.util.Services;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;

/**
 * Provides methods in the spirit of JUnit assert methods. The actual
 * JUnit methods can't be used for unboxed types because unboxed types
 * aren't objects.
 */
public final class AssertUnboxed {

  private AssertUnboxed() {
    // no instantiation
  }

  public static void assertZero(Address actual) {
    if (!actual.isZero()) {
      String actString = Services.unboxedValueString(actual);
      throw new AssertionError("Expected 0 but was " +
          actString + "!");
    }
  }

  public static void assertEquals(Address expected, Address actual) {
    if (!expected.EQ(actual)) {
      String expString = Services.unboxedValueString(expected);
      String actString = Services.unboxedValueString(actual);
      throw new AssertionError("Expected " + expString + " but was " +
          actString + "!");
    }
  }

  public static void assertEquals(Word expected, Word actual) {
    if (!expected.EQ(actual)) {
      String expString = Services.unboxedValueString(expected);
      String actString = Services.unboxedValueString(actual);
      throw new AssertionError("Expected " + expString + " but was " +
          actString + "!");
    }
  }

}

