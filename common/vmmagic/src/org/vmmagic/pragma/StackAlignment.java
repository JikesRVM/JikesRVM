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

import org.vmmagic.Pragma;
import java.lang.annotation.*;

/**
 * An annotation for static native methods to show that calls should be aligned
 * to 16 bytes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
@Documented
@Pragma
public @interface StackAlignment {
}
