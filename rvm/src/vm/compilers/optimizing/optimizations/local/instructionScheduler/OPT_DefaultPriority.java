/*
 * (C) Copyright IBM Corp. 2001
 */
// OPT_DefaultPriority.java



/**
 * Default (IR-order) instruction list
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Priority
 * @see OPT_Scheduler
 * @author Igor Pechtchanski
 */
class OPT_DefaultPriority extends OPT_Priority {
  // Underlying enumeration.
  private OPT_IR ir;
  private OPT_BasicBlock bb;
  private OPT_Instruction i;
  private OPT_InstructionEnumeration instr;

  /**
   * Creates new priority object for a given basic block
   *
   * @param ir IR in question
   * @param bb basic block
   */
  public OPT_DefaultPriority (OPT_IR ir, OPT_BasicBlock bb) {
    this.ir = ir;
    this.bb = bb;
    // i = bb.firstInstruction();
    // instr = bb.forwardRealInstrEnumerator();
  }

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public final void reset () {
    i = bb.firstInstruction();
    instr = bb.forwardRealInstrEnumerator();
  }

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public final boolean hasMoreElements () {
    return  i != null || instr.hasMoreElements();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final OPT_Instruction next () {
    if (i != null) {
      OPT_Instruction r = i;
      i = null;
      return  r;
    }
    return  instr.next();
  }
}



