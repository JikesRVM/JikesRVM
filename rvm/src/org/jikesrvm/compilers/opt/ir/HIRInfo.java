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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.controlflow.LSTGraph;
import org.jikesrvm.compilers.opt.dfsolver.DF_Solution;
import org.jikesrvm.compilers.opt.ssa.GlobalValueNumberState;
import org.jikesrvm.compilers.opt.ssa.SSADictionary;

/**
 * Wrapper class around IR info that is valid on the HIR/LIR/MIR
 */
public final class HIRInfo {

  public HIRInfo(IR ir) { }

  /** Place to hang dominator tree. */
  public DominatorTree dominatorTree;

  /** Were dominators computed successfully ? */
  public boolean dominatorsAreComputed;

  /** Place to hang post-dominator tree. */
  public DominatorTree postDominatorTree;

  /** Were post-dominators computed successfully ? */
  public boolean postDominatorsAreComputed;

  /** Place to hang Heap SSA information. */
  public SSADictionary dictionary;

  /** Place to hang global value number information. */
  public GlobalValueNumberState valueNumbers;

  /** Place to hang uniformly generated global value number information. */
  public GlobalValueNumberState uniformlyGeneratedValueNumbers;

  /** Place to hang Loop Structure Tree (LST) */
  public LSTGraph loopStructureTree;

  /** Place to hang results of index propagation analysis */
  public DF_Solution indexPropagationSolution;

  /** Did load elimination do anything last time? */
  public boolean loadEliminationDidSomething = true;
}
