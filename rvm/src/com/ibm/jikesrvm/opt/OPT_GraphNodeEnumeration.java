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

import java.util.Enumeration;

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
interface OPT_GraphNodeEnumeration extends Enumeration<OPT_GraphNode>
{

  /**
   *  Return the next graph node in the enumeration.
   * @return the next graph node in the enumeration
   */
  OPT_GraphNode next ();
}



