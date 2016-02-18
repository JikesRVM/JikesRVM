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
package org.jikesrvm.architecture;

import org.jikesrvm.VM;

public final class ArchitectureFactory {

  public static AbstractRegisters createRegisters() {
    if (VM.BuildForIA32) {
      return new org.jikesrvm.ia32.Registers();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return new org.jikesrvm.ppc.Registers();
    }
  }

  public static void initOutOfLineMachineCode() {
    if (VM.BuildForIA32) {
      org.jikesrvm.ia32.OutOfLineMachineCode.init();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.ppc.OutOfLineMachineCode.init();
    }
  }
}
