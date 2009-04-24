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

import java.util.ArrayList;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecificOpt.MIROptimizationPlanner;
import org.jikesrvm.adaptive.recompilation.instrumentation.InsertInstructionCounters;
import org.jikesrvm.adaptive.recompilation.instrumentation.InsertMethodInvocationCounter;
import org.jikesrvm.adaptive.recompilation.instrumentation.InsertYieldpointCounters;
import org.jikesrvm.adaptive.recompilation.instrumentation.InstrumentationSamplingFramework;
import org.jikesrvm.adaptive.recompilation.instrumentation.LowerInstrumentation;
import org.jikesrvm.compilers.opt.AdjustBranchProbabilities;
import org.jikesrvm.compilers.opt.FieldAnalysis;
import org.jikesrvm.compilers.opt.LocalCSE;
import org.jikesrvm.compilers.opt.LocalCastOptimization;
import org.jikesrvm.compilers.opt.LocalConstantProp;
import org.jikesrvm.compilers.opt.LocalCopyProp;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.bc2ir.ConvertBCtoHIR;
import org.jikesrvm.compilers.opt.bc2ir.OsrPointConstructor;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.BuildLST;
import org.jikesrvm.compilers.opt.controlflow.CFGTransformations;
import org.jikesrvm.compilers.opt.controlflow.DominanceFrontier;
import org.jikesrvm.compilers.opt.controlflow.DominatorsPhase;
import org.jikesrvm.compilers.opt.controlflow.EstimateBlockFrequencies;
import org.jikesrvm.compilers.opt.controlflow.LoopUnrolling;
import org.jikesrvm.compilers.opt.controlflow.ReorderingPhase;
import org.jikesrvm.compilers.opt.controlflow.StaticSplitting;
import org.jikesrvm.compilers.opt.controlflow.TailRecursionElimination;
import org.jikesrvm.compilers.opt.controlflow.YieldPoints;
import org.jikesrvm.compilers.opt.escape.EscapeTransformations;
import org.jikesrvm.compilers.opt.hir2lir.ConvertHIRtoLIR;
import org.jikesrvm.compilers.opt.hir2lir.ExpandRuntimeServices;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.regalloc.CoalesceMoves;
import org.jikesrvm.compilers.opt.ssa.GCP;
import org.jikesrvm.compilers.opt.ssa.LeaveSSA;
import org.jikesrvm.compilers.opt.ssa.LiveRangeSplitting;
import org.jikesrvm.compilers.opt.ssa.LoadElimination;
import org.jikesrvm.compilers.opt.ssa.LoopVersioning;
import org.jikesrvm.compilers.opt.ssa.PiNodes;
import org.jikesrvm.compilers.opt.ssa.RedundantBranchElimination;
import org.jikesrvm.compilers.opt.ssa.SSATuneUp;
import org.jikesrvm.osr.AdjustBCIndexes;

/**
 * This class specifies the order in which CompilerPhases are
 * executed during the HIR and LIR phase of the opt compilation of a method.
 *
 * @see MIROptimizationPlanner
 */
public class OptimizationPlanner {

  /**
   * The master optimization plan.
   * All plans that are actually executed should be subsets of this plan
   * constructed by calling createOptimizationPlan.
   */
  private static OptimizationPlanElement[] masterPlan;

  /**
   * Generate a report of time spent in various phases of the opt compiler.
   * <p> NB: This method may be called in a context where classloading and/or
   * GC cannot be allowed.
   * Therefore we must use primitive sysWrites for output and avoid string
   * appends and other allocations.
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateOptimizingCompilerSubsystemReport(boolean explain) {
    if (!VM.MeasureCompilationPhases) {
      return;
    }

    VM.sysWrite("\t\tOptimizing Compiler SubSystem\n");
    VM.sysWrite("\tPhase\t\t\t\t\tTime\n");
    VM.sysWrite("\t\t\t\t\t   (ms)    (%ofTotal)\n");
    double total = 0.0;

    for (OptimizationPlanElement element : masterPlan) {
      total += element.elapsedTime();
    }

    for (OptimizationPlanElement element : masterPlan) {
      element.reportStats(8, 40, total);
    }

    VM.sysWrite("\n\tTOTAL COMPILATION TIME\t\t");
    int t = (int) total, places = t;
    if (places == 0) {
      places = 1;
    }
    while (places < 1000000) { // Right-align 't'
      VM.sysWrite(" ");
      places *= 10;
    }
    VM.sysWrite(t);
    VM.sysWrite("\n");
  }

  /**
   * Using the passed options create an optimization plan
   * by selecting a subset of the elements in the masterPlan.
   *
   * @param options the Options to use
   * @return an OptimizationPlanElement[] selected from
   * the masterPlan based on options.
   */
  public static OptimizationPlanElement[] createOptimizationPlan(OptOptions options) {
    if (masterPlan == null) {
      initializeMasterPlan();
    }

    ArrayList<OptimizationPlanElement> temp = new ArrayList<OptimizationPlanElement>();
    for (OptimizationPlanElement element : masterPlan) {
      if (element.shouldPerform(options)) {
        temp.add(element);
      }
    }
    if (VM.writingBootImage) {
      masterPlan = null;  // avoid problems with classes not being in bootimage.
    }
    return toArray(temp);
  }

