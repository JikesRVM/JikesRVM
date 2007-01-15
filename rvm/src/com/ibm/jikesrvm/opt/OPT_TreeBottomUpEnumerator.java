/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import  java.util.Enumeration;
import  java.util.ArrayList;
import  java.util.ListIterator;

/**
 * This class provides enumeration of a tree in bottom-up order
 * It guarantees that all children of a node will be visited before the parent.
 * This is not necessarily the same as a bottom-up level walk.
 *
 * @author Michael Hind
 */
final class OPT_TreeBottomUpEnumerator implements Enumeration<OPT_TreeNode> {

  /**
   * List of nodes in postorder
   */
  private final ArrayList<OPT_TreeNode> list;

  /**
   * an iterator of the above list
   */
  private final ListIterator<OPT_TreeNode> iterator;

  /**
   * constructor: it creates the list of nodes
   * @param root  Root of the tree whose elements are to be visited.
   */
  OPT_TreeBottomUpEnumerator(OPT_TreeNode root) {
    list = new ArrayList<OPT_TreeNode>();

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
  public OPT_TreeNode nextElement() {
    return  iterator.next();
  }

  /**
   * A postorder depth first traversal, adding nodes to the list
   * @param node
   */
  private void DFS(OPT_TreeNode node) {
    Enumeration<OPT_TreeNode> childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_TreeNode child = (OPT_TreeNode)childEnum.nextElement();
      DFS(child);
    }
    list.add(node);
  }
}



