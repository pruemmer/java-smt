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
package org.sosy_lab.solver.portfolio;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.solver.api.NumeralFormulaManager;
import org.sosy_lab.solver.basicimpl.AbstractNumeralFormulaManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public abstract class PortfolioNumeralFormulaManager<
        ParamFormulaType extends NumeralFormula, ResultFormulaType extends NumeralFormula>
    extends AbstractNumeralFormulaManager<
        List<? extends Formula>, FormulaType<?>, Void, ParamFormulaType, ResultFormulaType> {

  protected final List<NumeralFormulaManager<ParamFormulaType, ResultFormulaType>> delegates;

  protected PortfolioNumeralFormulaManager(
      PortfolioFormulaCreator pCreator,
      List<FormulaManager> pMgrs,
      Function<FormulaManager, NumeralFormulaManager<ParamFormulaType, ResultFormulaType>>
          pExtractor) {
    super(pCreator);
    delegates = Lists.transform(pMgrs, pExtractor);
  }

  @SuppressWarnings("unchecked")
  private ParamFormulaType cast(Formula pFormula) {
    return (ParamFormulaType) pFormula;
  }

  @Override
  protected boolean isNumeral(List<? extends Formula> pVal) {
    // TODO check, if all formulas are numeral
    throw new UnsupportedOperationException();
  }

  @Override
  protected List<Formula> makeNumberImpl(long pI) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeNumber(pI));
    }
    return result;
  }

  @Override
  protected List<Formula> makeNumberImpl(BigInteger pI) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeNumber(pI));
    }
    return result;
  }

  @Override
  protected List<Formula> makeNumberImpl(String pI) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeNumber(pI));
    }
    return result;
  }

  @Override
  protected List<Formula> makeNumberImpl(double pNumber) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeNumber(pNumber));
    }
    return result;
  }

  @Override
  protected List<Formula> makeNumberImpl(BigDecimal pNumber) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeNumber(pNumber));
    }
    return result;
  }

  @Override
  protected List<Formula> makeVariableImpl(String pI) {
    List<Formula> result = new ArrayList<>();
    for (NumeralFormulaManager<ParamFormulaType, ResultFormulaType> delegate : delegates) {
      result.add(delegate.makeVariable(pI));
    }
    return result;
  }

  @Override
  protected List<Formula> negate(List<? extends Formula> pParam1) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).negate(cast(pParam1.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> add(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).add(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> subtract(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).subtract(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> multiply(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).multiply(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> divide(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).divide(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> modulo(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).modulo(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> modularCongruence(
      List<? extends Formula> pNumber1, List<? extends Formula> pNumber2, long pModulo) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .modularCongruence(cast(pNumber1.get(i)), cast(pNumber2.get(i)), pModulo));
    }
    return result;
  }

  @Override
  protected List<Formula> equal(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).equal(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> greaterThan(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).greaterThan(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> greaterOrEquals(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).greaterOrEquals(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> lessThan(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).lessThan(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }

  @Override
  protected List<Formula> lessOrEquals(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).lessOrEquals(cast(pParam1.get(i)), cast(pParam2.get(i))));
    }
    return result;
  }
}
