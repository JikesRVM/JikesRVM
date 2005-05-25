/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * GenCopy constants.
 * 
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenCopyConstants extends GenerationalConstants implements Uninterruptible {
	public static boolean COPY_MATURE() throws InlinePragma { return true; }	
}
