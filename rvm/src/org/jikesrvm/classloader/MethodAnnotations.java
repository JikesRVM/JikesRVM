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

public class MethodAnnotations extends Annotations {

  private static final MethodAnnotations NO_METHOD_ANNOTATIONS = new MethodAnnotations(null, null, null);

  private final RVMAnnotation[][] parameterAnnotations;
  private final Object annotationDefaults;

  private byte[] rawParameterAnnotations;
  private byte[] rawAnnotationDefault;

  /**
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefaults value for this annotation that appears
   */
  public MethodAnnotations(RVMAnnotation[] annotations,
      RVMAnnotation[][] parameterAnnotations, Object annotationDefaults) {
        super(annotations);
        this.parameterAnnotations = parameterAnnotations;
        this.annotationDefaults = annotationDefaults;
  }

  public RVMAnnotation[][] getParameterAnnotations() {
    return parameterAnnotations;
  }

  public Object getAnnotationDefaults() {
    return annotationDefaults;
  }

  public void setRawParameterAnnotations(byte[] rawParameterAnnotations) {
    this.rawParameterAnnotations = rawParameterAnnotations;

  }

  public void setRawAnnotationDefault(byte[] rawAnnotationDefault) {
    this.rawAnnotationDefault = rawAnnotationDefault;
  }

  public byte[] getRawParameterAnnotations() {
    return rawParameterAnnotations;
  }

  public byte[] getRawAnnotationDefault() {
    return rawAnnotationDefault;
  }

  static MethodAnnotations noMethodAnnotations() {
    return NO_METHOD_ANNOTATIONS;
  }

}
