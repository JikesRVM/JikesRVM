/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>VM_Processor</code> object.
 *
 * @see VM_Processor
 */
@Uninterruptible public abstract class VM_ProcessorLocalState {

  /**
   * The C bootstrap program has placed a pointer to the initial
   * VM_Processor in the processor register.  This is OK, so do nothing.
   */
  public static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current VM_Processor object
   */
  @Inline
  public static VM_Processor getCurrentProcessor() {
    return VM_Magic.getProcessorRegister();
  }

  /**
   * Set the current VM_Processor object
   */
  @Inline
  public static void setCurrentProcessor(VM_Processor p) {
    VM_Magic.setProcessorRegister(p);
  }
}
