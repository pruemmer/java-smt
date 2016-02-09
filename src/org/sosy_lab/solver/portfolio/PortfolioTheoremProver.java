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

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.ProverEnvironment;

import java.util.List;

public class PortfolioTheoremProver extends PortfolioAbstractTheoremProver<Void, ProverEnvironment>
    implements ProverEnvironment {

  public PortfolioTheoremProver(
      PortfolioFormulaCreator pCreator,
      ShutdownNotifier pShutdownNotifier,
      List<ProverEnvironment> pDelegates) {
    super(pCreator, pShutdownNotifier, pDelegates);
  }

  @Override
  public Void push(BooleanFormula pF) {
    push();
    return addConstraint(pF);
  }

  @Override
  public Void addConstraint(BooleanFormula pConstraint) {
    for (int i = 0; i < delegates.size(); i++) {
      delegates.get(i).addConstraint((BooleanFormula) creator.extractInfo(pConstraint).get(i));
    }
    return null;
  }

  @Override
  public List<BooleanFormula> getUnsatCore() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <T> T allSat(AllSatCallback<T> pCallback, List<BooleanFormula> pImportant)
      throws InterruptedException, SolverException {
    // TODO Auto-generated method stub
    return null;
  }
}
