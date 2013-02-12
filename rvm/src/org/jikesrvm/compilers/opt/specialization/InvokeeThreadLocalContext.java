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
package org.jikesrvm.compilers.opt.specialization;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanner;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;

/**
 * This class represents a specialization context meaning
 * "the invokee is thread local".
 * We use this context to remove unnecessary synchronizations.
 */
public final class InvokeeThreadLocalContext implements SpecializationContext {

  public InvokeeThreadLocalContext() {
  }

  /**
   * Find or create a specialized method in this context.
   * @param source
   */
  @Override
  public SpecializedMethod findOrCreateSpecializedVersion(NormalMethod source) {
    // first check if the specialization database contains
    // a specialized version from this context.
    java.util.Iterator<SpecializedMethod> versions = SpecializationDatabase.getSpecialVersions(source);
    if (versions != null) {
      while (versions.hasNext()) {
        SpecializedMethod spMethod = versions.next();
        SpecializationContext context = spMethod.getSpecializationContext();
        if (context == this) {
          return spMethod;
        }
      }
    }
    // none found. create one.
    SpecializedMethod spMethod = createSpecializedMethod(source);
    // register it in the database.
    SpecializationDatabase.registerSpecialVersion(spMethod);
    // return it.
    return spMethod;
  }

  /**
   * Create specialized method in this context.
   * @param method
   */
  private SpecializedMethod createSpecializedMethod(NormalMethod method) {
    return (new SpecializedMethod(method, this));
  }

  /**
   * Generate code to specialize a method in this context. Namely, invoke
   * the opt compiler with the INVOKEE_THREAD_LOCAL option.
   * @param source
   */
  @Override
  public CompiledMethod specialCompile(NormalMethod source) {
    CompilationPlan plan = new CompilationPlan(source, optimizationPlan, null, options);
    return OptimizingCompiler.compile(plan);
  }

  /**
   * The default optimization options, with the INVOKEE_THREAD_LOCAL flag
   * set true.
   */
  private static OptOptions options;
  /**
   * The default optimization plan.
   */
  private static OptimizationPlanElement[] optimizationPlan;

  /**
   * Initialize static members.
   */
  public static void init() {
    options = new OptOptions();
    optimizationPlan = OptimizationPlanner.createOptimizationPlan(options);
    // all objects in the specialized method will be thread local
    options.ESCAPE_INVOKEE_THREAD_LOCAL = true;
  }
}

