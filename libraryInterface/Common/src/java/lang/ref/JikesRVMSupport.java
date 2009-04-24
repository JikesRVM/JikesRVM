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
package java.lang.ref;

import org.vmmagic.pragma.Uninterruptible;

public class JikesRVMSupport {
  @SuppressWarnings("unchecked") // This method requires an unchecked cast
  @Uninterruptible
  public static <T> T uninterruptibleReferenceGet(Reference<T> ref) {
    return (T)ref.getInternal();
  }
}
