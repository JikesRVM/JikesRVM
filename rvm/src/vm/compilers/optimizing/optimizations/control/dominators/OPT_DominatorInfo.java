/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*; 
/**
 * OPT_DominatorInfo.java
 *
 * This structure holds dominator-related information for a basic block.
 *
 * @author Stephen Fink
 */
class OPT_DominatorInfo {
  /**
   * A BitVector which represents the dominators of the basic block
   */
  OPT_BitVector dominators;      
  /**
   * The basic block's immediate dominator.
   */
  OPT_BasicBlock idom;         

  /**
   * Make a structure with a given bit set holding the dominators
   * of the basic block.
   * 
   * @param  dominators the bit set
   */
  OPT_DominatorInfo (OPT_BitVector dominators) {
    this.dominators = dominators;
  }

  /** 
   * Return the immediate dominator of a basic block. 
   * 
   * <p> Note: the dominator info must be calculated before calling this
   * routine
   *
   * @param bb the basic block in question
   * @return bb's immediate dominator, as cached in bb's DominatorInfo
   */
  public static OPT_BasicBlock idom (OPT_BasicBlock bb) {
    OPT_DominatorInfo info = (OPT_DominatorInfo)bb.scratchObject;
    return  info.idom;
  }

  /**
   * Is the basic block represented by this structure dominated by another
   * basic block?
   * 
   * @param bb the basic block in question
   * @return true or false
   */
  boolean isDominatedBy (OPT_BasicBlock bb) {
    return  dominators.get(bb.getNumber());
  }

  /**
   * Is one basic block (the slave) dominated by another (the master)?
   * 
   * @param slave the potential dominatee
   * @param master the potential dominator
   * @return true or false
   */
  static boolean isDominatedBy (OPT_BasicBlock slave, OPT_BasicBlock master) {
    return  ((OPT_DominatorInfo)slave.scratchObject).
        dominators.get(master.getNumber());
  }
}



