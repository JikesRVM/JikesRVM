/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Represent a value that is a parameter
 *
 * @author Dave Grove
 */
class OPT_ValueGraphParamLabel {
  int paramNum;
  
  OPT_ValueGraphParamLabel(int pn) {
    paramNum = pn;
  }

  public String toString() {
    return "formal"+paramNum;
  }
}
