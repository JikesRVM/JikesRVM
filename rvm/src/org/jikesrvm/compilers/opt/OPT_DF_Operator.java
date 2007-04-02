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
 * OPT_DF_Operator.java
 *
 * represents a function for OPT_DF_LatticeCell values
 *
 * @author Stephen Fink
 */
abstract class OPT_DF_Operator {

  /** 
   * Evaluate this equation, setting a new value for the
   * left-hand side. 
   * 
   * @param operands The operands for this operator.  operands[0]
   *                is the left-hand side.
   * @return true if the lhs value changes. false otherwise.
   */
  abstract boolean evaluate (OPT_DF_LatticeCell[] operands);
}



