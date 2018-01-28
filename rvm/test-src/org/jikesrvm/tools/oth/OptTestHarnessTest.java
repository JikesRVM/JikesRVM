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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jikesrvm.classloader.ApplicationClassLoader;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresLackOfOptCompiler;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class OptTestHarnessTest {

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
  private static final String CLASS_WITHOUT_MAIN_METHOD = "org.jikesrvm.tools.oth.ClassWithoutMainMethod";
  private static final String CLASS_WITH_INSTANCE_METHOD = "org.jikesrvm.tools.oth.ClassWithInstanceMethod";
  private static final String CLASS_WITH_PRIVATE_CONSTRUCTOR = "org.jikesrvm.tools.oth.ClassWithPrivateConstructor";


  private TestOutput output;
  private TestFileAccess fileAccess;

  private void redirectStandardStreams() {
    System.setOut(output.getSystemOut());
    System.setErr(output.getSystemErr());
  }

  private void resetStandardStreams() {
    System.setOut(standardSysOut);
    System.setErr(standardSysErr);
  }

  @Test
  @Category(RequiresLackOfOptCompiler.class)
  public void doesNotCrashWhenNoOptCompilerIsAvailable() throws InvocationTargetException, IllegalAccessException {
    String[] emptyArgs = {};
    executeOptTestHarness(emptyArgs);
  }

  private OptTestHarness executeOptTestHarness(String[] commandLineArguments)
      throws InvocationTargetException, IllegalAccessException {
    output = new TestOutput();
    fileAccess = new TestFileAccess();
    OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
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
    if (startIndex == -1) {
      assertThat(getStandardOutput(), equalTo(expected));
    }
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
    return output.getStandardOutput();
  }

  private String getStandardOutput() {
    return getStandardStream().toString();
  }

  private String getErrorOutput() {
    return output.getStandardError().toString();
  }

  private void removeMessageFromErrorOutput(String msg) {
    int index = getErrorOutput().indexOf(msg);
    if (index == -1) {
        assertThat(getErrorOutput(), equalTo(msg));
    }
    output.getStandardError().replace(index, index + msg.length(), "");
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
  @Category(RequiresOptCompiler.class)
  public void knownArgumentsAreProcessedEvenIfUnknownArgumentsAppear() throws Exception {
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
  public void convertToClassNameWorksForFullyQualifiedClassNames() throws Exception {
    String result = "org.jikesrvm.tools.oth.TestClass2";
    assertThat(OptTestHarness.convertToClassName(result), is(result));
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
  public void convertToClassNameDoesNotWorkForComplexPathstrings() throws Exception {
    String className = "org.jikesrvm.tools.oth.TestClass7";
    String pathOfClass = "../.." + className;
    assertThat(OptTestHarness.convertToClassName(pathOfClass),is(pathOfClass));
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
  @Category(RequiresOptCompiler.class)
  public void methodsAreOptCompiledByDefault() throws Exception {
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
    int index = output.getStandardOutput().indexOf(msg);
    if (index == -1) {
      assertThat(getStandardOutput(), equalTo(msg));
    }
    output.getStandardOutput().replace(index, index + msg.length(), "");
  }

  private void assertThatOutputContainsMessageForCompiledMethod(
      NormalMethod normalMethod) {
    String messageForCompiledMethod = OptTestHarness.compiledMethodMessage(normalMethod);
    assertThat(getStandardOutput().contains(messageForCompiledMethod), is(true));
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void methodsAreBaselineCompiledIfRequested() throws Exception {
    String[] compileClass = {"+baseline", "-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD};
    executeOptTestHarness(compileClass);
    assertThatBothMethodsOfTestClass2HaveBeenBaselineCompiled();
  }

  @Test
  @Category(RequiresLackOfOptCompiler.class)
  public void defaultCompilerIsBaselineWhenVMIsBuildWithoutOptCompiler() throws Exception {
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
  @Category(RequiresOptCompiler.class)
  public void methodsAreOptCompiledByDefaultWhenSingleMethodIsCompiled() throws Exception {
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
  @Category(RequiresOptCompiler.class)
  public void methodsWillBeBaselineCompiledIfRequested() throws Exception {
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
  @Category(RequiresOptCompiler.class)
  public void methodsWillOptCompiledIfRequested() throws Exception {
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
  @Category(RequiresLackOfOptCompiler.class)
  public void defaultCompilerForMethodsIsBaselineWhenVMIsBuildWithoutOptCompiler() throws Exception {
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
  public void complainsWhenClassForMethodIsNotFound() throws Exception {
    String wrongClassName = "com.ibm.jikesrvm.TestClass";
    String wrongMethodName = "printWithWrongName";
    String useFirstMethod = "-";
    String[] compilePrintIntMethod = {"-method", wrongClassName, wrongMethodName,
        useFirstMethod};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "WARNING: Skipping method from " + wrongClassName +
        lineEnd;
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatErrorForUnrecognizedArgumentOccurred(wrongMethodName);
    assertThatErrorForUnrecognizedArgumentOccurred(useFirstMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
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
    executeOTHWithStreamRedirection(executeMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(getStandardOutput().contains(ClassWithStaticMethod.PRINTOUT), is(true));
  }

  @Test
  public void testHarnessCanExecuteInstanceMethods() throws Exception {
    String[] executeInstanceMethod = {"-er", CLASS_WITH_INSTANCE_METHOD, "printItWorks", "-"};
    executeOTHWithStreamRedirection(executeInstanceMethod);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(getStandardOutput().contains(ClassWithInstanceMethod.PRINTOUT), is(true));
  }

  private void executeOTHWithStreamRedirection(String[] othArguments)
      throws InvocationTargetException, IllegalAccessException {
    try {
      output = new TestOutput();
      fileAccess = new TestFileAccess();
      redirectStandardStreams();
      OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
      oth.mainMethod(othArguments);
    } finally {
      resetStandardStreams();
    }
  }

  @Test
  public void methodsCompiledUsingExecutedAndRunAreNotCounted() throws Exception {
    String[] executeAndRun = {"-er", CLASS_WITH_STATIC_METHOD, "printMessage", "-"};
    executeOTHWithStreamRedirection(executeAndRun);

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
    executeOTHWithStreamRedirection(compilePrintIntMethod);
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
  public void executeAndRunPrintsErrorMessageWhenMethodIsNotFound() throws Exception {
    String notExistingMethod = "printDoesNotExist";
    String[] executeMethodThatDoesNotExist = {"-er", CLASS_OVERLOADED_METHODS,
        notExistingMethod, "-"};
    executeOptTestHarness(executeMethodThatDoesNotExist);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "No method named " + notExistingMethod +
         " found in class " + CLASS_OVERLOADED_METHODS + lineEnd +
         "Canceling further option processing to prevent assertion failures." + lineEnd;
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void executeAndRunPrintsErrorMessageWhenMethodWithDescriptorIsNotFound() throws Exception {
    String existingMethod = "print";
    String descriptorThatDoesNotMatchAPrintMethod = "(IIII)V";
    String[] executeMethodThatDoesNotExist = {"-er", CLASS_OVERLOADED_METHODS,
        existingMethod, descriptorThatDoesNotMatchAPrintMethod};
    executeOptTestHarness(executeMethodThatDoesNotExist);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "No method matching " + existingMethod + " " +
         descriptorThatDoesNotMatchAPrintMethod + " found in class " +
        CLASS_OVERLOADED_METHODS + lineEnd +
         "Canceling further option processing to prevent assertion failures." +
        lineEnd;
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void executeAndRunCannotParseNormalClassTypes() throws Exception {
    String aString = "aStringPrintout";
    String[] compilePrintIntMethod = {"-er", CLASS_OVERLOADED_METHODS, "print", "(Ljava/lang/String;)V", aString};
    executeOTHWithStreamRedirection(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "Parsing args of type < BootstrapCL, Ljava/lang/String; > not implemented\n";
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatErrorForUnrecognizedArgumentOccurred(aString);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void executeAndRunCannotParseGeneralArrays() throws Exception {
    String anInt = "5";
    String[] compilePrintIntMethod = {"-er", CLASS_OVERLOADED_METHODS, "print", "([I)V", anInt};
    executeOptTestHarness(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String expectedErrorOutput = "Parsing args of array of < BootstrapCL, I > not implemented\n";
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatErrorForUnrecognizedArgumentOccurred(anInt);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void executeAndRunCanParseStringArrays() throws Exception {
    String firstString = "test";
    String secondString = "success";
    String[] compilePrintIntMethod = {"-er", CLASS_OVERLOADED_METHODS, "print", "([Ljava/lang/String;)V",
        firstString, secondString};
    executeOTHWithStreamRedirection(compilePrintIntMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatNoAdditionalErrorsHaveOccurred();
    Class<?> classWithOverloadedMethods = Class.forName(CLASS_OVERLOADED_METHODS);
    NormalMethod printMethod = TestingTools.getNormalMethod(classWithOverloadedMethods, "print", String[].class);
    assertThatOutputContainsMessageForCompiledMethod(printMethod);
    removeCompiledMethodMessageFromStandardOutput(printMethod);
    String output = getStandardOutput();
    String expectedOutput = lineEnd + OptTestHarness.startOfExecutionString(printMethod) + lineEnd +
        firstString + lineEnd + secondString + lineEnd + OptTestHarness.endOfExecutionString(printMethod) + lineEnd +
        OptTestHarness.resultString(null) + lineEnd;
    assertThat(output, equalTo(expectedOutput));
  }

  @Test(expected = InternalError.class)
  public void executeAndRunThrowsInternalErrorWhenNotEnoughArgumentsAreProvided() throws Exception {
    String[] compilePrintIntMethod = {"-er", CLASS_OVERLOADED_METHODS, "print", "(I)V"};
    executeOptTestHarness(compilePrintIntMethod);
  }

  @Test
  public void executeAndRunPrintsErrorMessageWhenExceptionOccursWhenInvokingDefaultConstructor() throws Exception {
    String[] executeInstanceMethod = {"-er", CLASS_WITH_PRIVATE_CONSTRUCTOR, "printItWorks", "-"};
    executeOptTestHarness(executeInstanceMethod);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    String invocationFailedMessage = "Invocation of default constructor failed for method";
    assertThat(getErrorOutput().startsWith(invocationFailedMessage), is(true));
  }

  @Test
  public void canReadCommandLineArgumentsFromFile() throws Exception {
    String fileName = "commandLineArgsForLongCommandLineTest";
    fileAccess = new TestFileAccess();
    String fileContent = "-er\n" + CLASS_WITH_STATIC_METHOD + lineEnd +
        "printMessage\n" + "-\n";
    fileAccess.putContentForFile(fileName, fileContent);
    String[] longCommandLine = {"-longcommandline", fileName};
    try {
      output = new TestOutput();
      redirectStandardStreams();
      OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
      oth.mainMethod(longCommandLine);
    } finally {
      resetStandardStreams();
    }
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  public void readingNonExistingFileCausesError() throws Exception {
    String fileName = "fileWhichWillNeverExist";
    fileAccess = new TestFileAccess();
    String[] longCommandLine = {"-longcommandline", fileName};
    output = new TestOutput();
    OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
    oth.mainMethod(longCommandLine);
    String fileNotFoundLineStart = "java.io.FileNotFoundException: ";
    String firstLineOfStackTrace = fileNotFoundLineStart + fileName + lineEnd;
    assertThat(getErrorOutput().startsWith(firstLineOfStackTrace), is(true));
  }

  @Test
  public void readingEmptyFilesDoesNotCauseProblems() throws Exception {
    String fileName = "emptyFile";
    fileAccess = new TestFileAccess();
    fileAccess.putContentForFile(fileName, "");
    String[] longCommandLine = {"-longcommandline", fileName};
    output = new TestOutput();
    OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
    oth.mainMethod(longCommandLine);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void commandLineArgumentFileSupportsComments() throws Exception {
    fileAccess = new TestFileAccess();
    String fileName = "commandLineArgsForLongCommandLineTestWithComments";
    String fileContent = "-baseline\n" + "#" + "+baseline\n";
    fileAccess.putContentForFile(fileName, fileContent);
    String[] longCommandLine = {"-longcommandline", fileName};
    output = new TestOutput();
    OptTestHarness oth = new OptTestHarness(output, new OptOptions(), fileAccess);
    oth.mainMethod(longCommandLine);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(oth.useBaselineCompiler, is(false));
  }

  @Test
  public void supportsPerformanceMeasurements() throws Exception {
    String[] args = { "-performance"};
    output = new TestOutput();
    OptTestHarness oth = new OptTestHarness(output, new OptOptions(), new TestFileAccess());
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

  @Test // Note: this test will break when the bootimage options change
  @Category(RequiresOptCompiler.class)
  public void usesSameOptionsAsBootimageCompilerWhenRequested() throws Exception {
    String[] useBootimageCompilerOptions = {"-useBootOptions"};
    output = new TestOutput();
    OptOptions optOptions = new OptOptions();
    optOptions.INLINE_GUARDED = !optOptions.guardWithCodePatch();
    OptTestHarness oth = new OptTestHarness(output, optOptions, new TestFileAccess());
    oth.mainMethod(useBootimageCompilerOptions);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThat(oth.options.INLINE_GUARDED, is(optOptions.guardWithCodePatch()));
  }

  @Test
  public void canRunAndExecuteMainMethod() throws Exception {
    String firstArg = "abc";
    String secondArg = "123";
    String[] args = { "-main", CLASS_WITH_MAIN_METHOD, firstArg, secondArg };
    executeOTHWithStreamRedirection(args);
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
    String[] useMainForClassWithoutMain = { "-main", CLASS_WITHOUT_MAIN_METHOD};
    executeOTHWithStreamRedirection(useMainForClassWithoutMain);
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(0);
    assertThatRemainingOutputIsEmptyWhenTrimmed();
    String expectedErrorOutput = CLASS_WITHOUT_MAIN_METHOD +
        " doesn't have a \"public static void main(String[])\" method to execute\n\n";
    removeMessageFromErrorOutput(expectedErrorOutput);
    assertThatNoAdditionalErrorsHaveOccurred();
  }

  @Test
  @Category(RequiresOptCompiler.class)
  public void argumentsAreEagerlyEvaluatedFromLeftToRight() throws Exception {
    String[] args = { "-class", ABSTRACT_CLASS_WITH_EMPTY_METHOD, "+baseline" };
    OptTestHarness oth = executeOptTestHarness(args);
    assertThatNoAdditionalErrorsHaveOccurred();
    assertThatNumberOfBaselineCompiledMethodsIs(0);
    assertThatNumberOfOptCompiledMethodsIs(2);
    assertThat(oth.useBaselineCompiler, is(true));
  }

  @Ignore("not enabled by default because it may interfere with other tests")
  @Test
  public void disableClassLoadingWorksOnlyInConjunctionWithExecuteAndRun() throws Exception {
    String[] args = { "-er", "org.jikesrvm.tools.oth.OptTestHarness",
        "convertToClassName", "-", "LTest;", "-disableClassLoading"};
    assertThat(RVMClass.isClassLoadingDisabled(), is(false));
    try {
      executeOptTestHarness(args);
      assertThat(RVMClass.isClassLoadingDisabled(), is(true));
    } catch (Exception ignore) {
      // exceptions don't matter in this test, we care only about the assertions
    } finally {
      RVMClass.setClassLoadingDisabled(false);
    }
  }

}
