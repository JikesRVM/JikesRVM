/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_EmptySet extends java.util.AbstractSet {
  public static OPT_EmptySet INSTANCE = new OPT_EmptySet();

  /**
   * put your documentation comment here
   * @return 
   */
  public java.util.Iterator iterator () {
    return  OPT_EmptyIterator.INSTANCE;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int size () {
    return  0;
  }
}



