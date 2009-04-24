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
package org.jikesrvm.adaptive;

import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.NormalMethod;
import static org.jikesrvm.runtime.EntrypointHelper.getField;
import static org.jikesrvm.runtime.EntrypointHelper.getMethod;

/**
 * Entrypoints that are valid when the build includes the adaptive optimization system.
 */
public interface AosEntrypoints {
  NormalMethod osrGetRefAtMethod =
      getMethod(org.jikesrvm.osr.ObjectHolder.class, "getRefAt", "(II)Ljava/lang/Object;");
  NormalMethod osrCleanRefsMethod = getMethod(org.jikesrvm.osr.ObjectHolder.class, "cleanRefs", "(I)V");
  RVMField methodListenerNumSamplesField =
      getField(org.jikesrvm.adaptive.measurements.listeners.MethodListener.class, "numSamples", int.class);
  RVMField edgeListenerUpdateCalledField =
      getField(org.jikesrvm.adaptive.measurements.listeners.EdgeListener.class, "updateCalled", int.class);
  RVMField edgeListenerSamplesTakenField =
      getField(org.jikesrvm.adaptive.measurements.listeners.EdgeListener.class, "samplesTaken", int.class);
  RVMField yieldCountListenerNumYieldsField =
      getField(org.jikesrvm.adaptive.measurements.listeners.YieldCounterListener.class, "numYields", int.class);
  RVMField counterArrayManagerCounterArraysField =
      getField(org.jikesrvm.adaptive.measurements.instrumentation.CounterArrayManager.class, "counterArrays", double[][].class);
  RVMField invocationCountsField =
      getField(org.jikesrvm.adaptive.recompilation.InvocationCounts.class, "counts", int[].class);
  NormalMethod invocationCounterTrippedMethod =
      getMethod(org.jikesrvm.adaptive.recompilation.InvocationCounts.class, "counterTripped", "(I)V");
  RVMField globalCBSField =
      getField(org.jikesrvm.adaptive.recompilation.instrumentation.CounterBasedSampling.class, "globalCounter", int.class);
  RVMField threadCBSField = getField(org.jikesrvm.scheduler.RVMThread.class, "thread_cbs_counter", int.class);
  RVMField cbsResetValueField =
      getField(org.jikesrvm.adaptive.recompilation.instrumentation.CounterBasedSampling.class, "resetValue", int.class);
  RVMField specializedMethodsField =
      getField(org.jikesrvm.compilers.opt.specialization.SpecializedMethodPool.class,
               "specializedMethods",
               org.jikesrvm.ArchitectureSpecific.CodeArray[].class);
}
