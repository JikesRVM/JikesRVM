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
package org.jikesrvm.compilers.baseline.ppc;

import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Architecture-specific information about PPC baseline compiled methods.
 * Allows the location of registers, locals, and stack slots to be recovered
 */
public final class ArchBaselineCompiledMethod extends BaselineCompiledMethod {

  public ArchBaselineCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  private byte lastFixedStackRegister;
  private byte lastFloatStackRegister;
  private short[] localFixedLocations;
  private short[] localFloatLocations;

  @Override
  protected void saveCompilerData(BaselineCompiler abstractComp) {
    BaselineCompilerImpl comp = (BaselineCompilerImpl) abstractComp;

    lastFixedStackRegister = comp.getLastFixedStackRegister();
    lastFloatStackRegister = comp.getLastFloatStackRegister();
    localFixedLocations = comp.getLocalFixedLocations();
    localFloatLocations = comp.getLocalFloatLocations();
  }

  /** @return position of operand stack within method's stackframe */
  @Uninterruptible
  public short getEmptyStackOffset() {
    return BaselineCompilerImpl.getEmptyStackOffset((NormalMethod)this.getMethod());
  }

  /** @return size of method's stackframe. */
  @Uninterruptible
  public int getFrameSize() {
    return BaselineCompilerImpl.getFrameSize((NormalMethod)this.getMethod(), lastFloatStackRegister, lastFixedStackRegister);
  }

  @Uninterruptible
  public byte getLastFixedStackRegister() {
    return lastFixedStackRegister;
  }

  @Uninterruptible
  public byte getLastFloatStackRegister() {
    return lastFloatStackRegister;
  }

  @Uninterruptible
  public short getGeneralLocalLocation(int index) {
    return localFixedLocations[index];
  }

  @Uninterruptible
  public short getFloatLocalLocation(int index) {
    return localFloatLocations[index];
  }

  @Uninterruptible
  public short getGeneralStackLocation(int stackIndex) {
    return BaselineCompilerImpl.offsetToLocation(getEmptyStackOffset() - (stackIndex << LOG_BYTES_IN_ADDRESS));
  }
}
