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
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;

/**
 * Use this annotation to mark fields that are read without the knowledge of the
 * memory management system. This means that barriers are not triggered and
 * the reference is also not traced by the garbage collector.<p>
 *
 * In order to guarantee that the constraints described above are honored by the compiler,
 * all accesses to untraced fields must be made when the field is resolved. If the field
 * were not resolved at the time when the code for the access was being compiled, the
 * Untraced annotation wouldn't be available (annotations are loaded at resolution
 * time).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Untraced { }
