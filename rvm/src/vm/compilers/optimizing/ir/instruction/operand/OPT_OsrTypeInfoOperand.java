/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.opt.ir;
import java.util.Arrays;
/*
 * An OPT_OsrTypeInfoOperand object keeps type information of locals
 * and stacks at a byte code index.
 *
 * @author Feng Qian
 */

public final class OPT_OsrTypeInfoOperand extends OPT_Operand {

  /**
   * The data type.
   */
  public byte[] localTypeCodes;
  public byte[] stackTypeCodes;

  /**
   * Create a new type operand with the specified data type.
   */
  public OPT_OsrTypeInfoOperand(byte[] ltcodes, byte[] stcodes) {
    this.localTypeCodes = ltcodes;
    this.stackTypeCodes = stcodes;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_OsrTypeInfoOperand(localTypeCodes, stackTypeCodes);
  }
  
  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public boolean similar(OPT_Operand op) {
    boolean result = true;

    if (!(op instanceof OPT_OsrTypeInfoOperand))
      return false;

    OPT_OsrTypeInfoOperand other = (OPT_OsrTypeInfoOperand)op;
    
    result = Arrays.equals(this.localTypeCodes, 
                           other.localTypeCodes) 
          && Arrays.equals(this.stackTypeCodes, 
                           other.stackTypeCodes);
    
    return result;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer("OsrTypeInfo(");
    for (int i=0, n=localTypeCodes.length; i<n; i++) {
      buf.append((char)localTypeCodes[i]);
    }

    buf.append(",");
    for (int i=0, n=stackTypeCodes.length; i<n; i++) {
      buf.append((char)stackTypeCodes[i]);
    }

    buf.append(")");

    return buf.toString();
  }
}
