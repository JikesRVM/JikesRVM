/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ListIterator;

/**
 *  This class provides enumeration of elements of a tree in a town-down manner
 *  It guarantees that all children of a node will only be visited after
 *  the parent.
 *  This is not necessarily the same as a top-down level walk.
 *
 */
final class OPT_TreeTopDownEnumerator implements Enumeration<OPT_TreeNode> {

  /**
   * List of nodes in preorder
   */
  private final ArrayList<OPT_TreeNode> list;

  /**
   * an iterator of the above list
   */
  private final ListIterator<OPT_TreeNode> iterator;

  /**
   * constructor: it creates the list of nodes
   * @param   root Root of the tree to traverse
   */
  OPT_TreeTopDownEnumerator(OPT_TreeNode root) {
    list = new ArrayList<OPT_TreeNode>();

    // Perform a DFS, saving nodes in preorder
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
   * A preorder depth first traversal, adding nodes to the list
   * @param node
   */
  private void DFS(OPT_TreeNode node) {
    list.add(node);
    Enumeration<OPT_TreeNode> childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_TreeNode child = childEnum.nextElement();
      DFS(child);
    }
  }
}



