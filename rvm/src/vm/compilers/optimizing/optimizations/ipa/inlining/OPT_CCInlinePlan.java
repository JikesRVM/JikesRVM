/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.io.*;
import  java.util.*;


/**
 * This class represents an "Inline Plan with calling context".
 * Essentially, this implements a forest of trees, where
 * each tree (called an "T_CCInlineComponent") represents the inlining 
 * decisions for a method foo.
 * Each node of the tree represents a method to inline and a
 * bytecode index.  For example the tree:
 * <pre>
 *   		<null, A>
 * 		   /    \
 *	        <5, B>	<15,C>
 * </pre>
 * means "when compiling method A, inline the call site at bytecode 5
 * with a call to B and the call site at bytecode 15 with a call to C.
 *
 * @author Stephen Fink
 * @modifications Peter Sweeney
 */

class OPT_CCInlinePlan {
  /**
   * verbose debugging output?
   */
  public static boolean debug = false;

  /**
   * Return the number of components in this plan
   * @return number of components in this plan
   */
  public int size () {
    return  map.size();
  }

  /**
   * Create an empty plan
   */
  OPT_CCInlinePlan () {
    if (debug)
      System.out.println("\tOPT_CCInlinePlan.<init>()");
  }

  /**
   * Dump each component in the plan to System.out
   */
  public void dump () {
    System.out.println("\nDump inline plans\n");
    java.util.Iterator iterator = map.values().iterator();
    while (iterator.hasNext()) {
      OPT_CCInlineComponent component = (OPT_CCInlineComponent)iterator.next();
      OPT_CCInlineNode node = component.getRoot();
      System.out.println("Dump inline plan for " + node);
      System.out.println("  " + component);
    }
  }

  /**
   * Return the VM_Methods to inline at a given call site.
   *
   * @param sequence the inlining context of the caller
   * @param bcIndex the bytecodeIndex of the call site
   * @return the method to inline at the callsite.
   *	      null if call site not to be inlined.
   */
  VM_Method[] getInlineTargets (OPT_InlineSequence sequence, int bcIndex) {
    VM_Method root = sequence.getRootMethod();
    OPT_CCInlineComponent c = (OPT_CCInlineComponent)map.get(root);
    if (c == null)
      return  null;
    VM_Method[] result = c.match(sequence, bcIndex);
    return  result;
  }

  /** 
   * Return a String representation of the object
   * @return a String representation of the object
   */
  public String toString () {
    StringWriter writer = new StringWriter();
    try {
      writeObject(writer);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return  writer.toString();
  }

  /** 
   * Write a serialized representation of the object to a stream.
   * Each line of the stream is a string representation of an
   * InlineComponent.
   */
  public void writeObject (Writer out) throws IOException {
    for (java.util.Iterator e = map.keySet().iterator(); e.hasNext();) {
      OPT_CCInlineComponent c = (OPT_CCInlineComponent)map.get(e.next());
      out.write(c + "\n");
    }
  }

  /** 
   * Merge a new inline component into this plan. 
   *
   * @param c the inline component to add to the plan
   */
  public void addComponent (OPT_CCInlineComponent c) {
    VM_Method root = c.getRootMethod();
    OPT_CCInlineComponent oldC = (OPT_CCInlineComponent)map.get(root);
    if (oldC == null) {
      // no previous inlining rule for this method
      map.put(c.getRootMethod(), c);
    } 
    else {
      // a rule already exists.  Merge the two inline components. 
      oldC.merge(c);
    }
  }

  /** 
   * Remove an inline component from this plan. 
   *
   * @param c the inline component to be removed from this plan
   */
  public void removeComponent (OPT_CCInlineComponent c) {
    VM_Method root = c.getRootMethod();
    if (map.remove(root) == null) {
      System.out.println("***OPT_CCInlinePlan.removeComponent(" + c + 
          ") returned null!");
    }
  }

  /**
   * Given a VM_Method, find the component in which it is the root.
   */
  public OPT_CCInlineComponent getComponent (VM_Method method) {
    OPT_CCInlineComponent result = (OPT_CCInlineComponent)map.get(method);
    return  result;
  }

  /** 
   * Enumerate the inline components of this plan.
   */
  public java.util.Iterator getComponents () {
    return  map.values().iterator();
  }

  /** 
   * Count the number of bytecodes that will be inlined by this plan.
   */
  public int countInlinedBytecodes () {
    int count = 0;
    for (java.util.Iterator i = getComponents(); i.hasNext();) {
      OPT_CCInlineComponent c = (OPT_CCInlineComponent)i.next();
      count += c.countInlinedBytecodes();
    }
    return  count;
  }

  /**
   * A mapping from VM_Method to Inline Component
   */
  java.util.Map map = new java.util.HashMap();  
}



