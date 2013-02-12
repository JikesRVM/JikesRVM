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
package org.jikesrvm.compilers.opt.inlining;

import org.jikesrvm.compilers.opt.util.Tree;

/**
 *  This class represents the set of inlined method calls that are
 * contained within a single method code body.  The tree is consists
 * of nodes each of which contains an InlineSequence object
 * representing an inlined method call.  The tree is rooted at the
 * inline sequence object representing the top level method, and the
 * inlined calls appear as children of that root, and so on
 * recursively.  These trees are used to construct the persistent
 * encoding of inlining information, stored in the
 * OptMachineCodeMap.
 *
 *
 * @see InlineSequence
 * @see CallSiteTreeNode
 * @see org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree
 * @see org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap
 */
public class CallSiteTree extends Tree {

  /**
   * Given an existing call site tree representing a method, add a new
   * inlined call to it.
   * @param seq a call to add to the call site tree
   * @return the call site tree node corresponding to the new call site
   */
  public CallSiteTreeNode addLocation(InlineSequence seq) {
    if (seq.caller == null) {
      CallSiteTreeNode x = (CallSiteTreeNode) getRoot();
      if (x == null) {
        x = new CallSiteTreeNode(seq);
        setRoot(x);
      }
      return x;
    } else {
      CallSiteTreeNode node = addLocation(seq.caller);
      CallSiteTreeNode x = (CallSiteTreeNode) node.getLeftChild();
      while (x != null) {
        if (x.callSite == seq) {
          return x;
        }
        x = (CallSiteTreeNode) x.getRightSibling();
      }
      CallSiteTreeNode xx = new CallSiteTreeNode(seq);
      node.addChild(xx);
      return xx;
    }
  }

  /**
   * Given an inline sequence representing an inlined call site, find
   * the corresponding call site tree node.
   * @param seq an inlined call site
   * @return the corresponding call site tree node
   */
  public CallSiteTreeNode find(InlineSequence seq) {
    if (seq.caller == null) {
      return (CallSiteTreeNode) getRoot();
    } else {
      CallSiteTreeNode parent = find(seq.caller);
      CallSiteTreeNode x = (CallSiteTreeNode) parent.getLeftChild();
      while (x != null) {
        if (x.callSite == seq) {
          return x;
        }
        x = (CallSiteTreeNode) x.getRightSibling();
      }
      return null;
    }
  }
}
