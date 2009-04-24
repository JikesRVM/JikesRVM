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
 * By default all Java code is interruptible, that is scheduling or garbage
 * collection may occur at points within the code. Code can be marked as
 * {@link Unpreemptible} or {@link Uninterruptible}, that instructs the JVM to
 * avoid garbage collection and thread scheduling. The {@link Uninterruptible}
 * annotation disallows any operation that may cause garbage collection or
 * thread scheduling, for example memory allocation. The {@link Unpreemptible}
 * annotation doesn't disallow operations that can cause garbage collection or
 * scheduling, but instructs the JVM to avoid inserting such operations during a
 * block of code.
 *
 * In the internals of a VM most code wants to be {@link Uninterruptible}.
 * However, code involved in scheduling and locking will cause context switches,
 * and creating exception objects may trigger garbage collection, this code is
 * therefore {@link Unpreemptible}.
 *
 * This pragma is used to declare that a particular method is preemptible. It
 * is used to override the class-wide pragma {@link Unpreemptible}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Pragma
public @interface Preemptible {
  /**
   * @return Explanation of why code needs to be preemptible
   */
  String value() default "";
}
