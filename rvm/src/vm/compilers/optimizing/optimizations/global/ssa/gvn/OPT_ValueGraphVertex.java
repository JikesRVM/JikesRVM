/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class implements a vertex in the value graph used in global 
 * value numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 *
 * @author Stephen Fink
 */


import  java.util.*;


/**
 * put your documentation comment here
 */
class OPT_ValueGraphVertex extends OPT_SpaceEffGraphNode {
  Object name;        // the name of the variable defined by this node
  Object label;       // the name of the operator that does the definition
  int valueNumber;    // integer value number
  OPT_ValueGraphVertex[] targets;   // operand vertices, in order
  int arity;                        // number of operands needed

  /**
   * put your documentation comment here
   * @param   Object name
   */
  OPT_ValueGraphVertex (Object name) {
    this.name = name;
  }

  /**
   * Set up properties of this vertex identically to another vertex
   */
  void copyVertex (OPT_ValueGraphVertex v) {
    this.label = v.label;
    this.valueNumber = v.valueNumber;
    this.arity = v.arity;
    this.targets = new OPT_ValueGraphVertex[v.targets.length];
    for (int i = 0; i < targets.length; i++) {
      this.targets[i] = v.targets[i];
    }
  }

  /**
   * Does this vertex represent an incoming parameter?
   */
  boolean representsParameter () {
    return (label instanceof OPT_ValueGraphParamLabel);
  }

  /**
   * Set the label for this vertex.
   *
   * @param label the label (an operator of some type)
   * @param arity the number of operands needed
   */
  void setLabel (Object label, int arity) {
    this.label = label;
    this.arity = arity;
    targets = new OPT_ValueGraphVertex[arity];
  }

  /**
   * put your documentation comment here
   * @return 
   */
  Object getLabel () {
    return  label;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  Object getName () {
    return  name;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  int getValueNumber () {
    return  valueNumber;
  }

  /**
   * put your documentation comment here
   * @param number
   */
  void setValueNumber (int number) {
    valueNumber = number;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  boolean isConstant () {
    return  (label instanceof OPT_ConstantOperand);
  }

  // is the def for this node an allocation instruction?
  boolean isBornAtAllocation () {
    return  (label instanceof OPT_Instruction);
  }

  /**
   * return the target of the ith operand of this node
   */
  public OPT_ValueGraphVertex getTarget (int i) {
    return  targets[i];
  }

  /**
   * put your documentation comment here
   * @param target
   * @param pos
   */
  public void addTarget (OPT_ValueGraphVertex target, int pos) {
    targets[pos] = target;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int getArity () {
    return  arity;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    StringBuffer s = new StringBuffer("Vertex: " + name + " " + label);
    s.append(" Targets: ");
    for (int i = 0; i < arity; i++) {
      if (targets[i] == null) {
        s.append("null  ");
      } 
      else {
        s.append(targets[i].getName() + "  ");
      }
    }
    return  s.toString();
  }
}



