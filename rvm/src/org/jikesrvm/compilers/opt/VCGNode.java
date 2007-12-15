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
package org.jikesrvm.compilers.opt;

/**
 * VCGNode provides the minimum set of routines for printing a graph
 * node in VCG format.  The graph should implement VCGGraph interface,
 * and its edges - VCGEdge interface.
 *
 * @see VCG
 * @see VCGGraph
 * @see VCGEdge
 */

public interface VCGNode extends VisNode {
  /**
   * Returns a VCG descriptor for the node which will provide VCG-relevant
   * information for the node.
   * If finer control over node options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public NodeDesc getVCGDescriptor() { return defaultVCGDesc; }
   * </pre>
   * @return node descriptor
   */
  NodeDesc getVCGDescriptor();

  /**
   * Default VCG descriptor
   */
  NodeDesc defaultVCGDesc = new NodeDesc();

  /**
   * VCG Graph Node Descriptor class
   * Subclass to extend functionality
   */
  class NodeDesc implements VCGConstants {
    /**
     * Returns the label of the node (contents).
     * Default is node number.
     * @return node label
     */
    public String getLabel() { return null; }

    /**
     * Returns the first info of the node (contents).
     * Default is empty.
     * @return node info 1
     */
    public String getInfo1() { return null; }

    /**
     * Returns the second info of the node (contents).
     * Default is empty.
     * @return node info 2
     */
    public String getInfo2() { return null; }

    /**
     * Returns the third info of the node (contents).
     * Default is empty.
     * @return node info 3
     */
    public String getInfo3() { return null; }

    /**
     * Returns the shape of the node.
     * Default is rectangle.
     * @return node shape
     */
    public String getShape() { return null; }

    /**
     * Returns the color of the node.
     * @return node color
     */
    public String getColor() { return null; }

    /**
     * Returns the border width of the node.
     * @return node border width
     */
    public int getBorderWidth() { return 1; }
  }

  /**
   * To be used for implementing edges() for graphs that don't
   * have explicit edge representation.
   */
  class DefaultEdge extends VisNode.DefaultEdge implements VCGEdge {
    private boolean _backEdge;

    public DefaultEdge(VCGNode s, VCGNode t) { this(s, t, false); }

    public DefaultEdge(VCGNode s, VCGNode t, boolean backEdge) {
      super(s, t);
      _backEdge = backEdge;
    }

    public boolean backEdge() { return _backEdge; }

    public VCGEdge.EdgeDesc getVCGDescriptor() { return VCGEdge.defaultVCGDesc; }
  }
}

