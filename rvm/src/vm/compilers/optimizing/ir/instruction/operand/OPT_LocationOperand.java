/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a location in memory. Used to keep track of memory aliasing.
 * 
 * @see OPT_Operand
 * 
 * @author Igor Pechtchanski
 * @author Mauricio Serrano
 * @author John Whaley
 */
public final class OPT_LocationOperand extends OPT_Operand 
  implements OPT_Constants  {

  /*
   * TODO: Now that we don't pay a large penalty for dynamic type checks
   * of non-final classes, redesign this mess to have separate subclasses
   * of location operands for each type of memory access.
   * In the process, also switch to using synthetic VM_Fields
   * for the various pieces of the object header 
   * (something like the following might work):
   *   (VM_Field) VM.getMember("[I", "length", "I");   .
   *      . . .                                        } all primitive types
   * (VM_Field) VM.getMember("[J", "length", "I");   '
   * (VM_Field) VM.getMember("[Ljava/lang/Object;", "length", "I");
   * (VM_Field) VM.getMember("Ljava/lang/Object;", "TIB", "[I");
   */

  /** Enumeration of Access type */
  public static final int FIELD_ACCESS   = 0;
  /** Enumeration of Access type */
  public static final int ARRAY_ACCESS   = 1;
  /** Enumeration of Access type */
  public static final int JTOC_ACCESS    = 2;
  /** Enumeration of Access type */
  public static final int SPILL_ACCESS   = 3;
  /** Enumeration of Access type */
  public static final int ALENGTH_ACCESS = 4;
  /** Enumeration of Access type */
  public static final int METHOD_ACCESS = 5;
  /** Enumeration of Access type */
  public static final int REDIRECTION_ACCESS = 6;

  /**
   * The type of this location.
   */
  int type;

  /**
   * Field that corresponds to this location.
   * null if this is not a field access.
   */
  VM_Field field;

  /**
   * Method operand that corresponds to this location
   * null if this is not a method access.
   */
  OPT_MethodOperand methOp;

  /**
   * Array element type that corresponds to the type of the array that contains
   * this location. null if this is not an array access.
   */
  VM_Type arrayElementType;

  /**
   * JTOC index that corresponds to this location.
   * -1 if this is not a JTOC access.
   */
  int JTOCindex = -1;

  /**
   * Spill offset that corresponds to this location.
   * -1 if this is not a spill access.
   */
  int spillOffset = -1;

  /**
   * Reference number.  Set by alias analysis.
   * Used to distinguish same-type accesses.
   */
  int refNumber;

  /**
   * Constructs a new location operand with the given field
   * 
   * @param loc location
   */
  OPT_LocationOperand(VM_Field loc) {
    type = FIELD_ACCESS;
    field = loc;
  }

  /**
   * Constructs a new location operand with the given method
   * 
   * @param loc location
   */
  OPT_LocationOperand(OPT_MethodOperand m) {
    type = METHOD_ACCESS;
    methOp = m;
  }

  /**
   * Constructs a new location operand with the given array element type.
   * 
   * @param type array element type
   */
  OPT_LocationOperand(VM_Type t) {
    type = ARRAY_ACCESS;
    arrayElementType = t;
  }

  /**
   * Constructs a new location operand with the given JTOC index or
   * spill offset.
   * 
   * @param index if positive, JTOC index, otherwise spill offset
   */
  OPT_LocationOperand(int index) {
    if (index > 0) {
      type = JTOC_ACCESS;
      JTOCindex = index;
    } else {
      type = SPILL_ACCESS;
      spillOffset = index;
    }
  }

  /**
   * Constructs a new location operand for array length access.
   */
  OPT_LocationOperand() {
    type = ALENGTH_ACCESS;
  }

  static OPT_LocationOperand createRedirection() {
      OPT_LocationOperand result = new OPT_LocationOperand();
      result.type = REDIRECTION_ACCESS;
      return result;
  }

  /**
   * Sets the reference number.  To be used by alias analysis.
   *
   * @param n the new reference number
   */
  public final void setRefNumber(int n) {
    refNumber = n;
  }

  /**
   * Returns the reference number.
   */
  public final int getRefNumber() {
    return refNumber;
  }

  public final OPT_LocationOperand asFieldAccess()   { return this; }
  public final OPT_LocationOperand asArrayAccess()   { return this; }
  public final OPT_LocationOperand asJTOCAccess()    { return this; }
  public final OPT_LocationOperand asSpillAccess()   { return this; }
  public final OPT_LocationOperand asALengthAccess() { return this; }
  public final OPT_LocationOperand asMethodAccess()  { return this; }

  public final VM_Field getField() { return field; }
  public final VM_Type getElementType() { return arrayElementType; }
  public final int getIndex() { return JTOCindex; }
  public final int getOffset() { return spillOffset; }

  public final boolean isFieldAccess()   { return type == FIELD_ACCESS; }
  public final boolean isArrayAccess()   { return type == ARRAY_ACCESS; }
  public final boolean isJTOCAccess()    { return type == JTOC_ACCESS; }
  public final boolean isSpillAccess()   { return type == SPILL_ACCESS; }
  public final boolean isALengthAccess() { return type == ALENGTH_ACCESS; }
  public final boolean isMethodAccess() { return type == METHOD_ACCESS; }
  public final boolean isRedirectionAccess() { return type == REDIRECTION_ACCESS; }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    OPT_LocationOperand o = null;
    switch (type) {
    case FIELD_ACCESS:   
      o = new OPT_LocationOperand(field); 
      break;
    case ARRAY_ACCESS:   
      o = new OPT_LocationOperand(arrayElementType); 
      break;
    case JTOC_ACCESS:    
      o = new OPT_LocationOperand(JTOCindex); 
      break;
    case SPILL_ACCESS:   
      o = new OPT_LocationOperand(spillOffset); 
      break;
    case ALENGTH_ACCESS: 
      o = new OPT_LocationOperand(); 
      break;
    case METHOD_ACCESS:  
      o = new OPT_LocationOperand(methOp);
      break;
    case REDIRECTION_ACCESS:  
      o = createRedirection();
      break;
    default:
      o = new OPT_LocationOperand(); 
      break;
    }
    o.refNumber = refNumber;
    return o;
  }

  // NOTE: not checking for (t1==null xor t2==null) for efficiency
  private static boolean arrayMayBeAliased(VM_Type t1, VM_Type t2) {
    return ((t1 == t2) ||
	    (OPT_ClassLoaderProxy.includesType(t1, t2) != NO) ||
	    (OPT_ClassLoaderProxy.includesType(t2, t1) != NO));
  }

  /**
   * Returns true if operands op1 and op2 may be aliased.
   * 
   * @param op1 the first operand 
   * @param op2 the second operand
   * @return <code>true</code> if the operands might be aliased or
   *         <code>false</code> if they are definitely not aliased
   */
  static boolean mayBeAliased(OPT_LocationOperand op1, 
			      OPT_LocationOperand op2) {
    if (op1 == null || op2 == null) return true;	// be conservative

    return (op1.type == op2.type) &&
      (op1.field == op2.field) &&
      arrayMayBeAliased(op1.arrayElementType, op2.arrayElementType) &&
      (op1.JTOCindex == op2.JTOCindex) &&
      (op1.spillOffset == op2.spillOffset) &&
      (op1.refNumber == op2.refNumber);
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
    return (op instanceof OPT_LocationOperand) &&
      mayBeAliased(this, (OPT_LocationOperand) op);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    if (methOp != null) return methOp.toString();
    switch (type) {
    case METHOD_ACCESS: 
      return "<mem loc: methOp is null!>";
    case FIELD_ACCESS:   
      return "<mem loc: "+field.getDeclaringClass().getName() + "." +field.getName()+">";
    case ARRAY_ACCESS:   
      return "<mem loc: array "+arrayElementType+"[]>";
    case JTOC_ACCESS:
      return "<mem loc: JTOC @"+JTOCindex+">";
    case SPILL_ACCESS:
      return "<mem loc: spill FP "+spillOffset+">";
    case ALENGTH_ACCESS: 
      return "<mem loc: array length>";
    case REDIRECTION_ACCESS: 
      return "<mem loc: redirect>";
    }
    return "<mem loc: no aliases>";
  }
}
