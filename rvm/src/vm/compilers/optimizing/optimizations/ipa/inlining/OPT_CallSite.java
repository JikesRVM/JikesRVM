/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * class that represents a call site in bytecode
 * @author Stephen Fink
 */
class OPT_CallSite {
  /**
   * The caller method
   */
  VM_Method method;
  /**
   * The bytecode index of the call site
   */
  int bcIndex;

  OPT_CallSite (VM_Method method, int x) {
    this.method = method;
    this.bcIndex = x;
  }

  public boolean equals (Object obj) {
    if (!(obj instanceof OPT_CallSite))
      return  false;
    if (obj == null)
      return  false;
    OPT_CallSite c = (OPT_CallSite)obj;
    return  (method == c.method) && (bcIndex == c.bcIndex);
  }

  public int hashCode () {
    int result = 7;
    if (method != null)
      result += method.hashCode();
    result += bcIndex*31;
    return  result;
  }

  public String toString () {
    return  method + " " + bcIndex;
  }
}



