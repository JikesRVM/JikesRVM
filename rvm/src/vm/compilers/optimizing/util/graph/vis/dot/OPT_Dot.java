/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_Dot.java
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
import java.util.Hashtable;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * OPT_Dot implements the set of routines to output a graph in Dot format.
 * The graph should implement the OPT_DotGraph interface, and the graph
 * nodes and edges should implement OPT_DotNode and OPT_DotEdge interfaces
 * respectively.
 *
 * @author Igor Pechtchanski
 * @see OPT_DotGraph
 * @see OPT_DotNode
 * @see OPT_DotEdge
 */

public final class OPT_Dot implements OPT_DotConstants
{
  /**
   * Returns a Dot representation for a given graph.
   * TODO: accomodate subgraphs
   * @param g the graph in question.
   */
  public static String toDot(OPT_DotGraph g) {
    OPT_DotGraph.GraphDesc gd = g.getDotDescriptor();
    StringBuffer res = new StringBuffer("digraph G {\n");

    // Emit graph options

    String gLbl = gd.getLabel();
    if (gLbl != null)
      res.append(indent(pair("label", quote(gLbl)), 1)).append(";\n");
    String gDir = gd.getDirection();
    if (gDir != null)
      res.append(indent(pair("rankdir", gDir), 1)).append(";\n");
    if (gd.concentrate()) res.append(indent("concentrate", 1)).append(";\n");
    String gClr = gd.getColor();
    if (gClr != null)
      res.append(indent(pair("color", gClr), 1)).append(";\n");
    String gLOP = gd.getLayoutParameters();
    if (gLOP != null)
      res.append(indent(gLOP, 1)).append("\n");
    res.append("\n");

    // Default node options

    OPT_DotNode.NodeDesc dnd = gd.getDefaultNodeDescriptor();
    if (dnd != null) {
      res.append(indent("node [", 1)).append("\n");
      String nLbl = dnd.getLabel();
      if (nLbl != null)
        res.append(indent(pair("label", quote(nLbl)), 2)).append(",\n");
      String nClr = dnd.getColor();
      if (nClr != null)
        res.append(indent(pair("color", quote(nClr)), 2)).append(",\n");
      String nShp = dnd.getShape();
      if (nShp != null)
        res.append(indent(pair("shape", nShp), 2)).append(",\n");
      String nSty = dnd.getStyle();
      if (nSty != null)
        res.append(indent(pair("style", nSty), 2)).append(",\n");
      float nWid = dnd.getWidth();
      if (nWid != OPT_DotNode.NodeDesc.DEFAULT_WIDTH)
        res.append(indent(pair("width", nWid), 2)).append(",\n");
      float nHgt = dnd.getHeight();
      if (nHgt != OPT_DotNode.NodeDesc.DEFAULT_HEIGHT)
        res.append(indent(pair("height", nHgt), 2)).append(",\n");
      res.append(indent("];\n\n", 1));
    }

    // Default edge options

    OPT_DotEdge.EdgeDesc ded = gd.getDefaultEdgeDescriptor();
    if (ded != null) {
      res.append(indent("edge [", 1)).append("\n");
      String eLbl = ded.getLabel();
      if (eLbl != null) {
        res.append(indent(pair("label", quote(eLbl)), 2)).append(",\n");
        // Verify that this makes sense only if label is defined!
        if (ded.decorate()) res.append(indent("decorate", 2)).append(",\n");
      }
      String eClr = ded.getColor();
      if (eClr != null)
        res.append(indent(pair("color", quote(eClr)), 2)).append(",\n");
      String eSty = ded.getStyle();
      if (eSty != null)
        res.append(indent(pair("style", eSty), 2)).append(",\n");
      String eId = ded.getId();
      if (eId != null)
        res.append(indent(pair("id", quote(eId)), 2)).append(",\n");
      String eDir = ded.getDirection();
      if (eDir != null)
        res.append(indent(pair("dir", eDir), 2)).append(",\n");
      int eWgt = ded.getWeight();
      if (eWgt != NONE)
        res.append(indent(pair("weight", eWgt), 2)).append(",\n");
      res.append(indent("];\n\n", 1));
    }

    // Process nodes

    Hashtable nodeNames = new Hashtable();
    int nodenum = 0;

    for (Enumeration nodes = g.nodes(); nodes.hasMoreElements();) {
      OPT_DotNode node = (OPT_DotNode) nodes.nextElement();
      String name = (String) nodeNames.get(node);
      if (name == null) {
        name = "Node"+(nodenum++);
        nodeNames.put(node, name);
      }
      res.append(indent(name, 1)).append(" [");
      OPT_DotNode.NodeDesc nd = node.getDotDescriptor();
      String nLbl = nd.getLabel();
      if (nLbl != null) res.append(pair("label", quote(nLbl))).append(",");
      String nClr = nd.getColor();
      if (nClr != null) res.append(pair("color", quote(nClr))).append(",");
      String nShp = nd.getShape();
      if (nShp != null) res.append(pair("shape", nShp)).append(",");
      String nSty = nd.getStyle();
      if (nSty != null) res.append(pair("style", nSty)).append(",");
      float nWid = nd.getWidth();
      if (nWid != OPT_DotNode.NodeDesc.DEFAULT_WIDTH)
        res.append(pair("width", nWid)).append(",");
      float nHgt = nd.getHeight();
      if (nHgt != OPT_DotNode.NodeDesc.DEFAULT_HEIGHT)
        res.append(pair("height", nHgt)).append(",");
      res.append("];\n");

      // Process edges

      for (Enumeration edges = node.edges(); edges.hasMoreElements(); ) {
        OPT_DotEdge edge = (OPT_DotEdge) edges.nextElement();
        OPT_DotEdge.EdgeDesc ed = edge.getDotDescriptor();
        OPT_VisNode sNode = edge.sourceNode();
        String sName = (String) nodeNames.get(sNode);
        if (sName == null) {
          sName = "Node"+(nodenum++);
          nodeNames.put(sNode, sName);
        }
        OPT_VisNode tNode = edge.targetNode();
        String tName = (String) nodeNames.get(tNode);
        if (tName == null) {
          tName = "Node"+(nodenum++);
          nodeNames.put(tNode, tName);
        }
        res.append(indent(sName, 1)).append(" -> ").append(tName).append(" [");
        String eLbl = ed.getLabel();
        if (eLbl != null) {
          res.append(pair("label", quote(eLbl))).append(",");
          // Verify that this makes sense only if label is defined!
          if (ed.decorate()) res.append("decorate").append(",");
        }
        String eClr = ed.getColor();
        if (eClr != null) res.append(pair("color", quote(eClr))).append(",");
        String eSty = ed.getStyle();
        if (eSty != null) res.append(pair("style", eSty)).append(",");
        String eId = ed.getId();
        if (eId != null) res.append(pair("id", quote(eId))).append(",");
        String eDir = ed.getDirection();
        if (eDir != null) res.append(pair("dir", eDir)).append(",");
        int eWgt = ed.getWeight();
        if (eWgt != NONE) res.append(pair("weight", eWgt)).append(",");
        res.append("];\n");
      }
    }

    // Emit graph footer

    res.append("}\n");
    return res.toString();
  }

