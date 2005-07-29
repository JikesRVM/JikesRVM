/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */
//$Id$

package com.ibm.JikesRVM;


import com.ibm.JikesRVM.classloader.*;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import com.ibm.JikesRVM.adaptive.VM_Controller;
import com.ibm.JikesRVM.adaptive.VM_InvocationCounts;
import com.ibm.JikesRVM.adaptive.VM_AOSLogging;
import com.ibm.JikesRVM.opt.OPT_CompilationPlan;
/**
 * Defines an attribute for dynamic call graph, and maintains a map
 * allowing attributes to be retrived by method and bytecode offset.
 * <p>
 * Each attribute encodes a call site. With caller, callee. For caller
 * and callee we record
 * <ul>
 * <li><code>&lt;class></code> <i>string</i> The name of the class</li>
 * <li><code>&lt;method></code> <i>string</i> The name of the method</li>
 * <li><code>&lt;signature></code> <i>string</i> The method signature</li>
 * <li><code>&lt;size></code> <i>string</i> The method size</li>
 * </ul>
 *
 * Also, it also record byte code index and weight of the call size.
 * @author: Xianglong Huang
 * @version $Revision$
 * @date $Date$
 *
 * @see VM_CompilerAdvice
 * @see VM_DynamicCallFileInfoReader
 */
class VM_DynamicCallAttribute {

  private static HashMap attribMap = null;
  private static VM_DynamicCallAttribute tempAttr = null;
  private static boolean hasAdvice = false;

  private VM_Atom callerClassName;  // The name of the class for the compiler site 
  private VM_Atom callerName; // The name of the method for the compiler site
  private VM_Atom callerSig;  // The signature of the method
  private int callerSize;
  private int bci;            // byte Code Index
  private VM_Atom calleeClassName;  // The name of the class for the compiler site 
  private VM_Atom calleeName; // The name of the method for the compiler site
  private VM_Atom calleeSig;  // The signature of the method
  private int calleeSize;
  private float weight;

  /**
   * Initialization of key compiler advice data structure.  
   */
  public static void postBoot () {
    attribMap = new HashMap();

    // With defaultAttr set up this way, methods will be BASELINE compiled
    // *unless* they appear in the advice file. If defaultAttr is set to
    // null, then methods will be compiled in the default way for the
    // current build configuration *unless* they appear in the advice file.
    tempAttr =
      new VM_DynamicCallAttribute(null, null, null, 0, 0,
                                  null, null, null, 0, (float)0.0);
  }

  /**
   * Constructor
   *
   * @param className The name of the class for the compiler site
   * @param methodName The name of the method for the compiler site
   * @param methodSig The signature of the method for the compiler site
   * @param compiler   The ID of the compiler to use for this method
   *
   * @see VM_DynamicCallInfoReader
   */
  public VM_DynamicCallAttribute(VM_Atom callerClassName, VM_Atom callerName,
                                 VM_Atom callerSig, int callerSize,
                                 int bci,
                                 VM_Atom calleeClassName, VM_Atom calleeName,
                                 VM_Atom calleeSig, int calleeSize,
                                 float weight) {
    this.callerClassName  = callerClassName;
    this.callerName = callerName;
    this.callerSig  = callerSig;
    this.callerSize = callerSize;
    this.calleeClassName  = calleeClassName;
    this.calleeName = calleeName;
    this.calleeSig  = calleeSig;
    this.calleeSize = calleeSize;
    this.bci   = bci;
    this.weight = weight;
  }
  
  /**
   * Stringify this instance
   * 
   * @return The state of this instance expressed as a string
   */
  public String toString() {
    return ("Dynamic call site attribute: " );
  }

  /**
   * Use a list of compiler advice attributes to create an advice map
   * keyed on <code>VM_Method</code> instances.  This map is used by
   * <code>getDynamicCallInfo()</code>.
   *
   * @param compilerAdviceList A list of compiler advice attributes
   * @see VM_DynamicCallAttribute.getDynamicCallInfo
   */
  public static void registerDynamicCall(List dynamicCallList) {
    // do nothing for empty list
    if (dynamicCallList == null) return;

    if (!VM_Controller.options.ENABLE_PRECOMPILE)
      hasAdvice = true;
    
    // iterate over each element of the list
    ListIterator it = dynamicCallList.listIterator();
    while (it.hasNext()) {
      // pick up an attribute
      VM_DynamicCallAttribute attr = (VM_DynamicCallAttribute) it.next();
      attribMap.put(attr, attr);
      // XXX if already there, should we warn the user?
    }
  }

  /**
   * Given a method and bytecode offset, return an compiler advice
   * attribute or null if none is found for that method and offset.
   *
   * @param method The method containing the site in question
   * @param offset The bytecode offset of the site
   * @return Attribute advice for that site or null if none is found.
   */
  public static VM_DynamicCallAttribute getDynamicCallInfo(VM_Method method,
                                                           int bci) {
    tempAttr.callerClassName  = method.getDeclaringClass().getDescriptor();
    tempAttr.callerName = method.getName();
    tempAttr.callerSig  = method.getDescriptor();
    tempAttr.bci = bci;
    VM_DynamicCallAttribute value =
      (VM_DynamicCallAttribute)attribMap.get(tempAttr);

    return value;
  }

  final static boolean hasAdvice() {
    return hasAdvice;
  }
  
  public int hashCode() {
    return callerClassName.hashCode() ^ callerName.hashCode() 
           ^ callerSig.hashCode()^bci;
  }
}
