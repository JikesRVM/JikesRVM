/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *  Generic interface for enumerations of graph nodes.  All graph
 * implementations should make sure that their enumerations of graph
 * nodes implement this interface, and all graph utilities that need
 * to enumerate nodes should use this interface.
 *
 * @author Julian Dolby
 *
 * @see OPT_Graph
 * @see OPT_GraphNode
 */
interface OPT_GraphNodeEnumeration extends java.util.Enumeration
{

  /**
   *  Return the next graph node in the enumeration.
   * @return the next graph node in the enumeration
   */
  OPT_GraphNode next ();
}



