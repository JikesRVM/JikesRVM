/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class represents the targets of sysCalls,
 * which are not Java methods and are in fact invoked using 
 * the host OS calling conventions not via JNI. 
 *
 * This class is PowerPC-specific.
 * 
 * @see OPT_Operand
 * @author Mauricio Serrano
 * @author Stephen Fink
 */
public final class OPT_SysMethodOperand extends OPT_Operand {

  /**
   * VM_Field of static field that contains instruction pointer
   */
  VM_Field ip;     
  
  /**
   * Create a system call method operand from the name.
   * 
   * @param name VM_Field (of the bootrecord object) that contains the target IP.
   */ 
  OPT_SysMethodOperand(VM_Field _ip) {
    ip = _ip;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_SysMethodOperand(ip);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  boolean similar(OPT_Operand op) {
    return (op instanceof OPT_SysMethodOperand) && ((OPT_SysMethodOperand)op).ip == ip;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return ip.toString(); 
  }
}
