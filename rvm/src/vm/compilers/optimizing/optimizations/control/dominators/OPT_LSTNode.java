/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * graph node for LST (Loop Structure Tree)
 *
 * @author: Mauricio J. Serrano
 */
final class OPT_LSTNode extends OPT_SpaceEffGraphNode {
  OPT_BasicBlock header;
  OPT_BitVector loop;

  OPT_LSTNode(OPT_BasicBlock bb) {
    header = bb;
  }

  OPT_BasicBlock getHeader() {
    return header;
  }

  OPT_BitVector getLoop() {
    return loop;
  }
  public String toString() {
    return header + " " + loop;
  }
}



