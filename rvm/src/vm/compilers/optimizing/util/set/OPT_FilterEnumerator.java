/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * A <code>FilterEnumerator</code> filters and maps a source
 * <code>Enumeration</code> to generate a new one.
 */


import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * put your documentation comment here
 */
public class OPT_FilterEnumerator
    implements Enumeration {
  final Enumeration e;
  final Filter f;
  private Object next;
  private boolean done;

  /**
   * put your documentation comment here
   * @param   Enumeration e
   * @param   Filter f
   */
  public OPT_FilterEnumerator (Enumeration e, Filter f) {
    this.e = e;
    this.f = f;
    advance();
  }

  /**
   * put your documentation comment here
   */
  private void advance () {
    while (e.hasMoreElements()) {
      next = e.nextElement();
      if (f.isElement(next))
        return;
    }
    done = true;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    if (done)
      throw  new NoSuchElementException();
    Object o = next;
    advance();
    return  f.map(o);
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    return  !done;
  }

  /**
   * put your documentation comment here
   */
  public static class Filter {                  // override with your mapping.

    /**
     * put your documentation comment here
     * @param o
     * @return 
     */
    public boolean isElement (Object o) {
      return  true;
    }

    /**
     * put your documentation comment here
     * @param o
     * @return 
     */
    public Object map (Object o) {
      return  o;
    }
  }
}



