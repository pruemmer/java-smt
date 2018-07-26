/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.sosy_lab.java_smt.test;

import static com.google.common.collect.FluentIterable.from;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.sosy_lab.java_smt.api.FormulaType.BooleanType;
import static org.sosy_lab.java_smt.api.FormulaType.IntegerType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.FloatingPointFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FunctionDeclaration;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.QuantifiedFormulaManager.Quantifier;
import org.sosy_lab.java_smt.api.SolverException;
import org.sosy_lab.java_smt.api.visitors.DefaultBooleanFormulaVisitor;
import org.sosy_lab.java_smt.api.visitors.DefaultFormulaVisitor;
import org.sosy_lab.java_smt.basicimpl.AbstractFormulaManager;

@RunWith(Parameterized.class)
@SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE")
public class VariableNamesTest extends SolverBasedTest0 {

  private static final ImmutableList<String> NAMES =
      ImmutableList.of(
          "javasmt",
          "sosylab",
          "test",
          "foo",
          "bar",
          "baz",
          "declare",
          "exit",
          "(exit)",
          "!=",
          "~",
          ",",
          ".",
          ":",
          " ",
          "  ",
          "(",
          ")",
          "[",
          "]",
          "{",
          "}",
          "[]",
          "\"",
          "\"\"",
          "\"\"\"",
          "'",
          "''",
          "'''",
          "\n",
          "\t",
          "\u0000",
          "\u0001",
          "\u1234",
          "\u2e80",
          " this is a quoted symbol ",
          " so is \n  this one ",
          " \" can occur too ",
          " af klj ^*0 asfe2 (&*)&(#^ $ > > >?\" ’]]984");

  private static final ImmutableSet<String> FURTHER_SMTLIB2_KEYWORDS =
      ImmutableSet.of(
          "let",
          "forall",
          "exists",
          "match",
          "Bool",
          "continued-execution",
          "error",
          "immediate-exit",
          "incomplete",
          "logic",
          "memout",
          "sat",
          "success",
          "theory",
          "unknown",
          "unsupported",
          "unsat",
          "_",
          "as",
          "BINARY",
          "DECIMAL",
          "exists",
          "HEXADECIMAL",
          "forall",
          "let",
          "match",
          "NUMERAL",
          "par",
          "STRING",
          "assert",
          "check-sat",
          "check-sat-assuming",
          "declare-const",
          "declare-datatype",
          "declare-datatypes",
          "declare-fun",
          "declare-sort",
          "define-fun",
          "define-fun-rec",
          "define-sort",
          "echo",
          "exit",
          "get-assertions",
          "get-assignment",
          "get-info",
          "get-model",
          "get-option",
          "get-proof",
          "get-unsat-assumptions",
          "get-unsat-core",
          "get-value",
          "pop",
          "push",
          "reset",
          "reset-assertions",
          "set-info",
          "set-logic",
          "set-option");

