/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VCG.java
//$Id$

import java.util.Enumeration;
import java.util.Hashtable;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * OPT_VCG implements the set of routines to output a graph
 * in VCG format.  The graph should implement the OPT_VCGGraph interface,
 * and the graph nodes and edges should implement OPT_VCGNode and
 * OPT_VCGEdge interfaces respectively.
 *
 * @author Igor Pechtchanski
 * @see OPT_VCGGraph
 * @see OPT_VCGNode
 * @see OPT_VCGEdge
 */

public final class OPT_VCG implements OPT_VCGConstants
{
  /**
   * Returns a VCG representation for a given graph.
   * @param g the graph in question.
   */
  public static String toVCG(OPT_VCGGraph g) {
    OPT_VCGGraph.GraphDesc gd = g.getVCGDescriptor();
    StringBuffer res = new StringBuffer("graph: {\n");

    /**
     * Emit graph header
     */

    res.append(pair("title", quote(gd.getTitle()), 1));
    // Options
    res.append(gd.getLayoutParameters()).append("\n");
    res.append(pair("display_edge_labels", gd.displayEdgeLabels(), 1));
    res.append(pair("late_edge_labels", gd.lateEdgeLabels(), 1));
    res.append(pair("portsharing", gd.portSharing(), 1));
    res.append(pair("node.width", gd.defaultNodeWidth(), 1));
    res.append(pair("node.color", gd.defaultNodeColor(), 1));
    res.append(pair("node.borderwidth", gd.defaultBorderWidth(), 1));
    res.append(pair("edge.linestyle", gd.defaultEdgeStyle(), 1));
    String[] eClasses = gd.getEdgeClasses();
    if (eClasses != null) {
      for (int i = 0; i < eClasses.length; i++)
        res.append(pair("classname "+i, quote(eClasses[i]), 1));
      res.append("\n");
    }
    String[] eColors = gd.getEdgeColors();

    /**
     * Process nodes
     */

    Hashtable nodeNames = new Hashtable();
    int nodenum = 0;

    for (Enumeration nodes = g.nodes(); nodes.hasMoreElements();) {
      OPT_VCGNode node = (OPT_VCGNode) nodes.nextElement();
      OPT_VCGNode.NodeDesc nd = node.getVCGDescriptor();
      res.append(indent("node: {", 1));
      String name = (String) nodeNames.get(node);
      if (name == null) {
        name = "Node "+(nodenum++);
        nodeNames.put(node, name);
      }
      res.append(pair("title", quote(name), 1));
      String label = nd.getLabel();
      if (label != null) res.append(pair("label", quote(label), 2));
      String info1 = nd.getInfo1();
      if (info1 != null) res.append(pair("info1", quote(info1), 2));
      String info2 = nd.getInfo2();
      if (info2 != null) res.append(pair("info2", quote(info2), 2));
      String info3 = nd.getInfo3();
      if (info3 != null) res.append(pair("info3", quote(info3), 2));
      String shape = nd.getShape();
      if (shape != null) res.append(pair("shape", shape, 2));
      String color = nd.getColor();
      if (color != null) res.append(pair("color", color, 2));
      int bwidth = nd.getBorderWidth();
      if (bwidth != 1) res.append(pair("borderwidth", bwidth, 2));
      res.append(indent("}", 1));
      res.append("\n");

      /**
       * Process edges
       */

      for (Enumeration edges = node.edges(); edges.hasMoreElements(); ) {
        OPT_VCGEdge edge = (OPT_VCGEdge) edges.nextElement();
        OPT_VCGEdge.EdgeDesc ed = edge.getVCGDescriptor();
        String eName = edge.backEdge()?"backedge":"edge";
        res.append(indent(eName+": {", 1));

        OPT_VisNode fromNode = edge.sourceNode();
        String fromName = (String) nodeNames.get(fromNode);
        if (fromName == null) {
          fromName = "Node "+(nodenum++);
          nodeNames.put(fromNode, fromName);
        }
        res.append(pair("sourcename", quote(fromName), 2));
        OPT_VisNode toNode = edge.targetNode();
        String toName = (String) nodeNames.get(toNode);
        if (toName == null) {
          toName = "Node "+(nodenum++);
          nodeNames.put(toNode, toName);
        }
        res.append(pair("targetname", quote(toName), 2));
        String eLabel = ed.getLabel();
        if (eLabel != null) res.append(pair("label", quote(eLabel), 2));
        int type = ed.getType();
        String eColor = ed.getColor();
        if (type != NONE) {
          res.append(pair("class", type, 2));
          if (eColor == null && eColors != null) eColor = eColors[type];
        }
        if (eColor != null) res.append(pair("color", eColor, 2));
        int thickness = ed.getThickness();
        if (thickness != 1) res.append(pair("thickness", thickness, 2));
        String linestyle = ed.getStyle();
        if (linestyle != null) res.append(pair("linestyle", linestyle, 2));
        res.append(indent("}", 1));
        res.append("\n");
      }
    }

    /**
     * Emit graph footer
     */

    res.append("}\n");
    return res.toString();
  }

