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
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;

import org.vmmagic.pragma.Uninterruptible;
import org.jikesrvm.VM;

/**
 * A common abstract super class for all elements that can be
 * annotated within the JVM. Namely classes, methods and fields.
 */
public abstract class AnnotatedElement implements java.lang.reflect.AnnotatedElement {
  /**
   * Annotations from the class file that are described as runtime
   * visible. These annotations are available to the reflection API.
   * This is either null, a RVMAnnotation if a single annotation is
   * present, or an array of RVMAnnotation if there is &gt;1
   */
  protected final Object declaredAnnotationDatas;
  /** Cached array of declared annotations. */
  private Annotation[] declaredAnnotations;
  /** Empty annotation array */
  private static final Annotation[] emptyAnnotationArray = new Annotation[0];

  /**
   * Constructor used by all annotated elements
   *
   * @param annotations array of runtime visible annotations
   */
  protected AnnotatedElement(RVMAnnotation[] annotations) {
    if (annotations == null) {
      declaredAnnotationDatas = null;
      declaredAnnotations = emptyAnnotationArray;
    } else if (annotations.length == 1) {
      this.declaredAnnotationDatas = annotations[0];
    } else {
      this.declaredAnnotationDatas = annotations;
    }
    if (annotations != null) {
      for (RVMAnnotation ann : annotations) {
        if (ann == null)  throw new Error("null annotation in " + toString());
      }
    }
  }

