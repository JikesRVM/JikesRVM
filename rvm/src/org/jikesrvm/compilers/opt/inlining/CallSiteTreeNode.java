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

import org.jikesrvm.compilers.opt.util.TreeNode;

/**
 * The nodes of an CallSiteTree.  They represent inlined call
 * sites of a given method code body; a node stands for a single call
 * site.  These trees are used to construct the persistent runtime
 * encoding of inlining information, which is stored in
 * OptMachineCodeMap objects.
 *
 *
 * @see CallSiteTree
 * @see InlineSequence
 * @see org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap
 * @see org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree
 */
public class CallSiteTreeNode extends TreeNode {
  /**
   * The call site represented by this tree node
   */
  public final InlineSequence callSite;

  /**
   * The position of this call site in the binary encoding.  It is set
   * by OptEncodedCallSiteTree.getEncoding.
   *
   * @see org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree#getEncoding
   */
  public int encodedOffset;

  /**
   * construct a a call site tree node corresponding to a given
   * inlined call site
   * @param   seq an inlined call site
   */
  public CallSiteTreeNode(InlineSequence seq) {
    callSite = seq;
  }
}



