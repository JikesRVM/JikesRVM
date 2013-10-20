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
package org.jikesrvm.tools.oth;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;
import static org.hamcrest.CoreMatchers.*;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

import org.jikesrvm.VM;
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.StringBuilderOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class OptTestHarnessTest {

  //TODO use interface for output in OTH to eliminate necessity for
  // redirection of streams

  private static final String lineEnd = System.getProperty("line.separator");

  private static final PrintStream standardSysOut = System.out;
  private static final PrintStream standardSysErr = System.err;

  private static final String baselineMethodCompileString = "Compiling X methods baseline" + lineEnd;
  private static final String optMethodCompileString = "Compiling X methods opt" + lineEnd;

  private StringBuilderOutputStream out;
  private StringBuilderOutputStream err;

  @Before
  public void redirectStandardStreams() {
    out = new StringBuilderOutputStream();
    PrintStream newSysOut = new PrintStream(out);
    System.setOut(newSysOut);

    err = new StringBuilderOutputStream();
    PrintStream newSysErr = new PrintStream(err);
    System.setErr(newSysErr);
  }

  @After
  public void cleanup() {
    resetStandardStreams();
  }

  private void resetStandardStreams() {
    System.setOut(standardSysOut);
    System.setErr(standardSysErr);
  }

  @Test
  public void doesNotCrashWhenNoOptCompilerIsAvailabe() throws InvocationTargetException, IllegalAccessException{
    assumeThat(VM.BuildForOptCompiler, is(false));
    String[] emptyArgs = {};
    OptTestHarness.main(emptyArgs);
  }

  @Test
  public void emptyArgsLeadToNoCompiles() throws InvocationTargetException, IllegalAccessException {
    String[] emptyArgs = new String[0];
    OptTestHarness.main(emptyArgs);

    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoUnexpectedOutputRemains();
    assertThatNoErrorsHaveOccurred();
  }

  private void assertNumberOfCompiledMethodsIsCorrect(String s, int i) {
    String output = out.getOutput().toString();
    int outputLength = output.length();

    String expected = s.replace('X', Integer.toString(i).charAt(0));
    int expectedLength = expected.length();

    int substringEnd = (expectedLength > outputLength) ? outputLength : expectedLength;
    String outputStart = output.substring(0, substringEnd);

    assertEquals(expected, outputStart);
    out.getOutput().replace(0, expectedLength, "");
  }

  private void assertThatNumberOfBaselineCompiledMethodsIs(int i) {
    assertNumberOfCompiledMethodsIsCorrect(baselineMethodCompileString, i);
  }

  private void assertThatNumberOfOptCompiledMethodsIs(int i) {
    assertNumberOfCompiledMethodsIsCorrect(optMethodCompileString, i);
  }

  private void assertThatNoUnexpectedOutputRemains() {
    assertTrue(out.getOutput().toString().trim().isEmpty());
  }

  private void assertThatNoErrorsHaveOccurred() {
    assertTrue(err.getOutput().toString().isEmpty());
  }

}
