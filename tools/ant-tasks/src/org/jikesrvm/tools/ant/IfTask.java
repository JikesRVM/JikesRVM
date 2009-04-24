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
package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.apache.tools.ant.taskdefs.condition.Condition;
import org.apache.tools.ant.taskdefs.condition.ConditionBase;

/**
 * The if task makes it easier to performs some tasks conditionally.
 * It contains a nested condition and associated sequential task.
 */
public class IfTask
    extends Task {
  private MacroDef macroDef;
  private ConditionSet conditions = new ConditionSet();

  public ConditionSet createConditions() { return conditions; }

  public MacroDef.NestedSequential createSequential() {
    macroDef = new MacroDef();
    macroDef.setProject(getProject());
    return macroDef.createSequential();
  }

  public void execute() {
    validate();

    if (conditions.getCondition().eval()) {
      final MacroInstance i = new MacroInstance();
      i.setProject(getProject());
      i.setOwningTarget(getOwningTarget());
      i.setMacroDef(macroDef);
      i.execute();
    }
  }

  private void validate() {
    if (!conditions.containsSingleCondition()) throw new BuildException("Must specify exactly one condition.");
    if (null == macroDef) throw new BuildException("Must specify a sequential task to execute.");
  }

  public static class ConditionSet extends ConditionBase {
    public boolean containsSingleCondition() { return 1 == super.countConditions(); }
    public Condition getCondition() { return (Condition) getConditions().nextElement(); }
  }
}
