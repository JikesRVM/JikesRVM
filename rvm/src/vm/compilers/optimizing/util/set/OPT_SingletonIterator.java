/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_SingletonIterator
    implements JDK2_Iterator {

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
    throw  new JDK2_NoSuchElementException();
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    throw  new JDK2_UnsupportedOperationException();
  }
}



