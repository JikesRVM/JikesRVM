/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;

/**
 * This class provides enumeration of a tree in bottom-up order
 * It guarantees that all children of a node will be visited before the parent.
 * This is not necessarily the same as a bottom-up level walk.
 *
 * @author Michael Hind
 */
final class OPT_TreeBottomUpEnumerator implements Enumeration {

  /**
   * List of nodes in postorder
   */
  private java.util.ArrayList list;

  /**
   * an iterator of the above list
   */
  private java.util.ListIterator iterator;

  /**
   * constructor: it creates the list of nodes
   * @param root  Root of the tree whose elements are to be visited.
   */
  OPT_TreeBottomUpEnumerator(OPT_TreeNode root) {
    list = new java.util.ArrayList();

    // Perform a DFS, saving nodes in postorder
    DFS(root);

    // setup the iterator
    iterator = list.listIterator();
  }

  /**
   * any elements left?
   * @return whether there are any elements left
   */
  public boolean hasMoreElements() {
    return  iterator.hasNext();
  }

  /**
   * returns the next element in the list iterator
   * @return the next element in the list iterator or null
   */
  public Object nextElement() {
    return  iterator.next();
  }

  /**
   * A postorder depth first traversal, adding nodes to the list
   * @param node
   */
  private void DFS(OPT_TreeNode node) {
    Enumeration childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_TreeNode child = (OPT_TreeNode)childEnum.nextElement();
      DFS(child);
    }
    list.add(node);
  }
}



