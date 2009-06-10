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
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * Methods of a class that implements this interface are treated specially
 * by the compilers.
 *
 * <p>Instead of saving just the non-volatile registers used by the method into
 * the register save area of the method's stackframe, the compiler generates
 * code to save *all* GPR and FPR registers except GPR0, FPR0, JTOC, and FP.
 *
 * <p>Methods of a class that implement this interface may not return.
 *    (it is assumed that execution is resumed via a call to Magic.dynamicBridgeTo)
 *
 * @see org.jikesrvm.runtime.Magic#dynamicBridgeTo(org.jikesrvm.ArchitectureSpecific.CodeArray)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Pragma
public @interface DynamicBridge { }
