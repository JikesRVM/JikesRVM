/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.opt.ir.*;
import java.util.Iterator;

/**
 * Instruction priority representation
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Scheduler
 * @author Igor Pechtchanski
 */
abstract class OPT_Priority
    implements Iterator<OPT_Instruction> {

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public abstract void reset ();
}



