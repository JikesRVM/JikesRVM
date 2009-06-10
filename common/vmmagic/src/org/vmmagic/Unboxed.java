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
 * The Unboxed annotation marks a type as unboxed. Besides the primitive types, all Java values
 * are boxed types. Conceptually, they are represented by a pointer to a heap object. However,
 * an unboxed type is represented by the value itself. All methods on an unboxed type are
 * {@link Intrinsic}s.
 *
 * <p>As an unboxed type is not a regular java object there are a few constraints on the way unboxed
 * types are handled;</p>
 * <ul>
 *   <li>All methods are {@link Intrinsic} and thus there can be no virtual methods.</li>
 *   <li>An unboxed type can not be synchronized on.</li>
 *   <li>An unboxed type has no hashcode.</li>
 *   <li>An unboxed type MUST NOT be passed where an object is expected or when two overloaded methods
 *       can only be distinguished by by Object vs unboxed parameter type.</li>
 * </ul>
 *
 * <p>NOTE: At the current time the Unboxed annotation is only used for documentation
 * purposes but in the near future it is expected that the semantics of the annotation will
 * be enforced by the compiler.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Unboxed { }
