/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.io.*;
import  java.util.*;

/**
 * This class represents the inlining decisions for a method, 
 * using calling context (CC) information.  
 * <p> An object of this class represents a tree.
 * Each node of the tree represents a method to inline and a
 * bytecode index.  For example the tree:
 * <pre>
 *   		<null, A>
 * 		/	\
 *	      <5, B>	<15,C>
 *  </pre>
 *  means "when compiling method A, inline the call site at bytecode 5
 *  with a call to B and the call site at bytecode 15 with a call to C."
 *
 * <p> The bytecode index of the component root is expected to be 0.
 *
 * @author Stephen Fink
 * @modified Peter Sweeney
 */


class OPT_CCInlineComponent {
  /**
   * Print verbose debugging information?
   */
  public static boolean debug = false;
  /**
   * the tree structure representing the inline component
   */
  OPT_Tree tree;                
  /**
   * a mapping from depth-first number to tree node
   */
  private OPT_CCInlineNode[] dfnMap;            

  /**
   * Return the tree structure representing the inline coponent
   * @return the tree structure representing the inline coponent
   */
  public OPT_Tree getTree () {
    return  tree;
  }

  /** 
   * Create a component with a single (root) node
   * @param m the single node in the new component.
   */
  public OPT_CCInlineComponent (VM_Method m) {
    OPT_CCInlineNode root = new OPT_CCInlineNode(m, -1);
    tree = new OPT_Tree(root);
  }

  /**
   * Create a component that represents a given inline sequence
   * @param seq the inline component
   */
  public OPT_CCInlineComponent (OPT_InlineSequence seq) {
    OPT_CCInlineNode[] nodes = sequence2array(seq);
    // set up the tree
    for (int i = 0; i < nodes.length - 1; i++) {
      nodes[i].addChild(nodes[i + 1]);
    }
    setRoot(nodes[0]);
  }

  /**
   * Construct a component with a single node
   * @param root the single node
   */
  public OPT_CCInlineComponent (OPT_CCInlineNode root) {
    tree = new OPT_Tree(root);
  }

  /**
   * Default constructor for an empty component.
   */
  protected OPT_CCInlineComponent () {
    tree = new OPT_Tree();
  }

  /** 
   * Return the root method of this component.
   * @return the root method of this component.
   */
  public VM_Method getRootMethod () {
    OPT_CCInlineNode root = (OPT_CCInlineNode)tree.getRoot();
    return  root.getMethod();
  }

  /** 
   * Return the number of call sites in the component.
   * @return the number of call sites in the component.
   */
  public int numberOfCallSites () {
    return  tree.numberOfNodes();
  }

  /** 
   * Return the OPT_CCInlineNode that is the root of this component.
   * @return the OPT_CCInlineNode that is the root of this component.
   */
  public OPT_CCInlineNode getRoot () {
    return  (OPT_CCInlineNode)tree.getRoot();
  }

  /**
   * Set the root of the tree
   * @param v the root
   */
  protected void setRoot (OPT_CCInlineNode v) {
    tree = new OPT_Tree(v);
  }

  /**
   * Return an enumeration of all the children in this component.
   * @return an enumeration of OPT_CCInlineNodes
   */
  Enumeration getChildrenInorder () {
    return  tree.getTopDownEnumerator();
  }

  /**
   * Return an enumeration of all the children in this component.
   * @return an enumeration of OPT_CCInlineNodes
   */
  Enumeration getAllChildren () {
    return  tree.elements();
  }

  /**
   * Return an enumeration of all the children of the given node.
   * @return an enumeration of OPT_CCInlineNodes
   */
  Enumeration getChildren (OPT_CCInlineNode node) {
    return  node.getChildren();
  }

  /**
   * Calculate the depth-first number for each node in the tree;
   * also cache a mapping from integer to node.
   */
  void calculateDFN () {
    int n = tree.numberOfNodes();
    dfnMap = new OPT_CCInlineNode[n];
    int i = 0;
    for (Enumeration e = tree.getTopDownEnumerator(); e.hasMoreElements();) {
      OPT_CCInlineNode node = (OPT_CCInlineNode)e.nextElement();
      dfnMap[i] = node;
      node.setDFN(i);
      i++;
    }
  }

