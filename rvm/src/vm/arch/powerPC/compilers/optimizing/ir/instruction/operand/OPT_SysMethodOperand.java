/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:

/**
 * This class represents the targets of
 * VM_Magic.Sys calls, which are not Java methods and are 
 * in fact invoked using the host OS calling conventions not via JNI. 
 *
 * This class is PowerPC-specific.
 * 
 * @see OPT_Operand
 * @see VM_Method
 * @author Mauricio Serrano
 * @author Stephen Fink
 */
public final class OPT_SysMethodOperand extends OPT_Operand {

  /**
   * record that contains the function descriptor
   */
  VM_Field record; 

  /**
   * instruction pointer offset from the record
   */
  VM_Field ip;     
  
  /**
   * toc from the record (AIX only)
   */ 
  VM_Field toc;

  /**
   * Create a system call method operand from the name.
   * 
   * @param name the name of the method
   */ 
  OPT_SysMethodOperand(String name) {
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom("the_boot_record");
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom("LVM_BootRecord;");
    record = OPT_ClassLoaderProxy.VM_BootRecord.findDeclaredField(memName,memDescriptor);

    memName       = VM_Atom.findOrCreateAsciiAtom(name+"IP");
    memDescriptor = VM_Atom.findOrCreateAsciiAtom("I");
    ip = OPT_ClassLoaderProxy.VM_BootRecord.findDeclaredField(memName, memDescriptor);
    if (ip == null)
      throw new OPT_OptimizingCompilerException("system call method does not exist: "+name);

    /* if the TOC does not exist, set it to NULL */
    memName       = VM_Atom.findOrCreateAsciiAtom(name+"TOC");
    memDescriptor = VM_Atom.findOrCreateAsciiAtom("I");
    toc = OPT_ClassLoaderProxy.VM_BootRecord.findDeclaredField(memName, memDescriptor);
  }

  /**
   * Create a system call method operand given the record, ip, and toc
   *
   * @param Record the record that contains the IP and TOC
   * @param IP the instruction pointer to jump to
   * @param TOC the TOC (AIX table of contents) to use for the callee
   */
  OPT_SysMethodOperand(VM_Field Record, VM_Field IP, VM_Field TOC) {
    record = Record;
    ip     = IP;
    toc    = TOC;
  }


  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_SysMethodOperand(record,ip,toc);
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
    OPT_SysMethodOperand nat= null;
    if (op instanceof OPT_SysMethodOperand)
      nat = (OPT_SysMethodOperand)op;
    else
      return false; 
    return (nat.record == record) && (nat.ip == ip) && (nat.toc == toc);
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
