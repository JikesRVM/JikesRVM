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
import org.vmmagic.Pragma;

/**
 * Methods of a class that implements this interface
 * are treated specially by the machine code compiler:
 * the method prologue saves all the volatile registers
 * and the method epilogue restores all the volatile registers
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Pragma
public @interface SaveVolatile { }
