/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_ReverseDFS extends OPT_DFS {

  OPT_ReverseDFS (OPT_Graph net) {
    super(net);
  }

  OPT_ReverseDFS (OPT_GraphNodeEnumeration nodes) {
    super(nodes);
  }

  protected OPT_GraphNodeEnumeration getConnected (OPT_GraphNode n) {
    return  n.inNodes();
  }
}
