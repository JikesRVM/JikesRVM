/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Instruction priority representation
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Scheduler
 * @author Igor Pechtchanski
 */
abstract class OPT_Priority
    implements OPT_InstructionEnumeration {

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public abstract void reset ();

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public abstract boolean hasMoreElements ();

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final Object nextElement () {
    return  next();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public abstract OPT_Instruction next ();
}



