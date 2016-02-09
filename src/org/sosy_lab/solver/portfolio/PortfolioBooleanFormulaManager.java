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

import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.BooleanFormulaManager;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.basicimpl.AbstractBooleanFormulaManager;

import java.util.ArrayList;
import java.util.List;

public class PortfolioBooleanFormulaManager
    extends AbstractBooleanFormulaManager<List<? extends Formula>, FormulaType<?>, Void> {

  private final List<BooleanFormulaManager> delegates;

  protected PortfolioBooleanFormulaManager(
      PortfolioFormulaCreator pCreator, List<FormulaManager> pMgrs) {
    super(pCreator);
    delegates = new ArrayList<>();
    for (FormulaManager fmgr : pMgrs) {
      delegates.add(fmgr.getBooleanFormulaManager());
    }
  }

  @Override
  protected List<Formula> makeVariableImpl(String pVar) {
    List<Formula> result = new ArrayList<>();
    for (BooleanFormulaManager delegate : delegates) {
      result.add(delegate.makeVariable(pVar));
    }
    return result;
  }

  @Override
  protected List<Formula> makeBooleanImpl(boolean pValue) {
    List<Formula> result = new ArrayList<>();
    for (BooleanFormulaManager delegate : delegates) {
      result.add(delegate.makeBoolean(pValue));
    }
    return result;
  }

  @Override
  protected List<Formula> not(List<? extends Formula> pParam1) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).not((BooleanFormula) pParam1.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> and(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).and((BooleanFormula) pParam1.get(i), (BooleanFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> or(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).or((BooleanFormula) pParam1.get(i), (BooleanFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> xor(List<? extends Formula> pParam1, List<? extends Formula> pParam2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).xor((BooleanFormula) pParam1.get(i), (BooleanFormula) pParam2.get(i)));
    }
    return result;
  }

  @Override
  protected List<Formula> equivalence(
      List<? extends Formula> pBits1, List<? extends Formula> pBits2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .equivalence((BooleanFormula) pBits1.get(i), (BooleanFormula) pBits2.get(i)));
    }
    return result;
  }

  @Override
  protected boolean isTrue(List<? extends Formula> pBits) {
    // all sub-formulas must be TRUE
    for (int i = 0; i < delegates.size(); i++) {
      if (!delegates.get(i).isTrue((BooleanFormula) pBits.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean isFalse(List<? extends Formula> pBits) {
    // all sub-formulas must be FALSE
    for (int i = 0; i < delegates.size(); i++) {
      if (!delegates.get(i).isFalse((BooleanFormula) pBits.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected List<Formula> ifThenElse(
      List<? extends Formula> pCond, List<? extends Formula> pF1, List<? extends Formula> pF2) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).ifThenElse((BooleanFormula) pCond.get(i), pF1.get(i), pF2.get(i)));
    }
    return result;
  }
}
