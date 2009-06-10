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
package org.vmmagic;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;

/**
 * A Pragma is a mechanism for supplying information to the compiler to alter the code it
 * generates for a method. A Pragma is similar to an {@link org.vmmagic.Intrinsic}
 * but it does not provide any behaviour but provides information to the compiler that modifies
 * optimizations, calling conventions and activation frame layout.
 *
 * <p>If the Pragma annotation is applied to a method then then the method is a handled specially
 * by the compiler. If the Pragma annotation is applied to a class then then all the methods in that
 * class are handled specially by the compiler.</p>
 *
 * <p>NOTE: At the current time the Pragma annotation is only used for documentation
 * purposes but in the near future it is expected that the semantics of the annotation will
 * be enforced by the compiler.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
@Inherited
public @interface Pragma { }
