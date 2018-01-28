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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.baseline.BaselineOptions;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresLackOfOptCompiler;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import static org.hamcrest.CoreMatchers.*;

@Category(RequiresBuiltJikesRVM.class)
@RunWith(VMRequirements.class)
public class BootImageCompilerTest {

  private static final String METHOD_NAME = "foobar";
  private BaselineOptions baselineOptions;

  @Before
  public void saveCompilerOptions() {
    baselineOptions = BaselineCompiler.options.dup();
  }

  @After
  public void restoreCompilerOptions() {
    BaselineCompiler.options = baselineOptions;
  }

  @Test
  @Category(RequiresLackOfOptCompiler.class)
  public void initializingBaselineCompilerOptionsInBaselineOnlyBuildsWorks() {
    String[] args = {"invocation_counters=true"};
    assertThat(BaselineCompiler.options.INVOCATION_COUNTERS, is(false));
    BootImageCompiler.init(args);
    assertThat(BaselineCompiler.options.INVOCATION_COUNTERS, is(true));
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void initializingBaselineCompilerOptionsInAdaptiveBuildsWorks() {
    assumeThat(VM.BuildWithBaseBootImageCompiler, is(true));
    String[] args = {"method_to_print=" + METHOD_NAME};
    assertThat(BaselineCompiler.options.fuzzyMatchMETHOD_TO_PRINT(METHOD_NAME), is(false));
    BootImageCompiler.init(args);
    assertThat(BaselineCompiler.options.fuzzyMatchMETHOD_TO_PRINT(METHOD_NAME), is(true));
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void initializingBaselineCompilerOptionsInOptBuildsWorks() {
    assumeThat(VM.BuildWithBaseBootImageCompiler, is(false));
    String[] args = {"method_to_print=" + METHOD_NAME};
    assertThat(BaselineCompiler.options.fuzzyMatchMETHOD_TO_PRINT(METHOD_NAME), is(false));
    BootImageCompiler.init(args);
    assertThat(BaselineCompiler.options.fuzzyMatchMETHOD_TO_PRINT(METHOD_NAME), is(true));
  }

}
