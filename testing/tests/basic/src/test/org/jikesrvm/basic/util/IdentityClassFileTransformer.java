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
package test.org.jikesrvm.basic.util;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * A ClassFileTransformer that just prints out when a class is loaded. Useful for testing loading
 * and resolving of classes.
 */
public class IdentityClassFileTransformer implements ClassFileTransformer {
  public static void premain(final String args, final Instrumentation instrumentation) {
    System.out.println("Registering transformer");
    instrumentation.addTransformer(new IdentityClassFileTransformer());
  }

  public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
    if (className.startsWith("test/org/jikesrvm/")) {
      System.out.println("Transforming class: " + className);
    }
    // I'm too lazy to actually change the class, so we'll just pretend we did by returning non-null
    return classfileBuffer;
  }
}
