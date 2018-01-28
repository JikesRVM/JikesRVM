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
package org.jikesrvm.compilers.baseline.ia32;

import static org.hamcrest.CoreMatchers.is;
import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresIA32;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.DynamicBridgeMethodsForTests;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresIA32.class, RequiresBuiltJikesRVM.class})
public class BaselineCompilerImplTest {

  @Test
  public void emptyStaticMethodRequiresThreeWords() throws Exception {
    Class<?>[] noArgs = {};
    NormalMethod emptyStaticMethod = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyStaticMethod);
    assertThat(size, is(3 * WORDSIZE));
  }

  @Test
  public void emptyInstanceMethodRequiresThreeWords() throws Exception {
    Class<?>[] noArgs = {};
    NormalMethod emptyInstanceMethod = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyInstanceMethod);
    assertThat(size, is(3 * WORDSIZE));
  }

  @Test
  public void emptyInstanceMethodWithParamsRequiresThreeWords() throws Exception {
    Class<?>[] args = {Object.class, double.class, int.class, long.class};
    NormalMethod emptyInstanceMethod = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithParams", args);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyInstanceMethod);
    assertThat(size, is(3 * WORDSIZE));
  }

  @Test
  public void printRandomIntegerMethodRequiresSixWords() throws Exception {
    Class<?>[] noArgs = {};
    NormalMethod printRandomIntegerMethod = TestingTools.getNormalMethod(MethodsForTests.class, "printRandomInteger", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(printRandomIntegerMethod);
    assertThat(size, is(6 * WORDSIZE));
  }

  @Test
  public void multiplyByAFewPrimesRequiresFiveWords() throws Exception {
    NormalMethod multiplyByAFewPrimesMethod = TestingTools.getNormalMethod(MethodsForTests.class, "multiplyByAFewPrimes", int.class);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(multiplyByAFewPrimesMethod);
    assertThat(size, is(5 * WORDSIZE));
  }

  @Test
  public void methodWithAFewLocalsRequiresFiveWords() throws Exception {
    Class<?>[] noArgs = {};
    NormalMethod methodWithAFewLocalsMethod = TestingTools.getNormalMethod(MethodsForTests.class, "methodWithAFewLocals", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(methodWithAFewLocalsMethod);
    assertThat(size, is(8 * WORDSIZE));
  }

  @Test
  public void emptyStaticMethodWithBaselineSaveLSRegistersAnnotationRequiresFourWords() throws Exception {
    Class<?>[] noArgs = {};
    NormalMethod emptyStaticMethod = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithBaselineSaveLSRegistersAnnotation", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyStaticMethod);
    assertThat(size, is(4 * WORDSIZE));
  }

  @Test
  public void emptyStaticMethodInClassWithDynamicBridgeRequiresThireenWordsForSSE2_FULLAndX86() throws Exception {
    assumeThat(VM.BuildForSSE2Full, is(true));
    assumeThat(VM.BuildFor32Addr, is(true));
    Class<?>[] noArgs = {};
    NormalMethod emptyStaticMethod = TestingTools.getNormalMethod(DynamicBridgeMethodsForTests.class, "emptyStaticMethodInClassWithDynamicBridge", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyStaticMethod);
    assertThat(size, is(13 * WORDSIZE));
  }

  @Test
  public void emptyStaticMethodInClassWithDynamicBridgeRequiresThirteenWordsForSSE2_FULLAndX64() throws Exception {
    assumeThat(VM.BuildForSSE2Full, is(true));
    assumeThat(VM.BuildFor64Addr, is(true));
    Class<?>[] noArgs = {};
    NormalMethod emptyStaticMethod = TestingTools.getNormalMethod(DynamicBridgeMethodsForTests.class, "emptyStaticMethodInClassWithDynamicBridge", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyStaticMethod);
    assertThat(size, is(9 * WORDSIZE));
  }

  @Test
  public void emptyStaticMethodInClassWithDynamicBridgeRequires32WordsForWhenSSE2_FULLisFalse() throws Exception {
    assumeThat(VM.BuildForSSE2Full, is(false));
    Class<?>[] noArgs = {};
    NormalMethod emptyStaticMethod = TestingTools.getNormalMethod(DynamicBridgeMethodsForTests.class, "emptyStaticMethodInClassWithDynamicBridge", noArgs);
    int size = BaselineCompilerImpl.calculateRequiredSpaceForFrame(emptyStaticMethod);
    assertThat(size, is(32 * WORDSIZE));
  }

}