  /**
   * Prints a VCG representation for a given graph to a file.
   * Overwrites the file.
   *
   * @param filename name of the file to append the graph to.
   * @param g the graph in question.
   */
  public static void printVCG(String filename, OPT_VCGGraph g) {
    printVCG(filename, g, false);
  }

  /**
   * Prints a VCG representation for a given graph to a file.
   *
   * @param filename name of the file to append the graph to.
   * @param g the graph in question.
   * @param append should this be appended to the end of the file?
   */
  public static void printVCG(String filename, OPT_VCGGraph g, boolean append) {
    writeToVCGFile(filename, toVCG(g), append);
  }

  /**
   * Initializes the vcg file for multiple graphs.
   *
   * @param filename name of the file.
   */
  public static void headerVCG(String filename) {
    appendToVCGFile(filename, "graph: {\n\n");
  }

  /**
   * Finalizes the vcg file after multiple graphs.
   *
   * @param filename name of the file.
   */
  public static void footerVCG(String filename) {
    appendToVCGFile(filename, "\n}\n");
  }

  /**
   * Appends a given string to a file.
   *
   * @param filename name of file
   * @param VCGOutput the string to write
   */
  private static void appendToVCGFile(String filename, String VCGOutput) {
    writeToVCGFile(filename, VCGOutput, true);
  }

  /**
   * Writes a given string to a file (overwriting its contents).
   *
   * @param filename name of file
   * @param VCGOutput the string to write
   */
  private static void writeToVCGFile(String filename, String VCGOutput) {
    writeToVCGFile(filename, VCGOutput, false);
  }

  /**
   * Writes a given string to a file.
   *
   * @param filename name of file
   * @param VCGOutput the string to write
   * @param append should the string be appended to the end?
   */
  private static void writeToVCGFile(String filename, String VCGOutput,
                                     boolean append)
  {
    try {
      PrintWriter out = new PrintWriter(new FileOutputStream(filename, append));
      out.println(VCGOutput);
      out.close() ;
    } catch(IOException e) {
      System.out.println("An error occurred: "+e);
    }
  }

  private static String[] indents = {
    "",
    "   ",
    "      ",
    "         ",
    "            ",
    "               ",
  };
  // Creates an {indent}value string
  // For internal use only.
  private static String indent(String value, int indent) {
    return indents[indent]+value+"\n";
  }
  // Creates an {indent}name:value string
  // For internal use only.
  private static String pair(String name, String value, int indent) {
    return indents[indent]+name+":"+value+"\n";
  }
  private static String pair(String name, int value, int indent) {
    return indents[indent]+name+":"+value+"\n";
  }
  private static String pair(String name, Object value, int indent) {
    return indents[indent]+name+":"+value+"\n";
  }
  private static String pair(String name, boolean value, int indent) {
    return indents[indent]+name+":"+(value?"yes":"no")+"\n";
  }
  // Place value in quotes, quoting all special characters (only '"' for now)
  // For internal use only.
  private static String quote(String value) {
    int k = 0;
    if ((k = value.indexOf('\"')) != -1) {
      StringBuffer sb = new StringBuffer();
      int s = k;
      for (; (k = value.indexOf('\"', s + 1)) != -1; s = k)
        sb.append(value.substring(s, k - 1)).append('\\');
      sb.append(value.substring(s));
      value = sb.toString();
    }
    return "\""+value+"\"";
  }
  private static String quote(int value) {
    return "\""+value+"\"";
  }
  private static String quote(Object value) {
    return "\""+value+"\"";
  }

  // private constructor, so no objects can be created.
  private OPT_VCG() { }
}

