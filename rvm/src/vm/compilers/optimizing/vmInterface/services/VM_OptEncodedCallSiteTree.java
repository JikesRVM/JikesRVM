/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import java.util.*;
import com.ibm.JikesRVM.opt.ir.OPT_CallSiteTree;
import com.ibm.JikesRVM.opt.ir.OPT_CallSiteTreeNode;
import org.vmmagic.pragma.*;

/**
 * Suppose the following inlining actions have been taken 
 * <pre>
 * (<callerMID, bcIndex, calleeMID>):
 * 
 * <A, 12, B>, <A,14,C>, <A,16,D>, < B,3,E>, < B,5,F >, <C,10,G>, <G,20,H>, 
 * <H,30,I>
 * <pre>
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
public abstract class VM_OptEncodedCallSiteTree implements Uninterruptible {

  public static int getMethodID(int entryOffset, int[] encoding) {
    return  encoding[entryOffset + 1];
  }

  static void setMethodID(int entryOffset, int[] encoding, int methodID) {
    encoding[entryOffset + 1] = methodID;
  }

  public static int getByteCodeOffset(int entryOffset, int[] encoding) {
    return  encoding[entryOffset];
  }

  public static int[] getEncoding(OPT_CallSiteTree tree) throws InterruptiblePragma {
    int size = 0;
    if (tree.isEmpty())
      return  null; 
    else {
      Enumeration e = tree.elements();
      while (e.hasMoreElements()) {
        OPT_TreeNode x = (OPT_TreeNode)e.nextElement();
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

  static int getEncoding(OPT_CallSiteTreeNode current, int offset, int parent, 
                         int[] encoding) throws InterruptiblePragma {
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
}



