/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_SingletonIterator
    implements java.util.Iterator {

  /**
   * put your documentation comment here
   * @param   Object o
   */
  OPT_SingletonIterator (Object o) {
    item = o;
    not_done = true;
  }
  boolean not_done;
  Object item;

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasNext () {
    return  not_done;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object next () {
    if (not_done) {
      not_done = false;
      return  item;
    }
    throw  new java.util.NoSuchElementException();
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    throw  new java.util.UnsupportedOperationException();
  }
}



