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


/**
 *  This class is a node in a tree.  Both up and down pointers are used.
 */
public class TreeNode {

  /**
   *  The parent of this node
   */
  private TreeNode parent;

  /**
   *  The first (leftmost) child
   */
  private TreeNode leftChild;

  /**
   *  The next node on the child list that I am on
   */
  private TreeNode rightSibling;

  /**
   * Constructor
   */
  public TreeNode() {
    parent = null;
    leftChild = null;
    rightSibling = null;
  }

  /**
   * return the parent of this node
   * @return my parent
   */
  public TreeNode getParent() {
    return parent;
  }

  /**
   * returns the first child of this node
   * @return the first child of this node
   */
  public TreeNode getLeftChild() {
    return leftChild;
  }

  /**
   * returns the next node with the same parent as me
   * @return the next node with the same parent as me
   */
  public TreeNode getRightSibling() {
    return rightSibling;
  }

  /**
   *  adds a child to this node
   *  @param node the new child
   */
  public void addChild(TreeNode node) {
    if (leftChild == null) {
      leftChild = node;
    } else {
      // get to the last sibling
      TreeNode siblingNode = leftChild;
      while (siblingNode.rightSibling != null) {
        siblingNode = siblingNode.rightSibling;
      }
      siblingNode.rightSibling = node;
    }
    node.parent = this;
  }

  /**
   *  Sets all tree pointers to null
   */
  public void clear() {
    leftChild = null;
    rightSibling = null;
    parent = null;
  }

  public Enumeration<TreeNode> getChildren() {
    return new TreeNodeChildrenEnumerator(this);
  }

}