  /**
   * Search the children of this node, and return a pointer
   * to the child which matches the given bytecode index and method.
   * Return null if not found.
   *
   * @param node the node whose children to search
   * @param bcIndex the bytecode index to match
   * @param vmMethod the vmMethod of the child to match
   * @return a reference to the child at that index and method
   */
  OPT_CCInlineNode findChild (OPT_CCInlineNode node, 
      int bcIndex, VM_Method vmMethod) {
    /*
     * TODO:  Make this faster with a hash table.  It is called a lot when 
     *	setting up 
     *        ccg_edge_list pointers in initializeCCGEdgeLists
     */
    for (Enumeration e = new OPT_TreeTopDownEnumerator(node); 
        e.hasMoreElements();) {
      OPT_CCInlineNode child = (OPT_CCInlineNode)e.nextElement();
      if (child.bcIndex == bcIndex && child.method == vmMethod)
        return  child;
    }
    return  null;
  }

  /**
   * Return a String representation of a inline plan component object.
   * <p>Method: 
   * <ul>
   *  <li> 1. number the nodes of the tree in their zero-based depth first 
   *	     order, and write out the nodes in their depth-first order
   *  <li> 2. write ":"
   *  <li> 3. write out the depth-first number of the parent of each
   *	     node, in order
   *  </ul>
   * <p> For example, the tree
   * <pre>
   * 				A
   *			       / \
   *			       B  C
   *			      /
   *			     D
   * </pre>
   * has a string representation of
   * <pre>
   *	"A B D C : -1 0 1 0"
   * </pre>
   * or equivalently,
   * <pre>
   *  "A C B D : -1 0 0 1"
   * </pre>
   * 
   * <p> TODO: this implementation uses two traversals.  If too slow,
   *	     merge into one traversal. Other optimizations are also
   *	     possible, like using a more compact representation.
   */
  public String toString () {
    StringBuffer s = new StringBuffer(100);
    // visit the nodes in depth-first order.  write out the nodes
    // in this order, and store the depth-first numbers in the 
    // vertices
    int i = 0;
    for (Enumeration e = tree.getTopDownEnumerator(); e.hasMoreElements();) {
      OPT_CCInlineNode v = (OPT_CCInlineNode)e.nextElement();
      s.append(v.method.getDeclaringClass().getDescriptor().toString()
          + " " + v.method.getName().toString() + " " 
          + v.method.getDescriptor().toString()
          + " @" + v.bcIndex + " ");
      v.setDFN(i++);
    }
    s.append(": ");
    // now traverse the nodes again, and write the dfn of the
    // parent of each node
    for (Enumeration e2 = tree.getTopDownEnumerator(); e2.hasMoreElements();) {
      OPT_CCInlineNode v = (OPT_CCInlineNode)e2.nextElement();
      OPT_CCInlineNode p = (OPT_CCInlineNode)v.getParent();
      if (p == null)
        s.append("-1 "); 
      else 
        s.append(p.dfn + " ");
    }
    return  s.toString();
  }

  /**
   * Construct an inline site.  
   * @param method the method the node represents
   * @param bcIndex the bytecodeIndex of the call
   */
  public static OPT_CCInlineNode makeSite (VM_Method method, int bcIndex) {
    return  new OPT_CCInlineNode(method, bcIndex);
  }

  /**
   * Count the number of bytecodes that will be inlined by this
   * component.
   * @return the number of bytecodes that will be inlined by this
   * component.
   */
  public int countInlinedBytecodes () {
    int count = 0;
    Enumeration e = tree.getTopDownEnumerator();
    // skip the root method;
    e.nextElement();
    // walk the tree, counting the size of the methods
    for (; e.hasMoreElements();) {
      OPT_CCInlineNode node = (OPT_CCInlineNode)e.nextElement();
      VM_Method m = node.method;
      byte[] bytecodes = m.getBytecodes();
      if (bytecodes != null)
        count += bytecodes.length;
    }
    return  count;
  }

  /**
   * Merge the tree representing this component with a new component.
   * @param c the new component
   */
  public void merge (OPT_CCInlineComponent c) {
    merge(tree, (OPT_CCInlineNode)tree.getRoot(), c.tree, c.getRoot());
  }

