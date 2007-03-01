/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package com.ibm.jikesrvm.opt;

import  java.util.Enumeration;

/**
 *  This class is a generic tree.  It uses OPT_TreeNode and some enumeration
 *  classes.
 *
 *  @author Michael Hind
 */
public class OPT_Tree {
  
  /**
   *  A tree is simply a pointer to the root
   */
  private OPT_TreeNode root;

  /**
   * constructor where the root is not initially known
   */
  public OPT_Tree() {
    root = null;
  }

  /**
   * constructor where the root is initially known
   * @param node  Root of the tree.
   */
  public OPT_Tree(OPT_TreeNode node) {
    root = node;
  }

  /**
   * Is the tree empty?
   * @return whether the tree is empty
   */
  public final boolean isEmpty() {
    return  root == null;
  }

  /**
   * Gets the root of the tree
   * @return the root of the tree
   */
  public final OPT_TreeNode getRoot() {
    return  root;
  }

  /**
   * Sets the root of the tree to be the passed node.
   * WARNING: the tree should be empty when this occurs
   */
  public final void setRoot(OPT_TreeNode node) {
    node.clear();  // make sure all pointers are pointing anywhere else
    root = node;
  }

  /**
   * Provides an undefined enumeration over all elements in the tree
   * @return enumeration
   */
  public final Enumeration<OPT_TreeNode> elements() {
    return  new OPT_TreeTopDownEnumerator(root);
  }

  /**
   * Counts and returns the number of nodes
   * @return the number of nodes.
   */
  public final int numberOfNodes() {
    int n = 0;
    for (Enumeration<OPT_TreeNode> e = elements(); e.hasMoreElements();) {
      e.nextElement();
      n++;
    }
    return  n;
  }

  /**
   * Provides a bottom-up enumeration over all elements in the tree
   * @return enumeration
   */
  public final  Enumeration<OPT_TreeNode> getBottomUpEnumerator() {
    return  new OPT_TreeBottomUpEnumerator(root);
  }

  /**
   * Provides a top-down enumeration over all elements in the tree
   * @return enumeration
   */
  public final Enumeration<OPT_TreeNode> getTopDownEnumerator() {
    return  new OPT_TreeTopDownEnumerator(root);
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
    return  sb.toString();
  }

  /**
   * A preorder depth first traversal, printing node as visited
   * @param sb  the string buffer to insert the results
   * @param node the node to process
   * @param depth the current depth (root = 0) in the tree
   */
  private StringBuffer DFS(StringBuffer sb,
                                 OPT_TreeNode node, 
                                 int depth) {
    // indent appropriate spaces and print node
    for (int i=0; i < 2 * depth; i++) {
      sb.append(" ");
    }
    sb.append(node).append("\n");

    Enumeration<OPT_TreeNode> childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_TreeNode child = childEnum.nextElement();
      DFS(sb, child, depth + 1);
    }
    return sb;
  }
}
