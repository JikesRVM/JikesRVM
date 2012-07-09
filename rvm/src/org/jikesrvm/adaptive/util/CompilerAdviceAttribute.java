/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * Defines an attribute for compiler advice, and maintains a map
 * allowing attributes to be retrieved by method and bytecode offset.
 * <p>
 * Each attribute encodes an compiler site and the advice for that
 * site:
 * <ul>
 * <li><code>&lt;class></code> <i>string</i> The name of the class</li>
 * <li><code>&lt;method></code> <i>string</i> The name of the method</li>
 * <li><code>&lt;signature></code> <i>string</i> The method signature</li>
 * <li><code>&lt;advice></code> <i>in </i> The integer value for the
 * compiler, as given in CompilerInfo</li>
 * <li><code>&lt;optLevel></code> <i>in </i> The optimization level when
 the Opt compiler is used
 * </ul>
 *
 *
 * @see CompilerAdvice
 * @see CompilerAdviceInfoReader
 */
public class CompilerAdviceAttribute {

  private static HashMap<CompilerAdviceAttribute, CompilerAdviceAttribute> attribMap = null;
  private static CompilerAdviceAttribute defaultAttr = null;
  private static CompilerAdviceAttribute tempAttr = null;
  private static boolean hasAdvice = false;

  private Atom className;  // The name of the class for the compiler site
  private Atom methodName; // The name of the method for the compiler site
  private Atom methodSig;  // The signature of the method
  private int compiler;   // The compiler to use for the method
  private int optLevel;   // The optimization level

  /**
   * Initialization of key compiler advice data structure.
   */
  public static void postBoot() {
    attribMap = new HashMap<CompilerAdviceAttribute, CompilerAdviceAttribute>();

    // With defaultAttr set up this way, methods will be BASELINE compiled
    // *unless* they appear in the advice file. If defaultAttr is set to
    // null, then methods will be compiled in the default way for the
    // current build configuration *unless* they appear in the advice file.
    defaultAttr = new CompilerAdviceAttribute(null, null, null, CompiledMethod.BASELINE);
    tempAttr = new CompilerAdviceAttribute(null, null, null, CompiledMethod.BASELINE);
  }

  /**
   * Getter method for class name
   *
   * @return the class name for this attribute
   */
  public Atom getClassName() { return className; }

  /**
   * Getter method for method name
   *
   * @return the method name for this attribute
   */
  public Atom getMethodName() { return methodName; }

  /**
   * Getter method for method signature
   *
   * @return the method signature for this attribute
   */
  public Atom getMethodSig() { return methodSig; }

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
   * @see CompilerAdviceInfoReader
   */
  public CompilerAdviceAttribute(Atom className, Atom methodName, Atom methodSig, int compiler) {
    this.className = className;
    this.methodName = methodName;
    this.methodSig = methodSig;
    this.compiler = compiler;
    this.optLevel = -1;
  }

  /**
   * Constructor
   *
   * @param className  The name of the class for the compiler site
   * @param methodName The name of the method for the compiler site
   * @param methodSig  The signature of the method for the compiler site
   * @param compiler   The ID of the compiler to use for this method
   * @param optLevel   The optimization level if using Opt compiler
   *
   * @see CompilerAdviceInfoReader
   */
  public CompilerAdviceAttribute(Atom className, Atom methodName, Atom methodSig, int compiler,
                                    int optLevel) {
    this.className = className;
    this.methodName = methodName;
    this.methodSig = methodSig;
    this.compiler = compiler;
    this.optLevel = optLevel;
  }

  /**
   * Stringify this instance
   *
   * @return The state of this instance expressed as a string
   */
  @Override
  public String toString() {
    return ("Compiler advice: " +
            className +
            " " +
            methodName +
            " " +
            methodSig +
            " " +
            compiler +
            "(" +
            optLevel +
            ")");
  }

  /**
   * Use a list of compiler advice attributes to create an advice map
   * keyed on <code>RVMMethod</code> instances.  This map is used by
   * <code>getCompilerAdviceInfo()</code>.
   *
   * @param compilerAdviceList A list of compiler advice attributes
   * @see #getCompilerAdviceInfo
   */
  public static void registerCompilerAdvice(List<CompilerAdviceAttribute> compilerAdviceList) {
    // do nothing for empty list
    if (compilerAdviceList == null) return;

    hasAdvice = true;

    // iterate over each element of the list
    ListIterator<CompilerAdviceAttribute> it = compilerAdviceList.listIterator();
    while (it.hasNext()) {
      // pick up an attribute
      CompilerAdviceAttribute attr = it.next();
      attribMap.put(attr, attr);
      // XXX if already there, should we warn the user?
    }
  }

  /**
   * Given a method and bytecode offset, return an compiler advice
   * attribute or null if none is found for that method and offset.
   *
   * @param method The method containing the site in question
   * @return Attribute advice for that site or null if none is found.
   */
  public static CompilerAdviceAttribute getCompilerAdviceInfo(RVMMethod method) {
    tempAttr.className = method.getDeclaringClass().getDescriptor();
    tempAttr.methodName = method.getName();
    tempAttr.methodSig = method.getDescriptor();
    CompilerAdviceAttribute value = attribMap.get(tempAttr);

    if (value == null) {
      return defaultAttr;
    } else {
      return value;
    }
  }

  public static Collection<CompilerAdviceAttribute> values() {
    return attribMap.values();
  }

  public static boolean hasAdvice() {
    return hasAdvice;
  }

  @Override
  public boolean equals(Object obj) {
    if (super.equals(obj)) {
      return true;
    }

    if (obj instanceof CompilerAdviceAttribute) {
      CompilerAdviceAttribute attr = (CompilerAdviceAttribute) obj;
      if (attr.className == className && attr.methodName == methodName && attr.methodSig == methodSig) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return className.hashCode() ^ methodName.hashCode() ^ methodSig.hashCode();
  }
}
