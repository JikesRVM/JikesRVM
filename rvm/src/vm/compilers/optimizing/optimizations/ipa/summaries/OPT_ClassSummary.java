/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Hold semantic information about a class that is not defined in
 * VM_Class.
 * 
 * @author Stephen Fink
 */
public class OPT_ClassSummary {

  /**
   * @param v lightweith class corresponding to this OPT_Class
   */
  OPT_ClassSummary (VM_Class v) {
    vmClass = v;
  }
  /**
   * class this object tracks
   */
  VM_Class vmClass; 
}

