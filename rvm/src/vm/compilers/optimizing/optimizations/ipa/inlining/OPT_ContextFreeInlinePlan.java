/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import  java.util.*;
import  java.io.*;

/**
 * An object of this class represents a set of triples <a,x,b>.
 * Each <a,x,b>, denotes the rule "inline b into a at bytecode b".
 *
 * @author Stephen Fink
 * @modified Peter Sweeney
 * @modified Matthew Arnold
 */
public class OPT_ContextFreeInlinePlan implements OPT_InlinePlan {

  /** 
   * Add the rule "inline b into a at bytecode x" to the object
   *
   * @param a the caller
   * @param x bytecodeIndex
   * @param b the callee
   */
  public void addRule (VM_Method a, int x, VM_Method b) {
    OPT_CallSite s = new OPT_CallSite(a, x);
    HashSet targets = findOrCreateTargets(s);
    targets.add(b);
  }

  /**
   * Return the set of methods to inline at a call site
   *
   * @param a the caller
   * @param x bytecodeIndex
   */
  public VM_Method[] getTargets (VM_Method a, int x) {
    HashSet targets = (HashSet)map.get(new OPT_CallSite(a, x));
    if (targets == null)
      return  null;
    int length = targets.size();
    VM_Method[] result = new VM_Method[length];
    Iterator j = targets.iterator();
    for (int i = 0; i < length; i++) {
      result[i] = (VM_Method)j.next();
    }
    return  result;
  }

  /**
   *  Allows iteration over the elements
   */
  public Iterator getIterator () {
    return  map.keySet().iterator();
  }

  /**
   * NOTE: must be kept in synch with readObject!
   */
  public String toString () {
    String tmp = "";
    for (Iterator i = getIterator(); i.hasNext();) {
      OPT_CallSite key = (OPT_CallSite)i.next();
      HashSet targets = (HashSet)map.get(key);
      for (Iterator j = targets.iterator(); j.hasNext();) {
        VM_Method callee = (VM_Method)j.next();
        tmp += "\t"+key.method.getDeclaringClass().getClassLoader() + 
          " "+ key.method.getDeclaringClass().getDescriptor()+" "+key.method.getName()+
          " "+key.method.getDescriptor()+ "," + key.bcIndex + "," + 
          callee.getDeclaringClass().getClassLoader()+ " "+ 
          callee.getDeclaringClass().getDescriptor()+" "+callee.getName()+
          " "+callee.getDescriptor() + "\n";
      }
    }
    return  tmp;
  }

  /** 
   * Read a serialized representation of the object from a stream.
   * Expected format is that produced by toString.
   */
  public void readObject(LineNumberReader in) throws IOException {
    for (String s = in.readLine(); s!= null; s = in.readLine()) {
      StringTokenizer parser = new StringTokenizer(s, " \t\n\r\f,");
      String nextToken1 = parser.nextToken();
      String nextToken2 = parser.nextToken();
      String nextToken3 = parser.nextToken();
      String nextToken4 = parser.nextToken();
      VM_MethodReference callerRef = null;
      VM_MethodReference calleeRef = null;
      if (!nextToken1.equals("null")) {
        VM_Atom callerClass = VM_Atom.findOrCreateUnicodeAtom(nextToken2);
        VM_Atom callerName = VM_Atom.findOrCreateUnicodeAtom(nextToken3);
        VM_Atom callerDescriptor = VM_Atom.findOrCreateUnicodeAtom(nextToken4);
        VM_TypeReference tref;
        if (nextToken1.equals("SystemCL")) {
          tref = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), callerClass);
        } else if (nextToken1.equals("AppCL")) {
          tref = VM_TypeReference.findOrCreate(VM_ClassLoader.getApplicationClassLoader(), callerClass);
        } else {
          VM.sysWriteln("Unknown classloader '"+nextToken1+"'. Skipping entry");
          continue;
        }
        callerRef = VM_MemberReference.findOrCreate(tref, callerName, callerDescriptor).asMethodReference();
      }
      nextToken1 = parser.nextToken();
      int bytecodeOffset = nextToken1.equals("null") ? 0 : Integer.parseInt(nextToken1);
      nextToken1 = parser.nextToken();
      nextToken2 = parser.nextToken();
      nextToken3 = parser.nextToken();
      nextToken4 = parser.nextToken();
      if (!nextToken1.equals("null")) {
        VM_Atom calleeClass = VM_Atom.findOrCreateUnicodeAtom(nextToken2);
        VM_Atom calleeName = VM_Atom.findOrCreateUnicodeAtom(nextToken3);
        VM_Atom calleeDescriptor = VM_Atom.findOrCreateUnicodeAtom(nextToken4);
        VM_TypeReference tref;
        if (nextToken1.equals("SystemCL")) {
          tref = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), calleeClass);
        } else if (nextToken1.equals("AppCL")) {
          tref = VM_TypeReference.findOrCreate(VM_ClassLoader.getApplicationClassLoader(), calleeClass);
        } else {
          VM.sysWriteln("Unknown classloader '"+nextToken1+"'. Skipping entry");
          continue;
        }
        calleeRef = VM_MemberReference.findOrCreate(tref, calleeName, calleeDescriptor).asMethodReference();
      }
      VM_Method caller = callerRef.resolve();
      VM_Method callee = calleeRef.resolve();
      if (caller != null && callee != null) {
        addRule(caller, bytecodeOffset, callee);
      }
    }
  }

  /**
   * Find the set of targets for a call site
   * If none found, create one
   */
  private HashSet findOrCreateTargets (OPT_CallSite c) {
    HashSet targets = (HashSet)map.get(c);
    if (targets == null) {
      targets = new HashSet();
      map.put(c, targets);
    }
    return  targets;
  }

  /** Backing data store */
  private HashMap map = new HashMap();        // f:call site -> Set<VM_Method>
}
