/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * put your documentation comment here
 */
final class OPT_LinkedListObjectEnumerator
    implements Enumeration {
  OPT_LinkedListElement curr;

  /**
   * put your documentation comment here
   * @param   OPT_LinkedListObjectElement start
   */
  OPT_LinkedListObjectEnumerator (OPT_LinkedListObjectElement start) {
    curr = start;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    return  curr != null;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    return  next();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object next () {
    try {
      OPT_LinkedListObjectElement e = (OPT_LinkedListObjectElement)curr;
      curr = curr.next;
      return  e.value;
    } catch (NullPointerException e) {
      throw  new NoSuchElementException("OPT_LinkedListObjectEnumerator");
    }
  }
}



