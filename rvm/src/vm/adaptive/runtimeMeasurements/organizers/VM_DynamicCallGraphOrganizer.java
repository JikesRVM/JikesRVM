/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_BaselineCompiledMethod;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.opt.*;
//-#if RVM_WITH_QUICK_COMPILER
import com.ibm.JikesRVM.quick.*;
//-#endif
import java.util.*;

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
 * 
 * @author Peter Sweeney 
 * @author Dave Grove
 * @modified Stephen Fink
 * @modified Michael Hind
 * @modified Matthew Arnold
 */
class VM_DynamicCallGraphOrganizer extends VM_Organizer {

  private final static boolean DEBUG = false;

  /*
   * buffer provides the communication channel between the edge listener
   * and the organizer.
   * The buffer contains an array of triples <callee, caller, address> where
   * the caller and callee are VM_CompiledMethodID's, and address identifies
   * the call site.
   * bufferSize is the number of triples contained in buffer.
   * The edge listener adds triples.  
   * At some point the listener deregisters itself and notifies the organizer 
   * by calling thresholdReached().
   */
  private int[] buffer;
  private int   bufferSize;
  private int   numberOfBufferTriples;

  /**
   * Constructor
   */
  VM_DynamicCallGraphOrganizer(VM_EdgeListener edgeListener) {
    listener = edgeListener;
    edgeListener.setOrganizer(this);
    makeDaemon(true);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    if (VM.LogAOSEvents) VM_AOSLogging.DCGOrganizerThreadStarted();

    numberOfBufferTriples = VM_Controller.options.DCG_SAMPLE_SIZE;
    numberOfBufferTriples *= VM_Scheduler.numProcessors;
    bufferSize = numberOfBufferTriples * 3;
    buffer     = new int[bufferSize];

    ((VM_EdgeListener)listener).setBuffer(buffer); 

    // Install the edge listener
    VM_RuntimeMeasurements.installTimerContextListener((VM_EdgeListener)listener);
  }

  /**
   * Method that is called when the sampling threshold is reached.
   * Process contents of buffer: 
   *    add call graph edges and increment their weights.
   */
  void thresholdReached() {
    if (DEBUG) VM.sysWriteln("DCG_Organizer.thresholdReached()");
    
    for (int i=0; i<bufferSize; i=i+3) {
      int calleeCMID = buffer[i+0];
      VM_CompiledMethod compiledMethod   = VM_CompiledMethods.getCompiledMethod(calleeCMID);
      if (compiledMethod == null) continue;
      VM_Method callee = compiledMethod.getMethod();
      if (callee.isRuntimeServiceMethod()) {
        if (DEBUG) VM.sysWrite("Skipping sample with runtime service callee");
        continue;
      }
      int callerCMID = buffer[i+1];
      compiledMethod   = VM_CompiledMethods.getCompiledMethod(callerCMID);
      if (compiledMethod == null) continue;
      VM_Method stackFrameCaller = compiledMethod.getMethod();
       
      int MCOffset = buffer[i+2];
      int bytecodeIndex = -1;
      VM_Method caller = null;

      switch (compiledMethod.getCompilerType()) {
      case VM_CompiledMethod.TRAP:
      case VM_CompiledMethod.JNI:
        if (DEBUG) VM.sysWrite("Skipping sample with TRAP/JNI caller");
        continue;
      case VM_CompiledMethod.BASELINE:
        {
          VM_BaselineCompiledMethod baseCompiledMethod = 
            (VM_BaselineCompiledMethod)compiledMethod;
          // note: the following call expects the offset in INSTRUCTIONS!
          bytecodeIndex = baseCompiledMethod.findBytecodeIndexForInstruction
            (MCOffset>>>VM.LG_INSTRUCTION_WIDTH);
          caller = stackFrameCaller;
        }
        break;
        //-#if RVM_WITH_QUICK_COMPILER
      case VM_CompiledMethod.QUICK:
        {
          VM_QuickCompiledMethod quickCompiledMethod = 
            (VM_QuickCompiledMethod)compiledMethod;
          // note: the following call expects the offset in INSTRUCTIONS!
          bytecodeIndex = quickCompiledMethod.findBytecodeIndexForInstruction
            (MCOffset>>>VM.LG_INSTRUCTION_WIDTH);
          caller = stackFrameCaller;
        }
        break;
        //-#endif
      case VM_CompiledMethod.OPT:
        {
          VM_OptCompiledMethod optCompiledMethod = (VM_OptCompiledMethod)compiledMethod;
          VM_OptMachineCodeMap mc_map = optCompiledMethod.getMCMap();
          try {
            bytecodeIndex = mc_map.getBytecodeIndexForMCOffset(MCOffset);
            if (bytecodeIndex == -1) {
              // this can happen we we sample a call 
              // to a runtimeSerivce routine. 
              // We aren't setup to inline such methods anyways, 
              // so skip the sample.
              if (DEBUG) {
                  VM.sysWrite("  *** SKIP SAMPLE ", stackFrameCaller.toString());
                  VM.sysWrite("@",compiledMethod.toString());
                  VM.sysWrite(" at MC offset ", MCOffset);
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
          } catch (OPT_OptimizingCompilerException e) {
            VM.sysWrite("***Error: SKIP SAMPLE: can't find bytecode index in OPT compiled "+
                        stackFrameCaller+"@"+compiledMethod+" at MC offset ",MCOffset);
            VM.sysWrite("!\n");
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            continue;  // skip sample
          }
          
          try {
            caller = mc_map.getMethodForMCOffset(MCOffset);
          } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            VM.sysWrite("  ***ERROR: getMethodForMCOffset(",MCOffset);
                 VM.sysWrite(") ArrayIndexOutOfBounds!\n");
            e.printStackTrace();
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            caller = stackFrameCaller;
            continue;
          } catch (OPT_OptimizingCompilerException e) {
            VM.sysWrite("***Error: SKIP SAMPLE: can't find caller in OPT compiled "+
                        stackFrameCaller+"@"+compiledMethod+" at MC offset ",MCOffset);
            VM.sysWrite("!\n");
            if (VM.ErrorsFatal) VM.sysFail("Exception in AI organizer.");
            continue;  // skip sample
          }

          if (caller == null) {
            VM.sysWrite("  ***ERROR: getMethodForMCOffset(",MCOffset);
            VM.sysWrite(") returned null!\n");
            caller = stackFrameCaller;
            continue;  // skip sample
          }
        }
        break;
      }

      // increment the call graph edge, adding it if needed
      VM_Controller.dcg.incrementEdge(caller, bytecodeIndex, callee);
    }
  }
}
