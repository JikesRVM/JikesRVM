/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Vector;
//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.OSR.*;
//-#endif

/**
 * This class specifies the order in which OPT_CompilerPhases are
 * executed during opt compilation of a method.
 * The methods BC2IR, HIROptimizations, SSA, HIR2LIR, LIROptimizations,
 * LIR2MIR, MIROptimizations, and MIR2MC each specify the elements
 * that make up the main compilation stages.
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 */
public class OPT_OptimizationPlanner {

  /** 
   * The master optimization plan.
   * All plans that are actually executed should be subsets of this plan
   * constructed by calling createOptimizationPlan.
   */
  private static OPT_OptimizationPlanElement[] masterPlan;

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
    if (!VM.MeasureCompilation) {
      return;
    }

    VM.sysWrite("\t\tOptimizing Compiler SubSystem\n");
    VM.sysWrite("\tPhase\t\t\t\t\tTime\n");
    VM.sysWrite("\t\t\t\t\t   (ms)    (%ofTotal)\n");
    double total = 0.0;

    for (int i = 0; i < masterPlan.length; i++) {
      total += masterPlan[i].elapsedTime();
    }

    for (int i = 0; i < masterPlan.length; i++) {
      masterPlan[i].reportStats(8, 40, total);
    }

    VM.sysWrite("\n\tTOTAL COMPILATION TIME\t\t");
    int t = (int)total;
    if (t < 1000000)
      VM.sysWrite(" ");
    if (t < 100000)
      VM.sysWrite(" ");
    if (t < 10000)
      VM.sysWrite(" ");
    if (t < 1000)
      VM.sysWrite(" ");
    if (t < 100)
      VM.sysWrite(" ");
    if (t < 10)
      VM.sysWrite(" ");
    VM.sysWrite(t);
    VM.sysWrite("\n");
  }

  /**
   * Using the passed options create an optimization plan
   * by selecting a subset of the elements in the masterPlan.
   *
   * @param options the OPT_Options to use
   * @return an OPT_OptimizationPlanElement[] selected from 
   * the masterPlan based on options.
   */
  public static OPT_OptimizationPlanElement[] createOptimizationPlan(OPT_Options options) {
    if (masterPlan == null) {
      initializeMasterPlan();
    }

    Vector temp = new Vector(masterPlan.length);
    for (int i = 0; i < masterPlan.length; i++) {
      if (masterPlan[i].shouldPerform(options)) {
        temp.addElement(masterPlan[i]);
      }
    }
    if (VM.writingBootImage)
      masterPlan = null;  // avoid problems with classes not being in bootimage.
    return finalize(temp);
  }

  /**
   * This method is called to initialize all phases to support
   *  measuring compilation.
   */
  public static void initializeMeasureCompilation() {
    for (int i = 0; i < masterPlan.length; i++) {
      masterPlan[i].initializeForMeasureCompilation();
    }
  }

  /**
   * Initialize the "master plan", which holds all optimization elements
   * that will normally execute.
   */
  private static void initializeMasterPlan() {
    Vector temp = new Vector();
    BC2HIR(temp);    
    HIROptimizations(temp);
    HIR2LIR(temp);
    LIROptimizations(temp);
    OPT_MIROptimizationPlanner.intializeMasterPlan(temp);
    masterPlan = finalize(temp);
  }

  /**
   * Convert the Vector to an array of elements.
   * <p> Note: The conversion to an [] is not quite as silly as it seems.
   * Vectors are synchronized, thus if we left our plan as a Vector,
   * we'd be serializing opt compilation.
   * TODO: this is a bad name (finalize), isn't it?
   */
  private static OPT_OptimizationPlanElement[] finalize(Vector v) {
    OPT_OptimizationPlanElement[] p = new OPT_OptimizationPlanElement[v.size()];
    for (int i = 0; i < v.size(); i++) {
      p[i] = (OPT_OptimizationPlanElement)v.elementAt(i);
    }
    return p;
  }

  /**
   * This method defines the optimization plan elements required to
   * generate HIR from bytecodes.
   *
   * @param p the plan under construction
   */
  private static void BC2HIR(Vector p) {
    composeComponents(p, "Convert Bytecodes to HIR", new Object[] {
                      // Generate HIR from bytecodes
                      new OPT_ConvertBCtoHIR(),

                      //-#if RVM_WITH_OSR
                      new OSR_AdjustBCIndexes(),
                      new OSR_OsrPointConstructor(),
                      //-#endif

                      // Always do initial wave of peephole branch optimizations
                      new OPT_BranchOptimizations(0, true, false),  

                      // Adjust static branch probabilites to account for infrequent blocks
                      new OPT_AdjustBranchProbabilities(),

                      // Optional printing of initial HIR 
                      // Do this after branch optmization, since without merging
                      // FallThroughOuts, the IR is quite ugly. 
                      new OPT_IRPrinter("Initial HIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_HIGH;
                      }
                      }
                      });
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed on the HIR.
   *
   * @param p the plan under construction
   */
  private static void HIROptimizations(Vector p) {
    // Various large-scale CFG transformations.
    // Do these very early in the pipe so that all HIR opts can benefit.
    composeComponents(p, "CFG Transformations", new Object[] {
      // tail recursion elimination
      new  OPT_TailRecursionElimination(),
      // Estimate block frequencies if doing any of
      // static splitting, cfg transformations, or loop unrolling.
      // Assumption: none of these are active at O0.
      new OPT_OptimizationPlanCompositeElement
        ("Basic Block Frequency Estimation", new Object[] {
          new OPT_BuildLST(),
          new OPT_EstimateBlockFrequencies()
        }) {
        public boolean shouldPerform(OPT_Options options) {
          return options.getOptLevel() >= 1;
        }},
      // CFG spliting
      new OPT_StaticSplitting(),
      // restructure loops
      new OPT_CFGTransformations(),
      // Loop unrolling
      new OPT_LoopUnrolling(),
      new OPT_BranchOptimizations(1, true, true),
    });

    // Use the LST to insert yieldpoints and estimate 
    // basic block frequency from branch probabilities
    composeComponents(p, "CFG Structural Analysis", new Object[] {
                      new OPT_BuildLST(),
                      new OPT_YieldPoints(),
                      new OPT_EstimateBlockFrequencies()
    });

    // Simple flow-insensitive optimizations
    addComponent(p, new OPT_Simple(1, true, true));

    // Simple escape analysis and related transformations
    addComponent(p, new OPT_EscapeTransformations());

    // Perform peephole branch optimizations to clean-up before SSA stuff
    addComponent(p, new OPT_BranchOptimizations(1, true, true));

    // SSA meta-phase
    SSAinHIR(p);

    // Perform local copy propagation for a factored basic block.
    addComponent(p, new OPT_LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new OPT_LocalConstantProp());
    // Perform local common-subexpression elimination for a 
    // factored basic block.
    addComponent(p, new OPT_LocalCSE(true));
    // Flow-insensitive field analysis
    addComponent(p, new OPT_FieldAnalysis());
    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // Insert counter on each method prologue
    // Insert yieldpoint counters
    addComponent(p,new OPT_InsertYieldpointCounters());
    // Insert counter on each HIR instruction
    addComponent(p,new OPT_InsertInstructionCounters());
    // Insert method invocation counters
    addComponent(p,new OPT_InsertMethodInvocationCounter());
    //-#endif
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on HIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinHIR(Vector p) {
    composeComponents
      (p, "SSA", new Object[] { 
       // Use the LST to estimate basic block frequency from branch probabilities
       new OPT_OptimizationPlanCompositeElement
       ("Basic Block Frequency Estimation", new Object[] {
        new OPT_BuildLST(),
        new OPT_EstimateBlockFrequencies()
        }) {
       public boolean shouldPerform(OPT_Options options) {
       return options.getOptLevel() >= 2;
       }},

       new OPT_OptimizationPlanCompositeElement 
       ("HIR SSA transformations", 
        new Object[] {
        // Local copy propagation
        new OPT_LocalCopyProp(),
        // Local constant propagation
        new OPT_LocalConstantProp(),
        // Insert PI Nodes
        new OPT_PiNodes(true), 
        // branch optimization
        new OPT_BranchOptimizations(2, true, true),
        // Compute dominators
        new OPT_DominatorsPhase(true), 
        // compute dominance frontier
        new OPT_DominanceFrontier(),
        // load elimination
        new OPT_LoadElimination(1), 
        // load elimination
        new OPT_LoadElimination(2), 
        // load elimination
        new OPT_LoadElimination(3), 
        // load elimination
        new OPT_LoadElimination(4), 
        // load elimination
        new OPT_LoadElimination(5), 
        // eliminate redundant conditional branches
        new OPT_RedundantBranchElimination(),
        // path sensitive constant propagation
        new OPT_SSATuneUp(), 
        // clean up Pi Nodes
        new OPT_PiNodes(false), 
        // Simple SSA optimizations,
        new OPT_SSATuneUp(), 
        // Global Code Placement,
        new OPT_GCP(), 
        // Leave SSA 
        new OPT_LeaveSSA() })     {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 2;
          }
        },
        // Coalesce moves
        new OPT_CoalesceMoves(), 

        // SSA reveals new opportunites for the following
        new OPT_OptimizationPlanCompositeElement
          ("Post SSA cleanup", new Object[] {
           new OPT_LocalCopyProp(),
           new OPT_LocalConstantProp(),
           new OPT_Simple(2, true, true),
           new OPT_EscapeTransformations(),
           new OPT_BranchOptimizations(2, true, true) 
           }) {
            public boolean shouldPerform(OPT_Options options) {
              return options.getOptLevel() >= 2;
            }
          }
      }
    );
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on LIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinLIR(Vector p) {
    composeComponents(p, "SSA", new Object[] {
                      // Use the LST to estimate basic block frequency from branch probabilities
                      new OPT_OptimizationPlanCompositeElement
                      ("Basic Block Frequency Estimation", new Object[] {
                       new OPT_BuildLST(),
                       new OPT_EstimateBlockFrequencies()
                       }){
                      public boolean shouldPerform(OPT_Options options) {
                      return options.getOptLevel() >= 2;
                      }
                      },

                      new OPT_OptimizationPlanCompositeElement 
                      ("LIR SSA transformations", 
                       new Object[] {
                       // restructure loops
                       new OPT_CFGTransformations(),
                       // Compute dominators
                       new OPT_DominatorsPhase(true), 
                       // compute dominance frontier
                       new OPT_DominanceFrontier(),
                       // Global Code Placement,
                       new OPT_GCP(), 
                       // Leave SSA 
                       new OPT_LeaveSSA()  
                       }
                      ) {
                        public boolean shouldPerform(OPT_Options options) {
                          return options.getOptLevel() >= 2;
                        }
                      },
    // Live range splitting 
    new OPT_LiveRangeSplitting(),

    // Coalesce moves
    new OPT_CoalesceMoves(), 

    // SSA reveals new opportunites for the following
    new OPT_OptimizationPlanCompositeElement
      ("Post SSA cleanup", 
       new Object[] {
       new OPT_LocalCopyProp(),
       new OPT_LocalConstantProp(),
       new OPT_Simple(2, true, true),
       new OPT_BranchOptimizations(2, true, true) 
       }) {
        public boolean shouldPerform(OPT_Options options) {
          return options.getOptLevel() >= 2;
        }
      }
    });
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed to lower HIR to LIR.
   *
   * @param p the plan under construction
   */
  private static void HIR2LIR(Vector p) {
    composeComponents(p, "Convert HIR to LIR", new Object[] {
                      // Optional printing of final HIR
                      new OPT_IRPrinter("Final HIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_FINAL_HIR;
                      }
                      }, 

                      // Inlining "runtime service" methods
                      new OPT_ExpandRuntimeServices(), 
                      // Peephole branch optimizations
                      new OPT_BranchOptimizations(1, true, true), 
                      // Local optimizations of checkcasts
                      new OPT_LocalCastOptimization(), 
                      // Massive operator expansion
                      new OPT_ConvertHIRtoLIR(), 
                      // Peephole branch optimizations 
                      new OPT_BranchOptimizations(0, true, true), 
                      // Adjust static branch probabilites to account for infrequent blocks
                      // introduced by the inlining of runtime services.
                      new OPT_AdjustBranchProbabilities(),
                      // Optional printing of initial LIR
                      new OPT_IRPrinter("Initial LIR") {
                        public boolean shouldPerform(OPT_Options options) {
                          return options.PRINT_LOW;
                        }
                      }
    });
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed on the LIR.
   *
   * @param p the plan under construction
   */
  private static void LIROptimizations(Vector p) {
    // SSA meta-phase
    SSAinLIR(p);
    // Perform local copy propagation for a factored basic block.
    addComponent(p, new OPT_LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new OPT_LocalConstantProp());
    // Perform local common-subexpression elimination for a factored basic block.
    addComponent(p, new OPT_LocalCSE(false));
    // Simple flow-insensitive optimizations
    addComponent(p, new OPT_Simple(0, false, false));
    // Late expansion of counter-based yieldpoints
    addComponent(p, new OPT_DeterministicYieldpoints());

    // Use the LST to estimate basic block frequency
    addComponent(p, new OPT_OptimizationPlanCompositeElement
                 ("Basic Block Frequency Estimation", new Object[] {
                  new OPT_BuildLST(),
                  new OPT_EstimateBlockFrequencies() }));

    // Perform basic block reordering
    addComponent(p, new OPT_ReorderingPhase());
    // Perform peephole branch optimizations
    addComponent(p, new OPT_BranchOptimizations(1, false, true));

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // Arnold & Ryder instrumentation sampling framework
    addComponent(p, new OPT_InstrumentationSamplingFramework());

    // Convert high level place holder instructions into actual instrumenation
    addComponent(p, new OPT_LowerInstrumentation());
    //-#endif
  }

  //-#if RVM_FOR_IA32
  ////////////////////////////////////////////////////////////////
  // Plan elements for the IA32 backend
  ////////////////////////////////////////////////////////////////
  /** 
   * This method defines the optimization plan elements that
   * are to be performed to convert LIR to IA32 MIR.
   *
   * @param p the plan under construction
   */
  private static void LIR2MIR(Vector p) {
    composeComponents(p, "Convert LIR to MIR", new Object[] {
                      // Split very large basic blocks into smaller ones.
                      new OPT_SplitBasicBlock(), 
                      // Optional printing of final LIR
                      new OPT_IRPrinter("Final LIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_FINAL_LIR;
                      }
                      }, 
                      // Convert from 3-operand to 2-operand ALU ops.
                      new OPT_ConvertALUOperators(), 
                      // Change operations that split live ranges to moves
                      new OPT_MutateSplits(),
                      // Instruction Selection
                      new OPT_ConvertLIRtoMIR(), 
                      // For now, always print the Initial MIR
                      new OPT_IRPrinter("Initial MIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_MIR;
                      }
                      }
                      });
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed on IA32 MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(Vector p) {
    // NullCheck combining and validation operand removal.
    addComponent(p, new OPT_NullCheckCombining());

    // Register Allocation
    composeComponents(p, "Register Mapping", new Object[] {
                      new OPT_MIRSplitRanges(),
                      // MANDATORY: Expand calling convention
                      new OPT_ExpandCallingConvention(),
                      // MANDATORY: Insert defs/uses due to floating-point stack
                      new OPT_ExpandFPRStackConvention(),
                      // MANDATORY: Perform Live analysis and create GC maps
                      new OPT_LiveAnalysis(true, false),
                      // MANDATORY: Perform register allocation
                      new OPT_RegisterAllocator(),
                      // MANDATORY: Add prologue and epilogue
                      new OPT_PrologueEpilogueCreator(),
                      });
    // Peephole branch optimizations
    addComponent(p, new OPT_MIRBranchOptimizations(1));
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed to convert IA32 MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   *
   * @param p the plan under construction
   */
  private static void MIR2MC(Vector p) {
    // MANDATORY: Final assembly
    addComponent(p, new OPT_ConvertMIRtoMC());
  }

  //-#elif RVM_FOR_POWERPC
  /////////////////////////////////////////////////////////////////////
  // Plan elements for the PowerPC backend
  /////////////////////////////////////////////////////////////////////
  /** 
   * This method defines the optimization plan elements that
   * are to be performed to convert LIR to PowerPC MIR.
   *
   * @param p the plan under construction
   */
  private static void LIR2MIR(Vector p) {
    composeComponents(p, "Convert LIR to MIR", new Object[] {
                      // Optional printing of final LIR
                      new OPT_IRPrinter("Final LIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_FINAL_LIR;
                      }
                      }, 
                      // Split very large basic blocks into smaller ones.
                      new OPT_SplitBasicBlock(), 
                      // Change operations that split live ranges to moves
                      new OPT_MutateSplits(),
                      // Instruction selection
                      new OPT_ConvertLIRtoMIR(), 
                      // Optional printing of initial MIR
                      new OPT_IRPrinter("Initial MIR") {
                      public boolean shouldPerform(OPT_Options options) {
                      return options.PRINT_MIR;
                      }
                      }
                      });
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed on PowerPC MIR.
   *
   * @param p the plan under construction
   */
  private static void MIROptimizations(Vector p) {
    ////////////////////
    // MIR OPTS(1) (before register allocation)
    ////////////////////
    // INSTRUCTION SCHEDULING (PRE-PASS --- PRIOR TO REGISTER ALLOCATION)
    addComponent(p, new OPT_PrePassScheduler());
    // NullCheck combining and validation operand removal.
    addComponent(p, new OPT_NullCheckCombining());
    ////////////////////
    // GCMapping part1 and RegisterAllocation
    ////////////////////

    composeComponents(p, "Register Mapping", new Object[] {
                      // MANDATORY: Expand calling convention
                      new OPT_ExpandCallingConvention(),
                      // Use the LST to estimate basic block frequency from branch probabilities
                      new OPT_OptimizationPlanCompositeElement("Basic Block Frequency Estimation", new Object[] {
                                                               new OPT_BuildLST(),
                                                               new OPT_EstimateBlockFrequencies()
                                                               }) {
                                                                public boolean shouldPerform(OPT_Options options) {
                                                                  return options.blockCountSpillCost();
                                                               }},
                      // MANDATORY: Perform Live analysis and create GC maps
                      new OPT_LiveAnalysis(true, false),
                      // MANDATORY: Perform register allocation
                      new OPT_RegisterAllocator(),
                      // MANDATORY: Add prologue and epilogue
                      new OPT_PrologueEpilogueCreator(),
                      });
    ////////////////////
    // MIR OPTS(2) (after register allocation)
    // NOTE: GCMapping part 1 has created the GC maps already.
    //       From now until the end of compilation, we cannot change
    //       the set of live references at a GC point 
    //       without updating the GCMaps.
    //       Effectively this means that we can only do the 
    //       most trivial optimizations from
    //       here on out without having to some potentially complex bookkeeping.
    ////////////////////
    // Peephole branch optimizations
    addComponent(p, new OPT_MIRBranchOptimizations(1));
  }

  /** 
   * This method defines the optimization plan elements that
   * are to be performed to convert PowerPC MIR into
   * ready-to-execute machinecode (and associated mapping tables).
   * 
   * @param p the plan under construction
   */
  private static void MIR2MC(Vector p) {
    // MANDATORY: Final assembly
    addComponent(p, new OPT_IRPrinter("Final MIR") {
                 public boolean shouldPerform(OPT_Options options) {
                 return options.PRINT_FINAL_MIR; } 
                 });
    addComponent(p, new OPT_ConvertMIRtoMC());
  }
  //-#endif

  // Helper functions for constructing the masterPlan.
  protected static void addComponent(Vector p, OPT_CompilerPhase e) {
    addComponent(p, new OPT_OptimizationPlanAtomicElement(e));
  }

  /**
   * Add an optimization plan element to a vector.
   */
  protected static void addComponent(Vector p, OPT_OptimizationPlanElement e) {
    p.addElement(e);
  }

  /**
   * Add a set of optimization plan elements to a vector.
   * @param p    the vector to add to
   * @param name the name for this composition
   * @param es   the array of composed elements
   */
  protected static void composeComponents(Vector p, String name, Object[] es) {
    p.addElement(OPT_OptimizationPlanCompositeElement.compose(name, es));
  }
}
