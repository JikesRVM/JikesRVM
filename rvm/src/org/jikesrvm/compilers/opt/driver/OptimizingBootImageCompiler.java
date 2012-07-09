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
package org.jikesrvm.compilers.opt.driver;

import java.util.Vector;

import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.common.BootImageCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * Use optimizing compiler to build virtual machine boot image.
 */
public final class OptimizingBootImageCompiler extends BootImageCompiler {

  // Cache objects needed to cons up compilation plans
  private final Vector<OptimizationPlanElement[]> optimizationPlans = new Vector<OptimizationPlanElement[]>();
  private final Vector<Boolean> optimizationPlanLocks = new Vector<Boolean>();
  private final Vector<OptOptions> options = new Vector<OptOptions>();
  private final OptOptions masterOptions = new OptOptions();

  /**
   * If excludePattern is {@code null}, all methods are opt-compiled (or attempted).
   * Otherwise, methods that match the pattern are not opt-compiled.
   * In any case, the class OptSaveVolatile is always opt-compiled.
   */
  private String excludePattern;

  private boolean match(RVMMethod method) {
    if (excludePattern == null) return true;
    RVMClass cls = method.getDeclaringClass();
    String clsName = cls.toString();
    if (clsName.compareTo("org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile") == 0) return true;
    String methodName = method.getName().toString();
    String fullName = clsName + "." + methodName;
    return (fullName.indexOf(excludePattern)) < 0;
  }

  @Override
  protected void initCompiler(String[] args) {
    try {
      BaselineCompiler.initOptions();
      VM.sysWrite("BootImageCompiler: init (opt compiler)\n");

      // Writing a boot image is a little bit special.  We're not really
      // concerned about compile time, but we do care a lot about the quality
      // and stability of the generated code.  Set the options accordingly.
      OptimizingCompiler.setBootOptions(masterOptions);

      // Allow further customization by the user.
      for (int i = 0, n = args.length; i < n; i++) {
        String arg = args[i];
        if (!masterOptions.processAsOption("-X:bc:", arg)) {
          if (arg.startsWith("exclude=")) {
            excludePattern = arg.substring(8);
          } else {
            VM.sysWrite("BootImageCompiler: Unrecognized argument " + arg + "; ignoring\n");
          }
        }
      }
      EdgeCounts.loadCountsFromFileIfAvailable(masterOptions.PROFILE_EDGE_COUNT_INPUT_FILE);
      OptimizingCompiler.init(masterOptions);
    } catch (OptimizingCompilerException e) {
      String msg = "BootImageCompiler: Compiler failed during initialization: " + e + "\n";
      if (e.isFatal) {
        // An unexpected error when building the opt boot image should be fatal
        e.printStackTrace();
        System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
      } else {
        VM.sysWrite(msg);
      }
    }
  }

  @Override
  protected CompiledMethod compileMethod(NormalMethod method, TypeReference[] params) {
    if (method.hasNoOptCompileAnnotation()) {
      return baselineCompile(method);
    } else {
      CompiledMethod cm = null;
      OptimizingCompilerException escape = new OptimizingCompilerException(false);
      try {
        Callbacks.notifyMethodCompile(method, CompiledMethod.OPT);
        boolean include = match(method);
        if (!include) {
          throw escape;
        }
        int freeOptimizationPlan = getFreeOptimizationPlan();
        OptimizationPlanElement[] optimizationPlan = optimizationPlans.get(freeOptimizationPlan);
        CompilationPlan cp =
          new CompilationPlan(method, params, optimizationPlan, null, options.get(freeOptimizationPlan));
        cm = OptimizingCompiler.compile(cp);
        if (VM.BuildForAdaptiveSystem) {
          /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
          int compilerId = CompilerDNA.getCompilerConstant(cp.options.getOptLevel());
          cm.setCompilationTime((float)CompilerDNA.estimateCompileTime(compilerId, method));
        }
        releaseOptimizationPlan(freeOptimizationPlan);
        return cm;
      } catch (OptimizingCompilerException e) {
        if (e.isFatal) {
          // An unexpected error when building the opt boot image should be fatal
          VM.sysWriteln("Error compiling method: "+method);
          e.printStackTrace();
          System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
        } else {
          boolean printMsg = true;
          if (e instanceof MagicNotImplementedException) {
            printMsg = !((MagicNotImplementedException) e).isExpected;
          }
          if (e == escape) {
            printMsg = false;
          }
          if (printMsg) {
            if (e.toString().indexOf("method excluded") >= 0) {
              String msg = "BootImageCompiler: " + method + " excluded from opt-compilation\n";
              VM.sysWrite(msg);
            } else {
              String msg = "BootImageCompiler: can't optimize \"" + method + "\" (error was: " + e + ")\n";
              VM.sysWrite(msg);
            }
          }
        }
        return baselineCompile(method);
      }
    }
  }

  private CompiledMethod baselineCompile(NormalMethod method) {
    Callbacks.notifyMethodCompile(method, CompiledMethod.BASELINE);
    CompiledMethod cm = BaselineCompiler.compile(method);
    /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
    cm.setCompilationTime((float)CompilerDNA.estimateCompileTime(CompilerDNA.BASELINE, method));
    return cm;
  }

  /**
   * Return an optimization plan that isn't in use
   * @return optimization plan
   */
  private int getFreeOptimizationPlan() {
    // Find plan
    synchronized (optimizationPlanLocks) {
      for (int i = 0; i < optimizationPlanLocks.size(); i++) {
        if (!optimizationPlanLocks.get(i)) {
          optimizationPlanLocks.set(i, Boolean.TRUE);
          return i;
        }
      }
      // Find failed, so create new plan
      OptimizationPlanElement[] optimizationPlan;
      OptOptions cloneOptions = masterOptions.dup();
      optimizationPlan = OptimizationPlanner.createOptimizationPlan(cloneOptions);
      optimizationPlans.addElement(optimizationPlan);
      optimizationPlanLocks.addElement(Boolean.TRUE);
      options.addElement(cloneOptions);
      return optimizationPlanLocks.size() - 1;
    }
  }

  /**
   * Release an optimization plan
   * @param plan an optimization plan
   */
  private void releaseOptimizationPlan(int plan) {
    synchronized (optimizationPlanLocks) {
      optimizationPlanLocks.set(plan, Boolean.FALSE);
    }
  }
}
