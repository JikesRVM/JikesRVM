/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.*;

import java.util.*;
import java.io.*;

/**
 * A partial call graph (PCG) is a partial mapping from callsites
 * to weighted targets.
 *
 * @author Dave Grove
 */
public final class VM_PartialCallGraph implements VM_Decayable,
                                                  VM_Reportable {

  /**
   * The dynamic call graph, which is a mapping from
   * VM_CallSites to VM_WeightedCallTargets.
   */
  private final HashMap callGraph = new HashMap();

  /**
   * sum of all edge weights in the call graph
   */
  private double totalEdgeWeights;

  /**
   * Initial seed weight; saved for use in the reset method
   */
  private final double seedWeight;
  
  /**
   * Create a partial call graph.
   * @param initialWeight an initial value for totalEdgeWeights.
   *        Used by AOS to cause inlining based in the dynamic call graph
   *        to initially be conservative until sufficient samples have
   *        accumulated that there is more confidence in the accuracy
   *        of the call graph.
   */
  VM_PartialCallGraph(double initialWeight) {
    seedWeight = initialWeight; // save for rest function
    totalEdgeWeights = initialWeight;
  }

  /**
   * Reset data
   */
  public synchronized void reset() {
    callGraph.clear();
    totalEdgeWeights = seedWeight;
  }
  
  /**
   * @return sum of all edge weights in the partial call graph
   */
  double getTotalEdgeWeights() { return totalEdgeWeights; }

  /**
   * Visit the WeightedCallTargets for every call site send them the
   * decay message.
   */
  public synchronized void decay() { 
    double rate = VM_Controller.options.DCG_DECAY_RATE;
    for (Iterator iterator = callGraph.values().iterator(); iterator.hasNext();) {
      VM_WeightedCallTargets ct = (VM_WeightedCallTargets)iterator.next();
      ct.decay(rate);
    }
    totalEdgeWeights /= rate;
  }
  
  /**
   * @param caller caller method
   * @param bcIndex bytecode index in caller method
   * @return the VM_WeightedCallTargets currently associated with the
   *         given caller bytecodeIndex pair.
   */
  public VM_WeightedCallTargets getCallTargets(VM_Method caller, int bcIndex) {
    return getCallTargets(new VM_CallSite(caller, bcIndex));
  }

  /**
   * @param callSite the callsite to look for
   * @return the VM_WeightedCallTargets currently associated with callSite.
   */
  public synchronized VM_WeightedCallTargets getCallTargets(VM_CallSite callSite) {
    return (VM_WeightedCallTargets)callGraph.get(callSite);
  }

  /**
   * Increment the edge represented by the input parameters, 
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   */
  public synchronized void incrementEdge(VM_Method caller, int bcIndex, VM_Method callee) {
    augmentEdge(caller, bcIndex, callee, 1);
  }

  private void augmentEdge(VM_Method caller,
                           int bcIndex,
                           VM_Method callee,
                           double weight) {
    VM_CallSite callSite = new VM_CallSite(caller, bcIndex);
    VM_WeightedCallTargets targets = (VM_WeightedCallTargets)callGraph.get(callSite);
    if (targets == null) {
      targets = VM_WeightedCallTargets.create(callee, weight);
      callGraph.put(callSite, targets);
    } else {
      VM_WeightedCallTargets orig = targets;
      targets = targets.augmentCount(callee, weight);
      if (orig != targets) {
        callGraph.put(callSite, targets);
      }
    }
    totalEdgeWeights += weight;
  }

  /**
   * Dump out set of edges in sorted order.
   */
  public synchronized void report() {
    System.out.println("Partial Call Graph");
    System.out.println("  Number of callsites "+callGraph.size()+
                       ", total weight: "+totalEdgeWeights);
    System.out.println();
    
    TreeSet tmp = new TreeSet(new Comparator() {
        public int compare(Object o1, Object o2) {
          if (o1.equals(o2)) return 0;
          double w1 = ((VM_WeightedCallTargets)(callGraph.get(o1))).totalWeight();
          double w2 = ((VM_WeightedCallTargets)(callGraph.get(o2))).totalWeight();
          if (w1 < w2) { return 1; }
          if (w1 > w2) { return -1; }
          // equal weights; sort lexicographically
          return o1.toString().compareTo(o2.toString());
        }
      });
    tmp.addAll(callGraph.keySet());

    for (Iterator i = tmp.iterator(); i.hasNext();) {
      final VM_CallSite cs = (VM_CallSite)i.next();
      VM_WeightedCallTargets ct = (VM_WeightedCallTargets)callGraph.get(cs);
      ct.visitTargets(new VM_WeightedCallTargets.Visitor() {
          public void visit(VM_Method callee, double weight) {
            System.out.println(weight+" <"+cs.getMethod()+", "+cs.getBytecodeIndex()+", "+callee+">");
          }
        });
      System.out.println();
    }
  }
}
