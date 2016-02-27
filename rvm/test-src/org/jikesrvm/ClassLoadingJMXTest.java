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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class ClassLoadingJMXTest {

  /** based on a prototype image, with hefty safety margin */
  private static final int MINIMAL_BOOTIMAGE_CLASS_COUNT_ESTIMATE = 800;

  private static ClassLoadingMXBean classLoadingMXBean;

  @BeforeClass
  public static void setupClassLoadingMXBean() {
    classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
  }

  @Test
  public void classloadedCountCountsClasssesInTheBootimage() {
    assertTrue(classLoadingMXBean.getLoadedClassCount() >= MINIMAL_BOOTIMAGE_CLASS_COUNT_ESTIMATE);
  }

  @Test
  public void classCountIncreasesWhenNewClassIsLoaded() throws ClassNotFoundException {
    int loadedClassCount = classLoadingMXBean.getLoadedClassCount();
    Class.forName("org.jikesrvm.ClassForLoadingDuringJMXTesting");
    int newLoadedClassCount = classLoadingMXBean.getLoadedClassCount();
    assertTrue(newLoadedClassCount >= loadedClassCount + 1);
  }

  @Test
  public void classLoadingNumbersAreConsistent() {
    long unloadedClassCount = classLoadingMXBean.getUnloadedClassCount();
    long loadedClassCount = classLoadingMXBean.getLoadedClassCount();
    long totalLoadedClassCount = classLoadingMXBean.getTotalLoadedClassCount();
    long notUnloadedClasses = totalLoadedClassCount - unloadedClassCount;
    assertThat(notUnloadedClasses, is(loadedClassCount));
  }

  @Test
  public void classloadingVerbosityCanBeToggled() {
    // May cause side effects (print outs about loaded classes)
    // but that should be unproblematic because VM outputs via
    // VM.sysWriteln cannot be redirected by tests and thus cannot
    // end up at the wrong place
    boolean isVerbose = classLoadingMXBean.isVerbose();
    boolean newVerbose = !isVerbose;
    classLoadingMXBean.setVerbose(newVerbose);
    assertThat(classLoadingMXBean.isVerbose(), is(newVerbose));
  }

}
