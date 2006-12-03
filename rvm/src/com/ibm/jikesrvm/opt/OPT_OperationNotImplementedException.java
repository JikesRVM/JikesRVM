/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

/**
 * Use this exception when the opt compiler attempts to 
 * compile/optimize a method containing a currently
 * unsupported (but expected) operation.
 * The main intended use is in prototype/incomplete optimizations
 * which may not handle every case, but which will eventually be 
 * extended to handle the excepting case. If the unsupported operation
 * really is a serious problem, then one should use 
 * an OptimzingCompilerException.
 *
 * We define this to be a non-fatal OPT_OptimizingCompilerException.
 *
 * @author Dave Grove
 */
public class OPT_OperationNotImplementedException extends 
    OPT_OptimizingCompilerException {
  /** Support for exception serialization */
  static final long serialVersionUID = -7215437494493545076L;
  
  public OPT_OperationNotImplementedException (String s) {
    super(s, false);
  }
}



