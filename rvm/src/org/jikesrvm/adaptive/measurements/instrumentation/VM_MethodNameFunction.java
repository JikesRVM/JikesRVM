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
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;

/**
 * VM_MethodNameFunction.java
 *
 *
 * This class takes a compiled method id and returns a string
 * representation of the method name.
 *
 **/

class VM_MethodNameFunction implements VM_CounterNameFunction {

  /**
   * @param key the compiled method id of a method
   */
  public String getName(int key) {
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(key);
    if (cm == null) {
      return "OBSOLETE";
    } else {
      return cm.getMethod().toString();
    }
  }
}
