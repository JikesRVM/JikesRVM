/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.*;
import java.io.*;

/**
 * Collection of static methods to assist with adaptive inlining.
 *
 * @author Stephen Fink
 * @modified Michael Hind
 * @modified Peter F. Sweeney
 * @modified Matthew Arnold
 * @modified Dave Grove
 */
public class VM_AdaptiveInlining {
  final private static boolean DEBUG = false;

  /** Interface */

  /**
   * Set parameters.
   * Must be called after parsing command-line.
   */
  static void boot(VM_AOSOptions options) {
    setHotEdgeThreshold(options.INITIAL_AI_THRESHOLD);

    // create and register the dcg as a decayable object
    dcg = new VM_PartialCallGraph(); 
    VM_RuntimeMeasurements.registerDecayableObject(dcg);

    plan = new OPT_ContextFreeInlinePlan();
    if (options.USE_OFFLINE_INLINE_PLAN) {
      try {
        //  Read the plan from disk
        String fn = options.OFFLINE_INLINE_PLAN_NAME;
        LineNumberReader in = new LineNumberReader(new FileReader(fn));
        VM_AdaptiveInlining.plan.readObject(in);
      } catch (Exception e) {
        e.printStackTrace();
        throw new OPT_OptimizingCompilerException("Inline",
                                                  "Error creating offline plan");
      }
      
      // now make an inlining oracle
      VM_RuntimeCompiler.offlineInlineOracle = 
        new OPT_AdaptiveInlineOracle(plan);
    }
  }

  /** 
   * Get the inlining oracle for a method
   */
  static OPT_AdaptiveInlineOracle getInlineOracle(VM_Method m) {
    OPT_AdaptiveInlineOracle oracle = null;
    synchronized(plan) {
      if (VM_Controller.options.ADAPTIVE_INLINING) {
        oracle = new OPT_AdaptiveInlineOracle(plan);
      }
    }
    return oracle;
  }

  /**
   * Return a pointer to the dynamic call graph
   */
  static VM_PartialCallGraph getPartialCallGraph() { return dcg; }

  /**
   * Recompute the set of "hot" edges used for adaptive inlining.
   */
  private static int recomputeHotEdgesTrips = 0;
  public static Vector recomputeHotEdges() {
    if(DEBUG) {
      VM.sysWrite(" VM_AdaptiveInlining.recomputeHotEdges() hotEdgeThreshold: "
                  + hotEdgeThreshold +" "+(++recomputeHotEdgesTrips)+"\n");
    }
    Vector vectorOfTriples = new Vector();
      
    /** Compute the set of hot edges */
    for (java.util.Iterator i = dcg.getEdges(); i.hasNext(); ) {
      VM_CallSiteTriple triple = (VM_CallSiteTriple)i.next();
      if(DEBUG)VM.sysWrite(" :"+ triple +"\n");
         
      double weight = triple.getWeight()/nYieldPointsTaken;
      if (weight >= hotEdgeThreshold) { 
        VM_Method caller = triple.getCaller();
        VM_Method callee = triple.getCallee();
        int bcIndex = triple.getBytecodeIndex();
        plan.addRule(caller,bcIndex,callee);
        vectorOfTriples.addElement(triple);
      }
    }

    if(DEBUG){ VM.sysWrite("\nEdges found:\n");
    for (int i=0; i<vectorOfTriples.size(); i++) {
      VM_CallSiteTriple triple = 
        (VM_CallSiteTriple)vectorOfTriples.elementAt(i);
      VM.sysWrite((i+1)+": "+triple.toString()+"\n");
    }  }

    if(DEBUG)VM.sysWrite(" VM_AdaptiveInlining.recomputeHotEdges() exit\n");

    // now adjust the AI Threshold
    double newThreshold = Math.max(hotEdgeThreshold/2.0,
                                   VM_Controller.options.FINAL_AI_THRESHOLD);
    setHotEdgeThreshold(newThreshold);

    return vectorOfTriples;
  }


