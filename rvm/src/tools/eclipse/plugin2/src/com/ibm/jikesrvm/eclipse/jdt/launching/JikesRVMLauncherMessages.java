/*
 * (C) Copyright IBM Corp 2001,2002
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
public class JikesRVMLauncherMessages {

  private static final String RESOURCE_BUNDLE= 
    //"org.eclipse.jdt.internal.launching.jikesrvm2.JikesRVMLauncherMessages";
    "com.ibm.jikesrvm.eclipse.jdt.launching.JikesRVMLauncherMessages";

  private static ResourceBundle resources = ResourceBundle.getBundle(RESOURCE_BUNDLE);

  public static String getString(String key) {
    try {
      return resources.getString(key);
    } catch (MissingResourceException e) {
      return "!" + key + "!";
    }
  }

  private JikesRVMLauncherMessages() {}

}
