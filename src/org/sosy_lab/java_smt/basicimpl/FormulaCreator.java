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
package org.sosy_lab.java_smt.basicimpl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.FloatingPointFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FormulaType.ArrayFormulaType;
import org.sosy_lab.java_smt.api.FormulaType.FloatingPointType;
import org.sosy_lab.java_smt.api.FunctionDeclaration;
import org.sosy_lab.java_smt.api.FunctionDeclarationKind;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.NumeralFormula.RationalFormula;
import org.sosy_lab.java_smt.api.visitors.DefaultFormulaVisitor;
import org.sosy_lab.java_smt.api.visitors.FormulaVisitor;
import org.sosy_lab.java_smt.api.visitors.TraversalProcess;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.ArrayFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.BitvectorFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.BooleanFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.FloatingPointFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.FloatingPointRoundingModeFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.IntegerFormulaImpl;
import org.sosy_lab.java_smt.basicimpl.AbstractFormula.RationalFormulaImpl;

/**
 * This is a helper class with several methods that are commonly used throughout the basicimpl
 * package and may have solver-specific implementations. Each solver package is expected to provide
 * an instance of this class, with the appropriate methods overwritten. Depending on the solver,
 * some or all non-final methods in this class may need to be overwritten.
 *
 * @param <TFormulaInfo> the solver specific type for formulas.
 * @param <TType> the solver specific type for formula types.
 * @param <TEnv> the solver specific type for the environment/context.
 */
public abstract class FormulaCreator<TFormulaInfo, TType, TEnv, TFuncDecl> {

  private final TType boolType;
  private final @Nullable TType integerType;
  private final @Nullable TType rationalType;
  protected final TEnv environment;

  protected FormulaCreator(
      TEnv env, TType boolType, @Nullable TType pIntegerType, @Nullable TType pRationalType) {
    this.environment = env;
    this.boolType = boolType;
    this.integerType = pIntegerType;
    this.rationalType = pRationalType;
  }

  public final TEnv getEnv() {
    return environment;
  }

  public final TType getBoolType() {
    return boolType;
  }

  public final TType getIntegerType() {
    if (integerType == null) {
      throw new UnsupportedOperationException("Integer theory is not supported by this solver.");
    }
    return integerType;
  }

  public final TType getRationalType() {
    if (rationalType == null) {
      throw new UnsupportedOperationException("Rational theory is not supported by this solver.");
    }
    return rationalType;
  }

  public abstract TType getBitvectorType(int bitwidth);

  public abstract TType getFloatingPointType(FloatingPointType type);

  public abstract TType getArrayType(TType indexType, TType elementType);

  public abstract TFormulaInfo makeVariable(TType type, String varName);

  public BooleanFormula encapsulateBoolean(TFormulaInfo pTerm) {
    assert getFormulaType(pTerm).isBooleanType();
    return new BooleanFormulaImpl<>(pTerm);
  }

  protected BitvectorFormula encapsulateBitvector(TFormulaInfo pTerm) {
    assert getFormulaType(pTerm).isBitvectorType();
    return new BitvectorFormulaImpl<>(pTerm);
  }

  protected FloatingPointFormula encapsulateFloatingPoint(TFormulaInfo pTerm) {
    assert getFormulaType(pTerm).isFloatingPointType();
    return new FloatingPointFormulaImpl<>(pTerm);
  }

  protected <TI extends Formula, TE extends Formula> ArrayFormula<TI, TE> encapsulateArray(
      TFormulaInfo pTerm, FormulaType<TI> pIndexType, FormulaType<TE> pElementType) {
    assert getFormulaType(pTerm).equals(FormulaType.getArrayType(pIndexType, pElementType))
        : "Expected: "
            + getFormulaType(pTerm)
            + " but found: "
            + FormulaType.getArrayType(pIndexType, pElementType);

    return new ArrayFormulaImpl<>(pTerm, pIndexType, pElementType);
  }

