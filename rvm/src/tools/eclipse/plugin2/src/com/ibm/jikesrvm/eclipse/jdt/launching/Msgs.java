/*
 * (C) Copyright IBM Corp 2001,2002, 2003
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */
package com.ibm.jikesrvm.eclipse.jdt.launching;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Jeffrey Palm
 */
public class Msgs {

  private static final String RESOURCE_BUNDLE= 
    //"org.eclipse.jdt.internal.launching.jikesrvm2.Msgs";
    "com.ibm.jikesrvm.eclipse.jdt.launching.Msgs";

    // XXX Not PropertyResourceBundle???
  private static ResourceBundle resources = ResourceBundle.getBundle(RESOURCE_BUNDLE);
  private static ResourceBundle r = resources;

  public static String getString(String key) {
    try {
      return r.getString(key);
    } catch (MissingResourceException e) {
      return "!" + key + "!";
    }
  }

  public static String getInternalErr(String txt) {
    return r.getString("internalError") + ": " + txt;
  }

  public final static Msgs m = new Msgs();

  private Msgs() {}

}
