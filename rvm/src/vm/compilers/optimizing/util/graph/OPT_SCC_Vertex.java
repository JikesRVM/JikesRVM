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

  OPT_SCC_Vertex(OPT_SCC scc) {
    this.scc = scc;
  }

  OPT_SCC getSCC() {
    return  scc;
  }

  public String toString() {
    return  scc.toString();
  }
}
