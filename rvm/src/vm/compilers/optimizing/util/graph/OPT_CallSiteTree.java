/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_Tree;
import com.ibm.JikesRVM.opt.OPT_TreeNode;
import java.util.*;

/**
 *  This class represents the set of inlined method calls that are
 * contained within a single method code body.  The tree is consists
 * of nodes each of whioch contains an OPT_InlineSequence object
 * representing an inlined method call.  The tree is rooted at the
 * inline sequence object representing the top level method, and the
 * inlined calls appear as children of that root, and so on
 * recursively.  These trees are used to construct the persistent
 * encoding of inlining information, stored in the
 * VM_OptMachineCodeMap.
 *
 * @author Julian Dolby
 *
 * @see OPT_InlineSequence
 * @see OPT_CallSiteTreeNode
 * @see VM_OptEncodedCallSiteTree
 * @see VM_OptMachineCodeMap
 */
public class OPT_CallSiteTree extends OPT_Tree {

  /**
   * Given an existing call site tree representing a method, add a new
   * inlined call to it.
   * @param seq a call to add to the call site tree
   * @return the call site tree node corresponding to the new call site
   */
  public OPT_CallSiteTreeNode addLocation (OPT_InlineSequence seq) {
    if (seq.caller == null) {
      OPT_CallSiteTreeNode x = (OPT_CallSiteTreeNode)getRoot();
      if (x == null) {
        x = new OPT_CallSiteTreeNode(seq);
        setRoot(x);
      }
      return  x;
    } else {
      OPT_CallSiteTreeNode node = addLocation(seq.caller);
      OPT_CallSiteTreeNode x = (OPT_CallSiteTreeNode)node.getLeftChild();
      while (x != null) {
        if (x.callSite == seq)
          return  x;
        x = (OPT_CallSiteTreeNode)x.getRightSibling();
      }
      OPT_CallSiteTreeNode xx = new OPT_CallSiteTreeNode(seq);
      node.addChild(xx);
      return  xx;
    }
  }

  /**
   * Given an inline sequence representing an inlined call site, find
   * the corresponding call site tree node.
   * @param seq an inlined call site
   * @return the corresponding call site tree node
   */
  public OPT_CallSiteTreeNode find (OPT_InlineSequence seq) {
    if (seq.caller == null) {
      return  (OPT_CallSiteTreeNode)getRoot(); 
    } else {
      OPT_CallSiteTreeNode parent = find(seq.caller);
      OPT_CallSiteTreeNode x = (OPT_CallSiteTreeNode)parent.getLeftChild();
      while (x != null) {
        if (x.callSite == seq)
          return  x;
        x = (OPT_CallSiteTreeNode)x.getRightSibling();
      }
      return  null;
    }
  }
}



