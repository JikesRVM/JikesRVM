/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
package org.jikesrvm.classloader;

import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.runtime.VM_Entrypoints;

/**
 * An abstract method of a java class.
 *
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
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  VM_AbstractMethod(VM_TypeReference declaringClass, VM_MemberReference memRef,
                    short modifiers, VM_TypeReference[] exceptionTypes, VM_Atom signature,
                    VM_Annotation[] annotations,
                    VM_Annotation[] parameterAnnotations,
                    Object annotationDefault)
  {
    super(declaringClass, memRef, modifiers, exceptionTypes, signature,
          annotations, parameterAnnotations, annotationDefault);
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() {
    VM_Entrypoints.unexpectedAbstractMethodCallMethod.compile();
    return VM_Entrypoints.unexpectedAbstractMethodCallMethod.getCurrentCompiledMethod();
  }
}
