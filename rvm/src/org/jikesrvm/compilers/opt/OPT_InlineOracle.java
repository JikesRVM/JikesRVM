/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

/**
 * Oracle interface: the compiler queries this oracle to decide whether
 * to inline a call site.
 *
 * @author Stephen Fink
 */
public interface OPT_InlineOracle {

  /**
   * Should we inline a particular call site?
   * @param state information needed to make the inlining decision
   * @return an OPT_InlineDecision with the result
   */
  OPT_InlineDecision shouldInline (org.jikesrvm.compilers.opt.ir.OPT_CompilationState state);
}



