/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;

/**
 *  An efficient topsort dataflow iterator to be used with
 * OPT_SortedGraphNode.  The graph represents entities (values,
 * statements, block, etc) to analyze and the graph makes explicit the
 * data-flow dependencies between them.  Fixed-point iteration is
 * expressed using a special iterator that takes a parameter denoting
 * whether analysis of the current element changed the data-flow
 * result.  If not, the iterator continues thru other unanalyzed
 * elements.  If there is a change, then the data-flow successors of
 * the current node become the new head of the order of remaining
 * nodes. 
 * 
 *  A typical use is as follows:
 *   OPT_BasicBlock start = ir.cfg.entry();
 *   OPT_SortedGraphIterator bbIter = new OPT_SortedGraphIterator(start, true);
 *       // true means forward analysis; false means backward analysis
 *   for (OPT_BasicBlock currBlock = start; currBlock!= null;) {
 *        
 *       // do your analysis of the currBlock here
 *     
 *       boolean changed = ... // true if the solution of currBlock has been changed since 
 *                             // the last visit of currBlock.
 *                             // false if not.
 *
 *       currBlock = (OPT_BasicBlock) bbIter.markAndGetNextTopSort(changed);
 *  }
 *
 * @author Jong-Deok Choi
 * @author Michael HInd
 *
 */
public class OPT_SortedGraphIterator {

  /**
   * The earliest place where we needed to move currentNode back in the list
   *  because its successor needed to be processed.
   */
  protected OPT_SortedGraphNode barrier;

  /**
   *  A unique marker to use to mark nodes
   */
  protected int changeMark;

  /**
   * The current node we are processing
   */
  protected OPT_SortedGraphNode currentNode;

  /**
   * The direction we are moving on the graph
   */
  protected boolean forward;

  /**
   * Cosntructor
   * @param current the node to start the iteration at
   * @param forward the direction we are processing the graph
   */
  public OPT_SortedGraphIterator(OPT_SortedGraphNode current, 
                                 boolean forward) {
    currentNode = current;
    barrier = current.getSortedNext(forward);
    this.forward = forward;
    changeMark = OPT_SortedGraphNode.getNewSortMarker(current);
    currentNode.setSortMarker(Integer.MIN_VALUE);
  }

  /**
   * General fixed-pointer iterator; call this repeatedly until there
   * is no more work to do. There are specialized (more efficient)
   * mechanisms provided by this class.
   *
   * @param changed Whether analysis of the current element changed
   * any data-flow result.
   *
   * @return the next node to analyze
   *
   * @see #isSingleSuccessor
   * @see #isSinglePredecessor
   */
  public OPT_SortedGraphNode markAndGetNextTopSort(boolean changed) {
    if (changed) {
      int currOrder = currentNode.getSortNumber(forward);
      int newOrder = currOrder + 1; // currentNode can be a target to be re-executed
      int barrierOrder;
      if (barrier == null)
        barrierOrder = Integer.MAX_VALUE; 
      else 
        barrierOrder = barrier.getSortNumber(forward);
      OPT_SortedGraphNode newNode = null;
      Enumeration e;
      if (forward)
        e = currentNode.getOutNodes();
      else
        e = currentNode.getInNodes();
      
      for (; e.hasMoreElements(); ) {
        // Select the node with the smallest sort number among the "successor" nodes
        OPT_SortedGraphNode outNode = (OPT_SortedGraphNode) e.nextElement();
        if (outNode.getSortNumber(forward) < barrierOrder) { // anything larger than barrier will be visited later
          outNode.setSortMarker(changeMark);
          if (outNode.getSortNumber(forward) < newOrder) { // have to go backward
            newOrder = outNode.getSortNumber(forward);
            newNode = outNode;
          }
        }
      }
      if (newOrder <= currOrder) {
        currentNode = newNode;
        // retreat
        advanceBarrier();
        return newNode;
      }
    } 
    
    // Either changed = false or no retreat
    // Return the first one with changeMark before barrier or 
    // barrier itself.
    currentNode = currentNode.getSortedNext(forward);
    for (; currentNode != barrier; currentNode = 
        currentNode.getSortedNext(forward)) {
      if (currentNode.getSortMarker() == changeMark) {
        advanceBarrier();
        return currentNode;
      }
    }
    
    // Nothing before the barrier
    advanceBarrier();
    return currentNode;
  }
  

  /**
   * This method checks to see if the second parameter has a single
   * predecessor, which is the first parameter.
   * If this condition is true, data flow analyses can reuse their
   * results from the previous iteration rather than perform a meet operation
   * (See OPT_LiveAnalysis.java for an example.)
   *
   * @param currentNode the possibly unique predecessor
   * @param nextNode the node of interest
   * @return if first parameter is the only predecessor of the 2nd parameter
   */
  public boolean isSingleSuccessor(OPT_SortedGraphNode currentNode, 
                                   OPT_SortedGraphNode nextNode) {
    // check that next node has only 1 predecessor
    if (!nextNode.hasOneIn()) return false;
    // now check that the predecessor is current node
    Enumeration inEnum = nextNode.getInNodes();
    return inEnum.nextElement() == currentNode;
  }

  /**
   * This method checks to see if the second parameter has a single
   * successor, which is the first parameter.
   * If this condition is true, data flow analyses can reuse their
   * results from the previous iteration rather than perform a meet operation
   * (See OPT_LiveAnalysis.java for an example.)
   *
   * @param currentNode the possibly unique predecessor
   * @param nextNode the node of interest
   * @return if first parameter is the only succesor of the 2nd parameter
   */
  public boolean isSinglePredecessor(OPT_SortedGraphNode currentNode, 
                                     OPT_SortedGraphNode nextNode) {
    // check that next node has only 1 successor
    if (!nextNode.hasOneOut()) return  false;
    // now check that the successor is current node
    Enumeration outEnum = nextNode.getOutNodes();
    return outEnum.nextElement() == currentNode;
  }

  /**
   *  This method keeps track of nodes in the graph that are known to
   * not have been visited yet even once. Advance the barrier, if needed 
   */
  private void advanceBarrier() {
    if (currentNode != null)
      currentNode.setSortMarker(Integer.MIN_VALUE);
    if ((currentNode == barrier) && (barrier != null))
      barrier = barrier.getSortedNext(forward);
  }
}

