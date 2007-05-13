package test.org.jikesrvm.basic.core.bytecode;

/**
 * According the the definition of checkcast the method must be resolved prior to any other checks.
 * See http://java.sun.com/docs/books/jvms/second_edition/html/Instructions2.doc6.html#checkcast.Description
 *
 * Also described in [ 1147107 ]  unresolved instanceof etc. on &lt;null&gt; not compliant.
 *
 */
public class TestResolveOnCheckcast {

  static interface A {
    String genString();
  }

  public static void main(String[] args) {
    try {
      doCheckcast(new Object());
    } catch (final ClassCastException cce) {
      System.out.println("Got CCE");
    }
  }

  static A doCheckcast(final Object a) {
    return (A) a;
  }
}