  /**
   * Merge the subtree of t2 rooted at v2 into the subtree of t1
   * rooted at v1
   * @param t1
   * @param v1
   * @param t2
   * @param v2
   */
  protected void merge (OPT_Tree t1, OPT_CCInlineNode v1, OPT_Tree t2, 
      OPT_CCInlineNode v2) {
    for (Enumeration e = v2.getChildren(); e.hasMoreElements();) {
      // get the next child of v2
      OPT_CCInlineNode child = (OPT_CCInlineNode)e.nextElement();
      int bcIndex = child.bcIndex;
      // see if this child exists in t1
      Vector oldChildren = v1.findChildren(bcIndex, t1);
      OPT_CCInlineNode oldChild = null;
      for (Enumeration e2 = oldChildren.elements(); e2.hasMoreElements();) {
        OPT_CCInlineNode node = (OPT_CCInlineNode)e2.nextElement();
        if (node.method == child.method)
          oldChild = node;
      }
      if (oldChild == null) {
        // child not found.  add the subtree
        addSubtreeAsChild(v1, child);
      } 
      else {
        // child was found.  recursively merge the child trees
        merge(t1, oldChild, t2, child);
      }
    }
  }

  /**
   * Add the subtree rooted at v2 as child of v1.
   */
  private void addSubtreeAsChild (OPT_CCInlineNode v1, OPT_CCInlineNode v2) {
    v1.addChild(v2);
  }

  /**
   * Add clone as child of node in node's component.
   */
  public void addComponentAsChild (OPT_CCInlineNode node, 
      OPT_CCInlineComponent clone) {
    OPT_CCInlineNode v2 = clone.getRoot();
    addSubtreeAsChild(node, v2);
  }

  /** 
   * Given a unique call site, return the VM_Methods to inline
   * at that call site.  Returns null if no inline target found.
   *
   * @param sequence the inline context of the caller
   * @param bc the bytecode index of the call site
   * @return the set of methods to inline
   */
  public VM_Method[] match (OPT_InlineSequence sequence, int bcIndex) {
    // convert the inline sequence to an array of nodes
    OPT_CCInlineNode[] context = sequence2array(sequence);
    OPT_CCInlineNode workingRoot = (OPT_CCInlineNode)tree.getRoot();
    // double check that the root of sequence is the same method as
    // the root of this component.
    if (VM.VerifyAssertions)
      VM.assert(context[0].method == workingRoot.method);
    // walk down the tree until workingRoot points to the CALLER context
    for (int i = 1; i < context.length; i++) {
      int index = context[i].bcIndex;
      // find the node in this component that matches the
      // required call site
      Vector children = workingRoot.findChildren(index, tree);
      // find the child that matches
      // node.method == context[i].method, 
      OPT_CCInlineNode match = null;
      for (Enumeration e = children.elements(); e.hasMoreElements();) {
        OPT_CCInlineNode node = (OPT_CCInlineNode)e.nextElement();
        if (node.method == context[i].method)
          match = node;
      }
      if (match == null)
        return  null;
      workingRoot = match;
    }
    // at this point, workingRoot points to the CALLER context.
    // find the child nodes corresponding to the callee.
    Vector children = workingRoot.findChildren(bcIndex, tree);
    if (children.size() == 0)
      return  null;
    // copy the resultant methods and return
    VM_Method[] result = new VM_Method[children.size()];
    int nullCount = 0;
    for (int i = 0; i < result.length; i++) {
      OPT_CCInlineNode node = (OPT_CCInlineNode)children.elementAt(i);
      result[i] = node.method;
      // null out methods with no bytecodes
      if ((!node.method.getDeclaringClass().isLoaded()) 
          || (node.method.getBytecodes() == null)) {
        nullCount++;
        result[i] = null;
      }
    }
    // if any methods were null, delete them
    if (nullCount > 0) {
      VM_Method[] result2 = new VM_Method[result.length - nullCount];
      int index = 0;
      for (int j = 0; j < result.length; j++) {
        if (result[j] != null) {
          result2[index++] = result[j];
        }
      }
      if (index == 0)
        result = null; 
      else 
        result = result2;
    }
    return  result;
  }

  /**
   * Convert an OPT_InlineSequence into an array, where the
   * first array element is the root of the sequence, and the
   * last array element is the leaf
   */
  private OPT_CCInlineNode[] sequence2array (OPT_InlineSequence seq) {
    OPT_CCInlineNode[] result = new OPT_CCInlineNode[seq.getInlineDepth()
        + 1];
    for (int i = result.length - 1; i >= 0; i--) {
      result[i] = new OPT_CCInlineNode(seq.getMethod(), seq.bcIndex);
      seq = seq.caller;
    }
    return  result;
  }
}



