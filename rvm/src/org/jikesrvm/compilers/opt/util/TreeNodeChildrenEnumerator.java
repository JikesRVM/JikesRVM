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
import java.util.NoSuchElementException;


/**
 * This class provides enumeration of all children of a TreeNode
 */
final class TreeNodeChildrenEnumerator implements Enumeration<TreeNode> {

  /**
   * the current child we are working on
   */
  private TreeNode currentChild;

  /**
   * Provides iteration over a list of children tree nodes
   * @param   node  Root of the tree to iterate over.
   */
  TreeNodeChildrenEnumerator(TreeNode node) {
    // start at the first child
    currentChild = node.getLeftChild();
  }

  /**
   * any elements left?
   * @return whether there are any elements left
   */
  @Override
  public boolean hasMoreElements() {
    return currentChild != null;
  }

  /**
   * returns the next element in the list iterator
   * @return the next element in the list iterator or null
   */
  @Override
  public TreeNode nextElement() {
    // save the return value
    TreeNode returnValue = currentChild;

    // update the currentChild pointer, if possible
    if (currentChild != null) {
      currentChild = currentChild.getRightSibling();
    } else {
      throw new NoSuchElementException("TreeNodeChildrenEnumerator");
    }

    // return the value
    return returnValue;
  }
}
