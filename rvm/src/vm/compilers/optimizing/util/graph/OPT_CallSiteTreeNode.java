/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.*;

/**
 * The nodes of an OPT_CallSiteTree.  They represent inlined call
 * sites of a given method code body; a node stands for a single call
 * site.  These trees are used to construct the persistent runtime
 * encoding of inlining information, which is stored in
 * VM_OptMachineCodeMap objects.
 *
 * @author Julian Dolby
 *
 * @see OPT_CallSiteTree
 * @see OPT_InlineSequence
 * @see VM_OptMachineCodeMap
 * @see VM_OptEncodedCallSiteTree
 *
 */
class OPT_CallSiteTreeNode extends OPT_TreeNode {
  /**
   * The call site represented by this tree node
   */
  OPT_InlineSequence callSite;
  /**
   * The position of this call site in the binary encoding.  It is set
   * when by VM_OptEncodedCallSiteTree.getEncoding.
   *
   * @see VM_OptEncodedCallSiteTree#getEncoding
   */
  int encodedOffset;

  /**
   * construct a a call site tree node corresponding to a given
   * inlined call site
   * @param   OPT_InlineSequence seq an inlined call site
   */
  OPT_CallSiteTreeNode (OPT_InlineSequence seq) {
    callSite = seq;
  }
}



