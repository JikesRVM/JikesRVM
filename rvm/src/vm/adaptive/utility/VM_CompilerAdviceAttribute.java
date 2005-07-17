/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */

package com.ibm.JikesRVM;


import com.ibm.JikesRVM.classloader.*;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * Defines an attribute for compiler advice, and maintains a map
 * allowing attributes to be retrived by method and bytecode offset.
 * <p>
 * Each attribute encodes an compiler site and the advice for that
 * site:
 * <ul>
 * <li><code>&lt;class></code> <i>string</i> The name of the class</li>
 * <li><code>&lt;method></code> <i>string</i> The name of the method</li>
 * <li><code>&lt;signature></code> <i>string</i> The method signature</li>
 * <li><code>&lt;advice></code> <i>in </i> The integer value for the
 * compiler, as given in VM_CompilerInfo</li>
 * <li><code>&lt;optLevel></code> <i>in </i> The optimization level when 
   the Opt compiler is used
 * </ul>
 *
 * @author: Xianglong Huang
 * @version $Revision$
 * @date $Date$
 *
 * @see VM_CompilerAdvice
 * @see VM_CompilerAdviceInfoReader
 */
public class VM_CompilerAdviceAttribute {

  private static HashMap attribMap = null;
  private static VM_CompilerAdviceAttribute defaultAttr = null;
  private static VM_CompilerAdviceAttribute tempAttr = null;
  private static boolean hasAdvice = false;

  private VM_Atom className;  // The name of the class for the compiler site 
  private VM_Atom methodName; // The name of the method for the compiler site
  private VM_Atom methodSig;  // The signature of the method
  private int     compiler;   // The compiler to use for the method
  private int     optLevel;   // The optimization level

  /**
   * Initialization of key compiler advice data structure.  
   */
  public static void postBoot () {
    attribMap = new HashMap();

    // With defaultAttr set up this way, methods will be BASELINE compiled
    // *unless* they appear in the advice file. If defaultAttr is set to
    // null, then methods will be compiled in the default way for the
    // current build configuration *unless* they appear in the advice file.
    defaultAttr =
      new VM_CompilerAdviceAttribute(null, null, null, 
                                     VM_CompiledMethod.BASELINE);
    tempAttr =
      new VM_CompilerAdviceAttribute(null, null, null, 
                                     VM_CompiledMethod.BASELINE);
  }

  /**
   * Getter method for class name
   *
   * @return the class name for this attribute
   */
  public VM_Atom getClassName() { return className; }

  /**
   * Getter method for method name
   *
   * @return the method name for this attribute
   */
  public VM_Atom getMethodName() { return methodName; }

  /**
   * Getter method for method signature
   *
   * @return the method signature for this attribute
   */
  public VM_Atom getMethodSig() { return methodSig; }

  /**
   * Getter method for compiler ID
   *
   * @return the compiler ID for this attribute
   */
  public int getCompiler() { return compiler; }

  /**
   * Getter method for optimization level
   *
   * @return the optimization level for this attribute
   */
  public int getOptLevel() { return optLevel; }

  /**
   * Constructor
   *
   * @param className The name of the class for the compiler site
   * @param methodName The name of the method for the compiler site
   * @param methodSig The signature of the method for the compiler site
   * @param compiler   The ID of the compiler to use for this method
   *
   * @see VM_CompilerAdviceInfoReader
   */
  public VM_CompilerAdviceAttribute(VM_Atom className, VM_Atom methodName,
				   VM_Atom methodSig, int  compiler) {
    this.className  = className;
    this.methodName = methodName;
    this.methodSig  = methodSig;
    this.compiler   = compiler;
    this.optLevel   = -1;
  }
  
  /**
   * Constructor
   *
   * @param className The name of the class for the compiler site
   * @param methodName The name of the method for the compiler site
   * @param methodSig The signature of the method for the compiler site
   * @param compiler   The ID of the compiler to use for this method
   * @param compiler   The optimization level if using Opt compiler
   *
   * @see VM_CompilerAdviceInfoReader
   */
  public VM_CompilerAdviceAttribute(VM_Atom className, VM_Atom methodName,
				    VM_Atom methodSig, int  compiler, 
                                    int optLevel) {
    this.className  = className;
    this.methodName = methodName;
    this.methodSig  = methodSig;
    this.compiler   = compiler;
    this.optLevel   = optLevel;
  }
  
  /**
   * Stringify this instance
   * 
   * @return The state of this instance expressed as a string
   */
  public String toString() {
    return ("Compiler advice: " + className + " " + methodName + " " 
	    + methodSig + " " + compiler + "("+optLevel+")");
  }

  /**
   * Use a list of compiler advice attributes to create an advice map
   * keyed on <code>VM_Method</code> instances.  This map is used by
   * <code>getCompilerAdviceInfo()</code>.
   *
   * @param compilerAdviceList A list of compiler advice attributes
   * @see VM_CompilerAdviceAttribute.getCompilerAdviceInfo
   */
  public static void registerCompilerAdvice(List compilerAdviceList) {
    // do nothing for empty list
    if (compilerAdviceList == null) return;

    hasAdvice = true;
    
    // iterate over each element of the list
    ListIterator it = compilerAdviceList.listIterator();
    while (it.hasNext()) {
      // pick up an attribute
      VM_CompilerAdviceAttribute attr = (VM_CompilerAdviceAttribute) it.next();
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
  public static VM_CompilerAdviceAttribute getCompilerAdviceInfo(
                                             VM_Method method) {
    tempAttr.className  = method.getDeclaringClass().getDescriptor();
    tempAttr.methodName = method.getName();
    tempAttr.methodSig  = method.getDescriptor();
    VM_CompilerAdviceAttribute value =
      (VM_CompilerAdviceAttribute)attribMap.get(tempAttr);

    if (value == null)
      return defaultAttr;
    else
      return value;
  }

  public static Iterator getEntries() {
    return attribMap.values().iterator();
  }

  final public static boolean hasAdvice() {
    return hasAdvice;
  }
  
  public boolean equals(Object obj) {
    if (super.equals(obj))
      return true;

    if (obj instanceof VM_CompilerAdviceAttribute) {
      VM_CompilerAdviceAttribute attr = (VM_CompilerAdviceAttribute)obj;
      if (attr.className  == className &&
          attr.methodName == methodName &&
          attr.methodSig  == methodSig)
        return true;
    }
    return false;
  }

  public int hashCode() {
    return className.hashCode() ^ methodName.hashCode() 
           ^ methodSig.hashCode();
  }
}
