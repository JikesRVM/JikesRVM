/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
package com.ibm.jikesrvm.classloader;

import java.lang.reflect.AnnotatedElement;
import java.lang.annotation.Annotation;
import java.io.DataInputStream;
import java.io.IOException;

import com.ibm.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A common abstract super class for all elements that can be
 * annotated within the JVM. Namely classes, methods and fields.
 *
 * @author Ian Rogers
 */
public abstract class VM_AnnotatedElement implements AnnotatedElement {
  /**
   * Annotations from the class file that are described as runtime
   * visible. These annotations are available to the reflection API.
   */
  protected final VM_Annotation[] annotations;

  /**
   * Constructor used by all annotated elements
   *
   * @param annotations array of runtime visible annotations
   */
  protected VM_AnnotatedElement(VM_Annotation[] annotations)
  {
    this.annotations = annotations;
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
  protected static VM_Annotation[] readAnnotations(int[] constantPool,
                                                   DataInputStream input,
                                                   int numAnnotationBytes,
                                                   ClassLoader classLoader
                                                   ) throws IOException
  {
    try {
      int numAnnotations;
      if(numAnnotationBytes == 2) {
        numAnnotations = input.readUnsignedShort();
      } else {
        if (VM.VerifyAssertions) VM._assert(numAnnotationBytes == 1);
        numAnnotations = input.readByte() & 0xFF;
      }
      final VM_Annotation[] annotations = new VM_Annotation[numAnnotations];
      for(int j=0; j < numAnnotations; j++) {
        annotations[j] = VM_Annotation.readAnnotation(constantPool, input, classLoader);
      }
      return annotations;
    }
    catch(ClassNotFoundException e) {
      throw new Error(e);
    }
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
    int    numAnnotations = (annotations != null) ? annotations.length : 0;
    final Annotation[] result = new Annotation[numAnnotations];
    for (int i = 0; i < result.length; i++) {
      result[i] = annotations[i].getValue();
    }
    return result;
  }
  /**
   * Get the annotation implementing the specified class or null
   */
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    VM_TypeReference annotationTypeRef = VM_TypeReference.findOrCreate(annotationClass);
    if (annotations != null) {
      for(int i=0; i < annotations.length; i++) {
        if(annotations[i].annotationType() == annotationTypeRef) {
          @SuppressWarnings("unchecked") // If T extends Annotation, surely an Annotation is a T ???
          T result = (T) annotations[i].getValue();
          return result;
        }
      }
    }
    return null;
  }
  /**
   * Is there an annotation of this type implemented on this annotated
   * element?
   */
  public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
    VM_TypeReference annotationTypeRef = VM_TypeReference.findOrCreate(annotationClass);
    if (annotations != null) {
      for(int i=0; i < annotations.length; i++) {
         if(annotations[i].annotationType() == annotationTypeRef) {
             return true;
         }
      }
    }
    return false;
  }

  /**
   * Is there an annotation of this type implemented on this annotated
   * element? Safe to be called from uninterruptible code.
   */
  @Uninterruptible
  boolean isAnnotationPresent(final VM_TypeReference annotationTypeRef) {
    if (annotations != null) {
      for (VM_Annotation annotation : annotations) {
        if( annotation.getType().equals(annotationTypeRef.getName()) &&
            annotation.getClassLoader() == annotationTypeRef.getClassLoader() ) {
          return true;
        }
      }
    }
    return false;
  }
}
