/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_EmptyIterator
    implements JDK2_Iterator {

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
    throw  new JDK2_NoSuchElementException();
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    throw  new JDK2_UnsupportedOperationException();
  }
  public static OPT_EmptyIterator INSTANCE = new OPT_EmptyIterator();
}



