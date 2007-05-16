/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

public interface OPT_SpecializationContext {

  /**
   * Find or create a specialized version of source for this
   * context.  Do NOT compile it immediately.  However, DO
   * allocate an spmd if needed
   */
  OPT_SpecializedMethod findOrCreateSpecializedVersion(VM_NormalMethod source);

  /**
   * Generate code for a specialized version of source in this
   * context.
   */
  VM_CompiledMethod specialCompile(VM_NormalMethod source);
}



