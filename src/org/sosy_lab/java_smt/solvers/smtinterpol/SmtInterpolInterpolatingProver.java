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
package org.sosy_lab.java_smt.solvers.smtinterpol;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

class SmtInterpolInterpolatingProver extends SmtInterpolBasicProver<String, String>
    implements InterpolatingProverEnvironment<String> {

  SmtInterpolInterpolatingProver(SmtInterpolFormulaManager pMgr) {
    super(pMgr);
  }

  @Override
  public void pop() {
    for (String removed : assertedFormulas.peek()) {
      annotatedTerms.remove(removed);
    }
    super.pop();
  }

  @Override
  public String addConstraint(BooleanFormula f) {
    Preconditions.checkState(!isClosed());
    String termName = generateTermName();
    Term t = mgr.extractInfo(f);
    Term annotatedTerm = env.annotate(t, new Annotation(":named", termName));
    env.assertTerm(annotatedTerm);
    assertedFormulas.peek().add(termName);
    annotatedTerms.put(termName, t);
    return termName;
  }

  @Override
  public BooleanFormula getInterpolant(List<String> pTermNamesOfA)
      throws SolverException, InterruptedException {
    Preconditions.checkState(!isClosed());

    // SMTInterpol is not able to handle the trivial cases
    // so we need to check them explicitly
    if (pTermNamesOfA.isEmpty()) {
      return mgr.getBooleanFormulaManager().makeBoolean(true);
    } else if (pTermNamesOfA.containsAll(annotatedTerms.keySet())) {
      return mgr.getBooleanFormulaManager().makeBoolean(false);
    }

    Set<String> termNamesOfA = ImmutableSet.copyOf(pTermNamesOfA);

    // calc difference: termNamesOfB := assertedFormulas - termNamesOfA
    Set<String> termNamesOfB =
        annotatedTerms
            .keySet()
            .stream()
            .filter(n -> !termNamesOfA.contains(n))
            .collect(ImmutableSet.toImmutableSet());

    // build 2 groups:  (and A1 A2 A3...) , (and B1 B2 B3...)
    Term termA = buildConjunctionOfNamedTerms(termNamesOfA);
    Term termB = buildConjunctionOfNamedTerms(termNamesOfB);

    return getInterpolant(termA, termB);
  }

  @Override
  public List<BooleanFormula> getSeqInterpolants(
      List<? extends Collection<String>> partitionedTermNames)
      throws SolverException, InterruptedException {
    Preconditions.checkState(!isClosed());

    final Term[] formulas = new Term[partitionedTermNames.size()];
    for (int i = 0; i < formulas.length; i++) {
      formulas[i] = buildConjunctionOfNamedTerms(partitionedTermNames.get(i));
    }

    // get interpolants of groups
    final Term[] itps = env.getInterpolants(formulas);

    final List<BooleanFormula> result = new ArrayList<>();
    for (Term itp : itps) {
      result.add(mgr.encapsulateBooleanFormula(itp));
    }
    return result;
  }

  @Override
  public List<BooleanFormula> getTreeInterpolants(
      List<? extends Collection<String>> partitionedTermNames, int[] startOfSubTree)
      throws SolverException, InterruptedException {
    Preconditions.checkState(!isClosed());
    assert InterpolatingProverEnvironment.checkTreeStructure(
        partitionedTermNames.size(), startOfSubTree);

    final Term[] formulas = new Term[partitionedTermNames.size()];
    for (int i = 0; i < formulas.length; i++) {
      formulas[i] = buildConjunctionOfNamedTerms(partitionedTermNames.get(i));
    }

    // get interpolants of groups
    final Term[] itps = env.getTreeInterpolants(formulas, startOfSubTree);

    final List<BooleanFormula> result = new ArrayList<>();
    for (Term itp : itps) {
      result.add(mgr.encapsulateBooleanFormula(itp));
    }
    assert result.size() == startOfSubTree.length - 1;
    return result;
  }

  protected BooleanFormula getInterpolant(Term termA, Term termB)
      throws SolverException, InterruptedException {
    Preconditions.checkState(!isClosed());
    // get interpolant of groups
    Term[] itp = env.getInterpolants(new Term[] {termA, termB});
    assert itp.length == 1; // 2 groups -> 1 interpolant

    return mgr.encapsulateBooleanFormula(itp[0]);
  }

  private Term buildConjunctionOfNamedTerms(Collection<String> termNames) {
    Preconditions.checkState(!isClosed());
    Preconditions.checkArgument(!termNames.isEmpty());

    if (termNames.size() == 1) {
      return env.term(Iterables.getOnlyElement(termNames));
    }
    return env.term("and", termNames.stream().map(env::term).toArray(Term[]::new));
  }

  @Override
  protected Collection<Term> getAssertedTerms() {
    return annotatedTerms.values();
  }
}
