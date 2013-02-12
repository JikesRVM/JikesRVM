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
package org.jikesrvm.compilers.opt;

/**
 * Use this exception when the opt compiler attempts to
 * compile an unsupported magic.
 * We define this to be a non-fatal OptimizingCompilerException.
 */
public final class MagicNotImplementedException extends OperationNotImplementedException {
  /** Support for exception serialization */
  static final long serialVersionUID = -5731701797088209175L;

  /**
   * A very few magics, we have no intention of ever implementing
   * in the opt compiler.  Suppress warning messages for them
   * to avoid confusing users with "expected" error messages
   */
  public final boolean isExpected;

  private MagicNotImplementedException(String s, boolean isExpected) {
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
  public static MagicNotImplementedException EXPECTED(String s) {
    return new MagicNotImplementedException(s, true);
  }

  /**
   * Create a MagicNotImplemented exception with message <code>s</code> when we
   * encounter a magic that we have decided to not implement in the opt
   * compiler.
   * @param  s   The exception's message
   * @return the newly created exception object
   */
  public static MagicNotImplementedException UNEXPECTED(String s) {
    return new MagicNotImplementedException(s, false);
  }
}



