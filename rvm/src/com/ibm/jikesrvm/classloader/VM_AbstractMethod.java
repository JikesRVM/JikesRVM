/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.jikesrvm.classloader;

import com.ibm.jikesrvm.*;

/**
 * An abstract method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @modified Ian Rogers
 */
public final class VM_AbstractMethod extends VM_Method {

  /**
   * Construct abstract method information
   *
   * @param declaringClass the VM_TypeReference object of the class that declared this method
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   * @param runtimeVisibleParameterAnnotations array of runtime
   * visible parameter annotations
   * @param runtimeInvisibleParameterAnnotations optional array of
   * runtime invisible parameter annotations
   * @param annotationDefault value for this annotation that appears
   * in annotation classes
   */
  VM_AbstractMethod(VM_TypeReference declaringClass, VM_MemberReference memRef,
                    int modifiers, VM_TypeReference[] exceptionTypes, VM_Atom signature,
                    VM_Annotation runtimeVisibleAnnotations[],
                    VM_Annotation runtimeInvisibleAnnotations[],
                    VM_Annotation runtimeVisibleParameterAnnotations[],
                    VM_Annotation runtimeInvisibleParameterAnnotations[],
                    Object annotationDefault)
  {
    super(declaringClass, memRef, modifiers, exceptionTypes, signature,
          runtimeVisibleAnnotations, runtimeInvisibleAnnotations,
          runtimeVisibleParameterAnnotations, runtimeInvisibleParameterAnnotations,
          annotationDefault);
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() {
    VM_Entrypoints.unexpectedAbstractMethodCallMethod.compile();
    return VM_Entrypoints.unexpectedAbstractMethodCallMethod.getCurrentCompiledMethod();
  }
}
