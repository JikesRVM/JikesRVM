package test.org.jikesrvm.basic.core.bytecode;

/**
 * According the the definition of instanceof the method must be resolved prior to any other checks.
 * See http://java.sun.com/docs/books/jvms/second_edition/html/Instructions2.doc6.html#instanceof.Description
 *
 * Also described in [ 1147107 ]  unresolved instanceof etc. on &lt;null&gt; not compliant.
 *
 */
public class TestResolveOnInstanceof {

  static interface A {
    String genString();
  }

  public static void main(String[] args) {
    System.out.println(doInstanceOf(new Object()));
  }

  static boolean doInstanceOf(final Object a) {
    return a instanceof A;
  }
}
