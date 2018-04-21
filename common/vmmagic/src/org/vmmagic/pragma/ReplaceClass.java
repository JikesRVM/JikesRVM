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
 * The ReplaceClass annotation indicates that the members of this class annotated with
 * <code>ReplaceMember</code> will replace members who have same name and same descriptor
 * in the targeting class. The <code>value()</code> of this annotation should be the
 * descriptor of targeting class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Pragma
public @interface ReplaceClass {

  /**
   * @return the name of the class to be replaced, e.g. {@code java.lang.System}
   */
  String className();
}
