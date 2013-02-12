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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.EdgeListener;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.NonMoving;

/**
 * An organizer to build a dynamic call graph from call graph edge
 * samples.
 * <p>
 * It communicates with an edge listener through a
 * integer array, denoted buffer.  When this organizer is woken up
 * via threshold reached, it processes the sequence of triples
 * that are contained in buffer.
 * <p>
 * After processing the buffer and updating the dynamic call graph,
 * it optionally notifies the AdaptiveInliningOrganizer who is responsible
 * for analyzing the dynamic call graph for the purposes of
 * feedback-directed inlining.
 * <p>
 * Note: Since this information is intended to drive feedback-directed inlining,
 *       the organizer drops edges that are not relevant.  For example, one of
 *       the methods is a native method, or the callee is a runtime service
 *       routine and thus can't be inlined into its caller even if it is reported
 *       as hot.  Thus, the call graph may not contain some hot edges since they
 *       aren't viable inlining candidates. One may argue that this is not the right
 *       design.  Perhaps instead the edges should be present for profiling purposes,
 *       but not reported as inlining candidates to the
 * <p>
 * EXPECTATION: buffer is filled all the way up with triples.
 */
@NonMoving
public class DynamicCallGraphOrganizer extends Organizer {

  private static final boolean DEBUG = false;

  /**
   * buffer provides the communication channel between the edge listener
   * and the organizer.<p>
   * The buffer contains an array of triples {@code <callee, caller, address>} where
   * the caller and callee are CompiledMethodID's, and address identifies
   * the call site.
   * The edge listener adds triples.
   * At some point the listener deregisters itself and notifies the organizer
   * by calling thresholdReached().
   */
  private int[] buffer;
  /** the buffer's size, i.e. length of {@link #buffer} */
  private int bufferSize;
  /** the maximum number of triples contained in buffer */
  private int numberOfBufferTriples;

  /**
   * Countdown of times we have to have called thresholdReached before
   * we believe the call graph has enough samples that it is reasonable
   * to use it to guide profile-directed inlining.  When this value reaches 0,
   * we stop decrementing it and start letting other parts of the adaptive
   * system use the profile data.
   */
  private int thresholdReachedCount;

