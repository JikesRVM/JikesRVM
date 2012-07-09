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
 * A pragma that can be used to declare that a particular method is logically
 * uninterruptible even though it contains bytecodes that are actually
 * interruptible.<p>
 *
 * The effect of this pragma is to suppress warning messages about violations of
 * uninterruptiblity when compiling a method that throws this exception. There
 * are two cases in which using the pragma is justified.
 * <ul>
 * <li> Uninterruptibility is ensured via some other mechanism. For example, the
 * method explicitly disables threadswitching around the interruptible regions
 * (VM.sysWrite on String). Or the interruptible regions are not reachable when
 * the VM is running (various VM.sysWrite that check VM.runningVM).
 * <li> The interruptible regions represent an 'error' condition that will never
 * be executed unless the VM is already in the process of reporting an error,
 * for example RuntimeEntrypoints.raiseClassCastException.
 * </ul>
 * Extreme care must be exercised when using this pragma since it suppresses the
 * checking of uninterruptibility.
 * <p>
 * Use of this pragma is being phased out since it lumps together two possible
 * special cases. Use either {@link Unpreemptible} or
 * {@link UninterruptibleNoWarn} instead.
 * {@link <a href="http://jira.codehaus.org/browse/RVM-115">RVM-115</a>} for more
 * context.
 * @deprecated
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Pragma
public @interface LogicallyUninterruptible { }
