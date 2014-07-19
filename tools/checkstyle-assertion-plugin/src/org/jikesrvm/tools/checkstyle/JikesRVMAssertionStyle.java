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
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.DOT;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.ENUM_DEF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.EXPR;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.IDENT;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.LAND;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.LITERAL_ASSERT;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.LITERAL_FALSE;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.LITERAL_IF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.METHOD_CALL;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.PACKAGE_DEF;
import static com.puppycrawl.tools.checkstyle.api.TokenTypes.*;

import java.util.Stack;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;

/**
 * Implements checking of the Jikes RVM assertion style.<p>
 *
 * Files that are part of the MMTk test harness are not checked against
 * the assertion coding style.<p>
 *
 * The implementation manually descends the tree (as opposed to relying on the
 * visitor for the traversal) from the interesting parts in order to keep the
 * set of declared tokens small (see {@link #defaultTokens}).
 */
public class JikesRVMAssertionStyle extends Check {

  private static final String ASSERT_IS_FORBIDDEN_MSG = "The assert keyword must not be used.";
  private static final String IMPROPERLY_GUARDED_ASSERTION_MSG = "All " +
    "uses of VM._assert must be guarded with one of the following on the left " +
    "side of the guard: VM.VerifyAssertions, VM.ExtremeAssertions, " +
    "IR.SANITY_CHECK or IR.PARANOID.";
  private static final String USE_VM_NOT_REACHED_MSG = "Use VM.NOT_REACHED " +
    "instead of false when writing assertions that fail when executed.";
  private static final String STRING_CONCATENATION_FORBIDDEN_MSG = "Message for assert must be " +
    "a string literal or a variable: String concatenation is not allowed in asserts.";

  private static final String IR = "IR";
  private static final String VM = "VM";

  private static final String VERIFY_ASSERTIONS = "VerifyAssertions";
  private static final String EXTREME_ASSERTIONS = "ExtremeAssertions";
  private static final String PARANOID = "PARANOID";
  private static final String SANITY_CHECK = "SANITY_CHECK";

  private static final String ASSERT_METHOD = "_assert";

  private static final boolean DEBUG = false;

  private final StringBuilder packageNameBuilder;
  private final StringBuilder classNameBuilder;
  private int classDepth;

  private boolean isMMTkHarnessClass;

  /**
   * Tokens that we're interested in. Checkstyle will only call
   * {@link #visitToken(DetailAST)} on AST nodes that have one of those
   * types.<p>
   */
  private static final int[] defaultTokens = new int[] {PACKAGE_DEF, CLASS_DEF,
    ENUM_DEF, LITERAL_IF, LITERAL_ASSERT, METHOD_CALL};

  private final Stack<Boolean> assertionGuardsPresent;

  public JikesRVMAssertionStyle() {
    packageNameBuilder = new StringBuilder();
    classNameBuilder = new StringBuilder();
    assertionGuardsPresent = new Stack<Boolean>();
  }

  @Override
  public int[] getDefaultTokens() {
    return defaultTokens;
  }

  @Override
  public void visitToken(DetailAST ast) {
    int astType = ast.getType();

    switch (astType) {
      case PACKAGE_DEF:
        visitPackageDef(ast);
        break;
      case CLASS_DEF: // fallthrough
      case ENUM_DEF:
        visitClassOrEnumDef(ast);
        break;
      case LITERAL_IF:
        visitIf(ast);
        break;
      case METHOD_CALL:
        visitMethodCall(ast);
        break;
      default:
        break;
    }

    if (isSubjectToAssertionStyleChecks()) {
      if (astType == LITERAL_ASSERT) {
        log(ast.getLineNo(), ast.getColumnNo(), ASSERT_IS_FORBIDDEN_MSG);
      }
    }
  }

