/*
 * (C) Copyright IBM Corp 2003
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */
package com.ibm.jikesrvm.eclipse.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Steven Augart
 * based on work by Jeffrey Palm
 */
public class Messages {
  public final static Messages pm = new Messages();

  public static String get(String key) {
    try {
      return resources.getString(key);
    } catch (MissingResourceException e) {
      return "! " + key + " !";
    }
  }

  private static final String BUNDLE
    = "com.ibm.jikesrvm.eclipse.ui.Messages";

  // XXX Not PropertyResourceBundle???  NO!  Wow!
  private static ResourceBundle resources
    = ResourceBundle.getBundle(BUNDLE);

  private Messages() {}
}
