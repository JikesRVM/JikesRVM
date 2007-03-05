/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ir;

import java.util.Enumeration;
/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty basic block enumeration.
 *
 * @author Dave Grove
 */
public interface OPT_BasicBlockEnumeration extends Enumeration<OPT_BasicBlock> {
  /**
   * Same as nextElement but avoid the need to downcast from Object.
   */
  OPT_BasicBlock next();

  /**
   * Single preallocated empty OPT_BasicBlockEnumeration.
   * WARNING: Think before you use this; getting two possible concrete
   * types may prevent inlining of hasMoreElements and next(), thus
   * blocking scalar replacement.  Only use Empty when we have no hope
   * of scalar replacing the alternative (real) enumeration object.
   */
  OPT_BasicBlockEnumeration Empty = new OPT_EmptyBasicBlockEnumeration();
}
