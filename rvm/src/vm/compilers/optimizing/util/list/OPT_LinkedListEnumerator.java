/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * put your documentation comment here
 */
final class OPT_LinkedListEnumerator
    implements Enumeration {
  OPT_LinkedListElement curr;

  /**
   * put your documentation comment here
   * @param   OPT_LinkedListElement start
   */
  OPT_LinkedListEnumerator (OPT_LinkedListElement start) {
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
    try {
      OPT_LinkedListElement e = curr;
      curr = curr.next;
      return  e;
    } catch (NullPointerException e) {
      throw  new NoSuchElementException("LinkedListEnumerator");
    }
  }
}



