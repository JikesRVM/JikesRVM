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
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;


public abstract class SortedGraphNode extends SpaceEffGraphNode {

  // Return enumerator for all the in nodes.
  public abstract Enumeration<? extends SortedGraphNode> getInNodes();  // should be overridden
  // by a subclass

  // Return enumerator for all the out nodes.

  public abstract Enumeration<? extends SortedGraphNode> getOutNodes(); // should be overridden by a
  // subclass

  public SortedGraphNode getSortedNext(boolean forward) {
    if (forward) {
      return sortedNext;
    } else {
      return sortedPrev;
    }
  }

  public SortedGraphNode getForwardSortedNext() {
    return sortedNext;
  }

  public SortedGraphNode getBackwardSortedNext() {
    return sortedPrev;
  }

  public void setSortedNext(SortedGraphNode next, boolean forward) {
    if (forward) {
      sortedNext = next;
    } else {
      sortedPrev = next;
    }
  }

  // preferred interface
  public void setForwardSortNumber(int number) {
    forwardSortNumber = number;
  }

  public int getForwardSortNumber() {
    return forwardSortNumber;
  }

  public void setBackwardSortNumber(int number) {
    backwardSortNumber = number;
  }

  public int getBackwardSortNumber() {
    return backwardSortNumber;
  }

  // probably less efficient than above, but more flexible
  public void setSortNumber(int number, boolean forward) {
    if (forward) {
      forwardSortNumber = number;
    } else {
      backwardSortNumber = number;
    }
  }

  public int getSortNumber(boolean forward) {
    if (forward) {
      return forwardSortNumber;
    } else {
      return backwardSortNumber;
    }
  }

  public void setSortNumber(int number) {
    forwardSortNumber = number;
  }

  // Do we need this?
  //  public int isForwardSorted(SortedGraphNode node) {
  //    return forwardSortNumber - node.forwardSortNumber;
  //  }
  public static int getNewSortMarker(SortedGraphNode anchor) {
    if (currentSortMarker == Integer.MAX_VALUE) {
      SortedGraphNode current;
      for (current = anchor; current != null; current = current.sortedPrev) {
        current.sortMarker = Integer.MIN_VALUE;
      }
      for (current = anchor; current != null; current = current.sortedNext) {
        current.sortMarker = Integer.MIN_VALUE;
      }
      currentSortMarker = Integer.MIN_VALUE;
    }
    return ++currentSortMarker;
  }

  int sortMarker = Integer.MIN_VALUE;
  private static int currentSortMarker = Integer.MIN_VALUE;

  public int getSortMarker() {
    return sortMarker;
  }

  public void setSortMarker(int sortMarker) {
    this.sortMarker = sortMarker;
  }

  public boolean isSortMarkedWith(int sortMarker) {
    return (this.sortMarker >= sortMarker);
  }

  public SortedGraphNode sortedPrev = null;
  public SortedGraphNode sortedNext = null;
  protected int forwardSortNumber;
  protected int backwardSortNumber;
}
