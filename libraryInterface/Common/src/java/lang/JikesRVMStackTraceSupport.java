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
package java.lang;

import org.jikesrvm.runtime.StackTrace;

/**
 * Provides methods to convert Jikes RVM internal stack trace elements to stack
 * trace elements of the Java API.
 * <p>
 * Cannot be in JikesRVMSupport because the methods of this class are needed for
 * all class libraries and GNU Classpath already has a JikesRVMSupport implementation
 * in java.lang.
 */
public class JikesRVMStackTraceSupport {

  public static StackTraceElement[] convertToJavaClassLibraryStackTrace(
      StackTrace.Element[] vmElements) {
    StackTraceElement[] elements = new StackTraceElement[vmElements.length];
    for (int i = 0; i < vmElements.length; i++) {
      StackTrace.Element vmElement = vmElements[i];
      String fileName = vmElement.getFileName();
      int lineNumber = vmElement.getLineNumber();
      String className = vmElement.getClassName();
      String methodName = vmElement.getMethodName();
      elements[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
    }
    return elements;
  }

}
