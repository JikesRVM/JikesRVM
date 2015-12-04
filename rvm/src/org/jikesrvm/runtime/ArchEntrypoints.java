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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.NormalMethod;

/**
 * Entrypoints that are specific to instruction architecture.
 */
public final class ArchEntrypoints {
  public static final String ArchCodeArrayName = "Lorg/jikesrvm/ArchitectureSpecific$CodeArray;";
  public static final String arch;

  static {
    if (VM.BuildForIA32) {
      arch = "ia32";
      saveVolatilesInstructionsField = null;
      restoreVolatilesInstructionsField = null;
      registersLRField = null;
      registersFPField =  EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
          "fp", "Lorg/vmmagic/unboxed/Address;");
      framePointerField = EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;",
          "framePointer", "Lorg/vmmagic/unboxed/Address;");
      hiddenSignatureIdField = EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;",
          "hiddenSignatureId", "I");
      arrayIndexTrapParamField = EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;",
          "arrayIndexTrapParam", "I");
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      arch = "ppc";
      saveVolatilesInstructionsField = EntrypointHelper.getField("Lorg/jikesrvm/" +
          arch + "/OutOfLineMachineCode;", "saveVolatilesInstructions", ArchCodeArrayName);
      restoreVolatilesInstructionsField = EntrypointHelper.getField("Lorg/jikesrvm/" +
          arch + "/OutOfLineMachineCode;", "restoreVolatilesInstructions", ArchCodeArrayName);
      registersLRField = EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
          "lr", "Lorg/vmmagic/unboxed/Address;");
      registersFPField = null;
      framePointerField = null;
      hiddenSignatureIdField = null;
      arrayIndexTrapParamField = null;
    }
  }

  public static final NormalMethod newArrayArrayMethod =
      EntrypointHelper.getMethod("Lorg/jikesrvm/" + ArchEntrypoints.arch + "/MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  public static final RVMField reflectiveMethodInvokerInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;",
               "reflectiveMethodInvokerInstructions",
               ArchCodeArrayName);
  public static final RVMField saveThreadStateInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "saveThreadStateInstructions", ArchCodeArrayName);
  public static final RVMField threadSwitchInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "threadSwitchInstructions", ArchCodeArrayName);
  public static final RVMField restoreHardwareExceptionStateInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;",
               "restoreHardwareExceptionStateInstructions",
               ArchCodeArrayName);
  public static final RVMField saveVolatilesInstructionsField;
  public static final RVMField restoreVolatilesInstructionsField;
  public static final RVMField trampolineRegistersField =
        EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;", "trampolineRegisters", "Lorg/jikesrvm/ArchitectureSpecific$Registers;");
  public static final RVMField hijackedReturnAddressField =
    EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;", "hijackedReturnAddress", "Lorg/vmmagic/unboxed/Address;");
  public static final RVMField registersIPField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "ip", "Lorg/vmmagic/unboxed/Address;");
  public static final RVMField registersFPRsField = EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "fprs", "[D");
  public static final RVMField registersGPRsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  public static final RVMField registersInUseField = EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "inuse", "Z");
  public static final RVMField registersLRField;
  public static final RVMField registersFPField;
  public static final RVMField framePointerField;
  public static final RVMField hiddenSignatureIdField;
  public static final RVMField arrayIndexTrapParamField;

  private ArchEntrypoints() {
    // prevent instantiation
  }

}
