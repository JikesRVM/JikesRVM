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
 *  This class is a generic tree.  It uses TreeNode and some enumeration
 *  classes.
 */
public class Tree {

  /**
   *  A tree is simply a pointer to the root
   */
  private TreeNode root;

  /**
   * constructor where the root is not initially known
   */
  public Tree() {
    root = null;
  }

  /**
   * constructor where the root is initially known
   * @param node  Root of the tree.
   */
  public Tree(TreeNode node) {
    root = node;
  }

  /**
   * Is the tree empty?
   * @return whether the tree is empty
   */
  public final boolean isEmpty() {
    return root == null;
  }

  /**
   * Gets the root of the tree
   * @return the root of the tree
   */
  public final TreeNode getRoot() {
    return root;
  }

  /**
   * Sets the root of the tree to be the passed node.
   * WARNING: the tree should be empty when this occurs
   */
  public final void setRoot(TreeNode node) {
    node.clear();  // make sure all pointers are pointing anywhere else
    root = node;
  }

  /**
   * Provides an undefined enumeration over all elements in the tree
   * @return enumeration
   */
  public final Enumeration<TreeNode> elements() {
    return new TreeTopDownEnumerator(root);
  }

  /**
   * Counts and returns the number of nodes
   * @return the number of nodes.
   */
  public final int numberOfNodes() {
    int n = 0;
    for (Enumeration<TreeNode> e = elements(); e.hasMoreElements();) {
      e.nextElement();
      n++;
    }
    return n;
  }

  /**
   * Provides a bottom-up enumeration over all elements in the tree
   * @return enumeration
   */
  public final Enumeration<TreeNode> getBottomUpEnumerator() {
    return new TreeBottomUpEnumerator(root);
  }

  /**
   * Provides a top-down enumeration over all elements in the tree
   * @return enumeration
   */
  public final Enumeration<TreeNode> getTopDownEnumerator() {
    return new TreeTopDownEnumerator(root);
  }

  /**
   * Prints the tree
   * @return the tree as a string
   */
  public final String toString() {
    StringBuffer sb = new StringBuffer();

    // visit the nodes in a depth first traversal, printing the nodes
    //  as they are visited, indenting by the depth of the traversal
    sb = DFS(sb, root, 0);
    return sb.toString();
  }

  /**
   * A preorder depth first traversal, printing node as visited
   * @param sb  the string buffer to insert the results
   * @param node the node to process
   * @param depth the current depth (root = 0) in the tree
   */
  private StringBuffer DFS(StringBuffer sb, TreeNode node, int depth) {
    // indent appropriate spaces and print node
    for (int i = 0; i < 2 * depth; i++) {
      sb.append(" ");
    }
    sb.append(node).append("\n");

    Enumeration<TreeNode> childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      TreeNode child = childEnum.nextElement();
      DFS(sb, child, depth + 1);
    }
    return sb;
  }
}
