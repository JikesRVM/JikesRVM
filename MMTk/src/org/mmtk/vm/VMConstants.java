/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */

package org.mmtk.vm;

/**
 * This file is a <b>stub</b> file representing all VM-specific
 * constants. This file will be shadowed by a <i>concrete</i> instance
 * of itself supplied by the client VM, populated with VM-specific
 * values.  <i>The specific values in this stub file are therefore
 * meaningless.</i><p>
 *
 * Note that these methods look as though they are constants.  This is
 * intentional.  They would be constants except that we want MMTk to
 * be Java->bytecode compiled separately, ahead of time, in a
 * VM-neutral way.  MMTk must be compiled against this stub, but if
 * these were actual constants rather than methods, then the Java
 * compiler would legally constant propagate and constant fold the
 * values in this file, thereby ignoring the real values held in the
 * concrete VM-specific file.  The constants are realized correctly at
 * class initialization time, so the performance overhead of this
 * approach is negligible (and has been measured to be insignificant).
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public interface VMConstants {
  /** @return The log base two of the size of an address */
  public static final byte LOG_BYTES_IN_ADDRESS() { return 3; }
  /** @return The log base two of the size of a word */
  public static final byte LOG_BYTES_IN_WORD() { return 3; }
  /** @return The log base two of the size of an OS page */
  public static final byte LOG_BYTES_IN_PAGE() { return 12; }
  /** @return The log base two of the minimum allocation alignment */
  public static final byte LOG_MIN_ALIGNMENT() { return 1; }
  /** @return The log base two of (MAX_ALIGNMENT/MIN_ALIGNMENT) */
  public static final byte MAX_ALIGNMENT_SHIFT() { return 1; }
  /** @return The maximum number of bytes of padding to prepend to an object */
  public static final int MAX_BYTES_PADDING() { return 1; }
}

