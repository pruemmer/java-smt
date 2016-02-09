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

import com.google.common.base.Preconditions;

import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FormulaType.FloatingPointType;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.solver.api.NumeralFormula.RationalFormula;
import org.sosy_lab.solver.basicimpl.FormulaCreator;
import org.sosy_lab.solver.visitors.FormulaVisitor;

import java.util.List;

public class PortfolioFormulaCreator
    extends FormulaCreator<List<? extends Formula>, FormulaType<?>, Void> {

  private PortfolioFormulaManager mgr = null;

  protected PortfolioFormulaCreator() {
    super(null, FormulaType.BooleanType, FormulaType.IntegerType, FormulaType.RationalType);
  }

  @Override
  public FormulaType<?> getBitvectorType(int pBitwidth) {
    return FormulaType.getBitvectorTypeWithSize(pBitwidth);
  }

  @Override
  public FormulaType<?> getFloatingPointType(FloatingPointType pType) {
    return pType;
  }

  @Override
  public FormulaType<?> getArrayType(FormulaType<?> pIndexType, FormulaType<?> pElementType) {
    return FormulaType.getArrayType(pIndexType, pElementType);
  }

  @Override
  public List<? extends Formula> makeVariable(FormulaType<?> pType, String pVarName) {
    if (pType.isBooleanType()) {
      return extractInfo(mgr.getBooleanFormulaManager().makeVariable(pVarName));
    } else if (pType.isIntegerType()) {
      return extractInfo(mgr.getIntegerFormulaManager().makeVariable(pVarName));
    } else if (pType.isRationalType()) {
      return extractInfo(mgr.getRationalFormulaManager().makeVariable(pVarName));
    } else {
      throw new IllegalArgumentException("Unknown formula type for sort: " + pType);
    }
  }

  void registerFormulaManager(PortfolioFormulaManager pPortfolioFormulaManager) {
    Preconditions.checkArgument(mgr == null);
    mgr = pPortfolioFormulaManager;
  }

  @Override
  public FormulaType<?> getFormulaType(List<? extends Formula> pFormula) {
    Formula first = pFormula.get(0);
    if (first instanceof BooleanFormula) {
      return FormulaType.BooleanType;
    } else if (first instanceof IntegerFormula) {
      return FormulaType.IntegerType;
    } else if (first instanceof RationalFormula) {
      return FormulaType.RationalType;
    } else {
      throw new AssertionError("unknown formula-type");
    }
  }

  @Override
  public <R> R visit(FormulaVisitor<R> pVisitor, Formula pFormula, List<? extends Formula> pF) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected List<? extends Formula> extractInfo(Formula input) {
    // for visibility only
    return super.extractInfo(input);
  }
}
