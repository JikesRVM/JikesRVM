/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class implements a graph vertex that holds an SCC.
 *
 * @author Stephen Fink
 */
class OPT_SCC_Vertex extends OPT_EdgelessGraphNode {
  private OPT_SCC scc;

  /**
   * put your documentation comment here
   * @param   OPT_SCC scc
   */
  OPT_SCC_Vertex (OPT_SCC scc) {
    this.scc = scc;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SCC getSCC () {
    return  scc;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    return  scc.toString();
  }
}



