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
package org.sosy_lab.java_smt.solvers.mathsat5;

import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_assert_formula;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_create_itp_group;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_get_interpolant;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_get_model;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_push_backtrack_point;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_set_itp_group;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;

class Mathsat5InterpolatingProver extends Mathsat5AbstractProver<Integer>
    implements InterpolatingProverEnvironment<Integer> {

  private static final ImmutableSet<String> ALLOWED_FAILURE_MESSAGES =
      ImmutableSet.of(
          "impossible to build a suitable congruence graph!",
          "can't build ie-local interpolant",
          "set_raised on an already-raised proof",
          "splitting of AB-mixed terms not supported",
          "Hypothesis belongs neither to A nor to B",
          "FP<->BV combination unsupported by the current configuration",
          "cur_eq unknown to the classifier",
          "unknown constraint in the ItpMapper",
          "AB-mixed term not found in eq_itp map",
          "uncolored atom found in Array proof",
          "uncolorable Array proof",
          "arr: proof splitting not supported");
  private static final ImmutableSet<String> ALLOWED_FAILURE_MESSAGE_PREFIXES =
      ImmutableSet.of("uncolorable NA lemma");

  Mathsat5InterpolatingProver(
      Mathsat5SolverContext pMgr,
      ShutdownNotifier pShutdownNotifier,
      Mathsat5FormulaCreator creator,
      Set<ProverOptions> options) {
    super(pMgr, options, creator, pShutdownNotifier);
  }

  @Override
  protected void createConfig(Map<String, String> pConfig) {
    pConfig.put("interpolation", "true");
    pConfig.put("model_generation", "true");
    pConfig.put("theory.bv.eager", "false");
  }

  @Override
  public Integer addConstraint(BooleanFormula f) {
    Preconditions.checkState(!closed);
    int group = msat_create_itp_group(curEnv);
    msat_set_itp_group(curEnv, group);
    long t = creator.extractInfo(f);
    msat_assert_formula(curEnv, t);
    return group;
  }

  @Override
  public void push() {
    Preconditions.checkState(!closed);
    msat_push_backtrack_point(curEnv);
  }

  @Override
  protected long getMsatModel() throws SolverException {
    // Interpolation in MathSAT is buggy at least for UFs+Ints and sometimes returns a wrong "SAT".
    // In this case, model generation fails and users should try again without interpolation.
    // Example failures: "Invalid model", "non-integer model value"
    // As this is a bug in MathSAT and not in our code, we throw a SolverException.
    // We do it only in InterpolatingProver because without interpolation this is not expected.
    try {
      return msat_get_model(curEnv);
    } catch (IllegalArgumentException e) {
      String msg = Strings.emptyToNull(e.getMessage());
      throw new SolverException(
          "msat_get_model failed"
              + (msg != null ? " with \"" + msg + "\"" : "")
              + ", probably the actual problem is interpolation",
          e);
    }
  }

  @Override
  public BooleanFormula getInterpolant(List<Integer> formulasOfA) throws SolverException {
    Preconditions.checkState(!closed);

    int[] groupsOfA = new int[formulasOfA.size()];
    int i = 0;
    for (Integer f : formulasOfA) {
      groupsOfA[i++] = f;
    }

    long itp;
    try {
      itp = msat_get_interpolant(curEnv, groupsOfA);
    } catch (IllegalArgumentException e) {
      final String message = e.getMessage();
      if (!Strings.isNullOrEmpty(message)
          && (ALLOWED_FAILURE_MESSAGES.contains(message)
              || ALLOWED_FAILURE_MESSAGE_PREFIXES.stream().anyMatch(message::startsWith))) {
        // This is not a bug in our code,
        // but a problem of MathSAT which happens during interpolation
        throw new SolverException(message, e);
      }
      throw e;
    }
    return creator.encapsulateBoolean(itp);
  }

  @Override
  public List<BooleanFormula> getSeqInterpolants(
      List<? extends Collection<Integer>> partitionedFormulas) throws SolverException {
    // the fallback to a loop is sound and returns an inductive sequence of interpolants
    final List<BooleanFormula> itps = new ArrayList<>();
    for (int i = 0; i < partitionedFormulas.size(); i++) {
      itps.add(
          getInterpolant(Lists.newArrayList(Iterables.concat(partitionedFormulas.subList(0, i)))));
    }
    return itps;
  }

  @Override
  public List<BooleanFormula> getTreeInterpolants(
      List<? extends Collection<Integer>> partitionedFormulas, int[] startOfSubTree) {
    throw new UnsupportedOperationException(
        "directly receiving tree interpolants is not supported."
            + "Use another solver or another strategy for interpolants.");
  }

  @Override
  public <T> T allSat(AllSatCallback<T> callback, List<BooleanFormula> important) {
    // TODO how can we support allsat in MathSat5-interpolation-prover?
    // error: "allsat is not compatible wwith proof generation"
    throw new UnsupportedOperationException(
        "allsat computation is not possible with interpolation prover.");
  }
}
