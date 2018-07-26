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

import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_divide;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_floor;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_int_modular_congruence;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_int_number;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_leq;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_number;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_term_ite;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_make_times;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

class Mathsat5IntegerFormulaManager
    extends Mathsat5NumeralFormulaManager<IntegerFormula, IntegerFormula>
    implements IntegerFormulaManager {

  Mathsat5IntegerFormulaManager(
      Mathsat5FormulaCreator pCreator, NonLinearArithmetic pNonLinearArithmetic) {
    super(pCreator, pNonLinearArithmetic);
  }

  @Override
  protected long getNumeralType() {
    return getFormulaCreator().getIntegerType();
  }

  @Override
  protected Long makeNumberImpl(double pNumber) {
    return makeNumberImpl((long) pNumber);
  }

  @Override
  protected Long makeNumberImpl(BigDecimal pNumber) {
    return decimalAsInteger(pNumber);
  }

  private long ceil(long t) {
    final long minusOne = msat_make_number(mathsatEnv, "-1");
    return msat_make_times(
        mathsatEnv,
        msat_make_floor(mathsatEnv, msat_make_times(mathsatEnv, t, minusOne)),
        minusOne);
  }

  @Override
  public Long divide(Long pNumber1, Long pNumber2) {
    // Follow SMTLib rounding definition (http://smtlib.cs.uiowa.edu/theories-Ints.shtml):
    // (t2 <= 0) ? ceil(t1/t2) : floor(t1/t2)
    // (t2 <= 0) ? -floor(-(t1/t2)) : floor(t1/2)
    // According to Alberto Griggio, it is not worth hand-optimizing this,
    // MathSAT will simplify this to something like floor(1/t2 * t1) for linear queries anyway.
    final long div = msat_make_divide(mathsatEnv, pNumber1, pNumber2);
    return msat_make_term_ite(
        mathsatEnv,
        msat_make_leq(mathsatEnv, pNumber2, msat_make_int_number(mathsatEnv, 0)),
        ceil(div),
        msat_make_floor(mathsatEnv, div));
  }

  @Override
  protected Long modularCongruence(Long pNumber1, Long pNumber2, BigInteger pModulo) {
    return modularCongruence0(pNumber1, pNumber2, pModulo.toString());
  }

  @Override
  protected Long modularCongruence(Long pNumber1, Long pNumber2, long pModulo) {
    return modularCongruence0(pNumber1, pNumber2, Long.toString(pModulo));
  }

  protected Long modularCongruence0(Long pNumber1, Long pNumber2, String pModulo) {
    return msat_make_int_modular_congruence(mathsatEnv, pModulo, pNumber1, pNumber2);
  }
}
