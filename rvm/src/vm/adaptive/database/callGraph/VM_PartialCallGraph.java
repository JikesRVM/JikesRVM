/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Method;

/**
 * A partial call graph (PCG) is implemented as a set of edges and each
 * edge is represented as a VM_CallSiteTriple object.
 * A PCG's internal representation is a HashMap of edges:
 *      hashCodeOf(E) -> E where 
 *             E is VM_CallSiteTriple<caller,bytecodeOffset,callee>
 *
 * @author Peter F. Sweeney
 * @modified Michael Hind
 * @date   24 May 2000
 */

public final class VM_PartialCallGraph implements VM_Decayable {

  public static final boolean DEBUG = false;
  
  /**
   * Constructor
   */
  VM_PartialCallGraph() {
    findTriples = new java.util.HashMap();
    totalEdgeWeights = 0.0;
  }
  
  /**
   * Return an iterator over the edges in the PCG
   *
   * @return iterator over edges 
   */
  java.util.Iterator getEdges() {
    return findTriples.values().iterator();
  }
  
  /**
   *  Visit each edge and decay its weight. 
   */
  public void decay() { 
    if (DEBUG) {VM.sysWrite(" Before decay\n");  dump(); }
    
    double rate = VM_Controller.options.DECAY_RATE;
    
    synchronized(findTriples) {
      for (java.util.Iterator iterator = getEdges(); iterator.hasNext();) {
        VM_CallSiteTriple triple = (VM_CallSiteTriple)iterator.next();
        triple.decayWeight(rate);
      }
      totalEdgeWeights /= rate;
    }
    
    if (DEBUG) {VM.sysWrite(" After decay\n");  dump(); }
  }
  
  /**
   * Increment the edge represented by the input parameters, 
   * creating it if needed.
   *
   * @param caller   method making the call
   * @param bytecode call site, if -1 then no call site is specified.
   * @param callee   method called
   */
  public void incrementEdge(VM_Method caller, int bcIndex, VM_Method callee) {
    
    VM_CallSiteTriple triple = findOrCreateEdge(caller, bcIndex, callee);
    
    triple.incrementWeight();
    totalEdgeWeights += 1.0;
  }
  
  /**
   * Find the edge in the partial call graph, if not found add it.
   *
   * @param caller   method making the call
   * @param bytecode call site, if -1 then no call site is specified.
   * @param callee   method called
   * @return         edge
   */
  public VM_CallSiteTriple findOrCreateEdge(VM_Method caller, 
                                            int bcIndex, 
                                            VM_Method callee) 
  {
    if (findTriples == null) {
      VM.sysWrite("FIND TRIPLES NULL");
    }
    if(DEBUG)VM.sysWrite(" VM_PartialCallGraph.findEdge("+caller+", "+
                         callee+", "+bcIndex+") entered\n");
    if (caller == null) {
      VM.sysWrite("***Error: VM_PartialCallGraph.findEdge("+caller+", "+
                  callee+") has null caller!\n");
      new Exception().printStackTrace();
    }
    if (callee == null) {
      VM.sysWrite("***Error: VM_PartialCallGraph.findEdge("+caller+", "+
                  callee+") has null callee!\n");
      new Exception().printStackTrace();
    }
    VM_CallSiteTriple triple = new VM_CallSiteTriple(caller, bcIndex, callee);
    
    synchronized(findTriples) {
      if (findTriples.containsKey(triple)) {
        if(DEBUG) VM.sysWrite
                    ("  VM_PartialCallGraph.findEdge() edge already called!\n");
        triple = (VM_CallSiteTriple)findTriples.get(triple);
      } else {
        if(DEBUG) VM.sysWrite
                    ("  VM_PartialCallGraph.findEdge() FIRST time edge called!\n");
        findTriples.put(triple,triple);
      }
    }
    
    if(DEBUG)VM.sysWrite(" VM_PartialCallGraph.increment() exit\n");
    return triple;
  }
  
  /**
   * Dump out set of edges in sorted order.
   */
  public void dump() {
    VM.sysWrite("VM_PartialCallGraph.dump()\n");
    VM.sysWrite("  Number of edges "+findTriples.size()+", total weight: "+totalEdgeWeights+"\n");
    java.util.TreeSet treeSet = new java.util.TreeSet(new VM_CallSiteTripleComparator(true));
    try {
      synchronized(findTriples) {
        treeSet.addAll(findTriples.values());
      }
    } catch (ClassCastException e) {
      VM.sysWrite("***VM_PartialCallGraph.dump(): addAll threw CallCastException!\n");
      VM.sysExit(-1);
    }
    int i=0;
    for (java.util.Iterator iterator = treeSet.iterator(); iterator.hasNext();) {
      VM_CallSiteTriple triple = null;
      try {
        triple = (VM_CallSiteTriple)iterator.next();
      } catch (java.util.NoSuchElementException e) {
        VM.sysWrite("***OPT_PCG.dump(): iterator.next() returns NoSuchElementException!\n");
        VM.sysExit(-1);
      }
      i++;
      VM.sysWrite(i+": "+triple.toString()+"\n");
    }
  }
  
  /**
   * Get sum of all edge weights in the partial call graph
   * @return edge weight sum
   */
  double getTotalEdgeWeights() { return totalEdgeWeights; }
  /*
   * hashcodeOf(callers,call site,callees) -> triple
   */
  private java.util.HashMap findTriples;
  /*
   * sum of all edge weights in the graph
   */
  private double totalEdgeWeights;
  
}
