/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Robin Garner, Australian National University
 */
//$Id:$
package org.jikesrvm.apt.annotations;

public @interface GenerateImplementation {
  String generatedClass();
}
