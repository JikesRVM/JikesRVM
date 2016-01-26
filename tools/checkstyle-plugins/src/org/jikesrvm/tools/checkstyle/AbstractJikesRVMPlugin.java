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
package org.jikesrvm.tools.checkstyle;

import static com.puppycrawl.tools.checkstyle.api.TokenTypes.IDENT;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;

/**
 * Super class for Jikes RVM checkstyle plugins.<p>
 *
 * This class implements a skeleton for the tracking of class and package names,
 * including inner classes. This is needed to provide good debug and error messages.<p>
 *
 * Note: It is the subclasses' responsibility to declare their interest in the required
 * tokens ({@code CLASS_DEF}, {@code ENUM_DEF}, {@code IDENT} and {@code PACKGE_DEF}
 * at the time of this writing).
 */
public abstract class AbstractJikesRVMPlugin extends Check {

  protected static final boolean PRINT_CLASS_NAME = false;

  protected final StringBuilder packageNameBuilder;
  protected final StringBuilder classNameBuilder;

  /** depth of the current class: 0 is top-level, 1 is inner class, ... */
  protected int classDepth;

  protected AbstractJikesRVMPlugin() {
    packageNameBuilder = new StringBuilder();
    classNameBuilder = new StringBuilder();
  }

  /**
   * Visits a class or enum definition and records
   * information about the class name.
   * <p>
   * This method will also call {@link #checkIfClassIsExcluded()}
   * to allow subclasses to take additional actions.
   */
  protected void visitClassOrEnumDef(DetailAST ast) {
    DetailAST classNameAST = ast.findFirstToken(IDENT);
    String className = classNameAST.getText();
    if (classDepth > 0) {
      classNameBuilder.append("$");
    }
    classNameBuilder.append(className);
    classDepth++;

    if (PRINT_CLASS_NAME) {
      System.out.println("Now processing " + classNameBuilder.toString());
    }

    checkIfClassIsExcluded();
  }

  /**
   * Hook for subclasses to determine whether
   * a class should be excluded from processing.
   */
  protected void checkIfClassIsExcluded() {
    // by default, do nothing
  }

}