  /**
   * Prints a Dot representation for a given graph to a file.
   * Overwrites the file.
   *
   * @param filename name of the file to append the graph to.
   * @param g the graph in question.
   */
  public static void printDot(String filename, OPT_DotGraph g) {
    printDot(filename, g, false);
  }

  /**
   * Prints a Dot representation for a given graph to a file.
   *
   * @param filename name of the file to append the graph to.
   * @param g the graph in question.
   * @param append should this be appended to the end of the file?
   */
  public static void printDot(String filename, OPT_DotGraph g, boolean append) {
    writeToDotFile(filename, toDot(g), append);
  }

  /**
   * Initializes the vcg file for multiple graphs.
   *
   * @param filename name of the file.
   */
  public static void headerDot(String filename) {
    appendToDotFile(filename, "digraph G {\n");
  }

  /**
   * Finalizes the vcg file after multiple graphs.
   *
   * @param filename name of the file.
   */
  public static void footerDot(String filename) {
    appendToDotFile(filename, "}\n");
  }

  /**
   * Appends a given string to a file.
   *
   * @param filename name of file
   * @param DotOutput the string to write
   */
  private static void appendToDotFile(String filename, String DotOutput) {
    writeToDotFile(filename, DotOutput, true);
  }

  /**
   * Writes a given string to a file (overwriting its contents).
   *
   * @param filename name of file
   * @param DotOutput the string to write
   */
  private static void writeToDotFile(String filename, String DotOutput) {
    writeToDotFile(filename, DotOutput, false);
  }

  /**
   * Writes a given string to a file.
   *
   * @param filename name of file
   * @param DotOutput the string to write
   * @param append should the string be appended to the end?
   */
  private static void writeToDotFile(String filename, String DotOutput,
                                     boolean append)
  {
    try {
      PrintWriter out = new PrintWriter(new FileOutputStream(filename, append));
      out.println(DotOutput);
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
  // Creates an {indent} string
  // For internal use only.
  private static String indent(int indent) {
    return indents[indent];
  }
  // Creates an {indent}value string
  // For internal use only.
  private static String indent(String value, int indent) {
    return indents[indent]+value;
  }
  // Creates an {indent}name=value string
  // For internal use only.
  private static String pair(String name, String value) {
    return name+"="+value;
  }
  private static String pair(String name, int value) {
    return name+"="+value;
  }
  private static String pair(String name, float value) {
    return name+"="+value;
  }
  private static String pair(String name, Object value) {
    return name+"="+value;
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
  private OPT_Dot() { }
}

