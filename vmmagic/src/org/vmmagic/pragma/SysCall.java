/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2007
 */
//$Id: $
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
public @interface SysCall {
}
