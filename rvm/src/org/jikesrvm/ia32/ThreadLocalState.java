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
package org.jikesrvm.ia32;

import static org.jikesrvm.ia32.RegisterConstants.THREAD_REGISTER;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.ia32.RegisterConstants.GPR;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>RVMThread</code> object.
 *
 * @see RVMThread
 */
public abstract class ThreadLocalState {

  /**
   * The C bootstrap program has placed a pointer to the initial
   * RVMThread in ESI.
   */
  @Uninterruptible
  public
  static void boot() {
    // do nothing - everything is already set up.
  }

  @Uninterruptible
  public static RVMThread getCurrentThread() {
    return Magic.getESIAsThread();
  }

  @Uninterruptible
  public static void setCurrentThread(RVMThread p) {
    Magic.setESIAsThread(p);
  }

  /**
   * Emit an instruction sequence to load current RVMThread
   * object from a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  public static void emitLoadThread(Assembler asm, GPR base, Offset offset) {
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(THREAD_REGISTER, base, offset);
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(THREAD_REGISTER, base, offset);
    }
  }
}

