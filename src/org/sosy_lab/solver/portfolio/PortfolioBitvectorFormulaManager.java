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

import org.sosy_lab.solver.api.BitvectorFormula;
import org.sosy_lab.solver.api.BitvectorFormulaManager;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.basicimpl.AbstractBitvectorFormulaManager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class PortfolioBitvectorFormulaManager
    extends AbstractBitvectorFormulaManager<List<? extends Formula>, FormulaType<?>, Void> {

  private final List<BitvectorFormulaManager> delegates;

  protected PortfolioBitvectorFormulaManager(
      PortfolioFormulaCreator pCreator, List<FormulaManager> pMgrs) {
    super(pCreator);
    delegates = new ArrayList<>();
    for (FormulaManager fmgr : pMgrs) {
      delegates.add(fmgr.getBitvectorFormulaManager());
    }
  }

  @Override
  protected List<Formula> negate(List<? extends Formula> pParam1) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).negate((BitvectorFormula) pParam1.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> add(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .add((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> subtract(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .subtract((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> divide(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .divide(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> modulo(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .modulo(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> modularCongruence(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, long pModulo) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .modularCongruence(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pModulo));
    }
    return result;
  }

  @Override
  protected List<Formula> multiply(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .multiply((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> equal(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .equal((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> greaterThan(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .greaterThan(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> greaterOrEquals(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .greaterOrEquals(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> lessThan(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .lessThan(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> lessOrEquals(
      List<? extends Formula> pParam1, List<? extends Formula> pParam2, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .lessOrEquals(
                  (BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> not(List<? extends Formula> pParam1) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).not((BitvectorFormula) pParam1.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> and(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .and((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> or(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .or((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> xor(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .xor((BitvectorFormula) pParam1.get(i), (BitvectorFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> makeBitvectorImpl(int pLength, long pI) {
    List<Formula> result = new ArrayList<>();
    for (BitvectorFormulaManager delegate : delegates) {
      result.add(delegate.makeBitvector(pLength, pI));
    }
    return result;
  }

  @Override
  protected List<Formula> makeBitvectorImpl(int pLength, BigInteger pI) {
    List<Formula> result = new ArrayList<>();
    for (BitvectorFormulaManager delegate : delegates) {
      result.add(delegate.makeBitvector(pLength, pI));
    }
    return result;
  }

  @Override
  protected List<Formula> makeVariableImpl(int pLength, String pVar) {
    List<Formula> result = new ArrayList<>();
    for (BitvectorFormulaManager delegate : delegates) {
      result.add(delegate.makeVariable(pLength, pVar));
    }
    return result;
  }

  @Override
  protected List<Formula> shiftRight(
      List<? extends Formula> pNumber, List<? extends Formula> pToShift, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .shiftRight(
                  (BitvectorFormula) pNumber.get(i), (BitvectorFormula) pToShift.get(i), pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> shiftLeft(
      List<? extends Formula> pNumber, List<? extends Formula> pToShift) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .shiftLeft((BitvectorFormula) pNumber.get(i), (BitvectorFormula) pToShift.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> concat(List<? extends Formula> pNumber, List<? extends Formula> pAppend) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .concat((BitvectorFormula) pNumber.get(i), (BitvectorFormula) pAppend.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> extract(
      List<? extends Formula> pNumber, int pMsb, int pLsb, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).extract((BitvectorFormula) pNumber.get(i), pMsb, pLsb, pSigned));
    }
    return result;
  }

  @Override
  protected List<Formula> extend(
      List<? extends Formula> pNumber, int pExtensionBits, boolean pSigned) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).extend((BitvectorFormula) pNumber.get(i), pExtensionBits, pSigned));
    }
    return result;
  }
}
