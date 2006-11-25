/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.pragma; 

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

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
 * <p>
 * You can use {@link UninterruptiblePragma} and
 * {@link InterruptiblePragma} to control
 * this property at a per-method granularity.
 * <P>
 * There is no matching <code>Interruptible</code> annotation,
 * since that is the default.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @author Ian Rogers
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Uninterruptible { }
