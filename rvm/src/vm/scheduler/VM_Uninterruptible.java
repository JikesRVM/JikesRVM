/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/** 
 * Methods of a class that implements this (pseudo-)interface
 * are treated specially by the  compilers:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 *
 * <P>
 * You can use {@link VM_PragmaUninterruptible} and
 * {@link VM_PragmaInterruptible} to control
 * this property at a per-method granularity.
 * <P>
 * There is no matching <code>VM_Interruptible</code> pseudo-interface, 
 * since that is the default.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public interface VM_Uninterruptible { }
