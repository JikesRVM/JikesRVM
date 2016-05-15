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
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.tests.BaseMMTkTest;
import org.vmmagic.unboxed.Address;

public class AbstractFragmentedMmapperTest extends BaseMMTkTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testFragmentedMmapper() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        new FragmentedMmapper();
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testHash() throws Throwable {
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00000000)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00000001)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00000010)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00000100)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00001000)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00010000)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x00100000)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x01000000)));
    assertEquals(0, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x20000000)));
    assertEquals(1, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x40000000)));
    assertEquals(2, FragmentedMmapper.hash(Address.fromIntZeroExtend(0x80000000)));
    assertEquals(3, FragmentedMmapper.hash(Address.fromIntZeroExtend(0xc0000000)));
  }

  @Test
  public void testSlabIndex() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        FragmentedMmapper m = new FragmentedMmapper();
        for (int i = 0; i < 16; i++) {
          assertNotNull(m.slabTable(Address.fromIntZeroExtend(0x10000000 * i)));
        }
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testEnsureMapped1() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        Mmapper m = new FragmentedMmapper();
        Address base = Address.fromIntZeroExtend(0x40000000);
        m.ensureMapped(base, 1);
        base.loadWord();
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testEnsureMapped2() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        Mmapper m = new FragmentedMmapper();
        Address base = Address.fromIntZeroExtend(0x80000000);

        m.ensureMapped(base.minus(4096), 2);
        base.minus(4096).loadWord();
        base.loadWord();
        base.plus(4095).loadWord();
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testEnsureMapped3() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        Mmapper m = new FragmentedMmapper();
        Address base = Address.fromIntZeroExtend(0xc0000000).minus(4096 * 2);

        m.ensureMapped(base, 7);
        for (int i = 0; i < 7; i++) {
          base.plus(4096 * i).loadWord();
          base.plus(4096 * i + 4095).loadWord();
        }
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testEnsureMapped4() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        Mmapper m = new FragmentedMmapper();
        Address base = Address.fromIntZeroExtend(0x68cfb000);

        m.ensureMapped(base, 23);
        for (int i = 0; i < 23; i++) {
          Address page = base.plus(4096 * i);
          page.loadWord();
          page.plus(4095).loadWord();
        }
      }
    };
    runMMTkThread(t);
  }


}
