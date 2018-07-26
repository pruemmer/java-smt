/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static org.sosy_lab.java_smt.api.FormulaType.BooleanType;
import static org.sosy_lab.java_smt.api.FormulaType.IntegerType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FunctionDeclaration;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.SolverException;

@RunWith(Parameterized.class)
public class FormulaManagerTest extends SolverBasedTest0 {

  @Parameters(name = "{0}")
  public static Object[] getAllSolvers() {
    return Solvers.values();
  }

  @Parameter(0)
  public Solvers solver;

  @Override
  protected Solvers solverToUse() {
    return solver;
  }

  @Test
  public void testEmptySubstitution() throws SolverException, InterruptedException {
    assume().withMessage("Princess fails").that(solver).isNotEqualTo(Solvers.PRINCESS);

    IntegerFormula variable1 = imgr.makeVariable("variable1");
    IntegerFormula variable2 = imgr.makeVariable("variable2");
    IntegerFormula variable3 = imgr.makeVariable("variable3");
    IntegerFormula variable4 = imgr.makeVariable("variable4");

    FunctionDeclaration<BooleanFormula> uf2Decl =
        fmgr.declareUF("uf", BooleanType, IntegerType, IntegerType);
    BooleanFormula f1 = fmgr.callUF(uf2Decl, variable1, variable3);
    BooleanFormula f2 = fmgr.callUF(uf2Decl, variable2, variable4);
    BooleanFormula input = bmgr.xor(f1, f2);

    BooleanFormula out = mgr.substitute(input, ImmutableMap.of());
    assertThatFormula(out).isEquivalentTo(input);
  }

  @Test
  public void testNoSubstitution() throws SolverException, InterruptedException {
    assume().withMessage("Princess fails").that(solver).isNotEqualTo(Solvers.PRINCESS);

    IntegerFormula variable1 = imgr.makeVariable("variable1");
    IntegerFormula variable2 = imgr.makeVariable("variable2");
    IntegerFormula variable3 = imgr.makeVariable("variable3");
    IntegerFormula variable4 = imgr.makeVariable("variable4");

    FunctionDeclaration<BooleanFormula> uf2Decl =
        fmgr.declareUF("uf", BooleanType, IntegerType, IntegerType);
    BooleanFormula f1 = fmgr.callUF(uf2Decl, variable1, variable3);
    BooleanFormula f2 = fmgr.callUF(uf2Decl, variable2, variable4);
    BooleanFormula input = bmgr.xor(f1, f2);

    Map<BooleanFormula, BooleanFormula> substitution =
        ImmutableMap.of(
            bmgr.makeVariable("a"), bmgr.makeVariable("a1"),
            bmgr.makeVariable("b"), bmgr.makeVariable("b1"),
            bmgr.and(bmgr.makeVariable("c"), bmgr.makeVariable("d")), bmgr.makeVariable("e"));

    BooleanFormula out = mgr.substitute(input, substitution);
    assertThatFormula(out).isEquivalentTo(input);
  }

  @Test
  public void testSubstitution() throws SolverException, InterruptedException {
    BooleanFormula input =
        bmgr.or(
            bmgr.and(bmgr.makeVariable("a"), bmgr.makeVariable("b")),
            bmgr.and(bmgr.makeVariable("c"), bmgr.makeVariable("d")));
    BooleanFormula out =
        mgr.substitute(
            input,
            ImmutableMap.of(
                bmgr.makeVariable("a"), bmgr.makeVariable("a1"),
                bmgr.makeVariable("b"), bmgr.makeVariable("b1"),
                bmgr.and(bmgr.makeVariable("c"), bmgr.makeVariable("d")), bmgr.makeVariable("e")));
    assertThatFormula(out)
        .isEquivalentTo(
            bmgr.or(
                bmgr.and(bmgr.makeVariable("a1"), bmgr.makeVariable("b1")),
                bmgr.makeVariable("e")));
  }

