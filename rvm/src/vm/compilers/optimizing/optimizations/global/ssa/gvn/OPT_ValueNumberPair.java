/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * utility class: represents a pair of value numbers.
 *
 * @author Stephen Fink
 */
class OPT_ValueNumberPair {
  int v1;       // the value number of an array pointer
  int v2;       // the value number of an array index

  OPT_ValueNumberPair () {
  }

  OPT_ValueNumberPair (int v1, int v2) {
    this.v1 = v1;
    this.v2 = v2;
  }

  OPT_ValueNumberPair (OPT_ValueNumberPair p) {
    this.v1 = p.v1;
    this.v2 = p.v2;
  }

  public boolean equals (Object o) {
    if (!(o instanceof OPT_ValueNumberPair))
      return  false;
    OPT_ValueNumberPair p = (OPT_ValueNumberPair)o;
    return  (v1 == p.v1) && (v2 == p.v2);
  }

  public String toString () {
    return  "<" + v1 + "," + v2 + ">";
  }

  // total order over OPT_ValueNumberPairs
  public boolean greaterThan (OPT_ValueNumberPair p) {
    if (v1 > p.v1)
      return  true;
    if (v1 < p.v1)
      return  false;
    // v1 == p.v1
    return  (v2 > p.v2);
  }
}



