/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * put your documentation comment here
 */
class OPT_LiveSetEnumerator
    implements Enumeration {

  /**
   * put your documentation comment here
   * @param   OPT_LiveSetElement list
   */
  OPT_LiveSetEnumerator (OPT_LiveSetElement list) {
    current = list;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    return  current != null;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    if (current != null) {
      OPT_LiveSetElement ret = current;
      current = current.getNext();
      return  ret.getRegisterOperand();
    } 
    else {
      throw  new NoSuchElementException("OPT_LiveSetEnumerator");
    }
  }
  private OPT_LiveSetElement current;
}



