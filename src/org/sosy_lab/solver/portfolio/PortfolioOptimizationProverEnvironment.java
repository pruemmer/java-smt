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

import com.google.common.base.Optional;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.OptimizationProverEnvironment;

import java.util.List;

public class PortfolioOptimizationProverEnvironment
    extends PortfolioAbstractTheoremProver<Void, OptimizationProverEnvironment>
    implements OptimizationProverEnvironment {

  public PortfolioOptimizationProverEnvironment(
      PortfolioFormulaCreator pCreator,
      ShutdownNotifier pShutdownNotifier,
      List<OptimizationProverEnvironment> pDelegates) {
    super(pCreator, pShutdownNotifier, pDelegates);
  }

  @Override
  public Void addConstraint(BooleanFormula pConstraint) {
    for (int i = 0; i < delegates.size(); i++) {
      delegates.get(i).addConstraint((BooleanFormula) creator.extractInfo(pConstraint).get(i));
    }
    return null;
  }

  @Override
  public int maximize(Formula pObjective) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int minimize(Formula pObjective) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public OptStatus check() throws InterruptedException, SolverException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<Rational> upper(int pHandle, Rational pEpsilon) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<Rational> lower(int pHandle, Rational pEpsilon) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String dump() {
    StringBuilder str = new StringBuilder("[");
    for (OptimizationProverEnvironment delegate : delegates) {
      str.append(delegate.dump()).append(", ");
    }
    return str.append("]").toString();
  }
}
