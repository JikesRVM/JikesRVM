/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_EmptySet extends JDK2_AbstractSet {
  public static OPT_EmptySet INSTANCE = new OPT_EmptySet();

  /**
   * put your documentation comment here
   * @return 
   */
  public JDK2_Iterator iterator () {
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



