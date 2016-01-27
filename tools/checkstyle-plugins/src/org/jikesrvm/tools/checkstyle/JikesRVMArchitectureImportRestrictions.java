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

import static com.puppycrawl.tools.checkstyle.api.TokenTypes.CLASS_DEF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.ENUM_DEF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.PACKAGE_DEF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.STATIC_IMPORT;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.IMPORT;

import java.util.Arrays;
import java.util.HashSet;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;

/**
 * Implements checking of imports in architecture packages.<p>
 *
 * Java sources files that are part of am architecture package (e.g. *.ia32.*)
 * must not import files from another architecture package (e.g. *.ppc.*).<p>
 *
 * The set of known architetures is configurable via a property. The plugin also
 * has some basic sanity checks to ensure that the architecture information is
 * consistent.<p>
 *
 * The implementation manually descends the tree (as opposed to relying on the
 * visitor for the traversal) from the interesting parts in order to keep the
 * set of declared tokens small (see {@link #defaultTokens}).
 */
public final class JikesRVMArchitectureImportRestrictions extends AbstractJikesRVMPlugin {

  private static final String PACKAGE_DECL_NOT_REASONABLE = "Package declaration does not make sense to plugin because " +
    "it seems to contain at least 2 architectures: ";

  private static final boolean DEBUG = false;

  /** the architecture of the current package, if applicable */
  private String currentArch;
  /** is the class that's currently processed subject to checking? */
  private boolean isJikesRVMClass;

  /** the list of known architectures. required for sanity checks in {@link #init()} */
  private HashSet<String> knownArchitectures;
  /** the current target architecture. required for sanity checks in {@link #init()} */
  private String targetArchitecture;

  /**
   * Tokens that we're interested in. Checkstyle will only call
   * {@link #visitToken(DetailAST)} on AST nodes that have one of those
   * types.<p>
   */
  private static final int[] defaultTokens = new int[] {PACKAGE_DEF, IMPORT,
      STATIC_IMPORT, CLASS_DEF, ENUM_DEF};

  /**
   * Checks that the architecture properties have sane, non-empty values. Also checks
   * that the current target architecture is contained in the known architectures.
   * If it isn't, the known architectures list is outdated and the plugin might miss
   * problems, e.g. disallowed imports into packages from the new architecture.
   */
  @Override
  public void init() {
    if (knownArchitectures == null || knownArchitectures.isEmpty()) {
      throw new Error("No architectures known. Has the value for knownArchitectures " +
          "been set correctly? Current value for knownArchitectures was " +
          knownArchitectures);
    }
    if (targetArchitecture == null || targetArchitecture.trim().isEmpty()) {
      throw new Error("No target archtiecture known. Has the value for " +
          "targetArchitecture been set correctly? Current value for " +
          "knownArchitectures was " + targetArchitecture);
    }
    if (!knownArchitectures.contains(targetArchitecture)) {
      throw new Error("Target architecture " + targetArchitecture + " not contained in list " +
          "of known architectures " + knownArchitectures + "! Please check the architecture " +
          "definition in the use of the checkstyle task");
    }
  }

  public void setKnownArchitectures(String... knownArchitectures) {
    this.knownArchitectures = new HashSet<String>(Arrays.asList(knownArchitectures));
    if (DEBUG) {
      System.out.println("Known architectures are now: " + Arrays.toString(knownArchitectures));
    }
  }

  public void setTargetArchitecture(String targetArchitecture) {
    this.targetArchitecture = targetArchitecture;
    if (DEBUG) {
      System.out.println("Target architecture is now: " + targetArchitecture);
    }
  }

  @Override
  public int[] getDefaultTokens() {
    return defaultTokens;
  }

