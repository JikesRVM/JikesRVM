/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.adaptive.*;
/**
 * Maintain statistic information about on stack replacement events
 *
 * @author Feng Qian
 */
public class OSR_Profiler implements VM_Callbacks.ExitMonitor {

  private static int invalidations = 0; 
  private static boolean registered = false;
 
  public void notifyExit(int value) {
    VM.sysWriteln("OSR invalidations "+invalidations);
  }

  // we know which assumption is invalidated
  // current we only reset the root caller method to be recompiled.
  public static void notifyInvalidation(OSR_ExecutionState state) { 

    if (!registered && VM.MeasureCompilation) {
      registered = true;
      VM_Callbacks.addExitMonitor(new OSR_Profiler());
    }
   
        if (VM.TraceOnStackReplacement || VM.MeasureCompilation) {
          OSR_Profiler.invalidations ++;
        }

        // find the root state
        while (state.callerState != null) {
          state = state.callerState;
        }
        
        // only invalidate the root state 
    invalidateState(state);
  }

  // invalidate an execution state    
  private synchronized static void invalidateState(OSR_ExecutionState state) {
        // step 1: invalidate the compiled method with this OSR assumption
    //         how does this affect the performance?
    VM_CompiledMethod mostRecentlyCompiledMethod = 
      VM_CompiledMethods.getCompiledMethod(state.cmid);
        
    if (VM.VerifyAssertions) {
          VM._assert(mostRecentlyCompiledMethod.getMethod() == state.meth);
        }
        
        // check if the compiled method is the latest still the latest one
        // this is necessary to check because the same compiled method may
        // be invalidated in more than one thread at the same time
        if (mostRecentlyCompiledMethod != state.meth.getCurrentCompiledMethod()) {
          return;
        }
        
    // make sure the compiled method is an opt one
    if (!(mostRecentlyCompiledMethod instanceof VM_OptCompiledMethod)) {
          return;
    }

        // reset the compiled method to null first, if other thread invokes
        // this method before following opt recompilation, it can avoid OSR
        state.meth.invalidateCompiledMethod(mostRecentlyCompiledMethod);
        
    // a list of state from callee -> caller
    if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("OSR "+OSR_Profiler.invalidations+" : "
                                                +state.bcIndex+"@"+state.meth);
    }
        
        // simply reset the compiled method to null is not good
    // for long run loops, because invalidate may cause
    // the method falls back to the baseline again...
        // NOW, we look for the previous compilation plan, and reuse
        // the compilation plan.
    boolean recmplsucc = false;
    if (VM_Controller.enabled) {
      VM_ControllerPlan ctrlplan = VM_ControllerMemory.findMatchingPlan(mostRecentlyCompiledMethod);
      if (ctrlplan != null) {
        OPT_CompilationPlan cmplplan= ctrlplan.getCompPlan();
        
        if (VM.VerifyAssertions) {VM._assert(cmplplan.getMethod() == state.meth);}
        
        // for invalidated method, we donot perform OSR guarded inlining anymore.
        // the OPT_Options object may be shared by several methods,
        // we have to reset it back
        boolean savedOsr = cmplplan.options.OSR_GUARDED_INLINING;
        cmplplan.options.OSR_GUARDED_INLINING = false;
        int newcmid = VM_RuntimeCompiler.recompileWithOpt(cmplplan);
        cmplplan.options.OSR_GUARDED_INLINING = savedOsr;
        
        if (newcmid != -1) {
          VM_AOSLogging.debug("recompiling state with opt succeeded " + state.cmid);
          VM_AOSLogging.debug("new cmid " + newcmid);
          
          // transfer hotness to the new cmid
          double oldSamples = VM_Controller.methodSamples.getData(state.cmid);
          VM_Controller.methodSamples.reset(state.cmid);
          VM_Controller.methodSamples.augmentData(newcmid,oldSamples);
          
          recmplsucc=true;
          if (VM.TraceOnStackReplacement) {
            VM.sysWriteln("  recompile "+state.meth
                          +" at -O"+cmplplan.options.getOptLevel());
          }
        }
      }
    } 
    
    if (!recmplsucc) {
      int newcmid = VM_RuntimeCompiler.recompileWithOpt(state.meth);
      if (newcmid == -1) {
        if (VM.TraceOnStackReplacement) {VM.sysWriteln("  opt recompilation failed!");}
        state.meth.invalidateCompiledMethod(mostRecentlyCompiledMethod);      
      }
    }
    
    if (VM.TraceOnStackReplacement) {VM.sysWriteln("  opt recompilation done!");}
  }
}
