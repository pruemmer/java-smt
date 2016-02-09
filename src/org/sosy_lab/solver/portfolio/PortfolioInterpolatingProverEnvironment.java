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
import org.sosy_lab.solver.api.InterpolatingProverEnvironmentWithAssumptions;
import org.sosy_lab.solver.portfolio.PortfolioInterpolatingProverEnvironment.PortfolioID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PortfolioInterpolatingProverEnvironment
    extends PortfolioAbstractTheoremProver<
        PortfolioID, InterpolatingProverEnvironmentWithAssumptions<?>>
    implements InterpolatingProverEnvironmentWithAssumptions<PortfolioID> {

  public PortfolioInterpolatingProverEnvironment(
      PortfolioFormulaCreator pCreator,
      ShutdownNotifier pShutdownNotifier,
      List<InterpolatingProverEnvironmentWithAssumptions<?>> pDelegates) {
    super(pCreator, pShutdownNotifier, pDelegates);
  }

  @Override
  public PortfolioID addConstraint(BooleanFormula pConstraint) {
    List<Object> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(
          delegates.get(i).addConstraint((BooleanFormula) creator.extractInfo(pConstraint).get(i)));
    }
    return new PortfolioID(result);
  }

  private static List<Object> projectTo(List<PortfolioID> lst, int index) {
    List<Object> result = new ArrayList<>();
    for (PortfolioID id : lst) {
      result.add(id.content.get(index));
    }
    return result;
  }

  private List<Set<Object>> projectToList(List<Set<PortfolioID>> pPartitionedFormulas, int index) {
    List<Set<Object>> delegatingFormulas = new ArrayList<>();
    for (Set<PortfolioID> set : pPartitionedFormulas) {
      Set<Object> inner = new HashSet<>();
      for (PortfolioID id : set) {
        inner.add(id.content.get(index));
      }
      delegatingFormulas.add(inner);
    }
    return delegatingFormulas;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public BooleanFormula getInterpolant(List<PortfolioID> pFormulasOfA)
      throws SolverException, InterruptedException {
    List<BooleanFormula> result = new ArrayList<>();
    for (int i = 0; i < delegates.size(); i++) {
      result.add(delegates.get(i).getInterpolant((List) projectTo(pFormulasOfA, i)));
    }
    return creator.encapsulateBoolean(result);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public List<BooleanFormula> getSeqInterpolants(List<Set<PortfolioID>> pPartitionedFormulas)
      throws SolverException, InterruptedException {
    List<ArrayList<BooleanFormula>> table = getEmptyTable(pPartitionedFormulas);
    for (int i = 0; i < delegates.size(); i++) {
      List<BooleanFormula> itps =
          delegates.get(i).getSeqInterpolants((List) projectToList(pPartitionedFormulas, i));
      updateTable(pPartitionedFormulas, table, itps);
    }
    return wrapTableContent(pPartitionedFormulas, table);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public List<BooleanFormula> getTreeInterpolants(
      List<Set<PortfolioID>> pPartitionedFormulas, int[] pStartOfSubTree)
      throws SolverException, InterruptedException {
    List<ArrayList<BooleanFormula>> table = getEmptyTable(pPartitionedFormulas);
    for (int i = 0; i < delegates.size(); i++) {
      List<BooleanFormula> itps =
          delegates
              .get(i)
              .getTreeInterpolants((List) projectToList(pPartitionedFormulas, i), pStartOfSubTree);
      updateTable(pPartitionedFormulas, table, itps);
    }
    return wrapTableContent(pPartitionedFormulas, table);
  }

  /** create an empty table, with empty columns for all interpolants */
  private List<ArrayList<BooleanFormula>> getEmptyTable(
      List<Set<PortfolioID>> pPartitionedFormulas) {
    List<ArrayList<BooleanFormula>> table = new ArrayList<>();
    for (int k = 0; k < pPartitionedFormulas.size() - 1; k++) {
      table.add(new ArrayList<BooleanFormula>());
    }
    return table;
  }

  /** insert values into the table, insert the k-th interpolant in the k-th column */
  private void updateTable(
      List<Set<PortfolioID>> pPartitionedFormulas,
      List<ArrayList<BooleanFormula>> table,
      List<BooleanFormula> itps) {
    assert pPartitionedFormulas.size() - 1 == itps.size();
    for (int k = 0; k < pPartitionedFormulas.size() - 1; k++) {
      table.get(k).add(itps.get(k));
    }
  }

  /** read ITPs column-wise and wrap them */
  private List<BooleanFormula> wrapTableContent(
      List<Set<PortfolioID>> pPartitionedFormulas, List<ArrayList<BooleanFormula>> table) {
    List<BooleanFormula> result = new ArrayList<>();
    for (int k = 0; k < pPartitionedFormulas.size() - 1; k++) {
      result.add(creator.encapsulateBoolean(table.get(k)));
    }
    return result;
  }

  @Override
  public boolean isUnsatWithAssumptions(List<BooleanFormula> pAssumptions)
      throws SolverException, InterruptedException {
    Boolean isUnsat = null;
    for (int i = 0; i < delegates.size(); i++) {
      boolean tmp = delegates.get(i).isUnsatWithAssumptions(projectToFormulas(pAssumptions, i));
      assert isUnsat == null || tmp == isUnsat;
      isUnsat = tmp;
    }
    assert isUnsat != null;
    return isUnsat;
  }

  private List<BooleanFormula> projectToFormulas(List<BooleanFormula> lst, int index) {
    List<BooleanFormula> result = new ArrayList<>();
    for (BooleanFormula bf : lst) {
      result.add((BooleanFormula) creator.extractInfo(bf).get(index));
    }
    return result;
  }

  /** just another wrapper-class */
  static class PortfolioID {

    private final List<?> content;

    public PortfolioID(List<?> pContent) {
      content = pContent;
    }

    @Override
    public int hashCode() {
      return content.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof PortfolioID && content.equals(((PortfolioID) other).content);
    }
  }
}
