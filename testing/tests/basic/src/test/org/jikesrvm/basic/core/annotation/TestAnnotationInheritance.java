/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package test.org.jikesrvm.basic.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class to test inheritance of annotations.
 *
 * @author Peter Donald
 */
public class TestAnnotationInheritance {

  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  public @interface A {
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface B {
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface C {
  }

  @A
  @C
  class X {
  }

  @B
  class Y extends X {
  }

  public static void main(String[] args) {
    final Annotation[] annotations = Y.class.getAnnotations();
    for (final Annotation annotation : annotations) {
      System.out.println(annotation);
    }
    check("getAnnotations must return 2 annotations, 1 inherited and 1 declared", Y.class.getAnnotations().length == 2);
    check("getAnnotations must return declared first", Y.class.getAnnotations()[0] instanceof B);
    check("getAnnotations must return inherited second", Y.class.getAnnotations()[1] instanceof A);
    check("getAnnotation on non-declared, non-inherited annotation must return null", Y.class.getAnnotation(C.class) == null);
    check("getAnnotation on declared annotation must not return null", Y.class.getAnnotation(B.class) != null);
    check("getAnnotation on inherited annotation must not return null", Y.class.getAnnotation(A.class) != null);
    check("getAnnotation on declared annotation must return same instance after multiple calls",
        Y.class.getAnnotation(B.class) == Y.class.getAnnotation(B.class));
    check("getAnnotation on inherited annotation must return same instance after multiple calls",
        Y.class.getAnnotation(A.class) == Y.class.getAnnotation(A.class));
    check("getAnnotation on inherited annotation must return same instance from parent and child classes",
        Y.class.getAnnotation(A.class) == X.class.getAnnotation(A.class));
  }

  private static void check(String message, boolean condition) {
    if (!condition) System.out.println("Failed check: " + message);
  }
}
