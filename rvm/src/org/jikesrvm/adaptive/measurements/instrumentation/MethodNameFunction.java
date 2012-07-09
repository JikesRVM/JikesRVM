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
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;

/**
 * This class takes a compiled method id and returns a string
 * representation of the method name.
 **/
class MethodNameFunction implements CounterNameFunction {

  /**
   * @param key the compiled method id of a method
   */
  @Override
  public String getName(int key) {
    CompiledMethod cm = CompiledMethods.getCompiledMethod(key);
    if (cm == null) {
      return "OBSOLETE";
    } else {
      return cm.getMethod().toString();
    }
  }
}