  /**
   * Some special chars are not allowed to appear in symbol names. See {@link
   * AbstractFormulaManager#DISALLOWED_CHARACTERS}.
   */
  @SuppressWarnings("javadoc")
  private static final ImmutableSet<String> UNSUPPORTED_NAMES =
      ImmutableSet.of(
          "|",
          "||",
          "|||",
          "|test",
          "|test|",
          "t|e|s|t",
          "\\",
          "\\s",
          "\\|\\|",
          "\\",
          "| this is a quoted symbol |",
          "| so is \n  this one |",
          "| \" can occur too |",
          "| af klj ^*0 asfe2 (&*)&(#^ $ > > >?\" ’]]984|");

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0} with varname {1}")
  public static List<Object[]> getAllCombinations() {
    List<String> allNames =
        from(NAMES)
            .append(AbstractFormulaManager.BASIC_OPERATORS)
            .append(AbstractFormulaManager.SMTLIB2_KEYWORDS)
            .append(FURTHER_SMTLIB2_KEYWORDS)
            .append(UNSUPPORTED_NAMES)
            .toList();
    return Lists.transform(
        Lists.cartesianProduct(Arrays.asList(Solvers.values()), allNames), List::toArray);
  }

  @Parameter(0)
  public Solvers solver;

  @Parameter(1)
  public String varname;

  @Override
  protected Solvers solverToUse() {
    return solver;
  }

  @CanIgnoreReturnValue
  private <T extends Formula> T createVariableWith(Function<String, T> creator) {
    final T result;
    if (!mgr.isValidName(varname)) {
      try {
        result = creator.apply(varname);
        fail();
      } catch (IllegalArgumentException e) {
        throw new AssumptionViolatedException("unsupported variable name", e);
      }
    } else {
      result = creator.apply(varname);
    }
    return result;
  }

  @Test
  public void testBoolVariable() {
    createVariableWith(bmgr::makeVariable);
  }

  private <T extends Formula> void testName0(
      Function<String, T> creator, BiFunction<T, T, BooleanFormula> eq, boolean isUF)
      throws SolverException, InterruptedException {

    // create a variable
    T var = createVariableWith(creator);

    // check whether it exists with the given name
    Map<String, Formula> map = mgr.extractVariables(var);
    if (isUF) {
      assertThat(map).isEmpty();
      map = mgr.extractVariablesAndUFs(var);
    }
    assertThat(map).hasSize(1);
    assertThat(map).containsEntry(varname, var);

    // check whether we can create the same variable again
    T var2 = createVariableWith(creator);
    // for simple formulas, we can expect a direct equality
    // (for complex formulas this is not satisfied)
    assertThat(var2).isEqualTo(var);
    assertThat(var2.toString()).isEqualTo(var.toString());
    assertThatFormula(eq.apply(var, var2)).isSatisfiable();
    if (var instanceof FloatingPointFormula) {
      // special case: NaN != NaN
      assertThatFormula(bmgr.not(eq.apply(var, var2))).isSatisfiable();
    } else {
      assertThatFormula(bmgr.not(eq.apply(var, var2))).isUnsatisfiable();
    }

    // check whether SMTLIB2-dump is possible
    @SuppressWarnings("unused")
    String dump = mgr.dumpFormula(eq.apply(var, var)).toString();

    // try to create a new (!) variable with a different name, the escaped previous name.
    varname = "|" + varname + "|";
    @SuppressWarnings("unused")
    T var3 = createVariableWith(creator);
    fail();
  }

  @Test
  public void testNameBool() throws SolverException, InterruptedException {
    testName0(bmgr::makeVariable, bmgr::equivalence, false);
  }

  @Test
  public void testNameInt() throws SolverException, InterruptedException {
    testName0(imgr::makeVariable, imgr::equal, false);
  }

  @Test
  public void testNameRat() throws SolverException, InterruptedException {
    requireRationals();
    testName0(rmgr::makeVariable, rmgr::equal, false);
  }

  @Test
  public void testNameBV() throws SolverException, InterruptedException {
    requireBitvectors();
    testName0(s -> bvmgr.makeVariable(4, s), bvmgr::equal, false);
  }

  @Test
  public void testNameFloat() throws SolverException, InterruptedException {
    requireFloats();
    testName0(
        s -> fpmgr.makeVariable(s, FormulaType.getSinglePrecisionFloatingPointType()),
        fpmgr::equalWithFPSemantics,
        false);
  }

  @Test
  public void testNameArray() throws SolverException, InterruptedException {
    requireArrays();
    testName0(s -> amgr.makeArray(s, IntegerType, IntegerType), amgr::equivalence, false);
  }

  @Test
  public void testNameUF1Bool() throws SolverException, InterruptedException {
    testName0(
        s -> fmgr.declareAndCallUF(s, BooleanType, imgr.makeNumber(0)), bmgr::equivalence, true);
  }

  @Test
  public void testNameUF1Int() throws SolverException, InterruptedException {
    testName0(s -> fmgr.declareAndCallUF(s, IntegerType, imgr.makeNumber(0)), imgr::equal, true);
  }

  @Test
  public void testNameUF2Bool() throws SolverException, InterruptedException {
    IntegerFormula zero = imgr.makeNumber(0);
    testName0(s -> fmgr.declareAndCallUF(s, BooleanType, zero, zero), bmgr::equivalence, true);
  }

  @Test
  public void testNameUF2Int() throws SolverException, InterruptedException {
    IntegerFormula zero = imgr.makeNumber(0);
    testName0(s -> fmgr.declareAndCallUF(s, IntegerType, zero, zero), imgr::equal, true);
  }

  @Test
  public void testNameExists() {
    requireQuantifiers();

    IntegerFormula var = createVariableWith(imgr::makeVariable);
    IntegerFormula zero = imgr.makeNumber(0);
    BooleanFormula exists = qmgr.exists(var, imgr.equal(var, zero));

    // check whether it exists with the given name
    assertThat(mgr.extractVariablesAndUFs(exists)).isEmpty();

    mgr.visit(
        exists,
        new DefaultFormulaVisitor<Void>() {

          @Override
          public Void visitQuantifier(
              BooleanFormula pF,
              Quantifier pQuantifier,
              List<Formula> pBoundVariables,
              BooleanFormula pBody) {
            for (Formula f : pBoundVariables) {
              Map<String, Formula> map = mgr.extractVariables(f);
              assertThat(map).hasSize(1);
              assertThat(map).containsEntry(varname, f);
            }
            return null;
          }

          @Override
          protected Void visitDefault(Formula pF) {
            return null;
          }
        });
  }

  @Test
  public void testBoolVariableNameInVisitor() {
    BooleanFormula var = createVariableWith(bmgr::makeVariable);

    bmgr.visit(
        var,
        new DefaultBooleanFormulaVisitor<Void>() {
          @Override
          protected Void visitDefault() {
            throw new AssertionError("unexpected case");
          }

          @Override
          public Void visitAtom(BooleanFormula pAtom, FunctionDeclaration<BooleanFormula> pDecl) {
            assertThat(pDecl.getName()).isEqualTo(varname);
            return null;
          }
        });
  }

  @Test
  public void testBoolVariableDump() {
    BooleanFormula var = createVariableWith(bmgr::makeVariable);
    @SuppressWarnings("unused")
    String dump = mgr.dumpFormula(var).toString();
  }

  @Test
  public void testEqBoolVariableDump() {
    BooleanFormula var = createVariableWith(bmgr::makeVariable);
    @SuppressWarnings("unused")
    String dump = mgr.dumpFormula(bmgr.equivalence(var, var)).toString();
  }

  @Test
  public void testIntVariable() {
    createVariableWith(imgr::makeVariable);
  }

  @Test
  public void testInvalidRatVariable() {
    requireRationals();
    createVariableWith(rmgr::makeVariable);
  }

  @Test
  public void testBVVariable() {
    requireBitvectors();
    createVariableWith(v -> bvmgr.makeVariable(4, v));
  }

  @Test
  public void testInvalidFloatVariable() {
    requireFloats();
    createVariableWith(
        v -> fpmgr.makeVariable(v, FormulaType.getSinglePrecisionFloatingPointType()));
  }

  @Test
  public void testArrayVariable() {
    requireArrays();
    createVariableWith(v -> amgr.makeArray(v, IntegerType, IntegerType));
  }

  @Test
  public void sameBehaviorTest() {
    if (mgr.isValidName(varname)) {
      // should pass without exception
      AbstractFormulaManager.checkVariableName(varname);
    } else {
      try {
        // should throw exception
        AbstractFormulaManager.checkVariableName(varname);
        fail();
      } catch (IllegalArgumentException e) {
      }
    }
  }
}
