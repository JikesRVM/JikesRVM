/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_EmptyIterator
    implements java.util.Iterator {

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasNext () {
    return  false;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object next () {
    throw  new java.util.NoSuchElementException();
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    throw  new java.util.UnsupportedOperationException();
  }
  public static OPT_EmptyIterator INSTANCE = new OPT_EmptyIterator();
}



