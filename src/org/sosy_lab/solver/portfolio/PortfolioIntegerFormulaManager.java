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
import org.sosy_lab.solver.api.IntegerFormulaManager;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.solver.api.NumeralFormulaManager;

import java.util.List;

public class PortfolioIntegerFormulaManager
    extends PortfolioNumeralFormulaManager<IntegerFormula, IntegerFormula>
    implements IntegerFormulaManager {

  protected PortfolioIntegerFormulaManager(
      PortfolioFormulaCreator pCreator, List<FormulaManager> pMgrs) {
    super(
        pCreator,
        pMgrs,
        new Function<FormulaManager, NumeralFormulaManager<IntegerFormula, IntegerFormula>>() {

          @Override
          public NumeralFormulaManager<IntegerFormula, IntegerFormula> apply(FormulaManager fmgr) {
            return fmgr.getIntegerFormulaManager();
          }
        });
  }

  @Override
  public FormulaType<IntegerFormula> getFormulaType() {
    return FormulaType.IntegerType;
  }
}
