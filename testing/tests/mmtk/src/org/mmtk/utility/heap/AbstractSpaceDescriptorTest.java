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
package org.mmtk.utility.heap;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.tests.BaseMMTkTest;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.layout.VMLayoutConstants;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

public abstract class AbstractSpaceDescriptorTest extends BaseMMTkTest {

  private static final boolean VERBOSE = false;

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testCreateDescriptorAddressAddress() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        Extent chunkSize = Extent.fromLong(1L << VMLayoutConstants.LOG_BYTES_IN_CHUNK);
        Extent increment = chunkSize.toWord().rshl(2).toExtent();
        Extent size = chunkSize;
        Address base = Address.zero().plus(chunkSize);
        while (size.LE(VMLayoutConstants.MAX_SPACE_EXTENT)) {
          int d = SpaceDescriptor.createDescriptor(base, base.plus(size));

          for (Address addr = base; addr.LT(base.plus(size)); addr = addr.plus(increment)) {
            if (VERBOSE) System.out.printf("Testing address %s in space (%s,%s)%n", addr, base, base.plus(size));
            Assert.assertTrue("addr " + addr + ", bounds(" + base + "," +
                            base.plus(size) + ")", Space.isInSpace(d, addr));
          }
          Assert.assertFalse(Space.isInSpace(d, base.minus(4)));
          Assert.assertFalse(Space.isInSpace(d, base.plus(size)));
          size = size.plus(size);
        }
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testCreateDescriptor() {
    SpaceDescriptor.createDescriptor();
  }

  @Test
  public void testIsContiguous() {
    Extent chunkSize = Extent.fromLong(1L << VMLayoutConstants.LOG_BYTES_IN_CHUNK);
    Address base = Address.zero().plus(chunkSize);
    int d = SpaceDescriptor.createDescriptor();
    int c = SpaceDescriptor.createDescriptor(base, base.plus(chunkSize));
    Assert.assertFalse(SpaceDescriptor.isContiguous(d));
    Assert.assertTrue(SpaceDescriptor.isContiguous(c));
  }

  @Test
  public void testIsContiguousHi() {
    fail("Not yet implemented");
  }

  @Test
  public void testGetStart() {
    fail("Not yet implemented");
  }

  @Test
  public void testGetChunks() {
    fail("Not yet implemented");
  }

}
