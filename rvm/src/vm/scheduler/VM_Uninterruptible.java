/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/** 
 * Methods of a class that implements this interface
 * are treated specially by the  compilers:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 *
 * You can use {@link VM_PragmaUninterruptible} and
 * {@link VM_PragmaInterruptible} to control
 * this property at a per-method granularity.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public interface VM_Uninterruptible { }
