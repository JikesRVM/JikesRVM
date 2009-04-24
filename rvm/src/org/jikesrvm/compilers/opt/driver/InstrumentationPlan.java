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
package org.jikesrvm.compilers.opt.driver;

import org.jikesrvm.classloader.NormalMethod;

/**
 * An instance of this class acts instructs the optimizing
 * compiler how to instrument a method to support the
 * gathering of runtime measurement information
 *
 * Currently empty, but will gradually add function here
 * as the Adaptive Optimization Subsystem evolves.
 */
public abstract class InstrumentationPlan {
  /**
   * Called before at the beginning of compilation
   */
  public abstract void initInstrumentation(NormalMethod method);

  /**
   * Called after compilation completes, but before method is executed
   */
  public abstract void finalizeInstrumentation(NormalMethod method);
}