  /**
   * Hook to allow the AdaptiveInliningOracle to record that the opt compiler
   * was aware that a call edge was hot, but still refused to inline it.
   */
  public static void recordRefusalToInlineHotEdge(int cmid, 
                                           VM_Method caller, 
                                           int bcX, 
                                           VM_Method callee) {
    VM_CallSiteTriple edge = new VM_CallSiteTriple(caller, bcX, callee);
    Integer key = new Integer(cmid);
    NonInlinedElement oldEdges = (NonInlinedElement)nonInlinedEdges.get(key);
    NonInlinedElement p = oldEdges;
    while (p != null) {
      if (p.cmid == cmid && 
          p.edge.getCaller() == edge.getCaller() &&
          p.edge.getCallee() == edge.getCallee() &&
          p.edge.getBytecodeIndex() == edge.getBytecodeIndex()) {
        return;
      }
      p = p.next;
    }
    if (DEBUG) 
      VM.sysWrite("Recording that "+edge+" was not inlined into "+cmid+"\n");
    nonInlinedEdges.put(key, new NonInlinedElement(cmid, edge, oldEdges));
  }


  /**
   * Allow AI Organizer to check to see if the compiler previously refused to inline a hot edge.
   */
  static boolean knownNonInlinedEdge(int cmid, VM_CallSiteTriple edge) {
    Integer key = new Integer(cmid);
    NonInlinedElement oldEdges = (NonInlinedElement)nonInlinedEdges.get(key);
    NonInlinedElement p = oldEdges;
    while (p != null) {
      if (p.cmid == cmid && 
          p.edge.getCaller() == edge.getCaller() &&
          p.edge.getCallee() == edge.getCallee() &&
          p.edge.getBytecodeIndex() == edge.getBytecodeIndex()) {
        if (DEBUG) VM.sysWrite ("Squashing "+edge+" into "+cmid);
        return true;
      }
      p = p.next;
    }
    return false;
  }

  /**
   * Called when a compiled version becomes obsolete 
   * (to clear the now irrelevant data)
   */
  static void clearNonInlinedEdges(int cmid) {
    Integer key = new Integer(cmid);
    nonInlinedEdges.remove(key);
  }
    
  // Mapping from Integer(CMID) to nonInlinedElement
  private static java.util.HashMap nonInlinedEdges = new java.util.HashMap();
  static class NonInlinedElement {
    int cmid;
    VM_CallSiteTriple edge;
    NonInlinedElement next;
    NonInlinedElement(int c, VM_CallSiteTriple e, NonInlinedElement n) {
      cmid = c;
      edge = e;
      next = n;
    }
  }


  /**
   * Set the threshold for hot edges.
   * If threshold is x, then an edge must accout for at least x% of
   * the total number of dynamic calls to be "hot"
   */
  static void setHotEdgeThreshold(double x) {
    hotEdgeThreshold = x;
  }

  /**
   */
  static void report() {
    VM.sysWrite("Adaptive inlining context-free plan:\n");
    synchronized(plan) {
      VM.sysWrite(plan.toString() + "\n");
    }
  }

  /**
   * record that more yield points have been taken
   * @param n
   */
  static void incrementNumYieldPoints(double n) {
    synchronized(plan) {
      nYieldPointsTaken += n; 
    }
  }
  /**
   * decay the number of yield points taken
   */
  static void decay() {
    synchronized(plan) {
      nYieldPointsTaken /= VM_Controller.options.DECAY_RATE; 
    }
  }
  /**
   */
  static double getNumYieldPoints() { return nYieldPointsTaken; }

  /** Implementation */
  static VM_PartialCallGraph dcg;
  static OPT_ContextFreeInlinePlan plan;
  static double hotEdgeThreshold = 1.0;
  // (decayed) number of yield points taken: maintained by AI organizer
  static double nYieldPointsTaken = 0.0; 
}
