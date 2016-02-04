/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.solver.smtinterpol;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.basicimpl.AbstractQuantifiedFormulaManager;

import java.util.List;

class SmtInterpolQuantifiedFormulaManager extends AbstractQuantifiedFormulaManager<Term, Sort,
        SmtInterpolEnvironment> {

    private final SmtInterpolEnvironment env;

    SmtInterpolQuantifiedFormulaManager(final SmtInterpolFormulaCreator pCreator) {
        super(pCreator);
        env = pCreator.getEnv();
    }

    @Override
    protected Term exists(List<Term> pVariables, Term pBody) {
        return mkQuantifier(Quantifier.EXISTS, pVariables, pBody);
    }

    @Override
    protected Term forall(List<Term> pVariables, Term pBody) {
        return mkQuantifier(Quantifier.FORALL, pVariables, pBody);
    }

    @Override
    protected Term eliminateQuantifiers(Term pExtractInfo) throws SolverException, InterruptedException {
        throw new IllegalArgumentException("Implement eliminateQuantifiers");
    }

    @Override
    public Term mkQuantifier(Quantifier q, List<Term> vars, Term body) {
        assert q == Quantifier.EXISTS || q == Quantifier.FORALL : "Unknown quantifier";
        final int quantifier = (q == Quantifier.EXISTS) ? Script.EXISTS : Script.FORALL;

        TermVariable[] variables = new TermVariable[vars.size()];
        for (int i = 0; i < vars.size(); i++) {
            assert vars.get(i) instanceof TermVariable
                    : "Cannot quantify variable not of type TermVariable";
            variables[i] = (TermVariable) vars.get(i);
        }

        return env.quantifier(quantifier, variables, body, (Term[]) null);

    }
}