  /**
   * Constructs a new dynamic call graph organizer that will get its data from the given edge listener.
   * @param edgeListener the listener that provides data for this organizer
   */
  public DynamicCallGraphOrganizer(EdgeListener edgeListener) {
    listener = edgeListener;
    edgeListener.setOrganizer(this);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   * <p>
   * Uses either timer based sampling or counter based sampling,
   * depending on {@link Controller#options}.
   */
  @Override
  public void initialize() {
    if (Controller.options.cgCBS()) {
      numberOfBufferTriples = Controller.options.DCG_SAMPLE_SIZE * VM.CBSCallSamplesPerTick;
    } else {
      numberOfBufferTriples = Controller.options.DCG_SAMPLE_SIZE;
    }
    numberOfBufferTriples *= RVMThread.availableProcessors;
    bufferSize = numberOfBufferTriples * 3;
    buffer = new int[bufferSize];

    ((EdgeListener) listener).setBuffer(buffer);

    /* We're looking for a thresholdReachedCount such that when we reach the count,
     * a single sample contributes less than the AI_HOT_CALLSITE_THRESHOLD. In other words, we
     * want the inequality
     *   thresholdReachedCount * samplesPerInvocationOfThresholdReached > 1 / AI_HOT_CALLSITE_THRESHOLD
     * to be true.
     */
    thresholdReachedCount = (int)Math.ceil(1.0 /(numberOfBufferTriples * Controller.options.INLINE_AI_HOT_CALLSITE_THRESHOLD));;

    // Install the edge listener
    if (Controller.options.cgTimer()) {
      RuntimeMeasurements.installTimerContextListener((EdgeListener) listener);
    } else if (Controller.options.cgCBS()) {
      RuntimeMeasurements.installCBSContextListener((EdgeListener) listener);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, "Unexpected value of call_graph_listener_trigger");
    }
  }

  /**
   * Process contents of buffer:
   *    add call graph edges and increment their weights.
   */
  @Override
  void thresholdReached() {
    if (DEBUG) VM.sysWriteln("DCG_Organizer.thresholdReached()");

    for (int i = 0; i < bufferSize; i = i + 3) {
      int calleeCMID=0;
      // FIXME: This is necessary but hacky and may not even be correct.
      while (calleeCMID == 0) {
        calleeCMID = buffer[i + 0];
      }
      Magic.isync();
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(calleeCMID);
      if (compiledMethod == null) continue;
      RVMMethod callee = compiledMethod.getMethod();
      if (callee.isRuntimeServiceMethod()) {
        if (DEBUG) VM.sysWrite("Skipping sample with runtime service callee");
        continue;
      }
      int callerCMID = buffer[i + 1];
      compiledMethod = CompiledMethods.getCompiledMethod(callerCMID);
      if (compiledMethod == null) continue;
      RVMMethod stackFrameCaller = compiledMethod.getMethod();

      int MCOff = buffer[i + 2];
      Offset MCOffset = Offset.fromIntSignExtend(buffer[i + 2]);
      int bytecodeIndex = -1;
      RVMMethod caller = null;

      switch (compiledMethod.getCompilerType()) {
        case CompiledMethod.TRAP:
        case CompiledMethod.JNI:
          if (DEBUG) VM.sysWrite("Skipping sample with TRAP/JNI caller");
          continue;
        case CompiledMethod.BASELINE: {
          BaselineCompiledMethod baseCompiledMethod = (BaselineCompiledMethod) compiledMethod;
          // note: the following call expects the offset in INSTRUCTIONS!
          bytecodeIndex = baseCompiledMethod.findBytecodeIndexForInstruction(MCOffset);
          caller = stackFrameCaller;
        }
        break;
        case CompiledMethod.OPT: {
          OptCompiledMethod optCompiledMethod = (OptCompiledMethod) compiledMethod;
          OptMachineCodeMap mc_map = optCompiledMethod.getMCMap();
          try {
            bytecodeIndex = mc_map.getBytecodeIndexForMCOffset(MCOffset);
            if (bytecodeIndex == -1) {
              // this can happen we we sample a call
              // to a runtimeSerivce routine.
              // We aren't setup to inline such methods anyways,
              // so skip the sample.
              if (DEBUG) {
                VM.sysWrite("  *** SKIP SAMPLE ", stackFrameCaller.toString());
                VM.sysWrite("@", compiledMethod.toString());
                VM.sysWrite(" at MC offset ", MCOff);
                VM.sysWrite(" calling ", callee.toString());
                VM.sysWriteln(" due to invalid bytecodeIndex");
              }
              continue; // skip sample.
            }
          } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            VM.sysWrite("  ***ERROR: getBytecodeIndexForMCOffset(", MCOffset);
            VM.sysWrite(") ArrayIndexOutOfBounds!\n");
            e.printStackTrace();
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            caller = stackFrameCaller;
            continue;  // skip sample
          } catch (OptimizingCompilerException e) {
            VM.sysWrite("***Error: SKIP SAMPLE: can't find bytecode index in OPT compiled " +
                        stackFrameCaller +
                        "@" +
                        compiledMethod +
                        " at MC offset ", MCOff);
            VM.sysWrite("!\n");
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            continue;  // skip sample
          }

          try {
            caller = mc_map.getMethodForMCOffset(MCOffset);
          } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            VM.sysWrite("  ***ERROR: getMethodForMCOffset(", MCOffset);
            VM.sysWrite(") ArrayIndexOutOfBounds!\n");
            e.printStackTrace();
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            caller = stackFrameCaller;
            continue;
          } catch (OptimizingCompilerException e) {
            VM.sysWrite("***Error: SKIP SAMPLE: can't find caller in OPT compiled " +
                        stackFrameCaller +
                        "@" +
                        compiledMethod +
                        " at MC offset ", MCOff);
            VM.sysWrite("!\n");
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            continue;  // skip sample
          }

          if (caller == null) {
            VM.sysWrite("  ***ERROR: getMethodForMCOffset(", MCOffset);
            VM.sysWrite(") returned null!\n");
            caller = stackFrameCaller;
            continue;  // skip sample
          }
        }
        break;
      }

      // increment the call graph edge, adding it if needed
      Controller.dcg.incrementEdge(caller, bytecodeIndex, callee);
    }
    if (thresholdReachedCount > 0) {
      thresholdReachedCount--;
    }
  }

  /**
   * Checks if the dynamic call graph organizer has gathered and processed enough samples to support decisions.
   * @return {@code true} if enough data is available, {@code false} otherwise
   */
  public boolean someDataAvailable() {
    return thresholdReachedCount == 0;
  }
}

