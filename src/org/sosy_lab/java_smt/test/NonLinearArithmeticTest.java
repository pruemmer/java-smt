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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.NumeralFormula;
import org.sosy_lab.java_smt.api.NumeralFormulaManager;
import org.sosy_lab.java_smt.api.SolverException;
import org.sosy_lab.java_smt.basicimpl.AbstractNumeralFormulaManager.NonLinearArithmetic;

@RunWith(Parameterized.class)
public class NonLinearArithmeticTest<T extends NumeralFormula> extends SolverBasedTest0 {

  // SMTInterpol and MathSAT5 do not fully support non-linear arithmetic
  // (though both support some parts)
  private static final ImmutableSet<Solvers> SOLVER_WITHOUT_NONLINEAR_ARITHMETIC =
      ImmutableSet.of(Solvers.SMTINTERPOL, Solvers.MATHSAT5);

  @Parameters(name = "{0} {1} {2}")
  public static Iterable<Object[]> getAllSolvers() {
    return Lists.cartesianProduct(
            Arrays.asList(Solvers.values()),
            ImmutableList.of(FormulaType.IntegerType, FormulaType.RationalType),
            Arrays.asList(NonLinearArithmetic.values()))
        .stream()
        .map(e -> e.toArray())
        .collect(toImmutableList());
  }

  @Parameter(0)
  public Solvers solver;

  @Override
  protected Solvers solverToUse() {
    return solver;
  }

  @Parameter(1)
  public FormulaType<?> formulaType;

  private NumeralFormulaManager<T, T> nmgr;

  @SuppressWarnings("unchecked")
  @Before
  public void chooseNumeralFormulaManager() {
    if (formulaType.isIntegerType()) {
      nmgr = (NumeralFormulaManager<T, T>) imgr;
    } else if (formulaType.isRationalType()) {
      requireRationals();
      nmgr = (NumeralFormulaManager<T, T>) rmgr;
    } else {
      throw new AssertionError();
    }
  }

  @Parameter(2)
  public NonLinearArithmetic nonLinearArithmetic;

  @Override
  protected ConfigurationBuilder createTestConfigBuilder() {
    return super.createTestConfigBuilder()
        .setOption("solver.nonLinearArithmetic", nonLinearArithmetic.name());
  }

  private T handleExpectedException(Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (UnsupportedOperationException e) {
      if (nonLinearArithmetic == NonLinearArithmetic.USE
          && SOLVER_WITHOUT_NONLINEAR_ARITHMETIC.contains(solver)) {
        throw new AssumptionViolatedException(
            "Expected UnsupportedOperationException was thrown correctly");
      }
      throw e;
    }
  }

  private void assertExpectedUnsatifiabilityForNonLinearArithmetic(BooleanFormula f)
      throws SolverException, InterruptedException {
    if (nonLinearArithmetic == NonLinearArithmetic.USE
        || (nonLinearArithmetic == NonLinearArithmetic.APPROXIMATE_FALLBACK
            && !SOLVER_WITHOUT_NONLINEAR_ARITHMETIC.contains(solver))) {
      assertThatFormula(f).isUnsatisfiable();
    } else {
      assertThatFormula(f).isSatisfiable();
    }
  }

