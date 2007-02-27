/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 *
 * ==========
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