  @Override
  public void visitToken(DetailAST ast) {
    int astType = ast.getType();

    switch (astType) {
      case CLASS_DEF: // fallthrough
      case ENUM_DEF:
        visitClassOrEnumDef(ast);
        break;
      case PACKAGE_DEF:
        visitPackageDef(ast);
        break;
      case IMPORT:
        if (isSubjectToArchitectureImportRestrictions()) {
          visitImport(ast);
        }
        break;
      case STATIC_IMPORT:
        if (isSubjectToArchitectureImportRestrictions()) {
          visitStaticImport(ast);
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void leaveToken(DetailAST ast) {
    switch (ast.getType()) {
      case ENUM_DEF: // fallthrough
      case CLASS_DEF:
        classDepth--;
        int innermostClassIndex = classNameBuilder.lastIndexOf("$");
        int newLength = (innermostClassIndex > 0) ? innermostClassIndex : 0;
        classNameBuilder.setLength(newLength);
        break;
      default:
        break;
    }
  }

  private void visitStaticImport(DetailAST ast) {
    StringBuilder sb = new StringBuilder();
    sb.append(FullIdent.createFullIdent(ast).getText());
    sb.append(" ");
    sb.append(FullIdent.createFullIdentBelow(ast).getText());
    sb.append(" ");
    DetailAST packageName = ast.getFirstChild().getNextSibling();
    String importString = FullIdent.createFullIdentBelow(packageName).getText();
    sb.append(importString);
    sb.append(".");
    DetailAST importedConstant = ast.getFirstChild().getNextSibling().getLastChild(); // ???
    String importedConstantText = FullIdent.createFullIdent(importedConstant).getText();
    sb.append(importedConstantText);
    sb.append(";");
    String[] tokensToCheck = importString.split("\\.");
    checkImport(ast, tokensToCheck, sb.toString(), tokensToCheck);
  }

  private void visitImport(DetailAST ast) {
    StringBuilder sb = new StringBuilder();
    sb.append("import ");
    String importString = FullIdent.createFullIdentBelow(ast).getText();
    sb.append(importString);
    sb.append(";");
    String[] tokens = importString.split("\\.");
    String[] tokensToCheck = Arrays.copyOfRange(tokens, 0, tokens.length - 1);
    checkImport(ast, tokensToCheck, sb.toString(), tokens);
  }

  private void checkImport(DetailAST ast, String[] tokensToCheck, String importString, String[] tokens) {
    HashSet<String> seenArchs = new HashSet<String>();
    for (String s : tokensToCheck) {
      if (knownArchitectures.contains(s)) {
        seenArchs.add(s);
      }
    }

    if (DEBUG) {
      log(ast.getLineNo(), ast.getColumnNo(), "Built import string is: " + importString +
          " - processing was done based on tokens " + Arrays.toString(tokens));
      log(ast.getLineNo(), ast.getColumnNo(), "Import: saw architectures: " + seenArchs);
    }

    for (String s : seenArchs) {
      if (!s.equals(currentArch)) {
        log(ast.getLineNo(), ast.getColumnNo(), "The import \"" + importString + "\" is forbidden " +
          "because the architecture of the imported package (" + s + ") does " +
          "not match the current architecture of " + currentArch);
      }
    }
  }

  private boolean isSubjectToArchitectureImportRestrictions() {
    return currentArch != null && isJikesRVMClass;
  }

  private void visitPackageDef(DetailAST ast) {
    packageNameBuilder.setLength(0);
    DetailAST startForFullIdent = ast.getLastChild().getPreviousSibling();
    FullIdent fullIdent = FullIdent.createFullIdent(startForFullIdent);
    String packageName = fullIdent.getText();
    isJikesRVMClass = packageName.startsWith("org.jikesrvm");
    String[] tokens = packageName.split("\\.");
    HashSet<String> seenArchs = new HashSet<String>();
    int archCount = 0;
        for (String s : tokens) {
      if (knownArchitectures.contains(s)) {
        archCount++;
        seenArchs.add(s);
      }
    }
    if (DEBUG) {
      log(ast.getLineNo(), ast.getColumnNo(), "Package name is: " + packageName + " with tokens " + Arrays.toString(tokens));
    }
    if (archCount == 0) {
      currentArch = null;
      if (DEBUG) {
        log(ast.getLineNo(), ast.getColumnNo(), "Package name has no architecture components");
      }
    } else if (archCount == 1) {
      currentArch = seenArchs.iterator().next();
      if (DEBUG) {
        log(ast.getLineNo(), ast.getColumnNo(), "Package name has exactly one architecture component: " + currentArch);
      }
    } else if (archCount >= 2) {
      log(ast.getLineNo(), ast.getColumnNo(), PACKAGE_DECL_NOT_REASONABLE + packageName);
    }

    packageNameBuilder.append(packageName);
  }

  @Override
  protected void checkIfClassIsExcluded() {
    String packageName = packageNameBuilder.toString();
    if (DEBUG) {
      printIfClassBelongsToJikesRVM(packageName);
    }
  }

  private void printIfClassBelongsToJikesRVM(String packageName) {
    String state = isJikesRVMClass ? "belongs to" : "does NOT belong to";
    StringBuilder sb = new StringBuilder();
    sb.append("Class ");
    sb.append(classNameBuilder.toString());
    if (packageNameBuilder.length() == 0) {
      sb.append(" from default package ");
    } else {
      sb.append(" from package ");
      sb.append(packageName);
      sb.append(" ");
    }
    sb.append(state);
    sb.append(" Jikes RVM core file set that is subject to architecture import restrictions. ");
    if (!isJikesRVMClass) {
      sb.append("It will be ignored!");
    }
    System.out.println(sb.toString());
  }

}
