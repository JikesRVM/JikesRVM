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
 * A pragma that has the same direct effect as {@link Uninterruptible}
 * but also suppresses checking of uninterruptibility violations for
 * the method. This should be used with care and is only justified
 * for code only executed when creating the boot image.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Pragma
public @interface UninterruptibleNoWarn {
  /**
   * @return Explanation of why uninterruptible warnings are disabled
   */
  String value() default "";
}
