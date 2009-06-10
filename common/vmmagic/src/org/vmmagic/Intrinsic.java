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
 * An intrinsic method is a method that is built in (that is, intrinsic) to the compiler
 * rather than part of a class library. Intrinsic methods are an escape hatch to provide
 * "magic" that is inexpressible int the Java programming language. A compiler intrinsic
 * will usually generate a specific code sequence that is inlined and optimized as part
 * by the compiler. Unless an intrinsic method is expected to be executed in a non-intrinsic
 * aware compiler, the body of the method should be empty.
 *
 * <p>If the Intrinsic annotation is applied to a method then then the method is a compiler
 * intrinsic. If the Intrinsic annotation is applied to a class then all the methods of
 * the class AND all subclasses are intrinsic.</p>
 *
 * <p>NOTE: At the current time the Intrinsic annotation is only used for documentation
 * purposes but in the near future it is expected that the semantics of the annotation will
 * be enforced by the compiler.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
@Inherited
public @interface Intrinsic { }
