/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

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
  public abstract OPT_SortedGraphNode startNode(boolean forward);

  /**
   * Return the number of nodes in the graph
   * @return the number of nodes in the graph
   */
  public abstract int numberOfNodes();

  /**
   * Return true if no resetTopSorted(forward) has been executed
   * since the last setTopSorted(forward) has been executed
   * @param forward whether we are viewing the graph in the forward direction
   * @return  true if no resetTopSorted(forward) has been executed
   * since the last setTopSorted(forward) has been executed
   */
  public abstract boolean isTopSorted(boolean forward);

  /**
   * Should have a side effect such that isTopSorted(forward)
   * returns the correct value.
   * @param forward whether we are viewing the graph in the forward direction
   */
  public abstract void setTopSorted(boolean forward);

  /**
   * Should have a side effect such that isTopSorted(forward)
   * returns the correct value.
   */
  public abstract void resetTopSorted();
}



