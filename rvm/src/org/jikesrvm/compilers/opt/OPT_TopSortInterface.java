/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

/**
 * Interface to allow building top-sort, by calling OPT_TopSort.buildTopSort()
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
   * @return true if no resetTopSorted(forward) has been executed
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



