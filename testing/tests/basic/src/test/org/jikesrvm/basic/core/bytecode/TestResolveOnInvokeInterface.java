package test.org.jikesrvm.basic.core.bytecode;

/**
 * According the the definition of invokeinterface the method must be resolved prior to any other checks.
 * See http://java.sun.com/docs/books/jvms/second_edition/html/Instructions2.doc6.html#invokeinterface.Description
 *
 * Also described in [ 1147107 ]  unresolved instanceof etc. on &lt;null&gt; not compliant.
 *
 * @author Peter Donald
 */
public class TestResolveOnInvokeInterface {

  static interface A {
    String genString();
  }

  public static void main(String[] args) {
    try {
      doInvoke(null);
    } catch (final NullPointerException npe) {
      System.out.println("Got NPE");
    }
  }

  static String doInvoke(final A a) {
    // A should be resolved here
    return a.genString();
  }
}
