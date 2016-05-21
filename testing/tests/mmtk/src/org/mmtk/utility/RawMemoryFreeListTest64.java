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
package org.mmtk.utility;

import java.util.Arrays;

import org.junit.BeforeClass;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.Address;

public class RawMemoryFreeListTest64 extends RawMemoryFreeListTest {

  private static final long TEST_BASE = 0xF0000000L;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initArchitecture(Arrays.asList("bits=64"));
    Harness.initOnce();
    baseAddress = Address.fromLong(TEST_BASE);
  }

}
