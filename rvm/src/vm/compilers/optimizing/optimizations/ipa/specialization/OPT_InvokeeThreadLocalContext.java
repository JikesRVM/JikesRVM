/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Vector;

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
   * @return 
   */
  public OPT_SpecializedMethod findOrCreateSpecializedVersion 
      (VM_Method source) {
    // first check if the specialization database contains
    // a specialized version from this context.
    java.util.Iterator versions = 
        OPT_SpecializationDatabase.getSpecialVersions(source);
    if (versions != null) {
      while (versions.hasNext()) {
        OPT_SpecializedMethod spMethod = (OPT_SpecializedMethod)versions.next();
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
   * @return 
   */
  private OPT_SpecializedMethod createSpecializedMethod (VM_Method method) {
    return  (new OPT_SpecializedMethod(method, this));
  }

  /**
   * Generate code to specialize a method in this context. Namely, invoke
   * the opt compiler with the INVOKEE_THREAD_LOCAL option.
   * @param source
   * @return 
   */
  public VM_CompiledMethod specialCompile (VM_Method source) {
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



