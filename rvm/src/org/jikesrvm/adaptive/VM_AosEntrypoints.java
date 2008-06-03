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
package org.jikesrvm.adaptive;

import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_NormalMethod;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getField;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getMethod;

/**
 * Entrypoints that are valid when the build includes the adaptive optimization system.
 */
public interface VM_AosEntrypoints {
  VM_NormalMethod osrGetRefAtMethod =
      getMethod("Lorg/jikesrvm/osr/OSR_ObjectHolder;", "getRefAt", "(II)Ljava/lang/Object;");
  VM_NormalMethod osrCleanRefsMethod = getMethod("Lorg/jikesrvm/osr/OSR_ObjectHolder;", "cleanRefs", "(I)V");
  VM_Field methodListenerNumSamplesField =
      getField("Lorg/jikesrvm/adaptive/measurements/listeners/VM_MethodListener;", "numSamples", "I");
  VM_Field edgeListenerUpdateCalledField =
      getField("Lorg/jikesrvm/adaptive/measurements/listeners/VM_EdgeListener;", "updateCalled", "I");
  VM_Field edgeListenerSamplesTakenField =
      getField("Lorg/jikesrvm/adaptive/measurements/listeners/VM_EdgeListener;", "samplesTaken", "I");
  VM_Field yieldCountListenerNumYieldsField =
      getField("Lorg/jikesrvm/adaptive/measurements/listeners/VM_YieldCounterListener;", "numYields", "I");
  VM_Field counterArrayManagerCounterArraysField =
      getField("Lorg/jikesrvm/adaptive/measurements/instrumentation/VM_CounterArrayManager;", "counterArrays", "[[D");
  VM_Field invocationCountsField =
      getField("Lorg/jikesrvm/adaptive/recompilation/VM_InvocationCounts;", "counts", "[I");
  VM_NormalMethod invocationCounterTrippedMethod =
      getMethod("Lorg/jikesrvm/adaptive/recompilation/VM_InvocationCounts;", "counterTripped", "(I)V");
  VM_Field globalCBSField =
      getField("Lorg/jikesrvm/adaptive/recompilation/instrumentation/VM_CounterBasedSampling;", "globalCounter", "I");
  VM_Field processorCBSField = getField("Lorg/jikesrvm/scheduler/VM_Processor;", "processor_cbs_counter", "I");
  VM_Field cbsResetValueField =
      getField("Lorg/jikesrvm/adaptive/recompilation/instrumentation/VM_CounterBasedSampling;", "resetValue", "I");
  VM_Field specializedMethodsField =
      getField("Lorg/jikesrvm/compilers/opt/SpecializedMethodPool;",
               "specializedMethods",
               "[Lorg/jikesrvm/ArchitectureSpecific$CodeArray;");
}
