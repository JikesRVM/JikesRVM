/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An object of this class represents a set of triples <a,x,b>.
 * Each <a,x,b>, denotes the rule "inline b into a at bytecode b".
 *
 * @author Stephen Fink
 * @modifications Peter Sweeney
 * @modifications Matthew Arnold
 */


import  java.io.*;
import  java.util.*;


/**
 * put your documentation comment here
 */
class OPT_ContextFreeInlinePlan {

  /** Interface */
  /** 
   * Add the rule "inline b into a at bytecode x" to the object
   *
   * @param a the caller
   * @param x bytecodeIndex
   * @param b the callee
   */
  public void addRule (VM_Method a, int x, VM_Method b) {
    OPT_CallSite s = new OPT_CallSite(a, x);
    java.util.HashSet targets = findOrCreateTargets(s);
    targets.add(b);
  }

  /**
   * Return the set of methods to inline at a call site
   *
   * @param a the caller
   * @param x bytecodeIndex
   */
  public VM_Method[] getTargets (VM_Method a, int x) {
    java.util.HashSet targets = (java.util.HashSet)map.get(new OPT_CallSite(a, x));
    if (targets == null)
      return  null;
    int length = targets.size();
    VM_Method[] result = new VM_Method[length];
    java.util.Iterator j = targets.iterator();
    for (int i = 0; i < length; i++) {
      result[i] = (VM_Method)j.next();
    }
    return  result;
  }

  /**
   *  Allows iteration over the elements
   */
  public java.util.Iterator getIterator () {
    return  map.keySet().iterator();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String toString () {
    String tmp = "";
    for (java.util.Iterator i = getIterator(); i.hasNext();) {
      OPT_CallSite key = (OPT_CallSite)i.next();
      java.util.HashSet targets = (java.util.HashSet)map.get(key);
      for (java.util.Iterator j = targets.iterator(); j.hasNext();) {
        VM_Method callee = (VM_Method)j.next();
        tmp += "  <" + key.method + "," + key.bcIndex + "," + "," + callee
            + ">\n";
      }
    }
    return  tmp;
  }

  /** Implementation */
  java.util.HashMap map = new java.util.HashMap();        // f:call site -> Set<VM_Method>

  /**
   * Find the set of targets for a call site
   * If none found, create one
   */
  private java.util.HashSet findOrCreateTargets (OPT_CallSite c) {
    java.util.HashSet targets = (java.util.HashSet)map.get(c);
    if (targets == null) {
      targets = new java.util.HashSet();
      map.put(c, targets);
    }
    return  targets;
  }

  /** 
   * Read a serialized representation of the object from a stream
   * Reincarnated from old RCS version: bytecode offset added
   *  format is <caller, bytecode offset, callee>
   */
  public void readObject (LineNumberReader in) throws IOException {
    int bytecodeOffset;
    String s = in.readLine();
    while (s != null) {
      bytecodeOffset = 0;
      StringTokenizer parser = new StringTokenizer(s);
      String nextToken1 = parser.nextToken();
      String nextToken2 = parser.nextToken();
      String nextToken3 = parser.nextToken();
      VM_Method caller = null;
      VM_Method callee = null;
      if (!nextToken1.equals("null")) {
        VM_Atom callerClass = VM_Atom.findOrCreateUnicodeAtom(nextToken1);
        VM_Atom callerName = VM_Atom.findOrCreateUnicodeAtom(nextToken2);
        VM_Atom callerDescriptor = VM_Atom.findOrCreateUnicodeAtom(nextToken3);
        caller = VM_ClassLoader.findOrCreateMethod(callerClass, callerName, 
            callerDescriptor);
        VM.sysWrite("Offline oracle: ");
        VM.sysWrite(callerClass);
        VM.sysWrite(" ");
        VM.sysWrite(callerName);
        VM.sysWrite(" ");
        VM.sysWrite(callerDescriptor);
        VM.sysWrite("\n");
      }
      nextToken1 = parser.nextToken();
      if (!nextToken1.equals("null")) {
        bytecodeOffset = Integer.parseInt(nextToken1);
      }
      nextToken1 = parser.nextToken();
      nextToken2 = parser.nextToken();
      nextToken3 = parser.nextToken();
      if (!nextToken1.equals("null")) {
        VM_Atom calleeClass = VM_Atom.findOrCreateUnicodeAtom(nextToken1);
        VM_Atom calleeName = VM_Atom.findOrCreateUnicodeAtom(nextToken2);
        VM_Atom calleeDescriptor = VM_Atom.findOrCreateUnicodeAtom(nextToken3);
        callee = VM_ClassLoader.findOrCreateMethod(calleeClass, calleeName, 
            calleeDescriptor);
      }
      addRule(caller, bytecodeOffset, callee);
      s = in.readLine();
    }
  }
}



