/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.classloader.*;

/**
 * This class represents a specialization context meaning
 * "the invokee is thread local".
 * We use this context to remove unnecessary synchronizations.
 *
 *
 * @author Rajesh Bordawekar
 * @author Stephen Fink
 */
public class OPT_InvokeeThreadLocalContext
    implements OPT_SpecializationContext {

  OPT_InvokeeThreadLocalContext () {
  }

  /**
   * Find or create a specialized method in this context.
   * @param source
   */
  public OPT_SpecializedMethod findOrCreateSpecializedVersion(VM_NormalMethod source) {
    // first check if the specialization database contains
    // a specialized version from this context.
    java.util.Iterator<OPT_SpecializedMethod> versions = 
        OPT_SpecializationDatabase.getSpecialVersions(source);
    if (versions != null) {
      while (versions.hasNext()) {
        OPT_SpecializedMethod spMethod = versions.next();
        OPT_SpecializationContext context = spMethod.getSpecializationContext();
        if (context == this) {
          return  spMethod;
        }
      }
    }
    // none found. create one.
    OPT_SpecializedMethod spMethod = createSpecializedMethod(source);
    // register it in the database.
    OPT_SpecializationDatabase.registerSpecialVersion(spMethod);
    // return it.
    return  spMethod;
  }

  /**
   * Create specialized method in this context.
   * @param method
   */
  private OPT_SpecializedMethod createSpecializedMethod (VM_NormalMethod method) {
    return  (new OPT_SpecializedMethod(method, this));
  }

  /**
   * Generate code to specialize a method in this context. Namely, invoke
   * the opt compiler with the INVOKEE_THREAD_LOCAL option.
   * @param source
   */
  public VM_CompiledMethod specialCompile (VM_NormalMethod source) {
    OPT_CompilationPlan plan = new OPT_CompilationPlan(source, 
                                                       optimizationPlan, 
                                                       null, 
                                                       options);
    return  OPT_Compiler.compile(plan);
  }
  /**
   * The default optimization options, with the INVOKEE_THREAD_LOCAL flag
   * set true.
   */
  private static OPT_Options options;
  /**
   * The default optimization plan.
   */
  private static OPT_OptimizationPlanElement[] optimizationPlan;

  /**
   * Initialize static members.
   */
  static void init () {
    options = new OPT_Options();
    optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
    options.INVOKEE_THREAD_LOCAL = true;
  }
}



