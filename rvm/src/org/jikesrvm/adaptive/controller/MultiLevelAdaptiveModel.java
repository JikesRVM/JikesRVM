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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * Implements the multi-level adaptive strategy using an analytic
 * model, as described in the OOPSLA 2000 paper.  Most behavior
 * inherited from AnalyticModel.  This class defines the the specific
 * recompilation choices that should be considered by the analytic model.
 */
class MultiLevelAdaptiveModel extends AnalyticModel {

  /**
   * List of all opt-level choices that can be considered by the
   * cost-benefit model
   */
  protected RecompileOptChoice[] allOptLevelChoices;

  /**
   * Keep a map from previous compiler to a set of recompilation
   * choices.  After initialization, viableChoices[x][y] means that if
   * x is the previous compiler, y makes sense as a possible
   * recompilation choice.
   */
  protected RecompilationChoice[][] viableChoices;

  /**
   * Normally, we will be profiling call edges to build a dynamic call graph.
   * When this is enabled in the system, we want to block the adaptive system
   * from choosing to compile at a level higher than O0 (only does trivial inlining)
   * until the system has built up at least a little knowledge of the call graph.
   * This the cached early-in-the-run viableChoices to be used until the call graph
   * is ready and we can enable all the opt compiler optimization levels.
   */
  protected RecompilationChoice[] earlyViableChoices = { new RecompileOptChoice(0) };

  /**
   * Initialize the set of "optimization choices" that the
   * cost-benefit model will consider.
   *
   * This method is conceptually simply, but becomes more complex
   * because sets of choices are precomputed and stored in a table so
   * they do not need to be recomputed to answer queries.
   */
  @Override
  void populateRecompilationChoices() {
    int maxOptLevel = Controller.options.DERIVED_MAX_OPT_LEVEL;
    int maxCompiler = CompilerDNA.getCompilerConstant(maxOptLevel);
    allOptLevelChoices = new RecompileOptChoice[maxOptLevel + 1];

    // Create one main list of all possible recompilation choices that
    // will be considered.  For each opt-level, create a recompilation
    // choice for that opt-level and record it indexed by opt-level
    for (int optLevel = 0; optLevel <= maxOptLevel; optLevel++) {
      allOptLevelChoices[optLevel] = new RecompileOptChoice(optLevel);
    }

    // Given the above choices, create lookup table so that the
    // controller's calls to
    // getViableRecompilationChoices(prevCompiler) are answered as
    // efficiently as possible.
    createViableOptionLookupTable(maxCompiler);
  }

  @Override
  RecompilationChoice[] getViableRecompilationChoices(int prevCompiler, CompiledMethod cmpMethod) {
    if (Controller.controllerThread.earlyRestrictOptLevels()) {
      return earlyViableChoices;
    } else {
      return viableChoices[prevCompiler];
    }
  }

  /**
   * Setup a lookup table that maps a "previous compiler" to a set
   * of viable recompilation choices.  In this case, a viable choice
   * is any compiler > prevCompiler.
   */
  protected void createViableOptionLookupTable(int maxCompiler) {
    viableChoices = new RecompilationChoice[maxCompiler][];

    // A temp place to store the list of viable choices
    RecompilationChoice[] temp = new RecompilationChoice[maxCompiler];

    // For each potential value of the previous compiler
    for (int prevCompiler = CompilerDNA.BASELINE; prevCompiler < maxCompiler; prevCompiler++) {

      // Consider each choice in the list of all choices.
      // If it is greater than cur compiler, add it.
      int curSlot = 0;
      for (RecompileOptChoice choice : allOptLevelChoices) {
        if (choice.getCompiler() > prevCompiler) {
          // Add the current opt-level as a choice to consider when
          // the previous compiler is prevCompiler
          temp[curSlot++] = choice;
        }
      }

      // Now that you know how many choices there are, create an array
      // of them and copy the choices in.
      viableChoices[prevCompiler] = new RecompilationChoice[curSlot];
      for (int i = 0; i < curSlot; i++) {
        viableChoices[prevCompiler][i] = temp[i];
        temp[i] = null;
      }
    }
  }
}
