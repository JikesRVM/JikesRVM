/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Represent a value that is a parameter
 *
 * @author Dave Grove
 */
final class OPT_ValueGraphParamLabel {
  private final int paramNum;
  
  OPT_ValueGraphParamLabel(int pn) {
    paramNum = pn;
  }

  public String toString() {
    return "formal"+paramNum;
  }
}
