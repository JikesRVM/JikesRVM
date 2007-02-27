/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

/** 
 * Interface to allow building top-sort, by calling OPT_TopSort.buildTopSort()
 * @author Jong Choi
 * 5/25/1999
 */
public interface OPT_TopSortInterface {

  /**
   * Return the start node if forward = true for forward topsort,
   * and return the end node if forward = false for backward topsort.
   * @param forward whether we are viewing the graph in the forward direction
   * @return the start node if forward = true for forward topsort,
   *         the end node if forward = false for backward topsort.
   */
  OPT_SortedGraphNode startNode(boolean forward);

  /**
   * Return the number of nodes in the graph
   * @return the number of nodes in the graph
   */
  int numberOfNodes();

  /**
   * Return true if no resetTopSorted(forward) has been executed
   * since the last setTopSorted(forward) has been executed
   * @param forward whether we are viewing the graph in the forward direction
   * @return  true if no resetTopSorted(forward) has been executed
   * since the last setTopSorted(forward) has been executed
   */
  boolean isTopSorted(boolean forward);

  /**
   * Should have a side effect such that isTopSorted(forward)
   * returns the correct value.
   * @param forward whether we are viewing the graph in the forward direction
   */
  void setTopSorted(boolean forward);

  /**
   * Should have a side effect such that isTopSorted(forward)
   * returns the correct value.
   */
  void resetTopSorted();
}



