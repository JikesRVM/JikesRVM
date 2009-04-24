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
package org.jikesrvm.compilers.opt.inlining;


/**
 * Oracle interface: the compiler queries this oracle to decide whether
 * to inline a call site.
 */
public interface InlineOracle {

  /**
   * Should we inline a particular call site?
   * @param state information needed to make the inlining decision
   * @return an InlineDecision with the result
   */
  InlineDecision shouldInline(org.jikesrvm.compilers.opt.inlining.CompilationState state);
}



