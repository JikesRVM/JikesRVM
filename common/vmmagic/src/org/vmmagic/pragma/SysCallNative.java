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
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * An annotation for static native methods to show that they should be
 * compiled as system calls. A system call is a lightweight call to a
 * C function that doesn't set up the JNI environment and is therefore
 * cheaper than JNI. The first argument to the function is the address
 * of the C function to run.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Pragma
public @interface SysCallNative {
}
