/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An <code>EnumerationIterator</code> converts an <code>Enumeration</code>
 * into an <code>Iterator</code>.
 */
public class EnumerationIterator
    implements JDK2_Iterator {
  private final java.util.Enumeration e;

  /**
   * put your documentation comment here
   * @param   java.util.Enumeration e
   */
  public EnumerationIterator (java.util.Enumeration e) {
    this.e = e;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasNext () {
    return  e.hasMoreElements();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object next () {
    return  e.nextElement();
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    throw  new JDK2_UnsupportedOperationException();
  }
}



