/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

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
 * @author Bowen Alpern
 * @see com.ibm.jikesrvm.VM_Magic#dynamicBridgeTo(com.ibm.jikesrvm.ArchitectureSpecific.VM_CodeArray)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DynamicBridge { }
