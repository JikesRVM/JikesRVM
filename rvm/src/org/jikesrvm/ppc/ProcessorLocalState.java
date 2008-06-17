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
package org.jikesrvm.ppc;

import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Processor;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>Processor</code> object.
 *
 * @see Processor
 */
@Uninterruptible
public abstract class ProcessorLocalState {

  /**
   * The C bootstrap program has placed a pointer to the initial
   * Processor in the processor register.  This is OK, so do nothing.
   */
  public static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current Processor object
   */
  @Inline
  public static Processor getCurrentProcessor() {
    return Magic.getProcessorRegister();
  }

  /**
   * Set the current Processor object
   */
  @Inline
  public static void setCurrentProcessor(Processor p) {
    Magic.setProcessorRegister(p);
  }
}
