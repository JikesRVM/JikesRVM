/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt.ir;

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 *
 * @author Igor Pechtchanski
 */
public interface OPT_OperandEnumeration extends java.util.Enumeration<OPT_Operand> {
  /** Same as nextElement but avoid the need to downcast from Object */
  OPT_Operand next();
}

