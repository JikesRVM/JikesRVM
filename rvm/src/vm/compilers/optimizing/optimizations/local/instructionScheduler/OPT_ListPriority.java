/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Vector;

/**
 * List instruction priority representation
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Priority
 * @see OPT_Scheduler
 * @author Igor Pechtchanski
 */
abstract class OPT_ListPriority extends OPT_Priority {
  /**
   * Instruction list.
   * Subclasses should fill.
   */
  protected final Vector instructionList = new Vector();
  // Current enumeration index.
  private int currIndex = 0;

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public final void reset () {
    currIndex = 0;
  }

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public final boolean hasMoreElements () {
    return  currIndex < instructionList.size();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final OPT_Instruction next () {
    return  (OPT_Instruction)instructionList.elementAt(currIndex++);
  }

  /**
   * Counts the instructions in sequence
   *
   * @return the number of instructions in sequence
   */
  public final int num () {
    return  instructionList.size();
  }

  /**
   * Appends new instruction to the list.
   *
   * @param instr new instruction
   */
  protected final void append (OPT_Instruction instr) {
    instructionList.addElement(instr);
  }

  /**
   * Inserts new instruction into the list.
   *
   * @param instr new instruction
   * @param i where to insert the new instruction
   */
  protected final void insertAt (OPT_Instruction instr, int i) {
    instructionList.insertElementAt(instr, i);
  }
}



