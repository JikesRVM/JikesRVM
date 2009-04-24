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
package test.org.jikesrvm.basic.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class to test inheritance of annotations.
 */
public class TestAnnotations {

  /** Annotation recursively defined */
  @Retention(RetentionPolicy.RUNTIME)
  @A
  public @interface A {
  }

  static void testA() {
    final Annotation[] annotations = A.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
  }

  /** Class value recursively defined */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface B {
    Class value() default B.class;
  }

  @B class testB{}

  static void testB() {
    final Annotation[] annotations = testB.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
    System.out.println(testB.class.getAnnotation(B.class).value());
    System.out.println(testB.class.getAnnotation(B.class));
  }

  /** Enum values */
  public enum testC_Enum {one, two, three};

  @Retention(RetentionPolicy.RUNTIME)
  public @interface C {
    testC_Enum value() default testC_Enum.one;
  }

  @C class testC_1{}
  @C(testC_Enum.two) class testC_2{}

  static void testC() {
    Annotation[] annotations = testC_1.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
    annotations = testC_2.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
    System.out.println(testC_1.class.getAnnotation(C.class).value());
    System.out.println(testC_1.class.getAnnotation(C.class));
    System.out.println(testC_2.class.getAnnotation(C.class).value());
    System.out.println(testC_2.class.getAnnotation(C.class));
  }

  /** Arrays */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface D {
    Class[] value() default {D.class};
  }

  @D class testD_1{}
  @D({testD_1.class, testD_2.class, D.class}) class testD_2{}

  static void testD() {
    Annotation[] annotations = testD_1.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
    annotations = testD_2.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation.annotationType().getName());
    }
    for (final Object o : testD_1.class.getAnnotation(D.class).value()) {
      System.out.println(o);
    }
    System.out.println(testD_1.class.getAnnotation(D.class));
    for (final Object o : testD_2.class.getAnnotation(D.class).value()) {
      System.out.println(o);
    }
    System.out.println(testD_2.class.getAnnotation(D.class));
  }

  public static void main(String[] args) {
    testA();
    testB();
    testC();
    testD();
  }
}
