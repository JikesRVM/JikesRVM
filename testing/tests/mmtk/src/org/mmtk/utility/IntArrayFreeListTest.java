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
import org.junit.Test;
import org.mmtk.harness.Harness;

/**
 * Junit unit-tests for IntArrayFreeList.
 */
public class IntArrayFreeListTest extends FreeListTests {

  private static final int ONE_MEG = 1024 * 1024;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initArchitecture(Arrays.asList("bits=32"));
    Harness.initOnce();
  }

  @Test
  public void testGenericFreeListInt() {
    new IntArrayFreeList(1024);
    new IntArrayFreeList(ONE_MEG);
  }

  @Test
  public void testGenericFreeListIntInt() {
    new IntArrayFreeList(1024,1);
    new IntArrayFreeList(ONE_MEG,2);
  }

  @Test
  public void testGenericFreeListIntIntInt() {
    new IntArrayFreeList(1024,1,1);
    new IntArrayFreeList(ONE_MEG,2,32);
  }

  @Test
  public void testGenericFreeListParent() {
    IntArrayFreeList parent = new IntArrayFreeList(ONE_MEG,2,32);
    new IntArrayFreeList(parent,1);
  }

  @Override
  protected GenericFreeList createFreeList(int units, int grain,
      int heads) {
    return new IntArrayFreeList(units,grain,heads);
  }

  @Override
  @Test
  public void testAlloc5() throws Throwable {
    super.testAlloc5();
  }


}
