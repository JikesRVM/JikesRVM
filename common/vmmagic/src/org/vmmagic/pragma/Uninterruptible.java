/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
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
 * Methods of a class that use this annotation
 * are treated specially by the  compilers:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 * <p>
 * Uninterruptible and {@link Unpreemptible} have the same direct effect on
 * the generated code.  The difference is that Uninterruptible
 * indicates a stronger invariant: It is a programming error (and will
 * be reported as such) for Uninterruptible code to contain any
 * bytecodes that could cause a loss of control. Furthermore,
 * Uninterruptible code will be generated assuming no
 * RuntimeExceptions are raised and without any GC maps (since by
 * definition there can be noGC if control is not lost). Unpreemtible
 * code will have GC maps for all potential GC points and may contain
 * places where a thread explicitly yields.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Pragma
public @interface Uninterruptible { }
