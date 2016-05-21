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
package org.mmtk.utility.heap.layout;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.Address;

public class FragmentedMmapperTest64 extends AbstractFragmentedMmapperTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initArchitecture(Arrays.asList("bits=64","timeout=600"));
    Harness.initOnce();
  }


  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Test
  public void testHash64() throws Throwable {
    assertEquals(0, FragmentedMmapper.hash(Address.fromLong(0x10000000L)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromLong(0x20000000L)));
    assertEquals(1, FragmentedMmapper.hash(Address.fromLong(0x40000000L)));
    assertEquals(2, FragmentedMmapper.hash(Address.fromLong(0x80000000L)));
    assertEquals(4, FragmentedMmapper.hash(Address.fromLong(0x100000000L)));
    assertEquals(8, FragmentedMmapper.hash(Address.fromLong(0x200000000L)));
    assertEquals(16, FragmentedMmapper.hash(Address.fromLong(0x400000000L)));
    assertEquals(32, FragmentedMmapper.hash(Address.fromLong(0x800000000L)));
    assertEquals(4570, FragmentedMmapper.hash(Address.fromLong(0xabcd1234c0000000L)));
    assertEquals(4570, FragmentedMmapper.hash(Address.fromLong(0xabcd1234c0000000L)));
    assertEquals(4570, FragmentedMmapper.hash(Address.fromLong(0xabcd1234c0000000L)));
    assertEquals(4570, FragmentedMmapper.hash(Address.fromLong(0xabcd1234c0000000L)));
    assertEquals(4554, FragmentedMmapper.hash(Address.fromLong(0xbbcd1234c0000000L)));
    assertEquals(4522, FragmentedMmapper.hash(Address.fromLong(0xbbce1234c0000000L)));
    assertEquals(4524, FragmentedMmapper.hash(Address.fromLong(0xbbce2234c0000000L)));
    assertEquals(4526, FragmentedMmapper.hash(Address.fromLong(0xbbce3234c0000000L)));
    assertEquals(4512, FragmentedMmapper.hash(Address.fromLong(0xbbce4234c0000000L)));
    assertEquals(4514, FragmentedMmapper.hash(Address.fromLong(0xbbce5234c0000000L)));
    assertEquals(4516, FragmentedMmapper.hash(Address.fromLong(0xbbce6234c0000000L)));
    assertEquals(4518, FragmentedMmapper.hash(Address.fromLong(0xbbce7234c0000000L)));
    assertEquals(4536, FragmentedMmapper.hash(Address.fromLong(0xbbce8234c0000000L)));
  }


}
