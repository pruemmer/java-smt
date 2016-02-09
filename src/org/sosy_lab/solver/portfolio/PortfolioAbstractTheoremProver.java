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
import org.sosy_lab.solver.api.BasicProverEnvironment;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.Model;
import org.sosy_lab.solver.basicimpl.AbstractModel;

import java.util.Iterator;
import java.util.List;

public abstract class PortfolioAbstractTheoremProver<T, PE extends BasicProverEnvironment<?>>
    implements BasicProverEnvironment<T> {

  protected final List<PE> delegates;
  protected final PortfolioFormulaCreator creator;

  public PortfolioAbstractTheoremProver(
      PortfolioFormulaCreator pCreator, ShutdownNotifier pShutdownNotifier, List<PE> pDelegates) {
    delegates = pDelegates;
    creator = pCreator;
  }

  @Override
  public T push(BooleanFormula pF) {
    push();
    return addConstraint(pF);
  }

  @Override
  public void pop() {
    for (BasicProverEnvironment<?> delegate : delegates) {
      delegate.pop();
    }
  }

  @Override
  public void push() {
    for (BasicProverEnvironment<?> delegate : delegates) {
      delegate.push();
    }
  }

  @Override
  public boolean isUnsat() throws SolverException, InterruptedException {
    // TODO parallel loop!
    Boolean isUnsat = null;
    for (BasicProverEnvironment<?> delegate : delegates) {
      boolean tmp = delegate.isUnsat();
      assert isUnsat == null || tmp == isUnsat;
      isUnsat = tmp;
    }
    assert isUnsat != null;
    return isUnsat;
  }

  @Override
  public Model getModel() throws SolverException {
    final int bestIndex = 0;
    final Model model = delegates.get(bestIndex).getModel();
    return new AbstractModel<List<? extends Formula>, FormulaType<?>, Void>(creator) {

      @Override
      public Iterator<ValueAssignment> iterator() {
        return model.iterator();
      }

      @Override
      protected Object evaluateImpl(List<? extends Formula> pF) {
        return model.evaluate(pF.get(bestIndex));
      }
    };
  }

  @Override
  public void close() {
    for (BasicProverEnvironment<?> delegate : delegates) {
      delegate.close();
    }
  }
}
