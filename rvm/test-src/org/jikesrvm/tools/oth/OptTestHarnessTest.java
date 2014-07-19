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
import org.jikesrvm.classloader.ApplicationClassLoader;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.TypeReference;
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

  // Design
  // TODO use interface for output in OTH to eliminate necessity for
  //  redirection of streams
  // TODO encapsulate VM specific functionality (e.g. class loading, compilers) in
  //  a separate class. After that, check if TestClass1 can be eliminated.

  // Bugs
  // TODO convertToClassName has some bugs and quirks

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
    assertTrue(getErrorOutput().isEmpty());
  }

  @Test
  public void incompleteArgsLeadToErrorMessage() throws Exception {
    String[] incompleteArgs = {"-load"};
    OptTestHarness.main(incompleteArgs);

    String incompleteArgsMsg = "Uncaught ArrayIndexOutOfBoundsException, possibly" +
        " not enough command-line arguments - aborting" + "\n";
    assertThat(getErrorOutput().startsWith(incompleteArgsMsg), is(true));
    removeMessageFromStartOfErrorOutput(incompleteArgsMsg);

    String formatStringMsg = "Format: rvm org.jikesrvm.tools.oth.OptTestHarness { <command> }" + "\n";
    assertThat(getErrorOutput().startsWith(formatStringMsg), is(true));
    removeMessageFromStartOfErrorOutput(formatStringMsg);

    assertThat(getErrorOutput().isEmpty(), is(false));
  }

  private String getErrorOutput() {
    return err.getOutput().toString();
  }

  private void removeMessageFromStartOfErrorOutput(String msg) {
    err.getOutput().replace(0, msg.length(), "");
  }

  @Test
  public void unknownArgsAreIgnored() throws Exception {
    String arg = "-foo";
    String[] unknownArg = {arg};
    OptTestHarness.main(unknownArg);

    String unknownArgMsg = "Unrecognized argument: " + arg + " - ignored" + "\n";
    assertThat(getErrorOutput().startsWith(unknownArgMsg), is(true));
    removeMessageFromStartOfErrorOutput(unknownArgMsg);

    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void canLoadAClassByItsClassname() throws Exception {
    String className = "org.jikesrvm.tools.oth.TestClass1";

    assertClassIsNotLoaded(className);

    String[] loadClass = {"-load", className};
    OptTestHarness.main(loadClass);

    assertClassIsLoaded(className);
    assertThatNoErrorsHaveOccurred();
  }

  private void assertClassIsLoaded(String className) {
    TypeReference tRef = getTypeReferenceForClass(className);
    assertNotNull(tRef.peekType());
  }

  private TypeReference getTypeReferenceForClass(String className) {
    Atom classDescriptorAsAtom = Atom.findAsciiAtom(className).descriptorFromClassName();
    ClassLoader appCl = ApplicationClassLoader.getSystemClassLoader();
    TypeReference tRef = TypeReference.findOrCreate(appCl, classDescriptorAsAtom);
    return tRef;
  }

  @Test
  public void convertToClassNameWorksForDescriptors() throws Exception {
    String result = replacePointsWithSlashes("org.jikesrvm.tools.oth.TestClass2");
    String classDescriptor = "Lorg/jikesrvm/tools/oth/TestClass2;";
    assertThat(OptTestHarness.convertToClassName(classDescriptor),is(result));
  }

  @Test
  public void convertToClassNameWorksForSourceFileNames() throws Exception {
    String className = "TestClass3";
    String classSourcefile = className + ".java";
    assertThat(OptTestHarness.convertToClassName(classSourcefile),is(className));
  }

  @Test
  public void convertToClassNameWorksForClassfileName() throws Exception {
    String className = "TestClass4";
    String classClassFile = className + ".class";
    assertThat(OptTestHarness.convertToClassName(classClassFile),is(className));
  }

  @Test
  public void convertToClassNameWorksForASimplePathString() throws Exception {
    String className = "TestClass5";
    String pathOfClass = "./" + className;
    assertThat(OptTestHarness.convertToClassName(pathOfClass),is(className));
  }

  @Test
  public void convertToClassNameSupportsSenselessCombinations() throws Exception {
    String result = replacePointsWithSlashes("org.jikesrvm.tools.oth.TestClass6");
    String strangeString = "./Lorg/jikesrvm/tools/oth/TestClass6;.class";
    assertThat(OptTestHarness.convertToClassName(strangeString),is(result));
  }

  @Test
  public void convertToClassNameDoesNotWorkForComplexPathstrings() throws Exception {
    String className = "org.jikesrvm.tools.oth.TestClass7";
    String pathOfClass = "../.." + className;
    String result = replacePointsWithSlashes(pathOfClass);
    assertThat(OptTestHarness.convertToClassName(pathOfClass),is(result));
  }

  private String replacePointsWithSlashes(String s) {
    s = s.replace(".", "/");
    return s;
  }

  private void assertClassIsNotLoaded(String className) {
    TypeReference tRef = getTypeReferenceForClass(className);
    assertNull(tRef.peekType());
  }

  private void assertThatNoAdditionalErrorsHaveOccurred() {
    assertTrue(getErrorOutput().isEmpty());
  }

}
