package org.eclipse.jdt.internal.launching.jikesrvm;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class JikesRVMLauncherMessages {

	private static final String RESOURCE_BUNDLE= 
	  "org.eclipse.jdt.internal.launching.jikesrvm.JikesRVMLauncherMessages";//$NON-NLS-1$

	private static ResourceBundle fgResourceBundle= ResourceBundle.getBundle(RESOURCE_BUNDLE);

	public static String getString(String key) {
		try {
			return fgResourceBundle.getString(key);
		} catch (MissingResourceException e) {
			return "!" + key + "!";//$NON-NLS-2$ //$NON-NLS-1$
		}
	}
	private JikesRVMLauncherMessages() {
	}
}
