/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Methods of a class that implements this interface are treated specially 
 * by the compilers.
 *
 * <p>Instead of saving just the non-volatile registers used by the method into 
 * the register save area of the method's stackframe, the compiler generates 
 * code to save *all* GPR and FPR registers except GPR0, FPR0, JTOC, and FP.
 *
 * <p>Prior to method return, all the non-volatile saved registers are restored.
 *
 * <p>!!TODO: consider not saving scratch registers
 * <p>!!TODO: consider not saving THREAD_ID_REGISTER and PROCESSOR_REGISTER 
 *    (since they aren't restored)
 *
 * <p> See also: VM_Magic.dynamicBridgeTo()
 *
 * @author Bowen Alpern
 */
interface VM_DynamicBridge { }
