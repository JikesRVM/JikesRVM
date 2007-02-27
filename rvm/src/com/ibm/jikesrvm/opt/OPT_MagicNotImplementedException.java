/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

/**
 * Use this exception when the opt compiler attempts to 
 * compile an unsupported magic.  
 * We define this to be a non-fatal OPT_OptimizingCompilerException.
 *
 * @author Dave Grove
 */
public class OPT_MagicNotImplementedException extends OPT_OperationNotImplementedException {
  /** Support for exception serialization */
  static final long serialVersionUID = -5731701797088209175L;

  /**
   * A very few magics, we have no intention of ever implementing 
   * in the opt compiler.  Supress warning messages for them
   * to avoid confusing users with "expected" error messages
   */
  public final boolean isExpected;

  private OPT_MagicNotImplementedException (String s, boolean isExpected) {
    super(s);
    this.isExpected = isExpected;
  }

  /**
   * Create a MagicNotImplemented exception with message <code>s</code> when we
   * encounter a magic that we have decided to not implement in the opt
   * compiler.
   * @param   s The message for the exception.
   * @return the newly created exception object
   */
  public static OPT_MagicNotImplementedException EXPECTED(String s) {
    return new OPT_MagicNotImplementedException(s, true);
  }
    
  /**
   * Create a MagicNotImplemented exception with message <code>s</code> when we
   * encounter a magic that we have decided to not implement in the opt
   * compiler.
   * @param  s   The exception's message
   * @return the newly created exception object
   */
  public static OPT_MagicNotImplementedException UNEXPECTED(String s) {
    return new OPT_MagicNotImplementedException(s, false);
  }
}



