/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An <code>IteratorEnumerator</code> converts an <code>Iterator</code>
 * into an <code>Enumeration</code>.
 *
 * @author Stephen Fink
 */
public class OPT_IteratorEnumerator
    implements java.util.Enumeration {
  private final JDK2_Iterator i;

  /**
   * put your documentation comment here
   * @param   JDK2_Iterator i
   */
  public OPT_IteratorEnumerator (JDK2_Iterator i) {
    this.i = i;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    return  i.hasNext();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    return  i.next();
  }
}



