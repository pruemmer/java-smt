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
package org.sosy_lab.java_smt.solvers.mathsat5;

import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_equal;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_int_number;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_leq;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_not;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_number;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_plus;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_times;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_term_is_number;

import java.math.BigInteger;
import org.sosy_lab.java_smt.api.NumeralFormula;
import org.sosy_lab.java_smt.basicimpl.AbstractNumeralFormulaManager;

abstract class Mathsat5NumeralFormulaManager<
        ParamFormulaType extends NumeralFormula, ResultFormulaType extends NumeralFormula>
    extends AbstractNumeralFormulaManager<
        Long, Long, Long, ParamFormulaType, ResultFormulaType, Long> {

  final long mathsatEnv;

  Mathsat5NumeralFormulaManager(
      Mathsat5FormulaCreator pCreator, NonLinearArithmetic pNonLinearArithmetic) {
    super(pCreator, pNonLinearArithmetic);
    this.mathsatEnv = pCreator.getEnv();
  }

  @Override
  protected boolean isNumeral(Long val) {
    return msat_term_is_number(mathsatEnv, val);
  }

  @Override
  public Long makeNumberImpl(long pNumber) {
    int i = (int) pNumber;
    if (i == pNumber) { // fits in an int
      return msat_make_int_number(mathsatEnv, i);
    }
    return msat_make_number(mathsatEnv, Long.toString(pNumber));
  }

  @Override
  public Long makeNumberImpl(BigInteger pI) {
    return msat_make_number(mathsatEnv, pI.toString());
  }

  @Override
  public Long makeNumberImpl(String pI) {
    return msat_make_number(mathsatEnv, pI);
  }

  protected abstract long getNumeralType();

  @Override
  public Long makeVariableImpl(String var) {
    return getFormulaCreator().makeVariable(getNumeralType(), var);
  }

  @Override
  public Long negate(Long pNumber) {
    return msat_make_times(mathsatEnv, pNumber, msat_make_number(mathsatEnv, "-1"));
  }

  @Override
  public Long add(Long pNumber1, Long pNumber2) {
    return msat_make_plus(mathsatEnv, pNumber1, pNumber2);
  }

  @Override
  public Long subtract(Long pNumber1, Long pNumber2) {
    return msat_make_plus(mathsatEnv, pNumber1, negate(pNumber2));
  }

  @Override
  public Long multiply(Long pNumber1, Long pNumber2) {
    return msat_make_times(mathsatEnv, pNumber1, pNumber2);
  }

  @Override
  public Long equal(Long pNumber1, Long pNumber2) {
    return msat_make_equal(mathsatEnv, pNumber1, pNumber2);
  }

  @Override
  public Long greaterThan(Long pNumber1, Long pNumber2) {
    return makeNot(lessOrEquals(pNumber1, pNumber2));
  }

  @Override
  public Long greaterOrEquals(Long pNumber1, Long pNumber2) {
    return lessOrEquals(pNumber2, pNumber1);
  }

  private long makeNot(long n) {
    return msat_make_not(mathsatEnv, n);
  }

  @Override
  public Long lessThan(Long pNumber1, Long pNumber2) {
    return makeNot(lessOrEquals(pNumber2, pNumber1));
  }

  @Override
  public Long lessOrEquals(Long pNumber1, Long pNumber2) {
    return msat_make_leq(mathsatEnv, pNumber1, pNumber2);
  }
}
