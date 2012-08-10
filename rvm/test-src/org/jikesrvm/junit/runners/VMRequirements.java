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
 * This is a custom JUnit runner that will run all test methods annotated with
 * {@link RequiresJikesRVM} or {@link RequiresBootstrapVM} whether the test is run
 * on Jikes RVM or the Bootstrap VM. Since there are some classes that don't
 * run on the Bootstrap VM (like InlineSequence) and tools like Mockito are not interpreted
 * correctly by Jikes RVM, this Custom Test Runner needed to be created.
 */

public class VMRequirements extends BlockJUnit4ClassRunner {

  public VMRequirements(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void runChild(FrameworkMethod method, RunNotifier notifier) {
    Description description = describeChild(method);

    if (!isRunningOnJikesRVM() && requiresVM(method, RequiresJikesRVM.class)) {
      ignoreTest(method, notifier, description);
      return;
    }

    if (isRunningOnJikesRVM() && requiresVM(method, RequiresBootstrapVM.class)) {
      ignoreTest(method, notifier, description);
      return;
    }

    super.runChild(method, notifier);
  }

  public static boolean isRunningOnJikesRVM() {
    return System.getProperty("java.vm.vendor").equals("Jikes RVM Project");
  }

  private void ignoreTest(FrameworkMethod method, RunNotifier notifier, Description description){
    EachTestNotifier eachTestNotifier = new EachTestNotifier(notifier, description);
    eachTestNotifier.fireTestIgnored();
  }

  private boolean requiresVM(FrameworkMethod method, Class<?> category) {

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
