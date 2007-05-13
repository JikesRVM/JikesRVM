/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.tools.template;

/**
 */
class UnterminatedStringException extends RuntimeException {
  private static final long serialVersionUID = 5639864127476661778L;
  public UnterminatedStringException() { super(); }
  public UnterminatedStringException(String msg) { super(msg); }
}
