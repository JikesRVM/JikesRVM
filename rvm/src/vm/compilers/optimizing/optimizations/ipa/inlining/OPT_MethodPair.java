/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Class that represents a pair of methods 
 *
 * @author Stephen Fink
 * @modified Peter Sweeney 
 * moved from OPT_ContextFreeInlinePlan.java.
 */
class OPT_MethodPair {
  VM_Method a;
  VM_Method b;

  OPT_MethodPair (VM_Method a, VM_Method b) {
    this.a = a;
    this.b = b;
  }

  public boolean equals (Object obj) {
    if (!(obj instanceof OPT_MethodPair))
      return  false;
    if (obj == null)
      return  false;
    OPT_MethodPair o = (OPT_MethodPair)obj;
    return  (a == o.a) && (b == o.b);
  }

  public int hashCode () {
    int result = 7;
    if (a != null)
      result += a.hashCode();
    if (b != null)
      result += b.hashCode();
    return  result;
  }
}



