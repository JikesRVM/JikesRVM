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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.ApplicationClassLoader;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.StringBuilderOutputStream;
import org.jikesrvm.tests.util.TestingTools;
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
  // TODO testing -longcommandline currently requires creating a file

  // Bugs
  // TODO convertToClassName has some bugs and quirks

  // Tests
  // TODO some error cases are still missing tests
  // TODO "-disableClassloading" has no tests

  private static final String lineEnd = System.getProperty("line.separator");

  private static final PrintStream standardSysOut = System.out;
  private static final PrintStream standardSysErr = System.err;

  private static final String baselineMethodCompileString = "Compiling X methods baseline" + lineEnd;
  private static final String optMethodCompileString = "Compiling X methods opt" + lineEnd;

  private static final String EMPTY_CLASS = "org.jikesrvm.tools.oth.EmptyClass";
  private static final String ABSTRACT_CLASS_WITH_EMPTY_METHOD = "org.jikesrvm.tools.oth.AbstractClassWithEmptyMethod";
  private static final String CLASS_WITH_STATIC_METHOD = "org.jikesrvm.tools.oth.ClassWithStaticMethod";
  private static final String CLASS_OVERLOADED_METHODS = "org.jikesrvm.tools.oth.ClassWithOverloadedMethods";
  private static final String CLASS_WITH_MAIN_METHOD = "org.jikesrvm.tools.oth.ClassWithMainMethod";


  private StringBuilderOutputStream out;
  private StringBuilderOutputStream err;

  private File f;

  private FileWriter fw;

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
  public void doesNotCrashWhenNoOptCompilerIsAvailable() throws InvocationTargetException, IllegalAccessException{
    assumeThat(VM.BuildForOptCompiler, is(false));
    String[] emptyArgs = {};
    executeOptTestHarness(emptyArgs);
  }

  private OptTestHarness executeOptTestHarness(String[] commandLineArguments)
      throws InvocationTargetException, IllegalAccessException {
    OptTestHarness oth = new OptTestHarness();
    oth.mainMethod(commandLineArguments);
    return oth;
  }

  @Test
  public void emptyArgsLeadToNoCompiles() throws InvocationTargetException, IllegalAccessException {
    String[] emptyArgs = new String[0];
    executeOptTestHarness(emptyArgs);

    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  private void assertNumberOfCompiledMethodsIsCorrect(String s, int i) {
    String expected = s.replace("X", Integer.toString(i));
    int expectedLength = expected.length();
    int startIndex = getStandardOutput().indexOf(expected);
    assertThat(startIndex, not(is(-1)));
    getStandardStream().replace(startIndex, startIndex + expectedLength, "");
  }

  private void assertThatNumberOfBaselineCompiledMethodsIs(int i) {
    assertNumberOfCompiledMethodsIsCorrect(baselineMethodCompileString, i);
  }

  private void assertThatNumberOfOptCompiledMethodsIs(int i) {
    assertNumberOfCompiledMethodsIsCorrect(optMethodCompileString, i);
  }

  @Test
  public void incompleteArgsLeadToErrorMessage() throws Exception {
    String[] incompleteArgs = {"-load"};
    executeOptTestHarness(incompleteArgs);

    String incompleteArgsMsg = "Uncaught ArrayIndexOutOfBoundsException, possibly" +
        " not enough command-line arguments - aborting" + lineEnd;
    assertThat(getErrorOutput().startsWith(incompleteArgsMsg), is(true));
    removeMessageFromErrorOutput(incompleteArgsMsg);

    String formatStringMsg = "Format: rvm org.jikesrvm.tools.oth.OptTestHarness { <command> }" + lineEnd;
    assertThat(getErrorOutput().startsWith(formatStringMsg), is(true));
    removeMessageFromErrorOutput(formatStringMsg);

    assertThat(getErrorOutput().isEmpty(), is(false));
  }

  private StringBuilder getStandardStream() {
    return out.getOutput();
  }

  private String getStandardOutput() {
    return getStandardStream().toString();
  }

  private String getErrorOutput() {
    return err.getOutput().toString();
  }

  private void removeMessageFromErrorOutput(String msg) {
    int index = err.getOutput().indexOf(msg);
    assertThat(index, not(is(-1)));
    err.getOutput().replace(index, index + msg.length(), "");
  }

  @Test
  public void singleUnknownArgIsIgnored() throws Exception {
    String arg = "-foo";
    String[] unknownArg = {arg};
    executeOptTestHarness(unknownArg);
    assertThatErrorForUnrecognizedArgumentOccurred(arg);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  private void assertThatErrorForUnrecognizedArgumentOccurred(
      String unrecognizedArgument) {
    String unknownArgMsg = "Unrecognized argument: " + unrecognizedArgument + " - ignored" + lineEnd;
    assertThat(getErrorOutput().startsWith(unknownArgMsg), is(true));
    removeMessageFromErrorOutput(unknownArgMsg);
  }

  @Test
  public void allUnknownArgsAreIgnored() throws Exception {
    String firstUnknownArg = "-foo";
    String secondUnknownArg = "-bar";
    String[] multipleUnknownArgs = {firstUnknownArg, secondUnknownArg};
    executeOptTestHarness(multipleUnknownArgs);

    assertThatErrorForUnrecognizedArgumentOccurred(firstUnknownArg);
    assertThatErrorForUnrecognizedArgumentOccurred(secondUnknownArg);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void knownArgumentsAreProcessedEvenIfUnknownArgumentsAppear() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String unknownArgument = "-foo";
    String[] arguments = {unknownArgument, "+baseline"};
    OptTestHarness oth = executeOptTestHarness(arguments);
    assertThatErrorForUnrecognizedArgumentOccurred(unknownArgument);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(oth.useBaselineCompiler, is(true));
  }

  @Test
  public void canLoadAClassByItsClassname() throws Exception {
    String className = EMPTY_CLASS;

    assertClassIsNotLoaded(className);

    String[] loadClass = {"-load", className};
    executeOptTestHarness(loadClass);

    assertClassIsLoaded(className);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  private void assertThatRemainingOutputIsEmptyWhenTrimmed() {
    assertThat(getStandardOutput().trim(), equalTo(""));
  }

  private void assertClassIsLoaded(String className) {
    TypeReference tRef = getTypeReferenceForClass(className);
    assertNotNull(tRef.peekType());
  }

  private TypeReference getTypeReferenceForClass(String className) {
    Atom classDescriptorAsAtom = Atom.findAsciiAtom(className).descriptorFromClassName();
    ClassLoader appCl = ApplicationClassLoader.getSystemClassLoader();
    return TypeReference.findOrCreate(appCl, classDescriptorAsAtom);
  }

  @Test
  public void loadClassThatDoesNotExist() throws Exception {
    String className = "com.ibm.jikesrvm.DoesNotExist";
    assertClassIsNotLoaded(className);
    String[] loadClass = {"-load", className};
    executeOptTestHarness(loadClass);
    String classNotFoundFirstLineStart = "java.lang.ClassNotFoundException: ";
    String classNotFoundFirstLineEnd = " not found in SystemAppCL";
    String firstLineOfStackTrace = classNotFoundFirstLineStart + className + classNotFoundFirstLineEnd;
    assertThat(getErrorOutput().startsWith(firstLineOfStackTrace), is(true));
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
    return s.replace(".", "/");
  }

  private void assertClassIsNotLoaded(String className) {
    TypeReference tRef = getTypeReferenceForClass(className);
    assertNull(tRef.peekType());
  }

  private void assertThatNoAdditionalErrorsHaveOccurred() {
    assertThat(getErrorOutput(), equalTo(""));
  }

  @Test
  public void methodsAreOptCompiledByDefault() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] compileClass = {"-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD};
    executeOptTestHarness(compileClass);

    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(2);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    NormalMethod constructor = TestingTools.getNoArgumentConstructor(testClass2);
    assertThatOutputContainsMessageForCompiledMethod(constructor);
    removeCompiledMethodMessageFromStandardOutput(constructor);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  private void removeCompiledMethodMessageFromStandardOutput(NormalMethod nm) {
    String compiledMethodMessage = OptTestHarness.compiledMethodMessage(nm);
    removeMessageFromStandardOutput(compiledMethodMessage);
  }

  private void removeMessageFromStandardOutput(String msg) {
    int index = out.getOutput().indexOf(msg);
    out.getOutput().replace(index, index + msg.length(), "");
  }

  private void assertThatOutputContainsMessageForCompiledMethod(
      NormalMethod normalMethod) {
    String messageForCompiledMethod = OptTestHarness.compiledMethodMessage(normalMethod);
    assertThat(getStandardOutput().contains(messageForCompiledMethod), is(true));
  }

  @Test
  public void methodsAreBaselineCompiledIfRequested() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] compileClass = {"+baseline", "-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD};
    executeOptTestHarness(compileClass);
    assertThatBothMethodsOfTestClass2HaveBeenBaselineCompiled();
  }

  @Test
  public void defaultCompilerIsBaselineWhenVMIsBuildWithoutOptCompiler() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(false));
    String[] compileClass = {"-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD};
    executeOptTestHarness(compileClass);
    assertThatBothMethodsOfTestClass2HaveBeenBaselineCompiled();
  }

  private void assertThatBothMethodsOfTestClass2HaveBeenBaselineCompiled()
      throws ClassNotFoundException, Exception {
    assertThatNumberOfBaselineCompiledMethodsIs(2);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    NormalMethod constructor = TestingTools.getNoArgumentConstructor(testClass2);
    assertThatOutputContainsMessageForCompiledMethod(constructor);
    removeCompiledMethodMessageFromStandardOutput(constructor);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void baselineCompilationCanBeSwitchedOnAndOff() throws Exception {
    String[] args = { "+baseline" };
    OptTestHarness oth = executeOptTestHarness(args);
    assertThat(oth.useBaselineCompiler, is(true));
    String[] useOpt = { "+baseline" , "-baseline" };
    oth = executeOptTestHarness(useOpt);
    assertThat(oth.useBaselineCompiler, is(false));
  }

  @Test
  public void methodsAreOptCompiledByDefaultWhenSingleMethodIsCompiled() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] compileClass = {"-method", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "emptyMethod", "-"};
    executeOptTestHarness(compileClass);

    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(1);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void methodsWillBeBaselineCompiledIfRequested() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] compileClass = {"-methodBase", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "emptyMethod", "-"};
    executeOptTestHarness(compileClass);

    assertThatNumberOfBaselineCompiledMethodsIs(1);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void methodsWillOptCompiledIfRequested() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] compileClass = {"+baseline", "-methodOpt", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "emptyMethod", "-"};
    executeOptTestHarness(compileClass);

    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(1);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void defaultCompilerForMethodsIsBaselineWhenVMIsBuildWithoutOptCompiler() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(false));
    String[] compileClass = {"-method", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "emptyMethod", "-"};
    executeOptTestHarness(compileClass);

    assertThatNumberOfBaselineCompiledMethodsIs(1);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> testClass2 = Class.forName(ABSTRACT_CLASS_WITH_EMPTY_METHOD);
    NormalMethod emptyMethod = TestingTools.getNormalMethod(testClass2, "emptyMethod");
    assertThatOutputContainsMessageForCompiledMethod(emptyMethod);
    removeCompiledMethodMessageFromStandardOutput(emptyMethod);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void complainsWhenMethodWithNameIsNotFound() throws Exception {
    String wrongMethodName = "printWithWrongName";
    String[] compilePrintIntMethod = {"-method", CLASS_OVERLOADED_METHODS, wrongMethodName, "-"};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "No method named " + wrongMethodName + " found in class " +
        CLASS_OVERLOADED_METHODS + lineEnd + "WARNING: Skipping method " + CLASS_OVERLOADED_METHODS + "." +
        wrongMethodName + lineEnd;
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void complainsWhenMethodWithDescriptorIsNotFound() throws Exception {
    String wrongDescriptor = "(BBB)I";
    String method = "print";
    String[] compilePrintIntMethod = {"-method", CLASS_OVERLOADED_METHODS, method, wrongDescriptor};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "No method matching " + method + " " + wrongDescriptor + " found in class " +
        CLASS_OVERLOADED_METHODS + lineEnd + "WARNING: Skipping method " + CLASS_OVERLOADED_METHODS + "." +
        method + lineEnd;
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void methodOptionsCanDistinguishMethodsBasedOnDescriptors() throws Exception {
    String[] compilePrintIntMethod = {"-methodBase", CLASS_OVERLOADED_METHODS, "print", "(I)V"};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(1);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> classWithOverloadedMethods = Class.forName(CLASS_OVERLOADED_METHODS);
    NormalMethod printMethod = TestingTools.getNormalMethod(classWithOverloadedMethods, "print", int.class);
    assertThatOutputContainsMessageForCompiledMethod(printMethod);
    removeCompiledMethodMessageFromStandardOutput(printMethod);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
  }

  @Test
  public void testHarnessCanExecuteStaticMethods() throws Exception {
    String[] executeMethod = {"-er", CLASS_WITH_STATIC_METHOD, "printMessage", "-"};
    executeOptTestHarness(executeMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(getStandardOutput().contains(ClassWithStaticMethod.PRINTOUT), is(true));
  }

  @Test
  public void methodsCompiledUsingExecutedAndRunAreNotCounted() throws Exception {
    String[] executeAndRun = {"-er", CLASS_WITH_STATIC_METHOD, "printMessage", "-"};
    executeOptTestHarness(executeAndRun);

    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    Class<?> testClass3 = Class.forName(CLASS_WITH_STATIC_METHOD);
    NormalMethod printMessage = TestingTools.getNormalMethod(testClass3, "printMessage");
    assertThatOutputContainsMessageForCompiledMethod(printMessage);
    removeCompiledMethodMessageFromStandardOutput(printMessage);

    String output = getStandardOutput();
    String expectedOutput = lineEnd + OptTestHarness.startOfExecutionString(printMessage) + lineEnd +
        ClassWithStaticMethod.PRINTOUT + lineEnd + OptTestHarness.endOfExecutionString(printMessage) + lineEnd +
        OptTestHarness.resultString(null) + lineEnd;
    assertThat(output, equalTo(expectedOutput));
  }

  @Test
  public void executeAndRunCanDistinguishMethodsBasedOnDescriptors() throws Exception {
    double d = -1.5d;
    String numberString = Double.toString(d);
    String[] compilePrintIntMethod = {"-er", CLASS_OVERLOADED_METHODS, "print", "(D)V", numberString};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> classWithOverloadedMethods = Class.forName(CLASS_OVERLOADED_METHODS);
    NormalMethod printMethod = TestingTools.getNormalMethod(classWithOverloadedMethods, "print", double.class);
    assertThatOutputContainsMessageForCompiledMethod(printMethod);
    removeCompiledMethodMessageFromStandardOutput(printMethod);
    String output = getStandardOutput();
    String expectedOutput = lineEnd + OptTestHarness.startOfExecutionString(printMethod) + lineEnd +
        numberString + lineEnd + OptTestHarness.endOfExecutionString(printMethod) + lineEnd +
        OptTestHarness.resultString(null) + lineEnd;
    assertThat(output, equalTo(expectedOutput));
  }


  @Test
  public void canReadCommandLineArgumentsFromFile() throws Exception {
    String fileName = "commandLineArgsForLongCommandLineTest";
    createFileWriter(fileName);
    fw.write("-er\n");
    fw.write(CLASS_WITH_STATIC_METHOD);
    fw.write(lineEnd);
    fw.write("printMessage\n");
    fw.write("-\n");
    fw.close();
    String[] longCommandLine = {"-longcommandline", f.getAbsolutePath()};
    executeOptTestHarness(longCommandLine);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void readingNonExistingFileCausesError() throws Exception {
    String fileName = "fileWhichWillNeverExist";
    String[] longCommandLine = {"-longcommandline", fileName};
    executeOptTestHarness(longCommandLine);
    String fileNotFoundLineStart = "java.io.FileNotFoundException: ";
    String firstLineOfStackTrace = fileNotFoundLineStart + fileName + lineEnd;
    assertThat(getErrorOutput().startsWith(firstLineOfStackTrace), is(true));
  }

  @Test
  public void readingEmptyFilesDoesNotCauseProblems() throws Exception {
    String fileName = "emptyFile";
    f = new File(fileName);
    assertThat(f.createNewFile(), is(true));
    f.deleteOnExit();
    String[] longCommandLine = {"-longcommandline", f.getAbsolutePath()};
    executeOptTestHarness(longCommandLine);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void commandLineArgumentFileSupportsComments() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String fileName = "commandLineArgsForLongCommandLineTestWithComments";
    createFileWriter(fileName);
    fw.write("-baseline\n");
    fw.write("#");
    fw.write("+baseline\n");
    fw.close();
    String[] longCommandLine = {"-longcommandline", f.getAbsolutePath()};
    OptTestHarness oth = executeOptTestHarness(longCommandLine);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(oth.useBaselineCompiler, is(false));
  }

  private void createFileWriter(String fileName) throws IOException {
    f = new File(fileName);
    assertThat(f.createNewFile(), is(true));
    f.deleteOnExit();
    fw = new FileWriter(f);
  }

  @Test
  public void supportsPerformanceMeasurements() throws Exception {
    String[] args = { "-performance"};
    OptTestHarness oth = new OptTestHarness();
    oth.addCallbackForPerformancePrintout = false;
    oth.mainMethod(args);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String output = getStandardOutput();

    String patternString = "\nPerformance of executed method\n" +
        "------------------------------\n" + "Elapsed wallclock time: ";
    patternString += "(\\d+\\.\\d+E\\d+)";
    patternString += " msec\n";
    Pattern p = Pattern.compile(patternString);
    Matcher m = p.matcher(output);
    assertThat(m.matches(), is(true));
  }

  @Test
  public void usesSameOptionsAsBootimageCompilerWhenRequested() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));
    String[] useBootimageCompilerOptions = {"-useBootOptions"};
    OptTestHarness oth = executeOptTestHarness(useBootimageCompilerOptions);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(oth.options.INLINE_GUARDED, is(true));
  }

  @Test
  public void canRunAndExecuteMainMethod() throws Exception {
    String firstArg = "abc";
    String secondArg = "123";
    String[] args = { "-main", CLASS_WITH_MAIN_METHOD, firstArg, secondArg };
    executeOptTestHarness(args);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    Class<?> classWithMain = Class.forName(CLASS_WITH_MAIN_METHOD);
    NormalMethod mainMethod = TestingTools.getNormalMethod(classWithMain, "main", String[].class);
    String expectedOutput = OptTestHarness.startOfExecutionString(mainMethod) + lineEnd +
        firstArg + lineEnd + secondArg + lineEnd + OptTestHarness.endOfExecutionString(mainMethod) +
        lineEnd;
    assertThat(getStandardOutput(), equalTo(expectedOutput));
    removeMessageFromStandardOutput(expectedOutput);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void complainsWhenNoMainMethodExists() throws Exception {
    String[] useMainForClassWithoutMain = { "-main", EMPTY_CLASS};
    executeOptTestHarness(useMainForClassWithoutMain);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    String expectedErrorOutput = EMPTY_CLASS +
        " doesn't have a \"public static void main(String[])\" method to execute\n\n";
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void argumentsAreEagerlyEvaluatedFromLeftToRight() throws Exception {
    assumeThat(VM.BuildForOptCompiler, is(true));

    String[] args = { "-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "+baseline" };
    OptTestHarness oth = executeOptTestHarness(args);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(2);
    assertThat(oth.useBaselineCompiler, is(true));
  }

}
