/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_ReverseDFS extends OPT_DFS {

  /**
   * put your documentation comment here
   * @param   OPT_Graph net
   */
  OPT_ReverseDFS (OPT_Graph net) {
    super(net);
  }

  /**
   * put your documentation comment here
   * @param   OPT_GraphNodeEnumeration nodes
   */
  OPT_ReverseDFS (OPT_GraphNodeEnumeration nodes) {
    super(nodes);
  }

  /**
   * put your documentation comment here
   * @param n
   * @return 
   */
  protected OPT_GraphNodeEnumeration getConnected (OPT_GraphNode n) {
    return  n.inNodes();
  }
}



