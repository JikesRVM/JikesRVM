/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

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



