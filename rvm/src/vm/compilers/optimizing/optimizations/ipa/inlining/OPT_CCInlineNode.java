/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.io.*;
import  java.util.*;

/**
 * This class represents a node in an OPT_CCInlineComponent.
 * It represents a call site that is either inlined or 
 * a candidate to be inlined.
 *
 * @author Stephen Fink
 * @modified Peter Sweeney
 */

class OPT_CCInlineNode extends OPT_TreeNode {
  /**
   * the target  method of the call site
   */
  VM_Method method;             
  /**
   * the bytecode index of the call site
   */
  int bcIndex;                  
  /**
   * depth-first numbering
   */
  int dfn;      

  public VM_Method getMethod () {
    return  method;
  }

  public int getBytecodeIndex () {
    return  bcIndex;
  }

  public void setDFN (int dfn) {
    this.dfn = dfn;
  }

  public int getDFN () {
    return  dfn;
  }

  public String toString () {
    String s = method + " " + bcIndex + " [" + dfn + "]";
    return  s;
  }

  OPT_CCInlineNode (VM_Method method, int bcIndex) {
    this.method = method;
    this.bcIndex = bcIndex;
    this.dfn = -1;
  }

  OPT_CCInlineNode (VM_Method method, int bcIndex, int dfn) {
    this.method = method;
    this.bcIndex = bcIndex;
    this.dfn = dfn;
  }

  public int hashCode () {
    int result = 7;
    if (method != null)
      result += method.hashCode();
    result += bcIndex;
    return  result;
  }

  /**
   * Search the children of this vertex, and return a vector
   * containing all children that
   * have a bytecode index matching bcIndex
   *
   * @param bcIndex the bytecode index to match
   * @param tree the tree to search
   * @return a Vector holding all children at that bytecode index
   */
  Vector findChildren (int bcIndex, OPT_Tree tree) {
    Vector v = new Vector();
    for (Enumeration e = getChildren(); e.hasMoreElements();) {
      OPT_CCInlineNode child = (OPT_CCInlineNode)e.nextElement();
      if (child.bcIndex == bcIndex)
        v.addElement(child);
    }
    return  v;
  }
}



