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

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.VM;

/**
 * Entrypoints that are specific to instruction architecture.
 */
public interface VM_ArchEntrypoints {
  VM_NormalMethod newArrayArrayMethod =
      VM_EntrypointHelper.getMethod("Lorg/jikesrvm/" + VM_ArchEntrypoints.arch + "/VM_MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  String arch = VM.BuildForIA32 ? "ia32" : "ppc";
  String ArchCodeArrayName = "Lorg/jikesrvm/ArchitectureSpecific$VM_CodeArray;";
  VM_Field reflectiveMethodInvokerInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;",
               "reflectiveMethodInvokerInstructions",
               ArchCodeArrayName);
  VM_Field saveThreadStateInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;", "saveThreadStateInstructions", ArchCodeArrayName);
  VM_Field threadSwitchInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;", "threadSwitchInstructions", ArchCodeArrayName);
  VM_Field restoreHardwareExceptionStateInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;",
               "restoreHardwareExceptionStateInstructions",
               ArchCodeArrayName);
  VM_Field invokeNativeFunctionInstructionsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_OutOfLineMachineCode;",
               "invokeNativeFunctionInstructions",
               ArchCodeArrayName);
  VM_Field registersIPField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "ip", "Lorg/vmmagic/unboxed/Address;");
  VM_Field registersFPRsField = VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "fprs", "[D");
  VM_Field registersGPRsField =
      VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  VM_Field registersInUseField = VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;", "inuse", "Z");
  VM_Field registersLRField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;",
                                      "lr",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
  VM_Field toSyncProcessorsField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Scheduler;", "toSyncProcessors", "I") : null;
  VM_Field registersFPField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/VM_Registers;",
                                   "fp",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  VM_Field jtocField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;",
                                   "jtoc",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  VM_Field framePointerField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;",
                                   "framePointer",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  VM_Field hiddenSignatureIdField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;", "hiddenSignatureId", "I") : null;
  VM_Field arrayIndexTrapParamField =
      (VM.BuildForIA32) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/scheduler/VM_Processor;", "arrayIndexTrapParam", "I") : null;
  VM_Field JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? VM_EntrypointHelper.getField("Lorg/jikesrvm/jni/VM_JNIEnvironment;",
                                      "savedJTOC",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
}
