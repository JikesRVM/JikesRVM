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
package org.jikesrvm.mm.mminterface;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mmtk.plan.Plan.ALLOC_NON_MOVING;
import static org.mmtk.plan.Plan.DEFAULT_SITE;

import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.categories.Category;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class MemoryManagerTest {

  @Test
  public void allocateArrayTestPositive() {
    int size = 20;
    RVMArray arrayType = RVMArray.IntArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    int[] test = (int[]) MemoryManager.allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 DEFAULT_SITE);

    assertEquals(test.length, 20);
  }

  @Test
  public void newTIBTestPositive() {
    TIB test = MemoryManager.newTIB(20, 0);
    assertNotNull(test);
  }

  @Test(expected = OutOfMemoryError.class)
  public void allocateArrayTestNegative() {
    int size = -5;
    RVMArray arrayType = RVMArray.IntArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    int[] test = (int[]) MemoryManager.allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 DEFAULT_SITE);

    fail("FAIL! Created array with length " + test.length);
  }

  @Test(expected = OutOfMemoryError.class)
  public void newTIBTestNegative() {
    MemoryManager.newTIB(-100, 0);
    fail();
  }

  @Test(expected = OutOfMemoryError.class)
  public void allocateArrayTestOverflowToNegative() {
    int size = 1 << 29;
    RVMArray arrayType = RVMArray.IntArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    int[] test = (int[]) MemoryManager.allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 DEFAULT_SITE);

    fail("FAIL! Created array with length " + test.length);
  }

  @Test(expected = OutOfMemoryError.class)
  public void newTIBTestOverflowToNegative() {
    MemoryManager.newTIB(1 << 29, 0);
    fail();
  }

  @Test(expected = OutOfMemoryError.class)
  public void allocateArrayTestOverflowToPositive() {
    int size = 1 << 30;
    RVMArray arrayType = RVMArray.IntArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    int[] test = (int[]) MemoryManager.allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 DEFAULT_SITE);

    fail("FAIL! Created array with length " + test.length);
  }

  @Test(expected = OutOfMemoryError.class)
  public void newTIBTestOverflowToPositive() {
    MemoryManager.newTIB(1 << 30, 0);
    fail();
  }
}