  @Test
  public void testSubstitutionTwice() throws SolverException, InterruptedException {
    BooleanFormula input =
        bmgr.or(
            bmgr.and(bmgr.makeVariable("a"), bmgr.makeVariable("b")),
            bmgr.and(bmgr.makeVariable("c"), bmgr.makeVariable("d")));
    ImmutableMap<BooleanFormula, BooleanFormula> substitution =
        ImmutableMap.of(
            bmgr.makeVariable("a"), bmgr.makeVariable("a1"),
            bmgr.makeVariable("b"), bmgr.makeVariable("b1"),
            bmgr.and(bmgr.makeVariable("c"), bmgr.makeVariable("d")), bmgr.makeVariable("e"));
    BooleanFormula out = mgr.substitute(input, substitution);
    assertThatFormula(out)
        .isEquivalentTo(
            bmgr.or(
                bmgr.and(bmgr.makeVariable("a1"), bmgr.makeVariable("b1")),
                bmgr.makeVariable("e")));

    BooleanFormula out2 = mgr.substitute(out, substitution);
    assertThatFormula(out2).isEquivalentTo(out);
  }

  @Test
  public void formulaEqualsAndHashCode() {
    FunctionDeclaration<IntegerFormula> f_b =
        fmgr.declareUF("f_b", FormulaType.IntegerType, FormulaType.IntegerType);

    new EqualsTester()
        .addEqualityGroup(bmgr.makeBoolean(true))
        .addEqualityGroup(bmgr.makeBoolean(false))
        .addEqualityGroup(bmgr.makeVariable("bool_a"))
        .addEqualityGroup(imgr.makeVariable("int_a"))

        // Way of creating numbers should not make a difference.
        .addEqualityGroup(
            imgr.makeNumber(0.0),
            imgr.makeNumber(0L),
            imgr.makeNumber(BigInteger.ZERO),
            imgr.makeNumber(BigDecimal.ZERO),
            imgr.makeNumber("0"))
        .addEqualityGroup(
            imgr.makeNumber(1.0),
            imgr.makeNumber(1L),
            imgr.makeNumber(BigInteger.ONE),
            imgr.makeNumber(BigDecimal.ONE),
            imgr.makeNumber("1"))

        // The same formula when created twice should compare equal.
        .addEqualityGroup(bmgr.makeVariable("bool_b"), bmgr.makeVariable("bool_b"))
        .addEqualityGroup(
            bmgr.and(bmgr.makeVariable("bool_a"), bmgr.makeVariable("bool_b")),
            bmgr.and(bmgr.makeVariable("bool_a"), bmgr.makeVariable("bool_b")))
        .addEqualityGroup(
            imgr.equal(imgr.makeNumber(0), imgr.makeVariable("int_a")),
            imgr.equal(imgr.makeNumber(0), imgr.makeVariable("int_a")))

        // UninterpretedFunctionDeclarations should not compare equal to Formulas,
        // but declaring one twice needs to return the same UIF.
        .addEqualityGroup(
            fmgr.declareUF("f_a", FormulaType.IntegerType, FormulaType.IntegerType),
            fmgr.declareUF("f_a", FormulaType.IntegerType, FormulaType.IntegerType))
        .addEqualityGroup(f_b)
        .addEqualityGroup(fmgr.callUF(f_b, imgr.makeNumber(0)))
        .addEqualityGroup(
            fmgr.callUF(f_b, imgr.makeNumber(1)), fmgr.callUF(f_b, imgr.makeNumber(1)))
        .testEquals();
  }

  @Test
  public void variableNameExtractorTest() {
    BooleanFormula constr =
        bmgr.or(
            imgr.equal(
                imgr.subtract(
                    imgr.add(imgr.makeVariable("x"), imgr.makeVariable("z")), imgr.makeNumber(10)),
                imgr.makeVariable("y")),
            imgr.equal(imgr.makeVariable("xx"), imgr.makeVariable("zz")));
    assertThat(mgr.extractVariables(constr).keySet()).containsExactly("x", "y", "z", "xx", "zz");
    assertThat(mgr.extractVariablesAndUFs(constr)).isEqualTo(mgr.extractVariables(constr));
  }

  @Test
  public void ufNameExtractorTest() {
    BooleanFormula constraint =
        imgr.equal(
            fmgr.declareAndCallUF(
                "uf1", FormulaType.IntegerType, ImmutableList.of(imgr.makeVariable("x"))),
            fmgr.declareAndCallUF(
                "uf2", FormulaType.IntegerType, ImmutableList.of(imgr.makeVariable("y"))));

    assertThat(mgr.extractVariablesAndUFs(constraint).keySet())
        .containsExactly("uf1", "uf2", "x", "y");

    assertThat(mgr.extractVariables(constraint).keySet()).containsExactly("x", "y");
  }
}
