/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.pragma; 

/** 
 * Methods of a class that implements this (pseudo-)interface
 * are treated specially by the  compilers:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 * <p>
 * {@link Uninterruptible} and Unpreemptible have the same direct effect on
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
 * You can use {@link UnpreemptiblePragma} and
 * {@link PreemptiblePragma} to control
 * this property at a per-method granularity.
 * <P>
 * There is no matching <code>Preembtible</code> pseudo-interface, 
 * since that is the default.
 * 
 * @author Dave Grove
 */
public interface Unpreemptible { }
