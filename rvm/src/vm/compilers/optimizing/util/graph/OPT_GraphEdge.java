/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 *  Graph representations that use explicit ede objects should have
 * their edge objects implement this interface.
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
interface OPT_GraphEdge {

    OPT_GraphNode from();

    OPT_GraphNode to();

}