  @Override
  public void leaveToken(DetailAST ast) {
    switch (ast.getType()) {
      case LITERAL_IF:
        assertionGuardsPresent.pop();
        break;
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

  private void visitIf(DetailAST ast) {
    DetailAST expressionOfIf = ast.findFirstToken(EXPR);

    DetailAST searchRoot = expressionOfIf;

    if (searchRoot != null) {
      DetailAST and = expressionOfIf.findFirstToken(LAND);
      if (and != null) {
        searchRoot = and;
      }

      DetailAST dot = searchRoot.findFirstToken(DOT);
      if (dot != null) {
        int dotChildrenCount = dot.getChildCount();
        if (dotChildrenCount == 2) {
          DetailAST firstIdent = dot.findFirstToken(IDENT);
          DetailAST secondIdent = firstIdent.getNextSibling();
          boolean isVMAssertionVariable = false;
          boolean isIRAssertionVariable = false;
          if (secondIdent != null) {
            String secondText = secondIdent.getText();
            isVMAssertionVariable = secondText.equals(VERIFY_ASSERTIONS) ||
                secondText.equals(EXTREME_ASSERTIONS);
            isIRAssertionVariable = secondText.equals(PARANOID) ||
                secondText.equals(SANITY_CHECK);
          }
          String firstText = firstIdent.getText();
          boolean isVMAssertion = firstText.equals(VM) && isVMAssertionVariable;
          boolean isIRAssertion = firstText.equals(IR) && isIRAssertionVariable;
          if (isVMAssertion || isIRAssertion) {
            assertionGuardsPresent.push(Boolean.TRUE);
            return;
          }
        }
      }
    }

    assertionGuardsPresent.push(Boolean.FALSE);
  }

  private void visitMethodCall(DetailAST ast) {
    DetailAST firstChild = ast.getFirstChild();
    if (firstChild.getType() != DOT) {
      return;
    }

    DetailAST callerNameAST = firstChild.getFirstChild();
    if (callerNameAST.getType() != IDENT) {
      return;
    }

    String callerName = callerNameAST.getText();
    boolean callerIsVM = callerName.equals(VM);

    DetailAST calleeNameAST = callerNameAST.getNextSibling();
    if (calleeNameAST.getType() != IDENT) {
      return;
    }
    String calleeName = calleeNameAST.getText();
    boolean calleeIsAssert = calleeName.equals(ASSERT_METHOD);

    if (calleeIsAssert && callerIsVM && !assertionGuardsPresent.contains(Boolean.TRUE)) {
      log(ast.getLineNo(), ast.getColumnNo(), IMPROPERLY_GUARDED_ASSERTION_MSG);
    }

    if (callerIsVM && calleeIsAssert) {
      DetailAST parameterList = firstChild.getNextSibling();
      DetailAST firstArgumentToVMAssert = parameterList.findFirstToken(EXPR);
      if (firstArgumentToVMAssert == null) {
        log(ast.getLineNo(), ast.getColumnNo(), "null!");
        return;
      }
      DetailAST literalFalse = firstArgumentToVMAssert.findFirstToken(LITERAL_FALSE);
      if (literalFalse != null) {
        log(ast.getLineNo(), ast.getColumnNo(), USE_VM_NOT_REACHED_MSG);
      }

      DetailAST secondArgumentToVMAssert = getASTForNextParameter(firstArgumentToVMAssert);
      checkForForbiddenStringConcatenation(ast, secondArgumentToVMAssert);
      if (secondArgumentToVMAssert != null) {
        DetailAST thirdArgumentToVMAssert = getASTForNextParameter(secondArgumentToVMAssert);
        checkForForbiddenStringConcatenation(ast, thirdArgumentToVMAssert);
      }
    }
  }

  private void checkForForbiddenStringConcatenation(DetailAST callAST, DetailAST parameterAST) {
    if (parameterAST != null) {
      int type = parameterAST.getType();
      if (type == EXPR) {
        parameterAST = parameterAST.getFirstChild();
        if (parameterAST == null) {
          log(callAST.getLineNo(), callAST.getColumnNo(), "null");
          return;
        }
      }
      type = parameterAST.getType();
      if (type == STRING_LITERAL ||
          type == IDENT) {
        return;
      }
      log(callAST.getLineNo(), callAST.getColumnNo(), STRING_CONCATENATION_FORBIDDEN_MSG);
    }
  }

  private DetailAST getASTForNextParameter(DetailAST currentParameter) {
    DetailAST newParameter = null;
    DetailAST tempAST = currentParameter.getNextSibling();
    if (tempAST != null) {
      newParameter = tempAST.getNextSibling();
    }
    return newParameter;
  }

  private boolean isSubjectToAssertionStyleChecks() {
    return !isMMTkHarnessClass;
  }

  private void visitClassOrEnumDef(DetailAST ast) {
    DetailAST classNameAST = ast.findFirstToken(IDENT);
    String className = classNameAST.getText();
    if (classDepth > 0) {
      classNameBuilder.append("$");
    }
    classNameBuilder.append(className);
    classDepth++;

    checkIfClassIsExcluded();
  }

  private void visitPackageDef(DetailAST ast) {
    isMMTkHarnessClass = false;
    packageNameBuilder.setLength(0);

    DetailAST startForFullIdent = ast.getLastChild().getPreviousSibling();
    FullIdent fullIdent = FullIdent.createFullIdent(startForFullIdent);
    packageNameBuilder.append(fullIdent.getText());
  }

  private void checkIfClassIsExcluded() {
    String packageName = packageNameBuilder.toString();
    isMMTkHarnessClass = packageName.endsWith("harness") || packageName.contains(".harness.");

    if (DEBUG) {
      printIfClassBelongsToMMTkHarness(packageName);
    }
  }

  private void printIfClassBelongsToMMTkHarness(String packageName) {
    String state = isMMTkHarnessClass ? "belongs to" : "does NOT belong to";
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
    sb.append(" the MMTk test harness. ");
    if (isMMTkHarnessClass) {
      sb.append("It will be ignored!");
    }
    System.out.println(sb.toString());
  }

}
