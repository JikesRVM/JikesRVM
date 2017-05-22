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
package org.jikesrvm.compilers.baseline.ia32;

import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Architecture-specific information about IA32 baseline compiled methods.
 * Allows the location of locals and stack slots to be recovered
 */
public final class ArchBaselineCompiledMethod extends BaselineCompiledMethod {

  public ArchBaselineCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  @Override
  protected void saveCompilerData(BaselineCompiler abstractComp) {
    // Nothing to do on IA32
  }

  /** @return position of operand stack within method's stackframe */
  @Uninterruptible
  public short getEmptyStackOffset() {
    return BaselineCompilerImpl.getEmptyStackOffset((NormalMethod)this.getMethod());
  }

  @Uninterruptible
  private Offset getStartLocalOffset() {
    return BaselineCompilerImpl.getStartLocalOffset((NormalMethod)this.getMethod());
  }

  @Uninterruptible
  public short getGeneralLocalLocation(int index) {
    return BaselineCompilerImpl.offsetToLocation(getStartLocalOffset().minus(index << LOG_BYTES_IN_ADDRESS));
  }

  @Uninterruptible
  public short getFloatLocalLocation(int index) {
    return BaselineCompilerImpl.offsetToLocation(getStartLocalOffset().minus(index << LOG_BYTES_IN_ADDRESS));
  }

  @Uninterruptible
  public short getGeneralStackLocation(int stackIndex) {
    return BaselineCompilerImpl.offsetToLocation(getEmptyStackOffset() - (stackIndex << LOG_BYTES_IN_ADDRESS));
  }
}
