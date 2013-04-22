/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.ast;

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * An MMTk/harness option defined in a script
 */
public class OptionDef extends AbstractAST {
  /** The option being set */
  private final String option;
  /** The original symbol */
  private final List<String> values;

  /**
   * Create a new assignment of the given option to the specified value(s).
   */
  public OptionDef(Token t, String option, List<String> values) {
    super(t);
    this.option = option;
    this.values = values;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  /**
   * @return the option being defined
   */
  public String getOption() {
    return option;
  }

  /**
   * @return The values of the specified option
   */
  public List<String> getValues() {
    return values;
  }

  /**
   * @return The option as though it was specified on the command line
   */
  public String toCommandLineArg() {
    StringBuilder buf = new StringBuilder(getOption());
    if (values.size() > 0) {
      buf.append("=");
      buf.append(values.get(0));
      for (int i=1; i < values.size(); i++) {
        buf.append(",");
        buf.append(values.get(i));
      }
    }
    return buf.toString();
  }
}
