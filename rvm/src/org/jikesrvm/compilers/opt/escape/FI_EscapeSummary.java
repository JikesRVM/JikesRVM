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
package org.jikesrvm.compilers.opt.escape;

import java.util.HashMap;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * This class holds the results of a flow-insensitive escape analysis
 * for a method.
 */
class FI_EscapeSummary {

  /**
   * Returns {@code true} iff ANY object pointed to by symbolic register r
   * MUST be thread local
   */
  boolean isThreadLocal(Register r) {
    Object result = hash.get(r);
    return result != null && result == THREAD_LOCAL;
  }

  /**
   * Returns {@code true} iff ANY object pointed to by symbolic register r
   * MUST be method local
   */
  boolean isMethodLocal(Register r) {
    Object result = hash2.get(r);
    return result != null && result == METHOD_LOCAL;
  }

  /**
   * record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this thread
   */
  void setThreadLocal(Register r, boolean b) {
    if (b) {
      hash.put(r, THREAD_LOCAL);
    } else {
      hash.put(r, MAY_ESCAPE_THREAD);
    }
  }

  /**
   * Record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this method
   */
  void setMethodLocal(Register r, boolean b) {
    if (b) {
      hash2.put(r, METHOD_LOCAL);
    } else {
      hash2.put(r, MAY_ESCAPE_METHOD);
    }
  }

  /* Implementation */
  /**
   * A mapping that holds the analysis result for thread-locality for each
   * Register.
   */
  private final HashMap<Register, Object> hash = new HashMap<Register, Object>();

  /**
   * A mapping that holds the analysis result for method-locality for each
   * Register.
   */
  private final HashMap<Register, Object> hash2 = new HashMap<Register, Object>();

  /**
   * Static object used to represent analysis result
   */
  static final Object THREAD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object MAY_ESCAPE_THREAD = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object METHOD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object MAY_ESCAPE_METHOD = new Object();
}



