/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

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
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   * @param runtimeVisibleParameterAnnotations array of runtime
   * visible parameter annotations
   * @param runtimeInvisiblePatarameterAnnotations optional array of
   * runtime invisible parameter annotations
   * @param annotationDefault value for this annotation that appears
   * in annotation classes
   */
  VM_AbstractMethod(VM_TypeReference declaringClass, VM_MemberReference memRef,
                    int modifiers, VM_TypeReference[] exceptionTypes,
                    VM_Annotation runtimeVisibleAnnotations[],
                    VM_Annotation runtimeInvisibleAnnotations[],
                    VM_Annotation runtimeVisibleParameterAnnotations[],
                    VM_Annotation runtimeInvisibleParameterAnnotations[],
                    Object annotationDefault)
  {
    super(declaringClass, memRef, modifiers, exceptionTypes,
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
