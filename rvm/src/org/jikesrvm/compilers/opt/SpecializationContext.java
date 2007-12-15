/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

public interface SpecializationContext {

  /**
   * Find or create a specialized version of source for this
   * context.  Do NOT compile it immediately.  However, DO
   * allocate an spmd if needed
   */
  SpecializedMethod findOrCreateSpecializedVersion(VM_NormalMethod source);

  /**
   * Generate code for a specialized version of source in this
   * context.
   */
  VM_CompiledMethod specialCompile(VM_NormalMethod source);
}



