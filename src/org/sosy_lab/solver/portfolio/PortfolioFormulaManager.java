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

import org.sosy_lab.common.Appender;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.basicimpl.AbstractFormulaManager;
import org.sosy_lab.solver.visitors.FormulaVisitor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PortfolioFormulaManager
    extends AbstractFormulaManager<List<? extends Formula>, FormulaType<?>, Void> {

  private final List<FormulaManager> delegates;

  protected PortfolioFormulaManager(
      PortfolioFormulaCreator pFormulaCreator, List<FormulaManager> pMgrs) {
    super(
        pFormulaCreator,
        new PortfolioFunctionFormulaManager(pFormulaCreator, pMgrs),
        new PortfolioBooleanFormulaManager(pFormulaCreator, pMgrs),
        new PortfolioIntegerFormulaManager(pFormulaCreator, pMgrs),
        new PortfolioRationalFormulaManager(pFormulaCreator, pMgrs),
        null,
        null,
        null,
        null);
    delegates = pMgrs;
    pFormulaCreator.registerFormulaManager(this);
  }

  @Override
  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    throw new UnsupportedOperationException("which sub-manager should parse this? all?");
  }

  @Override
  public <R> R visit(FormulaVisitor<R> pRFormulaVisitor, Formula pF) {
    throw new UnsupportedOperationException(
        "sub-formulas can be different, visitation is impossible");
  }

  @Override
  public Appender dumpFormula(final List<? extends Formula> pT) {
    return new Appender() {

      @Override
      public void appendTo(Appendable pAppendable) throws IOException {
        for (int i = 0; i < delegates.size(); i++) {
          delegates.get(i).dumpFormula((BooleanFormula) pT.get(i)).appendTo(pAppendable);
        }
      }
    };
  }

  @Override
  protected List<List<? extends Formula>> splitNumeralEqualityIfPossible(
      List<? extends Formula> pF) {
    // splitting formulas is not possible,
    // thus we fall back to the default case and return list of single element
    return Collections.<List<? extends Formula>>singletonList(pF);
  }
}
