/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.OPT_IR;
import com.ibm.JikesRVM.opt.ir.OPT_BasicBlock;
import java.util.*;

/**
 * An OPT_HeapVariable represents a heap variable for heap array SSA form
 *
 * @author Stephen Fink
 */
public class OPT_HeapVariable {

  /** 
   * Create a new Heap variable of a given type, with a given number.
   * 
   * @param type a VM_Field or VM_Type object, naming the type of this
   *              heap
   * @param number second part of the name of this heap variable
   * @param ir the governing IR 
   */
  public OPT_HeapVariable (Object type, int number, OPT_IR ir) {
    this.type = type;
    this.number = number;
    definedIn = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
  }

  /** 
   * Return a number that uniquely identifies this heap variable, among
   * all the heap variables with the same type.
   * @return the number
   */
  public int getNumber () {
    return  number;
  }

  /** 
   * Return the type representing this heap object.
   * @return either a VM_Type or VM_Field object
   */
  public Object getHeapType () {
    return  type;
  }

  /** 
   * Return a bit vector that represents the basic blocks that define
   * this heap variable.
   * @return a bit vector that represents the basic blocks that define
   * this heap variable.
   */
  public OPT_BitVector getDefBlocks () {
    return  definedIn;
  }

  /** 
   * Note that this heap variable is defined in a given basic block.
   * @param b a basic block that defines this heap variable
   */
  public void registerDef (OPT_BasicBlock b) {
    definedIn.set(b.getNumber());
  }

  /** 
   * Return a String representation of this variable
   * @return a String representation of this variable
   */
  public String toString () {
    return  "HEAP<" + type + ">" + number;
  }

  /**
   * Is this heap variable exposed on procedure entry?
   * <p> Equivalently: is the number = zero?
   * @return true or false
   */
  public boolean isExposedOnEntry () {
    return  (number == 0);
  }

  /**
   * a unique identifier for this heap variable among all heap variables
   * with this type.
   */
  public int number;        
  /**
   * a bit vector representing the basic blocks that write to this
   * variable
   */
  public OPT_BitVector definedIn;    
  /**
   * The type of this heap variable.  Must be either a VM_Type or
   * VM_Field.
   */
  public Object type;             
}



