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
package org.jikesrvm.runtime;

import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;

/**
 * Interface for exception delivery called by RuntimeEntrypoints.deliverException() to
 * pass control to a stackframe whose method has an appropriate "catch" block
 * or to step over a stackframe that does not have an appropriate catch block.
 * <p>
 * The exception delivery implementation is specific to the compiler
 * that generated the method's machine instructions.
 * <p>
 * Note that the "deliverException" and "unwindStackFrame" methods of this
 * class will be called in an environment that does not permit garbage
 * collection: see VM.disableGC().
 * We must do this because some of the parameters to these methods are raw
 * machine addresses. They are not recognized by the garbage collector as
 * Object references and so would not be correctly fixed up in the event of
 * object motion during GC. As a
 * consequence, implementors of these methods must not cause object allocations
 * to take place (i.e. by calling "new" either directly or indirectly).
 */
public abstract class ExceptionDeliverer {
  /**
   * Stackframe's method has a "catch" block for exception being
   * thrown and control is to be passed to that catch block.
   *
   * <p> Note:
   *   Implementers must issue the following two lines just before
   *   transferring control to the catch block:
   *
   * <pre>
   *           VM.enableGC();
   *           registers.inuse = false;
   * </pre>
   *
   * <p> Note: this method does not return
   * (execution resumes at catchBlockInstructionAddress)
   *
   * @param compiledMethod method whose catch block is to receive control
   * @param catchBlockInstructionAddress instruction address at which
   * to begin execution of catch block
   * @param exceptionObject exception object to be passed as argument to
   * catch block
   * @param registers registers to be loaded before passing control to
   * catch block
   */
  @Unpreemptible("Deliver exception possibly from unpreemptible code")
  public abstract void deliverException(CompiledMethod compiledMethod, Address catchBlockInstructionAddress,
                                        Throwable exceptionObject, Registers registers);

  /**
   * Stackframe's method has no "catch" block for exception being thrown
   * and stackframe is to be "unwound" as follows:
   *
   * <ul>
   * <li> 1. for a synchronized method, call ObjectModel.genericUnlock(),
   *     passing it the appropriate "lock" object
   *     <ul>
   *       <li>for non-static methods, the lock is the method's
   *         first argument ("this")
   *       <li>for static methods, the lock is the method's java.lang.Class
   *     </ul>
   *
   * <li> 2. restore the non-volatile registers (including fp) that were saved
   *     in the method's prologue, by copying them from the method's stackframe
   *     save area into the provided "registers" object
   * </ul>
   *
   * @param compiledMethod method whose stackframe is to be unwound
   * @param registers thread state to be updated by restoring non-volatiles
   *                  and unwinding the stackframe
   */
  @Unpreemptible("Unwind stack possibly from unpreemptible code")
  public abstract void unwindStackFrame(CompiledMethod compiledMethod, Registers registers);
}
