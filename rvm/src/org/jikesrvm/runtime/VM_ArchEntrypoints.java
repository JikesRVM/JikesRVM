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
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.VM_NormalMethod;

/**
 * Entrypoints that are specific to instruction architecture.
 */
public interface VM_ArchEntrypoints {
  VM_NormalMethod newArrayArrayMethod =
      VM_EntrypointHelper.getMethod("Lorg/jikesrvm/" + VM_ArchEntrypoints.arch + "/VM_MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  String arch = VM.BuildForIA32 ? "ia32" : "ppc";
  String ArchCodeArrayName = "Lorg/jikesrvm/ArchitectureSpecific$CodeArray;";
  RVMField reflectiveMethodInvokerInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;",
               "reflectiveMethodInvokerInstructions",
               ArchCodeArrayName);
  RVMField saveThreadStateInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;", "saveThreadStateInstructions", ArchCodeArrayName);
  RVMField threadSwitchInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;", "threadSwitchInstructions", ArchCodeArrayName);
  RVMField restoreHardwareExceptionStateInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;",
               "restoreHardwareExceptionStateInstructions",
               ArchCodeArrayName);
  RVMField registersIPField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "ip", "Lorg/vmmagic/unboxed/Address;");
  RVMField registersFPRsField = VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "fprs", "[D");
  RVMField registersGPRsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  RVMField registersInUseField = VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "inuse", "Z");
  RVMField registersLRField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;",
                                      "lr",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField toSyncProcessorsField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "toSyncProcessors", "I") : null;
  RVMField registersFPField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;",
                                   "fp",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField framePointerField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;",
                                   "framePointer",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField hiddenSignatureIdField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;", "hiddenSignatureId", "I") : null;
  RVMField arrayIndexTrapParamField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;", "arrayIndexTrapParam", "I") : null;
  RVMField JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;",
                                      "savedJTOC",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
}
