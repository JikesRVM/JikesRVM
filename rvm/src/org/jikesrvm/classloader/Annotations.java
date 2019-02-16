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

public class Annotations {

  private static final Annotations NO_ANNOTATIONS = new Annotations(new RVMAnnotation[0]);

  protected final RVMAnnotation[] annotations;

  protected byte[] rawAnnotations;

  /**
   * @param annotations array of runtime visible annotations
   */
  public Annotations(RVMAnnotation[] annotations) {
    this.annotations = annotations;
  }

  public RVMAnnotation[] getAnnotations() {
    return annotations;
  }

  static Annotations noAnnotations() {
    return NO_ANNOTATIONS;
  }

  public void setRawAnnotations(byte[] rawAnnotations) {
    this.rawAnnotations = rawAnnotations;
  }

  public byte[] getRawAnnotations() {
    return rawAnnotations;
  }
}
