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
package org.jikesrvm.adaptive.database.callgraph;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.measurements.Decayable;
import org.jikesrvm.adaptive.measurements.Reportable;
import org.jikesrvm.adaptive.util.UnResolvedCallSite;
import org.jikesrvm.adaptive.util.UnResolvedWeightedCallTargets;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;

/**
 * A partial call graph (PCG) is a partial mapping from callsites
 * to weighted targets.
 */
public final class PartialCallGraph implements Decayable, Reportable {

  /**
   * The dynamic call graph, which is a mapping from
   * CallSites to WeightedCallTargets.
   */
  private final HashMap<CallSite, WeightedCallTargets> callGraph =
      new HashMap<CallSite, WeightedCallTargets>();
  private final HashMap<UnResolvedCallSite, UnResolvedWeightedCallTargets> unresolvedCallGraph =
      new HashMap<UnResolvedCallSite, UnResolvedWeightedCallTargets>();

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
  public PartialCallGraph(double initialWeight) {
    seedWeight = initialWeight; // save for rest function
    totalEdgeWeights = initialWeight;
  }

  @Override
  public synchronized void reset() {
    callGraph.clear();
    totalEdgeWeights = seedWeight;
  }

  /**
   * @return sum of all edge weights in the partial call graph
   */
  public double getTotalEdgeWeights() { return totalEdgeWeights; }

  /**
   * Visit the WeightedCallTargets for every call site send them the
   * decay message.
   */
  @Override
  public synchronized void decay() {
    double rate = Controller.options.DCG_DECAY_RATE;
    // if we are dumping dynamic call graph, don't decay the graph
    if (Controller.options.DYNAMIC_CALL_FILE_OUTPUT != null) return;

    for (WeightedCallTargets ct : callGraph.values()) {
      ct.decay(rate);
    }
    totalEdgeWeights /= rate;
  }

  /**
   * @param caller caller method
   * @param bcIndex bytecode index in caller method
   * @return the WeightedCallTargets currently associated with the
   *         given caller bytecodeIndex pair.
   */
  public WeightedCallTargets getCallTargets(RVMMethod caller, int bcIndex) {
    MethodReference callerRef = caller.getMemberRef().asMethodReference();
    UnResolvedWeightedCallTargets unresolvedTargets =
        unresolvedCallGraph.get(new UnResolvedCallSite(callerRef, bcIndex));
    if (unresolvedTargets != null) {
      final RVMMethod fCaller = caller;
      final int fBcIndex = bcIndex;
      final PartialCallGraph pg = this;
      unresolvedTargets.visitTargets(new UnResolvedWeightedCallTargets.Visitor() {
        @Override
        public void visit(MethodReference calleeRef, double weight) {
          RVMMethod callee = calleeRef.getResolvedMember();
          if (callee != null) {
            pg.incrementEdge(fCaller, fBcIndex, callee, (float) weight);
          }
        }
      });
    }
    return getCallTargets(new CallSite(caller, bcIndex));
  }

  /**
   * @param callSite the callsite to look for
   * @return the WeightedCallTargets currently associated with callSite.
   */
  public synchronized WeightedCallTargets getCallTargets(CallSite callSite) {
    return callGraph.get(callSite);
  }

  /**
   * Increment the edge represented by the input parameters,
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   */
  public synchronized void incrementEdge(RVMMethod caller, int bcIndex, RVMMethod callee) {
    augmentEdge(caller, bcIndex, callee, 1);
  }

  /**
   * Increment the edge represented by the input parameters,
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   * @param weight   the frequency of this calling edge
   */
  public synchronized void incrementEdge(RVMMethod caller, int bcIndex, RVMMethod callee, float weight) {
    augmentEdge(caller, bcIndex, callee, (double) weight);
  }

