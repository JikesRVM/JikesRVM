/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */

interface OPT_GraphEdgeFilter {

    OPT_GraphNodeEnumeration inNodes(OPT_GraphNode node);

    OPT_GraphNodeEnumeration outNodes(OPT_GraphNode node);

}
