/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A common abstract super class for all elements that can be
 * annotated within the JVM. Namely classes, methods and fields.
 */
public abstract class VM_AnnotatedElement implements AnnotatedElement {
  /**
   * Annotations from the class file that are described as runtime
   * visible. These annotations are available to the reflection API.
   */
  protected final VM_Annotation[] declaredAnnotationDatas;
  /** Cached array of declared annotations. */
  private Annotation[] declaredAnnotations;

  /**
   * Constructor used by all annotated elements
   *
   * @param annotations array of runtime visible annotations
   */
  protected VM_AnnotatedElement(VM_Annotation[] annotations) {
    this.declaredAnnotationDatas = annotations;
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
  protected static VM_Annotation[] readAnnotations(int[] constantPool, DataInputStream input, int numAnnotationBytes,
                                                   ClassLoader classLoader) throws IOException {
    try {
      int numAnnotations;
      if (numAnnotationBytes == 2) {
        numAnnotations = input.readUnsignedShort();
      } else {
        if (VM.VerifyAssertions) VM._assert(numAnnotationBytes == 1);
        numAnnotations = input.readByte() & 0xFF;
      }
      final VM_Annotation[] annotations = new VM_Annotation[numAnnotations];
      for (int j = 0; j < numAnnotations; j++) {
        annotations[j] = VM_Annotation.readAnnotation(constantPool, input, classLoader);
      }
      return annotations;
    } catch (ClassNotFoundException e) {
      throw new Error(e);
    }
  }

  /**
   * Get the annotations for this and all super annotated elements.
   */
  public final Annotation[] getAnnotations() {
    return cloneAnnotations(getAnnotationsInternal());
  }

  Annotation[] getAnnotationsInternal() {
    return getDeclaredAnnotationsInternal();
  }

  /**
   * Get the annotations for this annotated element
   */
  public final Annotation[] getDeclaredAnnotations() {
    return cloneAnnotations(getDeclaredAnnotationsInternal());
  }

  final Annotation[] getDeclaredAnnotationsInternal() {
    if (null == declaredAnnotations) {
      declaredAnnotations = toAnnotations(declaredAnnotationDatas);
    }
    return declaredAnnotations;
  }

  /**
   * Copy array of annotations so can be safely returned to user.
   */
  private Annotation[] cloneAnnotations(final Annotation[] internal) {
    final Annotation[] annotations = new Annotation[internal.length];
    System.arraycopy(internal, 0, annotations, 0, internal.length);
    return annotations;
  }

  /**
   * Convert annotations from internal format to annotation instances.
   *
   * @param annotations the annotations.
   * @return the annotation instances.
   */
  final Annotation[] toAnnotations(final VM_Annotation[] annotations) {
    if (null == annotations) {
      return new Annotation[0];
    } else {
      final Annotation[] copy = new Annotation[annotations.length];
      for (int i = 0; i < copy.length; i++) {
        copy[i] = annotations[i].getValue();
      }
      return copy;
    }
  }

  /**
   * Get the annotation implementing the specified class or null
   */
  @SuppressWarnings({"unchecked"})
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    if (null == annotationClass) {
      throw new NullPointerException("annotationClass");
    }
    final Annotation[] annotations = getAnnotationsInternal();
    for (final Annotation annotation : annotations) {
      if (annotationClass.isInstance(annotation)) return (T) annotation;
    }
    return null;
  }

  /**
   * Is there an annotation of this type implemented on this annotated
   * element?
   */
  public final boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
    return getAnnotation(annotationClass) != null;
  }

  /**
   * Return true if annotation present.
   *
   * This is provided as an alternative to isAnnotationPresent() as isAnnotationPresent()
   * may require classloading and instantiation of annotations. Classloading would mean
   * that it would not be @Uninterruptible. Instantiation is not desirtable as checking
   * of annotations occurs prior to the bootimage compiler being ready to instantiate
   * objects.
   */
  @Uninterruptible
  final boolean isAnnotationDeclared(final VM_TypeReference annotationTypeRef) {
    if (null != declaredAnnotationDatas) {
      for (VM_Annotation annotation : declaredAnnotationDatas) {
        if (annotation.getType().equals(annotationTypeRef.getName()) &&
            annotation.getClassLoader() == annotationTypeRef.getClassLoader()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Return true if this element has a Interruptible annotation.
   * @see org.vmmagic.pragma.Interruptible
   */
  public final boolean hasInterruptibleAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.Interruptible);
  }

  /**
   * Return true if this element has a LogicallyUninterruptible annotation.
   * @see org.vmmagic.pragma.LogicallyUninterruptible
   */
  public final boolean hasLogicallyUninterruptibleAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.LogicallyUninterruptible);
  }

  /**
   * Return true if this element has a NoOptCompile annotation.
   * @see org.vmmagic.pragma.NoOptCompile
   */
  public final boolean hasNoOptCompileAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.NoOptCompile);
  }

  /**
   * Return true if this element has a Preemptible annotation.
   * @see org.vmmagic.pragma.Preemptible
   */
  public final boolean hasPreemptibleAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.Preemptible);
  }

  /**
   * Return true if this element has a UninterruptibleNoWarn annotation.
   * @see org.vmmagic.pragma.UninterruptibleNoWarn
   */
  public final boolean hasUninterruptibleNoWarnAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.UninterruptibleNoWarn);
  }

  /**
   * Return true if this element has a Uninterruptible annotation.
   * @see org.vmmagic.pragma.Uninterruptible
   */
  public final boolean hasUninterruptibleAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.Uninterruptible);
  }

  /**
   * Return true if this element has a Unpreemptible annotation.
   * @see org.vmmagic.pragma.Unpreemptible
   */
  public final boolean hasUnpreemptibleAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.Unpreemptible);
  }

  /**
   * Return true if this element has a Inline annotation.
   * @see org.vmmagic.pragma.Inline
   */
  public final boolean hasInlineAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.Inline);
  }

  /**
   * Return true if this element has a NoInline annotation.
   * @see org.vmmagic.pragma.NoInline
   */
  public final boolean hasNoInlineAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.NoInline);
  }

  /**
   * Return true if this element has a BaselineNoRegisters annotation.
   * @see org.vmmagic.pragma.BaselineNoRegisters
   */
  public final boolean hasBaselineNoRegistersAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.BaselineNoRegisters);
  }

  /**
   * Return true if this element has a BaselineSaveLSRegisters annotation.
   * @see org.vmmagic.pragma.BaselineSaveLSRegisters
   */
  public final boolean hasBaselineSaveLSRegistersAnnotation() {
    return isAnnotationDeclared(VM_TypeReference.BaselineSaveLSRegisters);
  }
}
