/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

/**
 * Utility class to get at implementation of SysCall interface.
 */
public class VM_SysCallUtil {
  @SuppressWarnings({"unchecked"})
  public static <T> T getImplementation(Class<T> type) {
    try {
      return (T) Class.forName(type.getName() + "Impl").newInstance();
    } catch (final Exception e) {
      throw new IllegalStateException("Error creating generated implementation of " + type.getName(), e);
    }
  }

}
