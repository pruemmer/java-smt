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

import com.google.common.base.Function;

import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.solver.api.NumeralFormula.RationalFormula;
import org.sosy_lab.solver.api.NumeralFormulaManager;
import org.sosy_lab.solver.api.RationalFormulaManager;

import java.util.List;

public class PortfolioRationalFormulaManager
    extends PortfolioNumeralFormulaManager<NumeralFormula, RationalFormula>
    implements RationalFormulaManager {

  protected PortfolioRationalFormulaManager(
      PortfolioFormulaCreator pCreator, List<FormulaManager> mgrs) {
    super(
        pCreator,
        mgrs,
        new Function<FormulaManager, NumeralFormulaManager<NumeralFormula, RationalFormula>>() {

          @Override
          public NumeralFormulaManager<NumeralFormula, RationalFormula> apply(FormulaManager fmgr) {
            return fmgr.getRationalFormulaManager();
          }
        });
  }

  @Override
  public FormulaType<RationalFormula> getFormulaType() {
    return FormulaType.RationalType;
  }
}