  /**
   * This method is called to initialize all phases to support
   *  measuring compilation.
   */
  public static void initializeMeasureCompilation() {
    for (OptimizationPlanElement element : masterPlan) {
      element.initializeForMeasureCompilation();
    }
  }

  /**
   * Initialize the "master plan", which holds all optimization elements
   * that will normally execute.
   */
  private static void initializeMasterPlan() {
    ArrayList<OptimizationPlanElement> temp = new ArrayList<OptimizationPlanElement>();
    BC2HIR(temp);
    HIROptimizations(temp);
    HIR2LIR(temp);
    LIROptimizations(temp);
    MIROptimizationPlanner.intializeMasterPlan(temp);
    masterPlan = toArray(temp);
  }

  /**
   * Convert the ArrayList to an array of elements.
   * TODO: this is a bad name (finalize), isn't it?
   */
  private static OptimizationPlanElement[] toArray(ArrayList<OptimizationPlanElement> planElementList) {
    OptimizationPlanElement[] p = new OptimizationPlanElement[planElementList.size()];
    planElementList.toArray(p);
    return p;
  }

  /**
   * This method defines the optimization plan elements required to
   * generate HIR from bytecodes.
   *
   * @param p the plan under construction
   */
  private static void BC2HIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "Convert Bytecodes to HIR", new Object[]{
        // Generate HIR from bytecodes
        new ConvertBCtoHIR(),

        new AdjustBCIndexes(), new OsrPointConstructor(),

        // Always do initial wave of peephole branch optimizations
        new BranchOptimizations(0, true, false),

        // ir now contains well formed HIR. Optionally do a verification pass.
        new CompilerPhase() {
          public String getName() {
            return "HIR Verification";
          }
          public void perform(IR ir) {
            if (IR.SANITY_CHECK) {
              ir.verify("Initial HIR", true);
            }
          }
          public CompilerPhase newExecution(IR ir) {
            return this;
          }
        },

        // Adjust static branch probabilities to account for infrequent blocks
        new AdjustBranchProbabilities(),

        // Optional printing of initial HIR
        // Do this after branch optimization, since without merging
        // FallThroughOuts, the IR is quite ugly.
        new IRPrinter("Initial HIR") {
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_HIGH;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on the HIR.
   *
   * @param p the plan under construction
   */
  private static void HIROptimizations(ArrayList<OptimizationPlanElement> p) {
    // Various large-scale CFG transformations.
    // Do these very early in the pipe so that all HIR opts can benefit.
    composeComponents(p, "CFG Transformations", new Object[]{
        // tail recursion elimination
        new TailRecursionElimination(),
        // Estimate block frequencies if doing any of
        // static splitting, cfg transformations, or loop unrolling.
        // Assumption: none of these are active at O0.
        new OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new BuildLST(), new EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 1;
          }
        },
        // CFG splitting
        new StaticSplitting(),
        // restructure loops
        new CFGTransformations(),
        // Loop unrolling
        new LoopUnrolling(), new BranchOptimizations(1, true, true),});

    // Use the LST to insert yieldpoints and estimate
    // basic block frequency from branch probabilities
    composeComponents(p,
                      "CFG Structural Analysis",
                      new Object[]{new BuildLST(), new YieldPoints(), new EstimateBlockFrequencies()});

    // Simple flow-insensitive optimizations
    addComponent(p, new Simple(1, true, true, false, false));

    // Simple escape analysis and related transformations
    addComponent(p, new EscapeTransformations());

    // Perform peephole branch optimizations to clean-up before SSA stuff
    addComponent(p, new BranchOptimizations(1, true, true));

    // SSA meta-phase
    SSAinHIR(p);

    // Perform local copy propagation for a factored basic block.
    addComponent(p, new LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new LocalConstantProp());
    // Perform local common-subexpression elimination for a
    // factored basic block.
    addComponent(p, new LocalCSE(true));
    // Flow-insensitive field analysis
    addComponent(p, new FieldAnalysis());
    if (VM.BuildForAdaptiveSystem) {
      // Insert counter on each method prologue
      // Insert yieldpoint counters
      addComponent(p, new InsertYieldpointCounters());
      // Insert counter on each HIR instruction
      addComponent(p, new InsertInstructionCounters());
      // Insert method invocation counters
      addComponent(p, new InsertMethodInvocationCounter());
    }
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on HIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinHIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "SSA", new Object[]{
        // Use the LST to estimate basic block frequency from branch probabilities
        new OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new BuildLST(), new EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        },

        new OptimizationPlanCompositeElement("HIR SSA transformations", new Object[]{
            // Local copy propagation
            new LocalCopyProp(),
            // Local constant propagation
            new LocalConstantProp(),
            // Insert PI Nodes
            new PiNodes(true),
            // branch optimization
            new BranchOptimizations(3, true, true),
            // Compute dominators
            new DominatorsPhase(true),
            // compute dominance frontier
            new DominanceFrontier(),
            // load elimination
            new LoadElimination(1),
            // load elimination
            new LoadElimination(2),
            // load elimination
            new LoadElimination(3),
            // load elimination
            new LoadElimination(4),
            // load elimination
            new LoadElimination(5),
            // eliminate redundant conditional branches
            new RedundantBranchElimination(),
            // path sensitive constant propagation
            new SSATuneUp(),
            // clean up Pi Nodes
            new PiNodes(false),
            // Simple SSA optimizations,
            new SSATuneUp(),
            // Global Code Placement,
            new GCP(),
            // Loop versioning
            new LoopVersioning(),
            // Leave SSA
            new LeaveSSA()}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        },
        // Coalesce moves
        new CoalesceMoves(),

        // SSA reveals new opportunites for the following
        new OptimizationPlanCompositeElement("Post SSA cleanup",
                                                 new Object[]{new LocalCopyProp(),
                                                              new LocalConstantProp(),
                                                              new Simple(3, true, true, false, false),
                                                              new EscapeTransformations(),
                                                              new BranchOptimizations(3, true, true)}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on LIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinLIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "SSA", new Object[]{
        // Use the LST to estimate basic block frequency from branch probabilities
        new OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new BuildLST(), new EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        },

        new OptimizationPlanCompositeElement("LIR SSA transformations", new Object[]{
            // restructure loops
            new CFGTransformations(),
            // Compute dominators
            new DominatorsPhase(true),
            // compute dominance frontier
            new DominanceFrontier(),
            // Global Code Placement,
            new GCP(),
            // Leave SSA
            new LeaveSSA()}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        },
        // Live range splitting
        new LiveRangeSplitting(),

        // Coalesce moves
        new CoalesceMoves(),

        // SSA reveals new opportunites for the following
        new OptimizationPlanCompositeElement("Post SSA cleanup",
                                                 new Object[]{new LocalCopyProp(),
                                                              new LocalConstantProp(),
                                                              new Simple(3, true, true, false, false),
                                                              new BranchOptimizations(3, true, true)}) {
          public boolean shouldPerform(OptOptions options) {
            return options.getOptLevel() >= 3;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to lower HIR to LIR.
   *
   * @param p the plan under construction
   */
  private static void HIR2LIR(ArrayList<OptimizationPlanElement> p) {
    composeComponents(p, "Convert HIR to LIR", new Object[]{
        // Optional printing of final HIR
        new IRPrinter("Final HIR") {
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_FINAL_HIR;
          }
        },

        // Inlining "runtime service" methods
        new ExpandRuntimeServices(),
        // Peephole branch optimizations
        new BranchOptimizations(1, true, true),
        // Local optimizations of checkcasts
        new LocalCastOptimization(),
        // Massive operator expansion
        new ConvertHIRtoLIR(),
        // Peephole branch optimizations
        new BranchOptimizations(0, true, true),
        // Adjust static branch probabilites to account for infrequent blocks
        // introduced by the inlining of runtime services.
        new AdjustBranchProbabilities(),
        // Optional printing of initial LIR
        new IRPrinter("Initial LIR") {
          public boolean shouldPerform(OptOptions options) {
            return options.PRINT_LOW;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on the LIR.
   *
   * @param p the plan under construction
   */
  private static void LIROptimizations(ArrayList<OptimizationPlanElement> p) {
    // SSA meta-phase
    SSAinLIR(p);
    // Perform local copy propagation for a factored basic block.
    addComponent(p, new LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new LocalConstantProp());
    // Perform local common-subexpression elimination for a factored basic block.
    addComponent(p, new LocalCSE(false));
    // Simple flow-insensitive optimizations
    addComponent(p, new Simple(0, false, false, false, VM.BuildForIA32));

    // Use the LST to estimate basic block frequency
    addComponent(p,
                 new OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                          new Object[]{new BuildLST(),
                                                                       new EstimateBlockFrequencies()}));

    // Perform basic block reordering
    addComponent(p, new ReorderingPhase());
    // Perform peephole branch optimizations
    addComponent(p, new BranchOptimizations(0, false, true));

    if (VM.BuildForAdaptiveSystem) {
      // Arnold & Ryder instrumentation sampling framework
      addComponent(p, new InstrumentationSamplingFramework());

      // Convert high level place holder instructions into actual instrumentation
      addComponent(p, new LowerInstrumentation());
    }
  }

  // Helper functions for constructing the masterPlan.
  protected static void addComponent(ArrayList<OptimizationPlanElement> p, CompilerPhase e) {
    addComponent(p, new OptimizationPlanAtomicElement(e));
  }

  /**
   * Add an optimization plan element to a vector.
   */
  protected static void addComponent(ArrayList<OptimizationPlanElement> p, OptimizationPlanElement e) {
    p.add(e);
  }

  /**
   * Add a set of optimization plan elements to a vector.
   * @param p    the vector to add to
   * @param name the name for this composition
   * @param es   the array of composed elements
   */
  protected static void composeComponents(ArrayList<OptimizationPlanElement> p, String name, Object[] es) {
    p.add(OptimizationPlanCompositeElement.compose(name, es));
  }
}