  @Test
  public void testLinearMultiplication() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.multiply(nmgr.makeNumber(2), nmgr.makeNumber(3))),
            nmgr.equal(nmgr.makeNumber(2 * 3 * 5), nmgr.multiply(a, nmgr.makeNumber(5))),
            nmgr.equal(nmgr.makeNumber(2 * 3 * 5), nmgr.multiply(nmgr.makeNumber(5), a)));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testLinearMultiplicationUnsatisfiable() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.multiply(nmgr.makeNumber(2), nmgr.makeNumber(3))),
            bmgr.xor(
                nmgr.equal(nmgr.makeNumber(2 * 3 * 5), nmgr.multiply(a, nmgr.makeNumber(5))),
                nmgr.equal(nmgr.makeNumber(2 * 3 * 5), nmgr.multiply(nmgr.makeNumber(5), a))));

    assertThatFormula(f).isUnsatisfiable();
  }

  @Test
  public void testMultiplicationOfVariables() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");
    T b = nmgr.makeVariable("b");
    T c = nmgr.makeVariable("c");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(c, handleExpectedException(() -> nmgr.multiply(a, b))),
            nmgr.equal(c, nmgr.makeNumber(2 * 3)));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testMultiplicationOfVariablesUnsatisfiable()
      throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");
    T b = nmgr.makeVariable("b");
    T c = nmgr.makeVariable("c");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(handleExpectedException(() -> nmgr.multiply(a, b)), c),
            nmgr.equal(a, nmgr.makeNumber(3)),
            nmgr.equal(b, nmgr.makeNumber(5)),
            bmgr.not(nmgr.equal(c, nmgr.makeNumber(15))));

    if (solver == Solvers.MATHSAT5
        && nonLinearArithmetic != NonLinearArithmetic.APPROXIMATE_ALWAYS) {
      // MathSAT supports non-linear multiplication
      assertThatFormula(f).isUnsatisfiable();

    } else {
      assertExpectedUnsatifiabilityForNonLinearArithmetic(f);
    }
  }

  @Test
  public void testDivisionByConstant() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(nmgr.makeNumber(2 * 3), a),
            nmgr.equal(nmgr.divide(a, nmgr.makeNumber(3)), nmgr.makeNumber(2)),
            nmgr.equal(nmgr.divide(a, nmgr.makeNumber(2)), nmgr.makeNumber(3)));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testDivisionByConstantUnsatisfiable() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(2 * 3)),
            bmgr.xor(
                nmgr.equal(nmgr.divide(a, nmgr.makeNumber(3)), nmgr.makeNumber(2)),
                nmgr.equal(nmgr.divide(a, nmgr.makeNumber(2)), nmgr.makeNumber(3))));

    if (formulaType.equals(FormulaType.IntegerType)
        && nonLinearArithmetic == NonLinearArithmetic.APPROXIMATE_ALWAYS) {
      // Integer division is always non-linear due to rounding rules
      assertThatFormula(f).isSatisfiable();

    } else {
      assertThatFormula(f).isUnsatisfiable();
    }
  }

  @Test
  public void testDivision() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(2)),
            nmgr.equal(
                nmgr.makeNumber(3),
                handleExpectedException(() -> nmgr.divide(nmgr.makeNumber(2 * 3), a))));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testDivisionUnsatisfiable() throws SolverException, InterruptedException {
    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            bmgr.not(nmgr.equal(a, nmgr.makeNumber(2))),
            bmgr.not(nmgr.equal(a, nmgr.makeNumber(0))), // some solver produce model a=0 otherwise
            nmgr.equal(
                nmgr.makeNumber(3),
                handleExpectedException(() -> nmgr.divide(nmgr.makeNumber(2 * 3), a))));

    if (solver == Solvers.MATHSAT5
        && nonLinearArithmetic != NonLinearArithmetic.APPROXIMATE_ALWAYS) {
      // MathSAT supports non-linear multiplication
      assertThatFormula(f).isUnsatisfiable();

    } else {
      assertExpectedUnsatifiabilityForNonLinearArithmetic(f);
    }
  }

  @Test
  public void testModuloConstant() throws SolverException, InterruptedException {
    assume()
        .withMessage("modulo is only defined for ints")
        .that(formulaType)
        .isEqualTo(FormulaType.IntegerType);

    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(3)),
            nmgr.equal(
                nmgr.makeNumber(1),
                handleExpectedException(() -> nmgr.modulo(a, nmgr.makeNumber(2)))));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testModuloConstantUnsatisfiable() throws SolverException, InterruptedException {
    assume()
        .withMessage("modulo is only defined for ints")
        .that(formulaType)
        .isEqualTo(FormulaType.IntegerType);

    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(5)),
            nmgr.equal(
                nmgr.makeNumber(1),
                handleExpectedException(() -> nmgr.modulo(a, nmgr.makeNumber(3)))));

    if (solver == Solvers.SMTINTERPOL
        && nonLinearArithmetic == NonLinearArithmetic.APPROXIMATE_FALLBACK) {
      // SMTInterpol supports modulo with constants
      assertThatFormula(f).isUnsatisfiable();

    } else {
      assertExpectedUnsatifiabilityForNonLinearArithmetic(f);
    }
  }

  @Test
  public void testModulo() throws SolverException, InterruptedException {
    assume()
        .withMessage("modulo is only defined for ints")
        .that(formulaType)
        .isEqualTo(FormulaType.IntegerType);

    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(2)),
            nmgr.equal(
                nmgr.makeNumber(1),
                handleExpectedException(() -> nmgr.modulo(nmgr.makeNumber(3), a))));

    assertThatFormula(f).isSatisfiable();
  }

  @Test
  public void testModuloUnsatisfiable() throws SolverException, InterruptedException {
    assume()
        .withMessage("modulo is only defined for ints")
        .that(formulaType)
        .isEqualTo(FormulaType.IntegerType);

    T a = nmgr.makeVariable("a");

    BooleanFormula f =
        bmgr.and(
            nmgr.equal(a, nmgr.makeNumber(3)),
            nmgr.equal(
                nmgr.makeNumber(1),
                handleExpectedException(() -> nmgr.modulo(nmgr.makeNumber(5), a))));

    assertExpectedUnsatifiabilityForNonLinearArithmetic(f);
  }
}
