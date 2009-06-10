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
 * This pragma is used to indicate a method has no side effects. Use this pragma
 * with care as it can cause compile time invocation of the method it is placed
 * on. This pragma is used to imply weak purity of a method, and as such cannot
 * remove calls to pure methods - as they may throw exceptions.
 * {@link <a href="http://jira.codehaus.org/browse/RVM-503">RVM-503</a>}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Pragma
public @interface Pure { /* annotation has no value */ }
