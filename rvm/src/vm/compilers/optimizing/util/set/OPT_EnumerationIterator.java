/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An <code>OPT_EnumerationIterator</code> converts an <code>Enumeration</code>
 * into an <code>Iterator</code>.
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public class OPT_EnumerationIterator
    implements java.util.Iterator {
  private final java.util.Enumeration e;

  /**
   * put your documentation comment here
   * @param   java.util.Enumeration e
   */
  public OPT_EnumerationIterator (java.util.Enumeration e) {
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
    throw  new java.lang.UnsupportedOperationException();
  }
}



