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
package org.jikesrvm.junit.runners;

import org.junit.experimental.categories.Category;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * This is a custom JUnit runner that allows to determine the set of tests to be
 * run based on the VM that is used for running the tests. It also supports
 * examination of VM features if the VM is Jikes RVM.
 * <p>
 * For example, some tests can only be run on Jikes RVM (e.g. because they use
 * Magic or other Jikes RVM features) and some tests can only be run on a "normal"
 * VM (e.g. HotSpot) because they use libraries such as Mockito that don't support
 * GNU Classpath or Jikes RVM. Note that the bootstrap VM can never by Jikes RVM
 * because Jikes RVM isn't able to build itself right now (Feb 2016). This makes
 * the implementation of the runner easy because it can just assume that the
 * bootstrap VM supports the full set of Java features and libraries.
 * <p>
 * The set of tests to run is determined via classes for JUnit categories,
 * e.g. {@link RequiresBuiltJikesRVM} or {@link RequiresBootstrapVM}.
 */
public class VMRequirements extends BlockJUnit4ClassRunner {

  private static final boolean RUNNING_ON_BOOTSTRAP_VM;
  private static final boolean RUNNING_ON_BUILT_JIKES_RVM;
  private static final boolean RUNNING_ON_IA32;
  private static final boolean RUNNING_ON_POWERPC;
  private static final boolean VM_HAS_OPT_COMPILER;

  static {
    String runnerVM = System.getProperty("jikesrvm.junit.runner.vm");
    RUNNING_ON_BOOTSTRAP_VM = runnerVM.equals("bootstrap");
    RUNNING_ON_BUILT_JIKES_RVM = runnerVM.equals("built-jikes-rvm");
    if (RUNNING_ON_BUILT_JIKES_RVM) {
      String arch = System.getProperty("jikesrvm.target.arch");
      RUNNING_ON_IA32 = arch.equals("ia32");
      RUNNING_ON_POWERPC = arch.equals("ppc");
      String opt = System.getProperty("jikesrvm.include.opt");
      VM_HAS_OPT_COMPILER = "true".equals(opt);
    } else {
      RUNNING_ON_IA32 = false;
      RUNNING_ON_POWERPC = false;
      VM_HAS_OPT_COMPILER = false;
    }
  }

  public VMRequirements(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void runChild(FrameworkMethod method, RunNotifier notifier) {
    Description description = describeChild(method);

    boolean ignoreTest =
        isRunningOnBootstrapVM() && annotatedWith(method, RequiresBuiltJikesRVM.class) ||
        isRunningOnBuiltJikesRVM() && annotatedWith(method, RequiresBootstrapVM.class) ||
        !VM_HAS_OPT_COMPILER && annotatedWith(method, RequiresOptCompiler.class) ||
        VM_HAS_OPT_COMPILER && (annotatedWith(method, RequiresLackOfOptCompiler.class)) ||
        RUNNING_ON_IA32 && annotatedWith(method, RequiresPowerPC.class) ||
        RUNNING_ON_POWERPC && annotatedWith(method, RequiresIA32.class);
    if (ignoreTest) {
      ignoreTest(method, notifier, description);
      return;
    }

    super.runChild(method, notifier);
  }

  public static boolean isRunningOnBootstrapVM() {
    return RUNNING_ON_BOOTSTRAP_VM;
  }

  public static boolean isRunningOnBuiltJikesRVM() {
    return RUNNING_ON_BUILT_JIKES_RVM;
  }

  private void ignoreTest(FrameworkMethod method, RunNotifier notifier, Description description) {
    EachTestNotifier eachTestNotifier = new EachTestNotifier(notifier, description);
    eachTestNotifier.fireTestIgnored();
  }

  private boolean annotatedWith(FrameworkMethod method, Class<?> category) {
    boolean methodRequiresVM = false;
    boolean classRequiresVM = false;

    Category methodAnnotation = method.getAnnotation(Category.class);
    Category classAnnotation = getTestClass().getJavaClass().getAnnotation(Category.class);

    methodRequiresVM = checkAnnotation(methodAnnotation, category);
    classRequiresVM = checkAnnotation(classAnnotation, category);

    return methodRequiresVM || classRequiresVM;
  }

  private boolean checkAnnotation(Category annotation, Class<?> category) {
    if (annotation == null) return false;

    Class<?>[] categories = annotation.value();

    if (categories == null) return false;

    for (Class<?> c : categories) {
      if (c.equals(category)) {
        return true;
      }
    }

    return false;
  }
}
