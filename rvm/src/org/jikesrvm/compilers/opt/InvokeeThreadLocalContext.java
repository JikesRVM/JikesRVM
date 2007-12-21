/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * This class represents a specialization context meaning
 * "the invokee is thread local".
 * We use this context to remove unnecessary synchronizations.
 */
public class InvokeeThreadLocalContext implements SpecializationContext {

  InvokeeThreadLocalContext() {
  }

  /**
   * Find or create a specialized method in this context.
   * @param source
   */
  public SpecializedMethod findOrCreateSpecializedVersion(VM_NormalMethod source) {
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
  private SpecializedMethod createSpecializedMethod(VM_NormalMethod method) {
    return (new SpecializedMethod(method, this));
  }

  /**
   * Generate code to specialize a method in this context. Namely, invoke
   * the opt compiler with the INVOKEE_THREAD_LOCAL option.
   * @param source
   */
  public VM_CompiledMethod specialCompile(VM_NormalMethod source) {
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
  static void init() {
    options = new OptOptions();
    optimizationPlan = OptimizationPlanner.createOptimizationPlan(options);
    options.INVOKEE_THREAD_LOCAL = true;
  }
}



