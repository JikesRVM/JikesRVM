/*
 * (C) Copyright IBM Corp 2001,2002
 *
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */

package org.eclipse.jdt.internal.launching.jikesrvm;

/**
 * @author Jeffrey Palm
 */
public interface BuildInfo {
  public final static String         TIME = "@long.date@";
  public final static java.util.Date DATE = new java.util.Date(TIME);
}
