/*
 * (C) Copyright IBM Corp. 2001
 */


/**
 *  Graph representations that use explicit ede objects should have
 * their edge objects implement this interface.
 *
 */
interface OPT_GraphEdge {

    OPT_GraphNode from();

    OPT_GraphNode to();

}



