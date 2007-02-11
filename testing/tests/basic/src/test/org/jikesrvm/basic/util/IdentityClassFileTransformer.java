package test.org.jikesrvm.basic.util;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * A ClassFileTransformer that just prints out when a class is loaded. Useful for testing loading
 * and resolving of classes.
 *
 * @author Peter Donald
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