/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_Pair {
  Object first;
  Object second;

  /**
   * put your documentation comment here
   * @param   Object f
   * @param   Object s
   */
  OPT_Pair (Object f, Object s) {
    first = f;
    second = s;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int hashCode () {
    return  (first.hashCode() | second.hashCode());
  }

  /**
   * put your documentation comment here
   * @param o
   * @return 
   */
  public boolean equals (Object o) {
    return  (o instanceof OPT_Pair) && first == ((OPT_Pair)o).first && 
        second == ((OPT_Pair)o).second;
  }
}



