/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
// $Id$
package com.ibm.jikesrvm.classloader;

import java.lang.reflect.AnnotatedElement;
import java.lang.annotation.Annotation;
import java.io.DataInputStream;
import java.io.IOException;

import com.ibm.jikesrvm.VM;

/**
 * A common abstract super class for all elements that can be
 * annotated within the JVM. Namely classes, methods and fields.
 *
 * Annotations can be runtime visible or invisible, invisible
 * annotations are typically used by the compiler and by default
 * aren't retained by the JVM.
 *
 * @author Ian Rogers
 */
public abstract class VM_AnnotatedElement implements AnnotatedElement {
  /**
   * Annotations from the class file that are described as runtime
   * visible. These annotations are available to the reflection API.
   */
  protected final VM_Annotation runtimeVisibleAnnotations[];
  /**
   * Should we retain runtime invisible annotations? Enabling this
   * option allows some support of annotations without a full
   * Classpath generics branch.
   */
  protected static final boolean retainRuntimeInvisibleAnnotations = false;
  /**
   * Annotations from the class file that are described as runtime
   * visible. These annotations aren't available to the reflection
   * API.
   */
  protected final VM_Annotation runtimeInvisibleAnnotations[];

  /**
   * Constructor used by all annotated elements
   *
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   */
  protected VM_AnnotatedElement (VM_Annotation runtimeVisibleAnnotations[],
                                 VM_Annotation runtimeInvisibleAnnotations[])
  {
    if (VM.VerifyAssertions && !retainRuntimeInvisibleAnnotations) {
      VM._assert(runtimeInvisibleAnnotations == null);
    }
    this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
    this.runtimeInvisibleAnnotations = runtimeInvisibleAnnotations;
  }

  /**
   * Read annotations from a class file and package in an array
   * @param constantPool the constantPool of the VM_Class object
   * that's being constructed
   * @param input the DataInputStream to read the method's attributes
   * from
   * @param numAnnotationBytes how many bytes are there in the number
   * of annotations field? Normally 2, but parameter annotations just
   * have 1.
   * @return an array of read annotations
   */
  protected static VM_Annotation[] readAnnotations(int constantPool[],
                                                   DataInputStream input,
                                                   int numAnnotationBytes,
                                                   ClassLoader classLoader
                                                   ) throws IOException
  {
    VM_Annotation annotations[] = null;
    try {
      int numAnnotations;
      if(numAnnotationBytes == 2) {
        numAnnotations = input.readUnsignedShort();
      } else {
        if (VM.VerifyAssertions) VM._assert(numAnnotationBytes == 1);
        numAnnotations = input.readByte() & 0xFF;
      }
      annotations = new VM_Annotation[numAnnotations];
      for(int j=0; j < numAnnotations; j++) {
        annotations[j] = VM_Annotation.readAnnotation(constantPool, input, classLoader);
      }
    }
    catch(ClassNotFoundException e) {
      throw new Error(e);
    }
    return annotations;
  }
  /**
   * Get the value of the super for this annotated element. Elements
   * are expected to override as appropriate
   * @return the super value or null
   */
  protected VM_AnnotatedElement getSuperAnnotatedElement() {
    return null;
  }
  /**
   * Get the annotations for this and all super annotated elements
   */
  public Annotation[] getAnnotations() {
    Annotation[] result = getDeclaredAnnotations();
    VM_AnnotatedElement superAE = getSuperAnnotatedElement();
    if (superAE != null) {
      Annotation[] superResult = superAE.getAnnotations();
      Annotation[] newResult = new Annotation[result.length + superResult.length];
      System.arraycopy(result, 0, superResult, 0, result.length);
      System.arraycopy(superResult, 0, superResult, result.length, superResult.length);
      result = newResult;
    }
    return result;
  }  
  /**
   * Get the annotations for this annotated element
   */
  public Annotation[] getDeclaredAnnotations() {
    int    numAnnotations = (runtimeVisibleAnnotations != null) ? runtimeVisibleAnnotations.length : 0;
    if (retainRuntimeInvisibleAnnotations) {
      numAnnotations += (runtimeInvisibleAnnotations != null) ? runtimeInvisibleAnnotations.length : 0;
    }
    Annotation result[] = new Annotation[numAnnotations];
    if (runtimeVisibleAnnotations != null) {       
      for(int i=0; i < runtimeVisibleAnnotations.length; i++) {
        result[i] = runtimeVisibleAnnotations[i].getValue();
      }
    }
    if (retainRuntimeInvisibleAnnotations && (runtimeInvisibleAnnotations != null)) {
      int start = (runtimeVisibleAnnotations != null) ? runtimeVisibleAnnotations.length : 0;
      for(int i=0; i < runtimeInvisibleAnnotations.length; i++) {
        result[start + i] = runtimeInvisibleAnnotations[i].getValue();
      }
    }
    return result;
  }
  /**
   * Get the annotation implementing the specified class or null
   */
  public Annotation getAnnotation(Class annotationClass) {
    Annotation annotations[] = getDeclaredAnnotations();
    for(int i=0; i<annotations.length; i++) {
      if(annotations[i].annotationType() == annotationClass) {
        return annotations[i];
      }
    }
    return null;
  }
  /**
   * Is there an annotation of this type implemented on this annotated
   * element?
   */
  public boolean isAnnotationPresent(Class annotationClass) {
    return getAnnotation(annotationClass) == null;
  }
}
