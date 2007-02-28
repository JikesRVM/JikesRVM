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
  }

}
