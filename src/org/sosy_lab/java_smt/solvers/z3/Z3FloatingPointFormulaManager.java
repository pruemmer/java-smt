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
package org.sosy_lab.java_smt.solvers.z3;

import com.google.common.collect.ImmutableList;
import com.microsoft.z3.Native;
import java.math.BigDecimal;
import org.sosy_lab.java_smt.api.FloatingPointRoundingMode;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FormulaType.FloatingPointType;
import org.sosy_lab.java_smt.basicimpl.AbstractFloatingPointFormulaManager;

class Z3FloatingPointFormulaManager
    extends AbstractFloatingPointFormulaManager<Long, Long, Long, Long> {

  private static final FloatingPointType highPrec = FormulaType.getFloatingPointType(15, 112);

  private final long z3context;
  private final long roundingMode;

  Z3FloatingPointFormulaManager(
      Z3FormulaCreator creator, FloatingPointRoundingMode pFloatingPointRoundingMode) {
    super(creator);
    z3context = creator.getEnv();
    roundingMode = getRoundingModeImpl(pFloatingPointRoundingMode);
  }

  @Override
  protected Long getDefaultRoundingMode() {
    return roundingMode;
  }

  @Override
  protected Long getRoundingModeImpl(FloatingPointRoundingMode pFloatingPointRoundingMode) {
    long out;
    switch (pFloatingPointRoundingMode) {
      case NEAREST_TIES_TO_EVEN:
        out = Native.mkFpaRoundNearestTiesToEven(z3context);
        break;
      case NEAREST_TIES_AWAY:
        out = Native.mkFpaRoundNearestTiesToAway(z3context);
        break;
      case TOWARD_POSITIVE:
        out = Native.mkFpaRoundTowardPositive(z3context);
        break;
      case TOWARD_NEGATIVE:
        out = Native.mkFpaRoundTowardNegative(z3context);
        break;
      case TOWARD_ZERO:
        out = Native.mkFpaRoundTowardZero(z3context);
        break;
      default:
        throw new AssertionError("Unexpected value");
    }
    Native.incRef(z3context, out);
    return out;
  }

  private long mkFpaSort(FloatingPointType pType) {
    return getFormulaCreator().getFloatingPointType(pType);
  }

  @Override
  public Long makeNumberImpl(double pN, FloatingPointType pType, Long pRoundingMode) {
    if (Double.isNaN(pN) || Double.isInfinite(pN)) {
      return Native.mkFpaNumeralDouble(z3context, pN, mkFpaSort(pType));
    }
    // Z3 has problems with rounding when giving a double value, so we go via Strings
    return makeNumberImpl(Double.toString(pN), pType, pRoundingMode);
  }

  @Override
  public Long makeNumberImpl(BigDecimal pN, FloatingPointType pType, Long pRoundingMode) {
    // Using toString() fails in CPAchecker with parse error for seemingly correct strings like
    // "3.4028234663852886E+38" and I have no idea why and cannot reproduce it in unit tests,
    // but toPlainString() seems to work at least.
    return makeNumberImpl(pN.toPlainString(), pType, pRoundingMode);
  }

  @Override
  protected Long makeNumberImpl(String pN, FloatingPointType pType, Long pRoundingMode) {
    // Z3 does not allow specifying a rounding mode for numerals,
    // so we create it first with a high precision and then round it down explicitly.
    if (pType.getExponentSize() <= highPrec.getExponentSize()
        || pType.getMantissaSize() <= highPrec.getMantissaSize()) {

      long highPrecNumber = Native.mkNumeral(z3context, pN, mkFpaSort(highPrec));
      Native.incRef(z3context, highPrecNumber);
      long smallPrecNumber = castToImpl(highPrecNumber, pType, pRoundingMode);
      Native.incRef(z3context, smallPrecNumber);
      long result = Native.simplify(z3context, smallPrecNumber);
      Native.decRef(z3context, highPrecNumber);
      Native.decRef(z3context, smallPrecNumber);
      return result;
    }
    return Native.mkNumeral(z3context, pN, mkFpaSort(pType));
  }

  @Override
  public Long makeVariableImpl(String var, FloatingPointType pType) {
    return getFormulaCreator().makeVariable(mkFpaSort(pType), var);
  }

  @Override
  protected Long makePlusInfinityImpl(FloatingPointType pType) {
    return Native.mkFpaInf(z3context, mkFpaSort(pType), false);
  }

  @Override
  protected Long makeMinusInfinityImpl(FloatingPointType pType) {
    return Native.mkFpaInf(z3context, mkFpaSort(pType), true);
  }

  @Override
  protected Long makeNaNImpl(FloatingPointType pType) {
    return Native.mkFpaNan(z3context, mkFpaSort(pType));
  }

  @Override
  protected Long castToImpl(Long pNumber, FormulaType<?> pTargetType, Long pRoundingMode) {
    if (pTargetType.isFloatingPointType()) {
      FormulaType.FloatingPointType targetType = (FormulaType.FloatingPointType) pTargetType;
      return Native.mkFpaToFpFloat(z3context, pRoundingMode, pNumber, mkFpaSort(targetType));

    } else if (pTargetType.isBitvectorType()) {
      FormulaType.BitvectorType targetType = (FormulaType.BitvectorType) pTargetType;
      return Native.mkFpaToSbv(z3context, pRoundingMode, pNumber, targetType.getSize());

    } else if (pTargetType.isRationalType()) {
      return Native.mkFpaToReal(z3context, pNumber);

    } else {
      return genericCast(pNumber, pTargetType);
    }
  }

  @Override
  protected Long castFromImpl(
      Long pNumber, boolean signed, FloatingPointType pTargetType, Long pRoundingMode) {
    FormulaType<?> formulaType = getFormulaCreator().getFormulaType(pNumber);

    if (formulaType.isFloatingPointType()) {
      return castToImpl(pNumber, pTargetType, pRoundingMode);

    } else if (formulaType.isBitvectorType()) {
      if (signed) {
        return Native.mkFpaToFpSigned(z3context, pRoundingMode, pNumber, mkFpaSort(pTargetType));
      } else {
        return Native.mkFpaToFpUnsigned(z3context, pRoundingMode, pNumber, mkFpaSort(pTargetType));
      }

    } else if (formulaType.isRationalType()) {
      return Native.mkFpaToFpReal(z3context, pRoundingMode, pNumber, mkFpaSort(pTargetType));

    } else {
      return genericCast(pNumber, pTargetType);
    }
  }

  private Long genericCast(Long pNumber, FormulaType<?> pTargetType) {
    FormulaType<?> argType = getFormulaCreator().getFormulaType(pNumber);
    long castFuncDecl =
        getFormulaCreator()
            .declareUFImpl(
                "__cast_" + argType + "_to_" + pTargetType,
                toSolverType(pTargetType),
                ImmutableList.of(toSolverType(argType)));
    return Native.mkApp(z3context, castFuncDecl, 1, new long[] {pNumber});
  }

  @Override
  protected Long fromIeeeBitvectorImpl(Long pNumber, FloatingPointType pTargetType) {
    // TODO: The following code misses the sign bit
    // return Native.mkFpaToFpBv(z3context, pNumber, mkFpaSort(pTargetType));
    throw new UnsupportedOperationException();
  }

  @Override
  protected Long toIeeeBitvectorImpl(Long pNumber) {
    // TODO: The following code misses the sign bit
    // return Native.mkFpaToIeeeBv(z3context, pNumber);
    throw new UnsupportedOperationException();
  }

  @Override
  public Long negate(Long pNumber) {
    return Native.mkFpaNeg(z3context, pNumber);
  }

  @Override
  public Long add(Long pNumber1, Long pNumber2, Long pRoundingMode) {
    return Native.mkFpaAdd(z3context, pRoundingMode, pNumber1, pNumber2);
  }

  @Override
  public Long subtract(Long pNumber1, Long pNumber2, Long pRoundingMode) {
    return Native.mkFpaSub(z3context, pRoundingMode, pNumber1, pNumber2);
  }

  @Override
  public Long multiply(Long pNumber1, Long pNumber2, Long pRoundingMode) {
    return Native.mkFpaMul(z3context, pRoundingMode, pNumber1, pNumber2);
  }

  @Override
  protected Long divide(Long pNumber1, Long pNumber2, Long pRoundingMode) {
    return Native.mkFpaDiv(z3context, pRoundingMode, pNumber1, pNumber2);
  }

  @Override
  protected Long assignment(Long pNumber1, Long pNumber2) {
    return Native.mkEq(z3context, pNumber1, pNumber2);
  }

  @Override
  public Long equalWithFPSemantics(Long pNumber1, Long pNumber2) {
    return Native.mkFpaEq(z3context, pNumber1, pNumber2);
  }

  @Override
  public Long greaterThan(Long pNumber1, Long pNumber2) {
    return Native.mkFpaGt(z3context, pNumber1, pNumber2);
  }

  @Override
  public Long greaterOrEquals(Long pNumber1, Long pNumber2) {
    return Native.mkFpaGeq(z3context, pNumber1, pNumber2);
  }

  @Override
  public Long lessThan(Long pNumber1, Long pNumber2) {
    return Native.mkFpaLt(z3context, pNumber1, pNumber2);
  }

  @Override
  public Long lessOrEquals(Long pNumber1, Long pNumber2) {
    return Native.mkFpaLeq(z3context, pNumber1, pNumber2);
  }

  @Override
  protected Long isNaN(Long pParam) {
    return Native.mkFpaIsNan(z3context, pParam);
  }

  @Override
  protected Long isInfinity(Long pParam) {
    return Native.mkFpaIsInfinite(z3context, pParam);
  }

  @Override
  protected Long isZero(Long pParam) {
    return Native.mkFpaIsZero(z3context, pParam);
  }

  @Override
  protected Long isSubnormal(Long pParam) {
    return Native.mkFpaIsSubnormal(z3context, pParam);
  }

  @Override
  protected Long round(Long pFormula, FloatingPointRoundingMode pRoundingMode) {
    return Native.mkFpaRoundToIntegral(z3context, getRoundingModeImpl(pRoundingMode), pFormula);
  }
}
