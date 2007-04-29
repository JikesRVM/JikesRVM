/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.compilers.opt.OPT_DF_Solution;
import org.jikesrvm.compilers.opt.OPT_DominatorTree;
import org.jikesrvm.compilers.opt.OPT_GlobalValueNumberState;
import org.jikesrvm.compilers.opt.OPT_LSTGraph;
import org.jikesrvm.compilers.opt.OPT_SSADictionary;

/**
 * Wrapper class around IR info that is valid on the HIR/LIR/MIR
 *
 * @author Dave Grove
 */
public final class OPT_HIRInfo {
  
  OPT_HIRInfo(OPT_IR ir) { }

  /** Place to hang dominator tree. */
  public OPT_DominatorTree dominatorTree;

  /** Were dominators computed successfully ? */
  public boolean dominatorsAreComputed;

  /** Place to hang post-dominator tree. */
  public OPT_DominatorTree postDominatorTree;

  /** Were post-dominators computed successfully ? */
  public boolean postDominatorsAreComputed;

  /** Place to hang Heap SSA information. */
  public OPT_SSADictionary SSADictionary;

  /** Place to hang global value number information. */
  public OPT_GlobalValueNumberState valueNumbers;

  /** Place to hang uniformly generated global value number information. */
  public OPT_GlobalValueNumberState uniformlyGeneratedValueNumbers;

  /** Place to hang Loop Structure Tree (LST) */
  public OPT_LSTGraph LoopStructureTree;

  /** Place to hang results of index propagation analysis */
  public OPT_DF_Solution indexPropagationSolution;

  /** Did load elimination do anything last time? */
  public boolean loadEliminationDidSomething = true;
}
