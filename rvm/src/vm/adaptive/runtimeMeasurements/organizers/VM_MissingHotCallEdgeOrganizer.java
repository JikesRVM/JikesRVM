/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.*;

/**
 * An organizer that periodically examines the hot edges in the dynamic call
 * graph and the hot max opt level methods and notifies the controller of
 * any hot max opt level method that could be recompiled to enable the inlining
 * of a hot call edge.
 * 
 * @author Dave Grove
 */
class VM_MissingHotCallEdgeOrganizer extends VM_Organizer {

  private final static boolean DEBUG = false;

  /**
   * Constructor
   */
  VM_MissingHotCallEdgeOrganizer() {
    makeDaemon(true);
  }

  protected void initialize() {
    listener = new VM_YieldCounterListener(VM_Controller.options.AI_MISSING_EDGE_FREQUENCY);
    listener.setOrganizer(this);
    VM_RuntimeMeasurements.installTimerNullListener((VM_NullListener)listener);
  }
  
  /**
   * Inspect all hot max opt level methods and compute the total
   * weight of non-inlined edges that if the method were recompiled
   * would be inlined into the method.
   * If any methods with candidate edges are found, notify the controller.
   */
  void thresholdReached() {
    VM_MethodCountSet hotMethods = 
      VM_Controller.methodSamples.collectHotMethods(VM_Controller.options.MAX_OPT_LEVEL,
                                                    VM_Controller.options.AI_METHOD_HOTNESS_THRESHOLD);

    if (DEBUG) VM.sysWriteln("\nVM_AIByEdgeOrganizer.findMethodsToRecompile() "
                             + hotMethods.cms.length);

    // Consider each hot max opt level method
    for (int i=0; i<hotMethods.cms.length; i++) {
      final VM_CompiledMethod hotMethod = hotMethods.cms[i];
      final double numSamples           = hotMethods.counters[i];
      final VM_OptMachineCodeMap mcMap  = ((VM_OptCompiledMethod)hotMethod).getMCMap();
      double edgeHotness          = 0.0;

      if (DEBUG) VM.sysWrite(" Process hot method: "+
                             hotMethod.getMethod()+" with "+numSamples+"\n");
       
      if (!hotMethod.getMethod().isInterruptible()) {
        // This is required because a very small subset of
        // uninterruptible methods need to have their code in a
        // non-moving heap.  For now, we use this simple, but
        // conservative test to avoid trouble.
        if (DEBUG) VM.sysWriteln("Not scanning uninterruptible method "+hotMethod.getMethod());
        continue;
      }

      // Get the non-inlined call sites from the machine code map
      // and estimate the total benefit available by inlining them.
      ArrayList edges = mcMap.getNonInlinedCallSites();
      if (edges == null) continue; 
      for (Iterator j = edges.iterator(); j.hasNext();) {
        final VM_CallSite cs = (VM_CallSite)j.next();
        if (DEBUG) VM.sysWriteln("Considering callsite "+cs);
        final VM_WeightedCallTargets ct = VM_Controller.dcg.getCallTargets(cs);
        if (ct != null) {
          final double[] dummy = new double[1]; //GACK. Real closures anyone??
          ct.visitTargets(new VM_WeightedCallTargets.Visitor() {
              public void visit(VM_MethodReference target, double weight) {
                if (VM_AdaptiveInlining.belowSizeThresholds(target, weight)) {
                  if (DEBUG) VM.sysWriteln(" FOUND MISSING EDGE:"+cs+" ==> "+target);
                  dummy[0] += weight;
                  if (VM.LogAOSEvents) 
                    VM_AOSLogging.inliningOpportunityDetected(hotMethod, 
                                                              numSamples, 
                                                              cs,
                                                              target);
                }
              }
            });
          edgeHotness += dummy[0];
        }
      }

      // Notify the controller if we found candidate edges in hotMethod
      if (edgeHotness > 0) {
        edgeHotness = VM_AdaptiveInlining.adjustedWeight(edgeHotness);
        double boost =  1.0 + (VM_Controller.options.MAX_EXPECTED_AI_BOOST * edgeHotness);
        VM_AINewHotEdgeEvent event = new VM_AINewHotEdgeEvent(hotMethod, numSamples, boost);
        VM_Controller.controllerInputQueue.insert(numSamples, event);
        if (VM.LogAOSEvents) VM_AOSLogging.controllerNotifiedForInlining(hotMethod,
                                                                         numSamples, 
                                                                         boost);
      }
    }
  }
}