  /**
   * Read annotations from a class file and package in an array
   * @param constantPool the constantPool of the RVMClass object
   * that's being constructed
   * @param input the DataInputStream to read the method's attributes
   * from
   * @return an array of read annotations
   */
  protected static RVMAnnotation[] readAnnotations(int[] constantPool, DataInputStream input,
                                                   ClassLoader classLoader) throws IOException {
    try {
      int numAnnotations = input.readUnsignedShort();
      final RVMAnnotation[] annotations = new RVMAnnotation[numAnnotations];
      for (int j = 0; j < numAnnotations; j++) {
        annotations[j] = RVMAnnotation.readAnnotation(constantPool, input, classLoader);
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
    if (!VM.runningVM) {
      return toAnnotations(declaredAnnotationDatas);
    }
    if (null == declaredAnnotations) {
      declaredAnnotations = toAnnotations(declaredAnnotationDatas);
    }
    return declaredAnnotations;
  }

  /**
   * Copy array of annotations so can be safely returned to user.
   */
  private Annotation[] cloneAnnotations(final Annotation[] internal) {
    if (internal.length == 0) {
      return emptyAnnotationArray;
    } else {
      final Annotation[] annotations = new Annotation[internal.length];
      System.arraycopy(internal, 0, annotations, 0, internal.length);
      return annotations;
    }
  }

  /**
   * Convert annotations from internal format to annotation instances.
   *
   * @param datas the annotations.
   * @return the annotation instances.
   */
  final Annotation[] toAnnotations(final Object datas) {
    if (null == datas) {
      return emptyAnnotationArray;
    } else if (datas instanceof RVMAnnotation) {
      final Annotation[] copy = new Annotation[1];
      copy[0] = ((RVMAnnotation)datas).getValue();
      return copy;
    } else {
      RVMAnnotation[] annotations = (RVMAnnotation[])datas;
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
   * that it would not be @Uninterruptible. Instantiation is not desirable as checking
   * of annotations occurs prior to the bootimage compiler being ready to instantiate
   * objects.
   */
  @Uninterruptible
  final boolean isAnnotationDeclared(final TypeReference annotationTypeRef) {
    if (declaredAnnotationDatas == null) {
      return false;
    } else if (declaredAnnotationDatas instanceof RVMAnnotation) {
      RVMAnnotation annotation = (RVMAnnotation)declaredAnnotationDatas;
      return annotation.annotationType() == annotationTypeRef;
    } else {
      for (RVMAnnotation annotation : (RVMAnnotation[])declaredAnnotationDatas) {
        if (annotation.annotationType() == annotationTypeRef) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Does the element have any annotations?
   */
  @Uninterruptible
  public final boolean hasAnnotations() {
    return declaredAnnotationDatas != null;
  }

  /**
   * Return true if this element has a Interruptible annotation.
   * @see org.vmmagic.pragma.Interruptible
   */
  public final boolean hasInterruptibleAnnotation() {
    return isAnnotationDeclared(TypeReference.Interruptible);
  }

  /**
   * Return true if this element has a LogicallyUninterruptible annotation.
   * @see org.vmmagic.pragma.LogicallyUninterruptible
   */
  public final boolean hasLogicallyUninterruptibleAnnotation() {
    return isAnnotationDeclared(TypeReference.LogicallyUninterruptible);
  }

  /**
   * Return true if this element has a Preemptible annotation.
   * @see org.vmmagic.pragma.Preemptible
   */
  public final boolean hasPreemptibleAnnotation() {
    return isAnnotationDeclared(TypeReference.Preemptible);
  }

  /**
   * Return true if this element has a UninterruptibleNoWarn annotation.
   * @see org.vmmagic.pragma.UninterruptibleNoWarn
   */
  public final boolean hasUninterruptibleNoWarnAnnotation() {
    return isAnnotationDeclared(TypeReference.UninterruptibleNoWarn);
  }

  /**
   * Return true if this element has a UninterruptibleNoWarn annotation.
   * @see org.vmmagic.pragma.UninterruptibleNoWarn
   */
  public final boolean hasUnpreemptibleNoWarnAnnotation() {
    return isAnnotationDeclared(TypeReference.UnpreemptibleNoWarn);
  }

  /**
   * Return true if this element has a Uninterruptible annotation.
   * @see org.vmmagic.pragma.Uninterruptible
   */
  public final boolean hasUninterruptibleAnnotation() {
    return isAnnotationDeclared(TypeReference.Uninterruptible);
  }
  /**
   * Return true if this element has a NoCheckStore annotation.
   * @see org.vmmagic.pragma.NoCheckStore
   */
  public final boolean hasNoCheckStoreAnnotation() {
    return isAnnotationDeclared(TypeReference.NoCheckStore);
  }

  /**
   * Return true if this element has a Unpreemptible annotation.
   * @see org.vmmagic.pragma.Unpreemptible
   */
  public final boolean hasUnpreemptibleAnnotation() {
    return isAnnotationDeclared(TypeReference.Unpreemptible);
  }

  /**
   * Return true if this element has a NoOptCompile annotation.
   * @see org.vmmagic.pragma.NoOptCompile
   */
  public final boolean hasNoOptCompileAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.NoOptCompile.class);
  }

  /**
   * Return true if this element has a Inline annotation.
   * @see org.vmmagic.pragma.Inline
   */
  public final boolean hasInlineAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.Inline.class);
  }

  /**
   * Return true if this element has a NoInline annotation.
   * @see org.vmmagic.pragma.NoInline
   */
  public final boolean hasNoInlineAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.NoInline.class);
  }

  /**
   * Return true if this element has a BaselineNoRegisters annotation.
   * @see org.vmmagic.pragma.BaselineNoRegisters
   */
  public final boolean hasBaselineNoRegistersAnnotation() {
    return isAnnotationDeclared(TypeReference.BaselineNoRegisters);
  }

  /**
   * Return true if this element has a BaselineSaveLSRegisters annotation.
   * @see org.vmmagic.pragma.BaselineSaveLSRegisters
   */
  @Uninterruptible
  public final boolean hasBaselineSaveLSRegistersAnnotation() {
    return isAnnotationDeclared(TypeReference.BaselineSaveLSRegisters);
  }

  /**
   * Return true if this element has a Pure annotation.
   * @see org.vmmagic.pragma.Pure
   */
  public final boolean hasPureAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.Pure.class);
  }

  /**
   * Return true if this element has a RuntimePure annotation.
   * @see org.vmmagic.pragma.RuntimePure
   */
  public final boolean hasRuntimePureAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.RuntimePure.class);
  }

  /**
   * Return true if this element has a NoNullCheck annotation.
   * @see org.vmmagic.pragma.NoNullCheck
   */
  public final boolean hasNoNullCheckAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.NoNullCheck.class);
  }

  /**
   * Return true if this element has a NoBoundsCheck annotation.
   * @see org.vmmagic.pragma.NoBoundsCheck
   */
  public final boolean hasNoBoundsCheckAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.NoBoundsCheck.class);
  }

  /**
   * Return true if this element has a RuntimeFinal annotation.
   * @see org.vmmagic.pragma.RuntimeFinal
   */
  public final boolean hasRuntimeFinalAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.RuntimeFinal.class);
  }

  /**
   * Return true if this element has a NoEscapes annotation.
   * @see org.vmmagic.pragma.NoEscapes
   */
  public final boolean hasNoEscapesAnnotation() {
    return isAnnotationPresent(org.vmmagic.pragma.NoEscapes.class);
  }

  /**
   * Return true if this element has a Untraced annotation.
   * @see org.vmmagic.pragma.Untraced
   */
  @Uninterruptible
  public final boolean hasUntracedAnnotation() {
    return isAnnotationDeclared(TypeReference.Untraced);
  }

  /**
   * Return true if this element has a NonMoving annotation.
   * @see org.vmmagic.pragma.NonMoving
   */
  public final boolean hasNonMovingAnnotation() {
    return isAnnotationDeclared(TypeReference.NonMoving);
  }

  /**
   * Return true if this element has a NonMovingAllocation annotation.
   * @see org.vmmagic.pragma.NonMovingAllocation
   */
  public final boolean hasNonMovingAllocationAnnotation() {
    return isAnnotationDeclared(TypeReference.NonMovingAllocation);
  }
}
