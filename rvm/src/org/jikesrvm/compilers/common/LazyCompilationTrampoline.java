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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class LazyCompilationTrampoline {

  public static CodeArray getInstructions() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.LazyCompilationTrampoline.instructions;
    } else {
      return org.jikesrvm.ppc.LazyCompilationTrampoline.instructions;
    }
  }
}
