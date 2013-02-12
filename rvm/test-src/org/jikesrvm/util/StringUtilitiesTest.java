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
package org.jikesrvm.util;

import static org.jikesrvm.util.StringUtilities.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class StringUtilitiesTest {

  public static final String ST = "something.java";

  @Test
  public void testStringToBytesNullTerminated() {
    assertEquals(ST+"\0", asciiBytesToString(stringToBytesNullTerminated(ST)));
  }

  @Test
  public void testStringToBytes() {
    assertEquals(ST, asciiBytesToString(stringToBytes(ST)));
  }

  @Test
  public void testAsciiBytesToString() {
    assertEquals(ST, asciiBytesToString(stringToBytes(ST)));
  }
}
