/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Enumeration;

/**
 *  This class provides enumeration of elements of a tree in a town-down manner
 *  It guarantees that all children of a node will only be visited after
 *  the parent.
 *  This is not necessarily the same as a top-down level walk.
 *
 *  @author Michael Hind
 */
final class OPT_TreeTopDownEnumerator implements Enumeration {

  /**
   * List of nodes in preorder
   */
  private JDK2_ArrayList list;

  /**
   * an iterator of the above list
   */
  private JDK2_ListIterator iterator;

  /**
   * constructor: it creates the list of nodes
   * @param   OPT_TreeNode root
   */
  OPT_TreeTopDownEnumerator(OPT_TreeNode root) {
    list = new JDK2_ArrayList();

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
  public Object nextElement() {
    return  iterator.next();
  }

  /**
   * A preorder depth first traversal, adding nodes to the list
   * @param node
   */
  private void DFS(OPT_TreeNode node) {
    list.add(node);
    Enumeration childEnum = node.getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_TreeNode child = (OPT_TreeNode)childEnum.nextElement();
      DFS(child);
    }
  }
}



