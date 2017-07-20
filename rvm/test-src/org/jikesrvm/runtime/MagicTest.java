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
package org.jikesrvm.runtime;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.jikesrvm.compilers.common.CompiledMethod.*;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Test cases for {@link Magic} that either need high-level operations to test or aren't required for
 * correct operation of the VM. Most tests for methods from Magic should be written for TestMagic from
 * the basic test run. In contrast to unit tests run on the built RVM image, the basic test run doesn't
 * assume a mostly working VM.
 */
@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class MagicTest {

  private NormalMethod testMethodForGetCompilerLevel;

  @After
  public void invalidateCompiledMethods() {
    testMethodForGetCompilerLevel.invalidateCompiledMethod(testMethodForGetCompilerLevel.getCurrentCompiledMethod());
  }

  @Test
  public void getCompilerLevelMapsToTheCorrectConstantsForBaseline() throws Exception {
    testMethodForGetCompilerLevel = TestingTools.getNormalMethod(MagicTest.class, "getCompilerLevel");
    CompiledMethod cm = RuntimeCompiler.baselineCompile(testMethodForGetCompilerLevel);
    assertThat(cm.getCompilerType(), is(BASELINE));
    assertThat(getCompilerLevel(), is(-1));
  }

  public void assertThatGetCompilerLevelisCorrectForOptLevel(int optLevel) {
    recompileTestMethodOnOptLevel(optLevel);
    assertThat(getCompilerLevel(), is(optLevel));
  }

  public void recompileTestMethodOnOptLevel(int optLevel) {
    int methodId = RuntimeCompiler.recompileWithOpt(testMethodForGetCompilerLevel, optLevel);
    assertThat(methodId, not(equalTo(-1)));
    CompiledMethod cm = testMethodForGetCompilerLevel.getCurrentCompiledMethod();
    assertThat(cm.getCompilerType(), is(OPT));
    assertThat(((OptCompiledMethod)cm).getOptLevel(), is(optLevel));
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void getCompilerLevelMapsToTheCorrectConstantsForOpt() throws Exception {
    testMethodForGetCompilerLevel = TestingTools.getNormalMethod(MagicTest.class, "getCompilerLevel");
    assertThatGetCompilerLevelisCorrectForOptLevel(0);
    assertThatGetCompilerLevelisCorrectForOptLevel(1);
    assertThatGetCompilerLevelisCorrectForOptLevel(2);
  }

  public static int getCompilerLevel() {
    return Magic.getCompilerLevel();
  }

}
