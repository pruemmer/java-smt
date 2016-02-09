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

import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FunctionFormulaManager;
import org.sosy_lab.solver.api.UfDeclaration;
import org.sosy_lab.solver.basicimpl.AbstractFunctionFormulaManager;

import java.util.ArrayList;
import java.util.List;

public class PortfolioFunctionFormulaManager
    extends AbstractFunctionFormulaManager<
        List<? extends Formula>, PortfolioUfDeclaration, FormulaType<?>, Void>
    implements FunctionFormulaManager {

  private final List<FunctionFormulaManager> delegates;

  protected PortfolioFunctionFormulaManager(
      PortfolioFormulaCreator pCreator, List<FormulaManager> pMgrs) {
    super(pCreator);
    delegates = new ArrayList<>();
    for (FormulaManager fmgr : pMgrs) {
      delegates.add(fmgr.getFunctionFormulaManager());
    }
  }

  @SuppressWarnings("unchecked")
  private static List<? extends Formula> extractFormulas(
      List<List<? extends Formula>> pArgs, int index) {
    List<Formula> result = new ArrayList<>();
    for (List<? extends Formula> t : pArgs) {
      result.add(t.get(index));
    }
    return result;
  }

  @Override
  protected PortfolioUfDeclaration declareUninterpretedFunctionImpl(
      String pName, FormulaType<?> pReturnType, List<FormulaType<?>> pArgTypes) {
    List<UfDeclaration<? extends Formula>> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).declareUninterpretedFunction(pName, pReturnType, pArgTypes));
    }
    return new PortfolioUfDeclaration(result);
  }

  @Override
  protected List<Formula> createUninterpretedFunctionCallImpl(
      PortfolioUfDeclaration pFunc, List<List<? extends Formula>> pArgs) {
    List<Formula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates
              .get(i)
              .callUninterpretedFunction(
                  pFunc.getDeclarations().get(i), extractFormulas(pArgs, i)));
    }
    return result;
  }
}
