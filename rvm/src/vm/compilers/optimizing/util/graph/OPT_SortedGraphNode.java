/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Jong Choi
 */


import  java.util.Enumeration;


// For now, OPT_SortedGraphNode inherits from DoublyLinkedList only 
// for compatibility
// with the current implementation of OPT_BasicBlock. 
// In the future, this inheritance will be removed.
// Therefore, the user of this class should ignore that
// this class extends DoublyLinkedList.
// In short, it does NOT INHERIT FROM DoublyLinkedListElement.
public abstract class OPT_SortedGraphNode extends OPT_SpaceEffGraphNode {

  // Return enumerator for all the in nodes.
  public abstract Enumeration getInNodes ();  // should be overridden 
                                              // by a subclass

  // Return enumerator for all the out nodes.
  public abstract Enumeration getOutNodes (); // should be overridden by a 
                                              // subclass

  /**
   * put your documentation comment here
   * @param forward
   * @return 
   */
  public OPT_SortedGraphNode getSortedNext (boolean forward) {
    if (forward)
      return  sortedNext; 
    else 
      return  sortedPrev;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public OPT_SortedGraphNode getForwardSortedNext () {
    return  sortedNext;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public OPT_SortedGraphNode getBackwardSortedNext () {
    return  sortedPrev;
  }

  /**
   * put your documentation comment here
   * @param next
   * @param forward
   */
  public void setSortedNext (OPT_SortedGraphNode next, boolean forward) {
    if (forward)
      sortedNext = next; 
    else 
      sortedPrev = next;
  }

  // preferred interface
  public void setForwardSortNumber (int number) {
    forwardSortNumber = number;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int getForwardSortNumber () {
    return  forwardSortNumber;
  }

  /**
   * put your documentation comment here
   * @param number
   */
  public void setBackwardSortNumber (int number) {
    backwardSortNumber = number;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int getBackwardSortNumber () {
    return  backwardSortNumber;
  }

  // probably less efficient than above, but more flexible
  public void setSortNumber (int number, boolean forward) {
    if (forward)
      forwardSortNumber = number; 
    else 
      backwardSortNumber = number;
  }

  /**
   * put your documentation comment here
   * @param forward
   * @return 
   */
  public int getSortNumber (boolean forward) {
    if (forward)
      return  forwardSortNumber; 
    else 
      return  backwardSortNumber;
  }

  /**
   * put your documentation comment here
   * @param number
   */
  public void setSortNumber (int number) {
    forwardSortNumber = number;
  }

  // Do we need this?
  //  public int isForwardSorted(OPT_SortedGraphNode node) { 
  //    return forwardSortNumber - node.forwardSortNumber;
  //  }
  public static int getNewSortMarker (OPT_SortedGraphNode anchor) {
    if (currentSortMarker == Integer.MAX_VALUE) {
      OPT_SortedGraphNode current;
      for (current = anchor; current != null; current = current.sortedPrev) {
        current.sortMarker = Integer.MIN_VALUE;
      }
      for (current = anchor; current != null; current = current.sortedNext) {
        current.sortMarker = Integer.MIN_VALUE;
      }
      currentSortMarker = Integer.MIN_VALUE;
    }
    ;
    return  ++currentSortMarker;
  }
  int sortMarker = Integer.MIN_VALUE;
  private static int currentSortMarker = Integer.MIN_VALUE;

  /**
   * put your documentation comment here
   * @return 
   */
  int getSortMarker () {
    return  sortMarker;
  }

  /**
   * put your documentation comment here
   * @param sortMarker
   */
  void setSortMarker (int sortMarker) {
    this.sortMarker = sortMarker;
  }

  /**
   * put your documentation comment here
   * @param sortMarker
   * @return 
   */
  boolean isSortMarkedWith (int sortMarker) {
    return  (this.sortMarker >= sortMarker);
  }
  protected OPT_SortedGraphNode sortedPrev = null, sortedNext = null;
  protected int forwardSortNumber;
  protected int backwardSortNumber;
}



