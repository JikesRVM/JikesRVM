/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt.ir;

import com.ibm.jikesrvm.opt.*;

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
public class OPT_CallSiteTreeNode extends OPT_TreeNode {
  /**
   * The call site represented by this tree node
   */
  public OPT_InlineSequence callSite;

  /**
   * The position of this call site in the binary encoding.  It is set
   * when by VM_OptEncodedCallSiteTree.getEncoding.
   *
   * @see VM_OptEncodedCallSiteTree#getEncoding
   */
  public int encodedOffset;

  /**
   * construct a a call site tree node corresponding to a given
   * inlined call site
   * @param   seq an inlined call site
   */
  public OPT_CallSiteTreeNode (OPT_InlineSequence seq) {
    callSite = seq;
  }
}



