/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*;

/**
 * OPT_DF_LatticeCell.java
 *
 * Represents a single lattice cell in a dataflow system.
 *
 * @author Stephen Fink
 */
abstract class OPT_DF_AbstractCell
    implements OPT_DF_LatticeCell {

  /**
   * Default Constructor 
   */
  OPT_DF_AbstractCell () {
    uses = new JDK2_HashSet(1);
    defs = new JDK2_HashSet(1);
  }

  /** 
   * This constructor bounds the initial capacity to save space.
   * @param capacity the initial capacity of the "uses" set
   */
  OPT_DF_AbstractCell (int capacity) {
    uses = new JDK2_HashSet(capacity);
    defs = new JDK2_HashSet(capacity);
  }

  /** 
   * Returns an enumeration of the equations in which this
   * lattice cell is used.
   * @return an enumeration of the equations in which this
   * lattice cell is used
   */
  public JDK2_Iterator getUses () {
    return  uses.iterator();
  }

  /** 
   * Return an enumeration of the equations in which this
   * lattice cell is defined.
   * @return an enumeration of the equations in which this
   * lattice cell is defined
   */
  public JDK2_Iterator getDefs () {
    return  defs.iterator();
  }

  /** 
   * Return a string representation of the cell
   * @return a string representation of the cell
   */
  public abstract String toString ();

  /** 
   * Note that this variable appears on the RHS of an equation.
   *
   * @param eq the equation
   */
  public void addUse (OPT_DF_Equation eq) {
    uses.add(eq);
  }

  /** 
   * Note that this variable appears on the LHS of an equation. 
   *
   * @param eq the equation
   */
  public void addDef (OPT_DF_Equation eq) {
    defs.add(eq);
  }

  /**
   * Set of OPT_DF_Equations which use this lattice cell.
   */
  JDK2_HashSet uses; 
  /**
   * Set of OPT_DF_Equations which define this lattice cell.
   */
  JDK2_HashSet defs; 

  public OPT_GraphNodeEnumeration inNodes() {
      return new OPT_GraphNodeEnumeration() {
	      private JDK2_Iterator i = defs.iterator();
	      public boolean hasMoreElements() { return i.hasNext(); }
	      public OPT_GraphNode next() { return (OPT_GraphNode)i.next(); }
	      public Object nextElement() { return next(); }
	  };
  }

  public OPT_GraphNodeEnumeration outNodes() {
      return new OPT_GraphNodeEnumeration() {
	      private JDK2_Iterator i = uses.iterator();
	      public boolean hasMoreElements() { return i.hasNext(); }
	      public OPT_GraphNode next() { return (OPT_GraphNode)i.next(); }
	      public Object nextElement() { return next(); }
	  };
  }

  /**
   * Field used for OPT_GraphNode interface.  TODO: is this needed?
   */
  private int index;

  /**
   * Implementation of OPT_GraphNode interface.  
   */
  public void setIndex (int i) {
    index = i;
  }

  /**
   * Implementation of OPT_GraphNode interface.  
   */
  public int getIndex () {
    return  index;
  }

  private Object scratchObject;
  private int scratch;

  public Object getScratchObject () {
    return  scratchObject;
  }
  public Object setScratchObject (Object o) {
    return  (scratchObject = o);
  }
  public int getScratch () {
    return  scratch;
  }
  public int setScratch (int o) {
    return  (scratch = o);
  }

}