  public Formula encapsulateWithTypeOf(TFormulaInfo pTerm) {
    return encapsulate(getFormulaType(pTerm), pTerm);
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T encapsulate(FormulaType<T> pType, TFormulaInfo pTerm) {
    assert pType.equals(getFormulaType(pTerm))
        : String.format(
            "Trying to encapsulate formula %s of type %s as %s",
            pTerm, getFormulaType(pTerm), pType);
    if (pType.isBooleanType()) {
      return (T) new BooleanFormulaImpl<>(pTerm);
    } else if (pType.isIntegerType()) {
      return (T) new IntegerFormulaImpl<>(pTerm);
    } else if (pType.isRationalType()) {
      return (T) new RationalFormulaImpl<>(pTerm);
    } else if (pType.isBitvectorType()) {
      return (T) new BitvectorFormulaImpl<>(pTerm);
    } else if (pType.isFloatingPointType()) {
      return (T) new FloatingPointFormulaImpl<>(pTerm);
    } else if (pType.isFloatingPointRoundingModeType()) {
      return (T) new FloatingPointRoundingModeFormulaImpl<>(pTerm);
    } else if (pType.isArrayType()) {
      ArrayFormulaType<?, ?> arrayType = (ArrayFormulaType<?, ?>) pType;
      return (T) encapsulateArray(pTerm, arrayType.getIndexType(), arrayType.getElementType());
    }
    throw new IllegalArgumentException(
        "Cannot create formulas of type " + pType + " in the Solver!");
  }

  @SuppressWarnings("unchecked")
  protected TFormulaInfo extractInfo(Formula pT) {
    if (pT instanceof AbstractFormula) {
      return ((AbstractFormula<TFormulaInfo>) pT).getFormulaInfo();
    }
    throw new IllegalArgumentException(
        "Cannot get the formula info of type " + pT.getClass().getSimpleName() + " in the Solver!");
  }

  @SuppressWarnings("unchecked")
  protected <TI extends Formula, TE extends Formula> FormulaType<TE> getArrayFormulaElementType(
      ArrayFormula<TI, TE> pArray) {
    return ((ArrayFormulaImpl<TI, TE, TFormulaInfo>) pArray).getElementType();
  }

  @SuppressWarnings("unchecked")
  protected <TI extends Formula, TE extends Formula> FormulaType<TI> getArrayFormulaIndexType(
      ArrayFormula<TI, TE> pArray) {
    return ((ArrayFormulaImpl<TI, TE, TFormulaInfo>) pArray).getIndexType();
  }

  /** Returns the type of the given Formula. */
  @SuppressWarnings("unchecked")
  protected <T extends Formula> FormulaType<T> getFormulaType(T formula) {
    checkNotNull(formula);
    FormulaType<?> t;
    if (formula instanceof BooleanFormula) {
      t = FormulaType.BooleanType;
    } else if (formula instanceof IntegerFormula) {
      t = FormulaType.IntegerType;
    } else if (formula instanceof RationalFormula) {
      t = FormulaType.RationalType;
    } else if (formula instanceof ArrayFormula) {
      throw new UnsupportedOperationException(
          "SMT solvers with support for arrays need to overwrite FormulaCreator.getFormulaType()");
    } else if (formula instanceof BitvectorFormula) {
      throw new UnsupportedOperationException(
          "SMT solvers with support for bitvectors "
              + "need to overwrite FormulaCreator.getFormulaType()");
    } else {
      throw new IllegalArgumentException("Formula with unexpected type " + formula.getClass());
    }
    return (FormulaType<T>) t;
  }

  public abstract FormulaType<?> getFormulaType(TFormulaInfo formula);

  @CanIgnoreReturnValue
  public <R> R visit(Formula input, FormulaVisitor<R> visitor) {
    return visit(visitor, input, extractInfo(input));
  }

  public abstract <R> R visit(FormulaVisitor<R> visitor, Formula formula, TFormulaInfo f);

  protected List<TFormulaInfo> extractInfo(List<? extends Formula> input) {
    return Lists.transform(input, this::extractInfo);
  }

  private final Predicate<Formula> alwaysTrue = t -> true;

  public void visitRecursively(FormulaVisitor<TraversalProcess> pFormulaVisitor, Formula pF) {
    visitRecursively(pFormulaVisitor, pF, alwaysTrue);
  }

  public void visitRecursively(
      FormulaVisitor<TraversalProcess> pFormulaVisitor,
      Formula pF,
      Predicate<Formula> shouldProcess) {
    RecursiveFormulaVisitorImpl recVisitor = new RecursiveFormulaVisitorImpl(pFormulaVisitor);
    recVisitor.addToQueue(pF);
    while (!recVisitor.isQueueEmpty()) {
      Formula tt = recVisitor.pop();
      if (shouldProcess == alwaysTrue || shouldProcess.test(tt)) {

        TraversalProcess process = visit(tt, recVisitor);
        if (process == TraversalProcess.ABORT) {
          return;
        }
      }
    }
  }

  public <T extends Formula> T transformRecursively(
      FormulaVisitor<? extends Formula> pFormulaVisitor, T pF) {
    return transformRecursively(pFormulaVisitor, pF, t -> true);
  }

  public <T extends Formula> T transformRecursively(
      FormulaVisitor<? extends Formula> pFormulaVisitor, T pF, Predicate<Object> shouldProcess) {

    final Deque<Formula> toProcess = new ArrayDeque<>();
    Map<Formula, Formula> pCache = new HashMap<>();
    FormulaTransformationVisitorImpl recVisitor =
        new FormulaTransformationVisitorImpl(pFormulaVisitor, toProcess, pCache);
    toProcess.push(pF);

    // Process the work queue
    while (!toProcess.isEmpty()) {
      Formula tt = toProcess.peek();

      if (pCache.containsKey(tt)) {
        toProcess.pop();
        continue;
      }

      if (shouldProcess.test(tt)) {
        visit(tt, recVisitor);
      } else {
        pCache.put(tt, tt);
      }
    }
    @SuppressWarnings("unchecked")
    T out = (T) pCache.get(pF);
    return out;
  }

  /**
   * Wrapper for {@link #extractVariablesAndUFs(Formula, boolean, BiConsumer)} which unwraps both
   * input and output.
   */
  public Map<String, TFormulaInfo> extractVariablesAndUFs(
      final TFormulaInfo pFormula, final boolean extractUFs) {
    Map<String, TFormulaInfo> found = new LinkedHashMap<>();
    extractVariablesAndUFs(
        encapsulateWithTypeOf(pFormula), extractUFs, (name, f) -> found.put(name, extractInfo(f)));
    return found;
  }

  /**
   * Wrapper for {@link #extractVariablesAndUFs(Formula, boolean, BiConsumer)} which unwraps both
   * input and output.
   */
  public void extractVariablesAndUFs(
      final TFormulaInfo pFormula,
      final boolean extractUFs,
      final BiConsumer<String, TFormulaInfo> pConsumer) {
    extractVariablesAndUFs(
        encapsulateWithTypeOf(pFormula),
        extractUFs,
        (name, f) -> pConsumer.accept(name, extractInfo(f)));
  }

  /** Extract all free variables from the formula, optionally including UFs. */
  public void extractVariablesAndUFs(
      final Formula pFormula,
      final boolean extractUF,
      final BiConsumer<String, Formula> pConsumer) {

    visitRecursively(
        new DefaultFormulaVisitor<TraversalProcess>() {

          @Override
          protected TraversalProcess visitDefault(Formula f) {
            return TraversalProcess.CONTINUE;
          }

          @Override
          public TraversalProcess visitFunction(
              Formula f, List<Formula> args, FunctionDeclaration<?> functionDeclaration) {

            if (functionDeclaration.getKind() == FunctionDeclarationKind.UF && extractUF) {
              pConsumer.accept(functionDeclaration.getName(), f);
            }
            return TraversalProcess.CONTINUE;
          }

          @Override
          public TraversalProcess visitFreeVariable(Formula f, String name) {
            pConsumer.accept(name, f);
            return TraversalProcess.CONTINUE;
          }
        },
        pFormula);
  }

  @SuppressWarnings("unchecked")
  public final <T extends Formula> T callFunction(
      FunctionDeclaration<T> declaration, List<? extends Formula> args) {
    return encapsulate(
        declaration.getType(),
        callFunctionImpl(
            ((FunctionDeclarationImpl<T, TFuncDecl>) declaration).getSolverDeclaration(),
            extractInfo(args)));
  }

  public abstract TFormulaInfo callFunctionImpl(TFuncDecl declaration, List<TFormulaInfo> args);

  public abstract TFuncDecl declareUFImpl(String pName, TType pReturnType, List<TType> pArgTypes);

  public TFuncDecl getBooleanVarDeclaration(BooleanFormula var) {
    return getBooleanVarDeclarationImpl(extractInfo(var));
  }

  protected abstract TFuncDecl getBooleanVarDeclarationImpl(TFormulaInfo pTFormulaInfo);
}
