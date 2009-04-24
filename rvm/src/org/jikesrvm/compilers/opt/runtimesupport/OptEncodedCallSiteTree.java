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
package org.jikesrvm.compilers.opt.runtimesupport;

import java.util.Enumeration;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.inlining.CallSiteTree;
import org.jikesrvm.compilers.opt.inlining.CallSiteTreeNode;
import org.jikesrvm.compilers.opt.util.TreeNode;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Suppose the following inlining actions have been taken
 * <pre>
 * (&lt;callerMID, bcIndex, calleeMID&gt;):
 *
 * &lt;A, 12, B&gt;, &lt;A,14,C&gt;, &lt;A,16,D&gt;, &lt; B,3,E&gt;, &lt; B,5,F &gt;, &lt;C,10,G&gt;, &lt;G,20,H&gt;,
 * &lt;H,30,I&gt;
 * </pre>
 *
 * Then the <code>OptEncodedCallSiteTree </code> would be:
 *
 * <pre>
 * -1, A, -2, 12, B, 14, C, 16, D, -6, 3, E, 5, F, -9, 10, G, -2, 20 H -2 30 I
 * </pre>
 */
@Uninterruptible
public abstract class OptEncodedCallSiteTree {

  public static int getMethodID(int entryOffset, int[] encoding) {
    return encoding[entryOffset + 1];
  }

  static void setMethodID(int entryOffset, int[] encoding, int methodID) {
    encoding[entryOffset + 1] = methodID;
  }

  public static int getByteCodeOffset(int entryOffset, int[] encoding) {
    return encoding[entryOffset];
  }

  @Interruptible
  public static int[] getEncoding(CallSiteTree tree) {
    int size = 0;
    if (tree.isEmpty()) {
      return null;
    } else {
      Enumeration<TreeNode> e = tree.elements();
      while (e.hasMoreElements()) {
        TreeNode x = e.nextElement();
        if (x.getLeftChild() == null) {
          size += 2;
        } else {
          size += 3;
        }
      }
      int[] encoding = new int[size];
      getEncoding((CallSiteTreeNode) tree.getRoot(), 0, -1, encoding);
      return encoding;
    }
  }

  @Interruptible
  static int getEncoding(CallSiteTreeNode current, int offset, int parent, int[] encoding) {
    int i = offset;
    if (parent != -1) {
      encoding[i++] = parent - offset;
    }
    CallSiteTreeNode x = current;
    int j = i;
    while (x != null) {
      x.encodedOffset = j;
      int byteCodeIndex = x.callSite.bcIndex;
      encoding[j++] = (byteCodeIndex >= 0) ? byteCodeIndex : -1;
      encoding[j++] = x.callSite.getMethod().getId();
      x = (CallSiteTreeNode) x.getRightSibling();
    }
    x = current;
    int thisParent = i;
    while (x != null) {
      if (x.getLeftChild() != null) {
        j = getEncoding((CallSiteTreeNode) x.getLeftChild(), j, thisParent, encoding);
      }
      thisParent += 2;
      x = (CallSiteTreeNode) x.getRightSibling();
    }
    return j;
  }

  public static int getParent(int index, int[] encodedTree) {
    while (index >= 0 && encodedTree[index] >= -1) {
      index--;
    }
    if (index < 0) {
      return -1;
    } else {
      return index + encodedTree[index];
    }
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
        if (encoding[idx] == desiredBCIndex && encoding[idx + 1] == desiredCallee) {
          return true;
        }
      }
      idx += 2;
    }
    return false;
  }
}



