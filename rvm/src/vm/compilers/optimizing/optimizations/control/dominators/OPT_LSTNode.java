/*
 * (C) Copyright IBM Corp. 2001
 */

// graph node for LST (Loop Structure Tree)
//
final class OPT_LSTNode extends OPT_SpaceEffGraphNode {
  OPT_BasicBlock header;
  OPT_BitVector loop;
  OPT_BasicBlock components[];   // basic block composing the loop 
                                 // (outermost portion only)

  /**
   * put your documentation comment here
   * @param   OPT_BasicBlock bb
   */
  OPT_LSTNode (OPT_BasicBlock bb) {
    header = bb;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_BasicBlock getBasicBlock () {
    return  header;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    return  header + " " + loop;
  }
}



