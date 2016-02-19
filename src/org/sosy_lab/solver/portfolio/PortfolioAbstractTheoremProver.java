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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class PortfolioAbstractTheoremProver<T, PE extends BasicProverEnvironment<?>>
    implements BasicProverEnvironment<T> {

  protected final List<PE> delegates;
  protected final PortfolioFormulaCreator creator;
  private final ShutdownNotifier shutdownNotifier;

  public PortfolioAbstractTheoremProver(
      PortfolioFormulaCreator pCreator, ShutdownNotifier pShutdownNotifier, List<PE> pDelegates) {
    delegates = pDelegates;
    creator = pCreator;
    shutdownNotifier = pShutdownNotifier;
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
    // Boolean isUnsat = sequentialIsUnsat();
    Boolean isUnsat = parallelIsUnsat();
    assert isUnsat != null;
    return isUnsat;
  }

  @SuppressWarnings("unused")
  private Boolean sequentialIsUnsat() throws SolverException, InterruptedException {
    Boolean isUnsat = null;
    for (BasicProverEnvironment<?> delegate : delegates) {
      boolean tmp = delegate.isUnsat();
      assert isUnsat == null || tmp == isUnsat;
      isUnsat = tmp;
    }
    return isUnsat;
  }

  /**
   * We run all solvers in parallel and use the first result.
   * The remaining solvers are killed.
   * We expect that all solvers would return the same result SAT/UNSAT.
   */
  private Boolean parallelIsUnsat() throws InterruptedException {
    Boolean isUnsat = null;
    CompletionService<Boolean> service =
        new ExecutorCompletionService<>(Executors.newCachedThreadPool());
    List<Future<Boolean>> futures = new ArrayList<>();
    try {
      // create some workers
      for (PE delegate : delegates) {
        futures.add(service.submit(new UnsatCaller(delegate)));
      }
      // wait for the first worker, we only want one result (except Null or Exception)
      for (int i = 0; i < delegates.size(); i++) {
        try {
          Boolean tmp = service.take().get();
          if (tmp != null) {
            isUnsat = tmp;
            // the first result is good enough, abort further steps
            break;
          }
        } catch (ExecutionException ignore) {
          // TODO should we log this?
        }
      }
      shutdownNotifier.shutdownIfNecessary();
    } finally {
      // kill all other threads
      // TODO let them run in background until finished,
      //      this is a waste of resources, but faster due missing kill-overhead.
      for (Future<Boolean> f : futures) {
        f.cancel(true);
      }
    }
    return isUnsat;
  }

  /** This class is just a wrapper to call isUnsat() in another thread. */
  private class UnsatCaller implements Callable<Boolean> {

    private final PE delegate;

    private UnsatCaller(PE pDelegate) {
      delegate = pDelegate;
    }

    @Override
    public Boolean call() throws Exception {
      return delegate.isUnsat();
    }
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
