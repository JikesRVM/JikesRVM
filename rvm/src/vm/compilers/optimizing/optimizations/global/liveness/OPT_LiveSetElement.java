/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * OPT_LiveSetElement.java
 *
 * @author Michael Hind
 *
 * A simple class that holds an element in a LiveSet.
 */
final class OPT_LiveSetElement {

  /**
   * put your documentation comment here
   * @param   OPT_RegisterOperand register
   */
  OPT_LiveSetElement (OPT_RegisterOperand register) {
    regOp = register;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public final OPT_RegisterOperand getRegisterOperand () {
    return  regOp;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public final OPT_Register getRegister () {
    return  regOp.register;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public final VM_Type getRegisterType () {
    return  regOp.type;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public final OPT_LiveSetElement getNext () {
    return  next;
  }

  /**
   * put your documentation comment here
   * @param newNext
   */
  public final void setNext (OPT_LiveSetElement newNext) {
    next = newNext;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    StringBuffer buf = new StringBuffer("");
    ;
    buf.append(regOp);
    return  buf.toString();
  }
  private OPT_RegisterOperand regOp;
  private OPT_LiveSetElement next;
}



