/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;

/**
 * Depth First Spanning Tree
 * March 14, 1998
 * @author  Jong Choi (jdchoi@us.ibm.com)
 * @author  Michael Hind
 * Builds topological sort of a graph consisting of OPT_SortedGraphNode.
 */
public class OPT_TopSort extends OPT_Stack {

  /**
   * the "visited" marker to use
   */
  private int sortMarker;       

  /**
   * the next "number" to give out
   */
  private int sortNumber;       

  /**
   * the last node to get a number
   */
  private OPT_SortedGraphNode lastNumberedNode; 

  /**
   * are we processing the graph in forward order?
   */
  private boolean forward;      

  /**
   * Prevent instantiation
   */
  private OPT_TopSort() {  }

  /**
   * @param graph the graph
   * @param forward should we treat edges as forward?
   *  This is the second version of the implementation 
   *   (see CVS file for older one)
   * @author Michael Hind 
   */
  public static OPT_SortedGraphNode 
    buildTopological(OPT_TopSortInterface graph, boolean forward) {

    OPT_SortedGraphNode start = graph.startNode(forward);
    OPT_TopSort sorter = new OPT_TopSort();
    sorter.sortMarker = OPT_SortedGraphNode.getNewSortMarker(start);
    sorter.forward = forward;
    sorter.DFS(start, graph.numberOfNodes());
    return  sorter.lastNumberedNode;
  }

  /**
   * Depth-first numbering in a non-recursive manner
   * @param node the root node
   * @param numNodes the number of nodes in this graph
   */
  private void DFS(OPT_SortedGraphNode node, int numNodes) {

    // push node on to the emulated activation stack
    push(node);
    Enumeration[] nodeEnum = new Enumeration[numNodes];

  recurse:
    while (!empty()) {

      node = (OPT_SortedGraphNode) peek();

      // check if we were already processing this node, if so we would have
      // saved the state of the enumeration in the loop below
      Enumeration enum = nodeEnum[node.getNumber()];
      if (enum == null) {
        // mark node as visited
        node.setSortMarker(sortMarker);
        if (forward) {
          enum = node.getOutNodes();
        } 
        else {
          enum = node.getInNodes();
        }
      }

      while (enum.hasMoreElements()) {
        OPT_SortedGraphNode target = 
          (OPT_SortedGraphNode) enum.nextElement();

        // have we visited target?
        if (target.getSortMarker() != sortMarker) {
          // simulate a recursive call
          // but first save the enumeration state for resumption later
          nodeEnum[node.getNumber()] = enum;
          push(target);
          continue recurse;  
        }
      }

      // give node the next smallest number
      node.setSortNumber(sortNumber--, forward);
      // link it to the previous smallest node, even if that node is null
      node.setSortedNext(lastNumberedNode, forward);
      // update the smallest node
      lastNumberedNode = node;
      
      // "Pop" from the emulated activiation stack
      pop();
    }
  }
}



