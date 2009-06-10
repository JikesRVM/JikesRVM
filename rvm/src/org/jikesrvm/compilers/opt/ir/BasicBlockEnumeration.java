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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty basic block enumeration.
 */
public interface BasicBlockEnumeration extends Enumeration<BasicBlock> {
  /**
   * Same as nextElement but avoid the need to downcast from Object.
   */
  BasicBlock next();

  /**
   * Single preallocated empty BasicBlockEnumeration.
   * WARNING: Think before you use this; getting two possible concrete
   * types may prevent inlining of hasMoreElements and next(), thus
   * blocking scalar replacement.  Only use Empty when we have no hope
   * of scalar replacing the alternative (real) enumeration object.
   */
  BasicBlockEnumeration Empty = new EmptyBasicBlockEnumeration();
}
