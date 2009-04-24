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
package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * This pragma is a variant of Pure that is used to mark methods that have a
 * special behaviour at boot image writing time and are Pure at runtime
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Pragma
public @interface RuntimePure {
  /** Enumeration of the special boot image return values */
  public enum ReturnValue {
    /** the return value is unavailable until runtime*/
    Unavailable
  }
  /** What value should be returned */
  ReturnValue value() default ReturnValue.Unavailable;
}
