/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * This class provides enumeration of all children of a OPT_TreeNode
 *
 * @author Michael Hind
 */
final class OPT_TreeNodeChildrenEnumerator implements Enumeration {

  /**
   * the current child we are working on
   */
  private OPT_TreeNode currentChild;

  /**
   * Provides iteration over a list of children tree nodes
   * @param   node  Root of the tree to iterate over.
   */
  OPT_TreeNodeChildrenEnumerator(OPT_TreeNode node) {
    // start at the first child
    currentChild = node.getLeftChild();
  }

  /**
   * any elements left?
   * @return whether there are any elements left
   */
  public boolean hasMoreElements() {
    return  currentChild != null;
  }

  /**
   * returns the next element in the list iterator
   * @return the next element in the list iterator or null
   */
  public Object nextElement() {
    // save the return value
    OPT_TreeNode returnValue = currentChild;

    // update the currentChild pointer, if possible
    if (currentChild != null) {
      currentChild = currentChild.getRightSibling();
    } 
    else {
      throw  new NoSuchElementException("OPT_TreeNodeChildrenEnumerator");
    }

    // return the value
    return  returnValue;
  }
}



