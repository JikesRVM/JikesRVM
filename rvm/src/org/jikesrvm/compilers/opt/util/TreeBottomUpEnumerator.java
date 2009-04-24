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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ListIterator;


/**
 * This class provides enumeration of a tree in bottom-up order
 * It guarantees that all children of a node will be visited before the parent.
 * This is not necessarily the same as a bottom-up level walk.
 */
final class TreeBottomUpEnumerator implements Enumeration<TreeNode> {

  /**
   * List of nodes in postorder
   */
  private final ArrayList<TreeNode> list;

  /**
   * an iterator of the above list
   */
  private final ListIterator<TreeNode> iterator;

  /**
   * constructor: it creates the list of nodes
   * @param root  Root of the tree whose elements are to be visited.
   */
  TreeBottomUpEnumerator(TreeNode root) {
    list = new ArrayList<TreeNode>();

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
    return iterator.hasNext();
  }

  /**
   * returns the next element in the list iterator
   * @return the next element in the list iterator or null
   */
  public TreeNode nextElement() {
    return iterator.next();
  }

  /**
   * A postorder depth first traversal, adding nodes to the list
   * @param node
   */
  private void DFS(TreeNode node) {
    Enumeration<TreeNode> childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      TreeNode child = childEnum.nextElement();
      DFS(child);
    }
    list.add(node);
  }
}



