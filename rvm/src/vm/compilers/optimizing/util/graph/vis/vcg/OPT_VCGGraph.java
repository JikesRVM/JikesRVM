/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VCGGraph.java
//$Id$

/**
 * OPT_VCGGraph provides the minimum set of routines for printing a graph
 * in VCG format.  The graph nodes and edges should implement OPT_VCGNode
 * and OPT_VCGEdge interfaces respectively.
 *
 * @author Igor Pechtchanski
 * @see OPT_VCG
 * @see OPT_VCGNode
 * @see OPT_VCGEdge
 */

public interface OPT_VCGGraph extends OPT_VisGraph {
  /**
   * Returns a VCG descriptor for the graph which will provide VCG-relevant
   * information for the graph.
   * If finer control over graph options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public GraphDesc getVCGDescriptor() { return defaultVCGDesc; }
   * </pre>
   * @return graph descriptor
   */
  public GraphDesc getVCGDescriptor();

  /**
   * Default VCG descriptor
   */
  public final GraphDesc defaultVCGDesc = new GraphDesc();

  /**
   * VCG Graph Descriptor class
   * Subclass to extend functionality
   */
  public static class GraphDesc implements OPT_VCGConstants {
    /**
     * Returns the title of the graph.
     * @return graph title
     */
    public String getTitle() { return ""; }

    /**
     * Returns colors for edge classes in the graph.
     * @return colors for edge classes
     */
    public String[] getEdgeColors() { return null; }

    /**
     * Returns names for edge classes in the graph.
     * @return names for edge classes
     */
    public String[] getEdgeClasses() { return null; }

    /** 
     * Returns InfoName: for labeling node information.
     * @return node info labels
     */
    public String[] getNodeNames() { return null; }

    /** 
     * Returns default width of a node.
     * @return default node width
     */
    public int defaultNodeWidth() { return NONE; }

    /** 
     * Returns default border width of a node.
     * @return default node border width
     */
    public int defaultBorderWidth() { return 1; }

    /** 
     * Returns default color of a node.
     * @return default node color
     */
    public String defaultNodeColor() { return "pink"; }

    /** 
     * Returns default line style of an edge.
     * @return default edge line style
     */
    public String defaultEdgeStyle() { return "solid"; }

    /** 
     * Returns whether the viewer should share "ports"
     * (edge connection points).
     * @return true if sharing is allowed, false otherwise
     */
    public boolean portSharing() { return false; }

    /** 
     * Returns whether the viewer should display labels for edges.
     * @return true if labels should be displayed, false otherwise
     */
    public boolean displayEdgeLabels() { return true; }

    /** 
     * Returns whether the viewer should ? labels for edges.
     * @return true if labels should be ?, false otherwise
     */
    public boolean lateEdgeLabels() { return true; }

    /** 
     * Returns layout parameters of the graph.
     * @return graph layout parameters
     */
    public String getLayoutParameters() {
      return "   layout_algorithm:minbackward\n" +
        "   layout_nearfactor:1\n" +
        "   layout_downfactor:100\n";
    }
  }
}

