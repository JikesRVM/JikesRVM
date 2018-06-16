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
package org.jikesrvm;

import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class MemoryJMXTest {

  private static MemoryMXBean memoryMXBean;

  @BeforeClass
  public static void setupMXBeans() {
    memoryMXBean = ManagementFactory.getMemoryMXBean();
  }

  @Test
  public void queryingMemoryMxBeanDoesntCauseErros() {
    MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
    assertThat(heapMemoryUsage.getInit(), greaterThanOrEqualTo(-1L));
    MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
    assertThat(nonHeapMemoryUsage.getInit(), greaterThanOrEqualTo(-1L));
  }

  @Test
  public void objectPendingFinilizationCountIsNonNegative() throws Exception {
    assertThat(memoryMXBean.getObjectPendingFinalizationCount(), greaterThanOrEqualTo(0));
  }

}
