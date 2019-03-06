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
package org.jikesrvm.tools.bootImageWriter;

public class OpenJDKMappingError extends LinkageError {

  private static final long serialVersionUID = -2138803931008168210L;

  public OpenJDKMappingError(Throwable t) {
    super();
    initCause(t);
  }

}
