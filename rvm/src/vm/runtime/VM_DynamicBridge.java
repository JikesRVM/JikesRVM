/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Methods of a class that implements this interface are treated specially 
 * by the compilers.
 *
 * <p>Instead of saving just the non-volatile registers used by the method into 
 * the register save area of the method's stackframe, the compiler generates 
 * code to save *all* GPR and FPR registers except GPR0, FPR0, JTOC, and FP.
 *
 * <p>Methods of a class that implement this interface may not return.
 *    (it is assumed that execution is resumed via a call to VM_Magic.dynamicBridgeTo)
 *
 * <p> See also: VM_Magic.dynamicBridgeTo()
 *
 * @author Bowen Alpern
 */
interface VM_DynamicBridge { }
