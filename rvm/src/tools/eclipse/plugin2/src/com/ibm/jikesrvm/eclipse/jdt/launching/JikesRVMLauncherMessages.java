/*
 * ===========================================================================
 * IBM Confidential
 * Software Group 
 *
 * Eclipse/Jikes
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * The source code for this program is not published or otherwise divested of
 * its trade secrets, irrespective of what has been deposited with the U.S.
 * Copyright office.
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 */
//package org.eclipse.jdt.internal.launching.jikesrvm2;
package com.ibm.jikesrvm.eclipse.jdt.launching;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

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
