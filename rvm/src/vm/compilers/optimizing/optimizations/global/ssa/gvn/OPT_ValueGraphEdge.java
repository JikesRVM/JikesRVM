/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class implements an edge in the value graph used in global value 
 * numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 *
 * @author Stephen Fink
 */


import  java.util.*;


/**
 * put your documentation comment here
 */
class OPT_ValueGraphEdge extends OPT_SpaceEffGraphEdge {

  /**
   * put your documentation comment here
   * @param   OPT_ValueGraphVertex src
   * @param   OPT_ValueGraphVertex target
   */
  OPT_ValueGraphEdge (OPT_ValueGraphVertex src, OPT_ValueGraphVertex target) {
    super(src, target);
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    OPT_ValueGraphVertex src = (OPT_ValueGraphVertex)fromNode();
    OPT_ValueGraphVertex dest = (OPT_ValueGraphVertex)toNode();
    return  src.name + " --> " + dest.name;
  }
}