  /**
   * For the calling edge we read from a profile, we may not have
   * the methods loaded in yet. Therefore, we will record the method
   * reference infomation first, the next time we resolved the method,
   * we will promote it into the regular call graph.
   * Increment the edge represented by the input parameters,
   * creating it if it is not already in the call graph.
   *
   * @param callerRef   method making the call
   * @param bcIndex     call site, if -1 then no call site is specified.
   * @param calleeRef   method called
   * @param weight      the frequency of this calling edge
   */
  public synchronized void incrementUnResolvedEdge(MethodReference callerRef, int bcIndex,
                                                   MethodReference calleeRef, float weight) {
    UnResolvedCallSite callSite = new UnResolvedCallSite(callerRef, bcIndex);
    UnResolvedWeightedCallTargets targets = unresolvedCallGraph.get(callSite);
    if (targets == null) {
      targets = UnResolvedWeightedCallTargets.create(calleeRef, weight);
      unresolvedCallGraph.put(callSite, targets);
    } else {
      UnResolvedWeightedCallTargets orig = targets;
      targets = targets.augmentCount(calleeRef, weight);
      if (orig != targets) {
        unresolvedCallGraph.put(callSite, targets);
      }
    }
  }

  /**
   * Increment the edge represented by the input parameters,
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   * @param weight   the frequency of this calling edge
   */
  private void augmentEdge(RVMMethod caller, int bcIndex, RVMMethod callee, double weight) {
    CallSite callSite = new CallSite(caller, bcIndex);
    WeightedCallTargets targets = callGraph.get(callSite);
    if (targets == null) {
      targets = WeightedCallTargets.create(callee, weight);
      callGraph.put(callSite, targets);
    } else {
      WeightedCallTargets orig = targets;
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
  @Override
  public synchronized void report() {
    System.out.println("Partial Call Graph");
    System.out.println("  Number of callsites " + callGraph.size() + ", total weight: " + totalEdgeWeights);
    System.out.println();

    TreeSet<CallSite> tmp = new TreeSet<CallSite>(new OrderByTotalWeight());
    tmp.addAll(callGraph.keySet());

    for (final CallSite cs : tmp) {
      WeightedCallTargets ct = callGraph.get(cs);
      ct.visitTargets(new WeightedCallTargets.Visitor() {
        @Override
        public void visit(RVMMethod callee, double weight) {
          System.out.println(weight + " <" + cs.getMethod() + ", " + cs.getBytecodeIndex() + ", " + callee + ">");
        }
      });
      System.out.println();
    }
  }

  /**
   * Dump all profile data to the given file
   */
  public synchronized void dumpGraph() {
    dumpGraph(Controller.options.DYNAMIC_CALL_FILE_OUTPUT);
  }

  /**
   * Dump all profile data to the given file
   * @param fn output file name
   */
  public synchronized void dumpGraph(String fn) {
    final BufferedWriter f;
    try {
      f = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fn), "ISO-8859-1"));
    } catch (IOException e) {
      VM.sysWrite("\n\nPartialCallGraph.dumpGraph: Error opening output file!!\n\n");
      return;
    }
    TreeSet<CallSite> tmp = new TreeSet<CallSite>(new OrderByTotalWeight());
    tmp.addAll(callGraph.keySet());

    for (final CallSite cs : tmp) {
      WeightedCallTargets ct = callGraph.get(cs);
      ct.visitTargets(new WeightedCallTargets.Visitor() {
        @Override
        public void visit(RVMMethod callee, double weight) {
          CodeArray callerArray = cs.getMethod().getCurrentEntryCodeArray();
          CodeArray calleeArray = callee.getCurrentEntryCodeArray();
          try {
            f.write("CallSite " +
                    cs.getMethod().getMemberRef() +
                    " " +
                    callerArray.length() +
                    " " +
                    +cs.getBytecodeIndex() +
                    " " +
                    callee.getMemberRef() +
                    " " +
                    +calleeArray.length() +
                    " weight: " +
                    weight +
                    "\n");
            f.flush();
          } catch (IOException exc) {
            System.err.println("I/O error writing to dynamic call graph profile.");
          }
        }
      });
    }
  }

  /**
   * Used to compare two call sites by total weight.
   */
  private final class OrderByTotalWeight implements Comparator<CallSite> {
    @Override
    public int compare(CallSite o1, CallSite o2) {
      if (o1.equals(o2)) return 0;
      double w1 = callGraph.get(o1).totalWeight();
      double w2 = callGraph.get(o2).totalWeight();
      if (w1 < w2) { return 1; }
      if (w1 > w2) { return -1; }
      // equal weights; sort lexicographically
      return o1.toString().compareTo(o2.toString());
    }
  }

}
