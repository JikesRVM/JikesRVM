/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.compilers.opt.OPT_TreeNode;

/**
 * The nodes of an OPT_CallSiteTree.  They represent inlined call
 * sites of a given method code body; a node stands for a single call
 * site.  These trees are used to construct the persistent runtime
 * encoding of inlining information, which is stored in
 * VM_OptMachineCodeMap objects.
 *
 *
 * @see OPT_CallSiteTree
 * @see OPT_InlineSequence
 * @see org.jikesrvm.compilers.opt.VM_OptMachineCodeMap
 * @see org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree
 */
public class OPT_CallSiteTreeNode extends OPT_TreeNode {
  /**
   * The call site represented by this tree node
   */
  public final OPT_InlineSequence callSite;

  /**
   * The position of this call site in the binary encoding.  It is set
   * by VM_OptEncodedCallSiteTree.getEncoding.
   *
   * @see org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree#getEncoding
   */
  public int encodedOffset;

  /**
   * construct a a call site tree node corresponding to a given
   * inlined call site
   * @param   seq an inlined call site
   */
  public OPT_CallSiteTreeNode(OPT_InlineSequence seq) {
    callSite = seq;
  }
}



