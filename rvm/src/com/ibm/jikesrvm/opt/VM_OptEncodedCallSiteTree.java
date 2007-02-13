/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.*;
import java.util.*;
import com.ibm.jikesrvm.opt.ir.OPT_CallSiteTree;
import com.ibm.jikesrvm.opt.ir.OPT_CallSiteTreeNode;
import org.vmmagic.pragma.*;

/**
 * Suppose the following inlining actions have been taken 
 * <pre>
 * (&lt;callerMID, bcIndex, calleeMID&gt;):
 * 
 * &lt;A, 12, B&gt;, &lt;A,14,C&gt;, &lt;A,16,D&gt;, &lt; B,3,E&gt;, &lt; B,5,F &gt;, &lt;C,10,G&gt;, &lt;G,20,H&gt;,
 * &lt;H,30,I&gt;
 * </pre>
 *
 * Then the <code>VM_OptEncodedCallSiteTree </code> would be:
 *
 * <pre>
 * -1, A, -2, 12, B, 14, C, 16, D, -6, 3, E, 5, F, -9, 10, G, -2, 20 H -2 30 I
 * </pre>
 * 
 * @author Julian Dolby
 * @modified Dave Grove
 */
@Uninterruptible public abstract class VM_OptEncodedCallSiteTree {

  public static int getMethodID(int entryOffset, int[] encoding) {
    return  encoding[entryOffset + 1];
  }

  static void setMethodID(int entryOffset, int[] encoding, int methodID) {
    encoding[entryOffset + 1] = methodID;
  }

  public static int getByteCodeOffset(int entryOffset, int[] encoding) {
    return  encoding[entryOffset];
  }

  @Interruptible
  public static int[] getEncoding(OPT_CallSiteTree tree) { 
    int size = 0;
    if (tree.isEmpty())
      return  null; 
    else {
      Enumeration<OPT_TreeNode> e = tree.elements();
      while (e.hasMoreElements()) {
        OPT_TreeNode x = e.nextElement();
        if (x.getLeftChild() == null)
          size += 2; 
        else 
          size += 3;
      }
      int[] encoding = new int[size];
      getEncoding((OPT_CallSiteTreeNode)tree.getRoot(), 0, -1, encoding);
      return  encoding;
    }
  }

  @Interruptible
  static int getEncoding(OPT_CallSiteTreeNode current, int offset, int parent, 
                         int[] encoding) { 
    int i = offset;
    if (parent != -1)
      encoding[i++] = parent - offset;
    OPT_CallSiteTreeNode x = current;
    int j = i;
    while (x != null) {
      x.encodedOffset = j;
      int byteCodeIndex = x.callSite.bcIndex;
      encoding[j++] = (byteCodeIndex >= 0) ? byteCodeIndex : -1;
      encoding[j++] = x.callSite.getMethod().getId();
      x = (OPT_CallSiteTreeNode)x.getRightSibling();
    }
    x = current;
    int thisParent = i;
    while (x != null) {
      if (x.getLeftChild() != null)
        j = getEncoding((OPT_CallSiteTreeNode)x.getLeftChild(), j, thisParent, 
            encoding);
      thisParent += 2;
      x = (OPT_CallSiteTreeNode)x.getRightSibling();
    }
    return  j;
  }

  public static int getParent(int index, int[] encodedTree) {
    while (index >= 0 && encodedTree[index] >= -1)
      index--;
    if (index < 0)
      return  -1; 
    else 
      return  index + encodedTree[index];
  }

  public static boolean edgePresent(int desiredCaller, int desiredBCIndex, int desiredCallee, int[] encoding) {
    if (encoding.length < 3) return false; // Why are we creating an encoding with no real data???
    if (VM.VerifyAssertions) {
      VM._assert(encoding[0] == -1);
      VM._assert(encoding[2] == -2);
    }
    int idx = 3;
    int parent = encoding[1];
    while (idx < encoding.length) {
      if (encoding[idx] < 0) {
        parent = idx + encoding[idx];
        idx++;
      }
      if (parent == desiredCaller) {
        if (encoding[idx] == desiredBCIndex && encoding[idx+1] == desiredCallee) {
          return true;
        }
      }
      idx += 2;
    }
    return false;
  }
}



