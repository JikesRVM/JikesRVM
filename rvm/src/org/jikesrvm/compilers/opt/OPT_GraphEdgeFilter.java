/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
package org.jikesrvm.compilers.opt;


interface OPT_GraphEdgeFilter {

    OPT_GraphNodeEnumeration inNodes(OPT_GraphNode node);

    OPT_GraphNodeEnumeration outNodes(OPT_GraphNode node);

}
