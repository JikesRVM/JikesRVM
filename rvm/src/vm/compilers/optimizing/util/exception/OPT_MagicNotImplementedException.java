/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Use this exception when the opt compiler attempts to 
 * compile an unsupported magic.  
 * We define this to be a non-fatal OPT_OptimizingCompilerException.
 *
 * @author Dave Grove
 */
public class OPT_MagicNotImplementedException extends OPT_OperationNotImplementedException {

  /**
   * A very few magics, we have no intention of ever implementing 
   * in the opt compiler.  Supress warning messages for them
   * to avoid confusing users with "expected" error messages
   */
  public boolean isExpected = false;

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



