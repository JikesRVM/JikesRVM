/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Enumeration that doesn't have any elements.
 * Use the EMPTY object to access.
 * 
 * @author Igor Pechtchanski
 */


import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * put your documentation comment here
 */
final class OPT_EmptyEnumerator
    implements Enumeration {
  public static final OPT_EmptyEnumerator EMPTY = new OPT_EmptyEnumerator();

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    return  false;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    throw  new NoSuchElementException();
  }

  /**
   * put your documentation comment here
   */
  private OPT_EmptyEnumerator () {
  }
}



