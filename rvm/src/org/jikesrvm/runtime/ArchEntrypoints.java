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
  public static final NormalMethod newArrayArrayMethod =
      EntrypointHelper.getMethod("Lorg/jikesrvm/" + ArchEntrypoints.arch + "/MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  public static final String arch = VM.BuildForIA32 ? "ia32" : "ppc";
  public static final String ArchCodeArrayName = "Lorg/jikesrvm/ArchitectureSpecific$CodeArray;";
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
  public static final RVMField saveVolatilesInstructionsField =
      (VM.BuildForPowerPC) ?
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "saveVolatilesInstructions", ArchCodeArrayName) : null;
  public static final RVMField restoreVolatilesInstructionsField =
      (VM.BuildForPowerPC) ?
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "restoreVolatilesInstructions", ArchCodeArrayName) : null;

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
  public static final RVMField registersLRField =
      (VM.BuildForPowerPC) ? EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
                                      "lr",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
  public static final RVMField registersFPField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
                                   "fp",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  public static final RVMField framePointerField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;",
                                   "framePointer",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  public static final RVMField hiddenSignatureIdField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;", "hiddenSignatureId", "I") : null;
  public static final RVMField arrayIndexTrapParamField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/RVMThread;", "arrayIndexTrapParam", "I") : null;

  private ArchEntrypoints() {
    // prevent instantiation
  }

}
