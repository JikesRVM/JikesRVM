/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*;

/**
 * This class implements the Comparator comparing two inline plans
 *
 * @author Peter Sweeney
 */

class OPT_InlinePlanComparator
    implements JDK2_Comparator {
  public static boolean debug = false;

  /** 
   * @param o1 first OPT_MethodPair to compare
   * @param o2 second OPT_MethodPair to compare
   * @return -1 iff nameOfPair(o1) < nameOfPair(o2);
   *         +1 iff nameOfPair(o1) > nameOfPair(o2)
   */
  public int compare (Object o1, Object o2) {
    // This should never happen!
    if (!(o1 instanceof OPT_MethodPair) || !(o2 instanceof OPT_MethodPair)) {
      System.out.println("***OPT_InlinePlanComparator.compare(Object,Object)"
          + " one object is not a OPT_MethodPair\n");
      return  0;
    }
    OPT_MethodPair p1 = (OPT_MethodPair)o1;
    OPT_MethodPair p2 = (OPT_MethodPair)o2;
    if (p1.equals(p2))
      return  0;
    VM_Method p1a = p1.a;
    VM_Method p1b = p1.b;
    VM_Method p2a = p2.a;
    VM_Method p2b = p2.b;
    int value = 0;
    // get the fully qualified names of the VM_Methods
    String p1a_fqname = null;
    String p1b_fqname = null;
    String p2a_fqname = null;
    String p2b_fqname = null;
    if (p1a != null) {
      p1a_fqname = p1a.toString();
    }
    if (p1b != null) {
      p1b_fqname = p1b.toString();
    }
    if (p2a != null) {
      p2a_fqname = p2a.toString();
    }
    if (p2b != null) {
      p2b_fqname = p2b.toString();
    }
    //       if(debug)System.out.println("OPT_InlinePlan.compare(<"+
    //				   p1a_fqname+","+p1b_fqname+">,<"+
    //				   p2a_fqname+","+p2b_fqname+">");
    if (p1a_fqname == null || p2a_fqname == null) {
      if (!(p1a_fqname == null && p2a_fqname == null)) { // only one is null
        if (p1a_fqname == null) {
          value = -1;
        } 
        else {
          value = +1;
        }
      } 
      else {
      // both null and value == 0;
      }
    } 
    else {      // neither are null
      try {
        value = p1a_fqname.compareTo(p2a_fqname);
      } catch (Exception e) {                   // null always comes first
        // This should never happen!
        System.out.println("***OPT_InlinePlanComparator.compare(Object,"
            + "Object) compareTo failed when p1a and p2a " + "are not null!\n");
        return  0;
      }
    }
    if (value == 0) {
      try {
        value = p1b_fqname.compareTo(p2b_fqname);
      } catch (Exception e) {
        // This should never happen! 
        System.out.println("***OPT_InlinePlanComparator.compare(Object,"
            + "Object) compareTo failed with p1b and p2b\n");
        return  0;
      }
    }
    //       if(debug)System.out.println("\n"+value);
    return  value;
  }
}



