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
package org.sosy_lab.java_smt.solvers.princess;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sosy_lab.java_smt.api.QuantifiedFormulaManager.Quantifier.EXISTS;
import static org.sosy_lab.java_smt.api.QuantifiedFormulaManager.Quantifier.FORALL;

import ap.basetypes.IdealInt;
import ap.parser.IAtom;
import ap.parser.IBinFormula;
import ap.parser.IBinJunctor;
import ap.parser.IBoolLit;
import ap.parser.IConstant;
import ap.parser.IEpsilon;
import ap.parser.IExpression;
import ap.parser.IFormula;
import ap.parser.IFormulaITE;
import ap.parser.IFunApp;
import ap.parser.IFunction;
import ap.parser.IIntFormula;
import ap.parser.IIntLit;
import ap.parser.IIntRelation;
import ap.parser.INot;
import ap.parser.IPlus;
import ap.parser.IQuantified;
import ap.parser.ITerm;
import ap.parser.ITermITE;
import ap.parser.ITimes;
import ap.parser.IVariable;
import ap.terfor.conjunctions.Quantifier;
import ap.terfor.preds.Predicate;
import ap.theories.ModuloArithmetic;
import ap.theories.ModuloArithmetic$;
import ap.theories.SimpleArray;
import ap.theories.nia.GroebnerMultiplication$;
import ap.types.Sort;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.FormulaType.ArrayFormulaType;
import org.sosy_lab.java_smt.api.FunctionDeclarationKind;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.visitors.FormulaVisitor;
import org.sosy_lab.java_smt.basicimpl.FormulaCreator;
import org.sosy_lab.java_smt.basicimpl.FunctionDeclarationImpl;
import org.sosy_lab.java_smt.solvers.princess.PrincessFunctionDeclaration.PrincessByExampleDeclaration;
import org.sosy_lab.java_smt.solvers.princess.PrincessFunctionDeclaration.PrincessEquationDeclaration;
import org.sosy_lab.java_smt.solvers.princess.PrincessFunctionDeclaration.PrincessIFunctionDeclaration;
import org.sosy_lab.java_smt.solvers.princess.PrincessFunctionDeclaration.PrincessMultiplyDeclaration;
import scala.Enumeration;

class PrincessFormulaCreator
    extends FormulaCreator<IExpression, Sort, PrincessEnvironment, PrincessFunctionDeclaration> {

  // Hash-maps from interpreted functions and predicates to their corresponding
  // Java-SMT kind
  private static final Map<IFunction, FunctionDeclarationKind> theoryFunctionKind = new HashMap<>();
  private static final Map<Predicate, FunctionDeclarationKind> theoryPredKind = new HashMap<>();

  static {
    final ModuloArithmetic$ modmod = ModuloArithmetic$.MODULE$;
    theoryFunctionKind.put(modmod.bv_concat(), FunctionDeclarationKind.BV_CONCAT);
    theoryFunctionKind.put(modmod.bv_extract(), FunctionDeclarationKind.BV_EXTRACT);
    theoryFunctionKind.put(modmod.bv_not(), FunctionDeclarationKind.BV_NOT);
    theoryFunctionKind.put(modmod.bv_neg(), FunctionDeclarationKind.BV_NEG);
    theoryFunctionKind.put(modmod.bv_and(), FunctionDeclarationKind.BV_AND);
    theoryFunctionKind.put(modmod.bv_or(), FunctionDeclarationKind.BV_OR);
    theoryFunctionKind.put(modmod.bv_add(), FunctionDeclarationKind.BV_ADD);
    theoryFunctionKind.put(modmod.bv_sub(), FunctionDeclarationKind.BV_SUB);
    theoryFunctionKind.put(modmod.bv_mul(), FunctionDeclarationKind.BV_MUL);
    theoryFunctionKind.put(modmod.bv_udiv(), FunctionDeclarationKind.BV_UDIV);
    theoryFunctionKind.put(modmod.bv_sdiv(), FunctionDeclarationKind.BV_SDIV);
    theoryFunctionKind.put(modmod.bv_urem(), FunctionDeclarationKind.BV_UREM);
    theoryFunctionKind.put(modmod.bv_srem(), FunctionDeclarationKind.BV_SREM);
    // modmod.bv_smod()?
    theoryFunctionKind.put(modmod.bv_shl(), FunctionDeclarationKind.BV_SHL);
    theoryFunctionKind.put(modmod.bv_lshr(), FunctionDeclarationKind.BV_LSHR);
    theoryFunctionKind.put(modmod.bv_ashr(), FunctionDeclarationKind.BV_ASHR);
    theoryFunctionKind.put(modmod.bv_xor(), FunctionDeclarationKind.BV_XOR);
    // modmod.bv_xnor()?
    // modmod.bv_comp()?

    // casts to integer, sign/zero-extension?

    theoryPredKind.put(modmod.bv_ult(), FunctionDeclarationKind.BV_ULT);
    theoryPredKind.put(modmod.bv_ule(), FunctionDeclarationKind.BV_ULE);
    theoryPredKind.put(modmod.bv_slt(), FunctionDeclarationKind.BV_SLT);
    theoryPredKind.put(modmod.bv_sle(), FunctionDeclarationKind.BV_SLE);

    theoryFunctionKind.put(GroebnerMultiplication$.MODULE$.mul(), FunctionDeclarationKind.MUL);
  }

  PrincessFormulaCreator(PrincessEnvironment pEnv) {
    super(pEnv, PrincessEnvironment.BOOL_SORT, PrincessEnvironment.INTEGER_SORT, null);
  }

  @Override
  public FormulaType<?> getFormulaType(IExpression pFormula) {
    if (pFormula instanceof IFormula) {
      return FormulaType.BooleanType;
    } else if (pFormula instanceof ITerm) {
      final Sort sort = Sort.sortOf((ITerm) pFormula);
      if (sort == PrincessEnvironment.BOOL_SORT) {
        return FormulaType.BooleanType;
      } else if (sort == PrincessEnvironment.INTEGER_SORT) {
        return FormulaType.IntegerType;
      } else if (sort instanceof SimpleArray.ArraySort) {
        return new ArrayFormulaType<>(FormulaType.IntegerType, FormulaType.IntegerType);
      } else {
        scala.Option<Object> bitWidth = ModuloArithmetic.UnsignedBVSort$.MODULE$.unapply(sort);
        if (bitWidth.isDefined()) {
          return FormulaType.getBitvectorTypeWithSize((Integer) bitWidth.get());
        }
      }
    }
    throw new IllegalArgumentException("Unknown formula type");
  }

  @Override
  public IExpression makeVariable(Sort type, String varName) {
    return getEnv().makeVariable(type, varName);
  }

  @Override
  public Sort getBitvectorType(int pBitwidth) {
    return ModuloArithmetic.UnsignedBVSort$.MODULE$.apply(pBitwidth);
  }

  @Override
  public Sort getFloatingPointType(FormulaType.FloatingPointType type) {
    throw new UnsupportedOperationException("FloatingPoint theory is not supported by Princess");
  }

  @Override
  public Sort getArrayType(Sort pIndexType, Sort pElementType) {
    // no special cases here, princess does only support int arrays with int indexes
    // TODO: check sorts
    return SimpleArray.ArraySort$.MODULE$.apply(1);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Formula> FormulaType<T> getFormulaType(final T pFormula) {
    if (pFormula instanceof BitvectorFormula) {
      ITerm input = (ITerm) extractInfo(pFormula);
      Sort sort = Sort.sortOf(input);
      scala.Option<Object> bitWidth = ModuloArithmetic.UnsignedBVSort$.MODULE$.unapply(sort);
      checkArgument(
          bitWidth.isDefined(), "BitvectorFormula with actual type " + sort + ": " + pFormula);
      return (FormulaType<T>) FormulaType.getBitvectorTypeWithSize((Integer) bitWidth.get());

    } else if (pFormula instanceof ArrayFormula<?, ?>) {
      final FormulaType<?> arrayIndexType = getArrayFormulaIndexType((ArrayFormula<?, ?>) pFormula);
      final FormulaType<?> arrayElementType =
          getArrayFormulaElementType((ArrayFormula<?, ?>) pFormula);
      return (FormulaType<T>) new ArrayFormulaType<>(arrayIndexType, arrayElementType);
    }

    return super.getFormulaType(pFormula);
  }

  private String getName(IExpression input) {
    if (input instanceof IAtom || input instanceof IConstant) {
      return input.toString();
    } else if (input instanceof IBinFormula) {
      return ((IBinFormula) input).j().toString();
    } else if (input instanceof IFormulaITE || input instanceof ITermITE) {
      // in princess ite representation is the complete formula which we do not want here
      return "ite";
    } else if (input instanceof IIntFormula) {
      return ((IIntFormula) input).rel().toString();
    } else if (input instanceof INot) {
      // in princess not representation is the complete formula which we do not want here
      return "not";
    } else if (input instanceof IFunApp) {
      return ((IFunApp) input).fun().name();
    } else if (input instanceof IPlus) {
      // in princess plus representation is the complete formula which we do not want here
      return "+";
    } else if (input instanceof ITimes) {
      // in princess times representation is the complete formula which we do not want here
      return "*";
    } else if (input instanceof IEpsilon) {
      // in princess epsilon representation is the complete formula which we do not want here
      return "eps";
    } else {
      throw new AssertionError("Unhandled type " + input.getClass());
    }
  }

  @Override
  public <R> R visit(FormulaVisitor<R> visitor, final Formula f, final IExpression input) {
    if (input instanceof IIntLit) {
      IdealInt value = ((IIntLit) input).value();
      return visitor.visitConstant(f, value.bigIntValue());

    } else if (input instanceof IBoolLit) {
      IBoolLit literal = (IBoolLit) input;
      return visitor.visitConstant(f, literal.value());

      // this is a quantifier
    } else if (input instanceof IQuantified) {

      BooleanFormula body = encapsulateBoolean(((IQuantified) input).subformula());
      return visitor.visitQuantifier(
          (BooleanFormula) f,
          ((IQuantified) input).quan().equals(Quantifier.apply(true)) ? FORALL : EXISTS,

          // Princess does not hold any metadata about bound variables,
          // so we can't get meaningful list here.
          // HOWEVER, passing this list to QuantifiedFormulaManager#mkQuantifier
          // works as expected.
          new ArrayList<>(),
          body);

      // variable bound by a quantifier
    } else if (input instanceof IVariable) {
      return visitor.visitBoundVariable(f, ((IVariable) input).index());

      // nullary atoms and constant are variables
    } else if (((input instanceof IAtom) && ((IAtom) input).args().isEmpty())
        || input instanceof IConstant) {
      return visitor.visitFreeVariable(f, input.toString());

      // Princess encodes multiplication as "linear coefficient and factor" with arity 1.
    } else if (input instanceof ITimes) {
      assert input.length() == 1;

      ITimes multiplication = (ITimes) input;
      IIntLit coeff = new IIntLit(multiplication.coeff());
      FormulaType<IntegerFormula> coeffType = FormulaType.IntegerType;
      IExpression factor = multiplication.subterm();
      FormulaType<?> factorType = getFormulaType(factor);

      return visitor.visitFunction(
          f,
          ImmutableList.of(encapsulate(coeffType, coeff), encapsulate(factorType, factor)),
          FunctionDeclarationImpl.of(
              getName(input),
              getDeclarationKind(input),
              ImmutableList.of(coeffType, factorType),
              getFormulaType(f),
              PrincessMultiplyDeclaration.INSTANCE));

    } else {

      // then we have to check the declaration kind
      final FunctionDeclarationKind kind = getDeclarationKind(input);

      if (kind == FunctionDeclarationKind.EQ) {
        scala.Option<scala.Tuple2<ap.parser.ITerm, ap.parser.ITerm>> maybeArgs =
            IExpression.Eq$.MODULE$.unapply((IFormula) input);

        assert maybeArgs.isDefined();

        final ITerm left = maybeArgs.get()._1;
        final ITerm right = maybeArgs.get()._2;

        ImmutableList.Builder<Formula> args = ImmutableList.builder();
        ImmutableList.Builder<FormulaType<?>> argTypes = ImmutableList.builder();

        FormulaType<?> argumentTypeLeft = getFormulaType(left);
        args.add(encapsulate(argumentTypeLeft, left));
        argTypes.add(argumentTypeLeft);
        FormulaType<?> argumentTypeRight = getFormulaType(right);
        args.add(encapsulate(argumentTypeRight, right));
        argTypes.add(argumentTypeRight);

        return visitor.visitFunction(
            f,
            args.build(),
            FunctionDeclarationImpl.of(
                getName(input),
                FunctionDeclarationKind.EQ,
                argTypes.build(),
                getFormulaType(f),
                PrincessEquationDeclaration.INSTANCE));

      } else if (kind == FunctionDeclarationKind.UF && input instanceof IIntFormula) {

        assert ((IIntFormula) input).rel().equals(IIntRelation.EqZero());

        // this is really a Boolean formula, visit the lhs of the equation
        return visit(visitor, f, ((IIntFormula) input).t());

      } else {

        ImmutableList.Builder<Formula> args = ImmutableList.builder();
        ImmutableList.Builder<FormulaType<?>> argTypes = ImmutableList.builder();
        int arity = input.length();
        for (int i = 0; i < arity; i++) {
          IExpression arg = input.apply(i);
          FormulaType<?> argumentType = getFormulaType(arg);
          args.add(encapsulate(argumentType, arg));
          argTypes.add(argumentType);
        }

        PrincessFunctionDeclaration solverDeclaration;

        if (input instanceof IFunApp) {
          if (kind == FunctionDeclarationKind.UF) {
            solverDeclaration = new PrincessIFunctionDeclaration(((IFunApp) input).fun());
          } else if (kind == FunctionDeclarationKind.MUL) {
            solverDeclaration = PrincessMultiplyDeclaration.INSTANCE;
          } else {
            solverDeclaration = new PrincessByExampleDeclaration(input);
          }
        } else {
          solverDeclaration = new PrincessByExampleDeclaration(input);
        }

        return visitor.visitFunction(
            f,
            args.build(),
            FunctionDeclarationImpl.of(
                getName(input), kind, argTypes.build(), getFormulaType(f), solverDeclaration));
      }
    }
  }

  private FunctionDeclarationKind getDeclarationKind(IExpression input) {
    assert !(((input instanceof IAtom) && ((IAtom) input).args().isEmpty())
            || input instanceof IConstant)
        : "Variables should be handled somewhere else";

    if (input instanceof IFormulaITE || input instanceof ITermITE) {
      return FunctionDeclarationKind.ITE;
    } else if (input instanceof IFunApp) {
      final IFunction fun = ((IFunApp) input).fun();
      final FunctionDeclarationKind theoryKind = theoryFunctionKind.get(fun);
      if (theoryKind != null) {
        return theoryKind;
      } else if (SimpleArray.Select$.MODULE$.unapply(fun)) {
        return FunctionDeclarationKind.SELECT;
      } else if (SimpleArray.Store$.MODULE$.unapply(fun)) {
        return FunctionDeclarationKind.STORE;
      } else {
        return FunctionDeclarationKind.UF;
      }
    } else if (input instanceof IAtom) {
      final Predicate pred = ((IAtom) input).pred();
      final FunctionDeclarationKind theoryKind = theoryPredKind.get(pred);
      if (theoryKind != null) {
        return theoryKind;
      } else {
        return FunctionDeclarationKind.UF;
      }
    } else if (isBinaryFunction(input, IBinJunctor.And())) {
      return FunctionDeclarationKind.AND;
    } else if (isBinaryFunction(input, IBinJunctor.Or())) {
      return FunctionDeclarationKind.OR;
    } else if (input instanceof INot) {
      return FunctionDeclarationKind.NOT;
    } else if (isBinaryFunction(input, IBinJunctor.Eqv())) {
      return FunctionDeclarationKind.IFF;
    } else if (input instanceof ITimes) {
      return FunctionDeclarationKind.MUL;
    } else if (input instanceof IPlus) {
      // SUB does not exist in princess a - b is a + (-b) there
      return FunctionDeclarationKind.ADD;
    } else if (input instanceof IIntFormula) {
      IIntFormula f = (IIntFormula) input;
      if (f.rel().equals(IIntRelation.EqZero())) {
        final Sort sort = Sort.sortOf(((IIntFormula) input).t());
        if (sort == PrincessEnvironment.BOOL_SORT) {
          // this is really a Boolean formula, it has to be UF
          return FunctionDeclarationKind.UF;
        } else if (sort == PrincessEnvironment.INTEGER_SORT) {
          return FunctionDeclarationKind.EQ_ZERO;
        } else {
          return FunctionDeclarationKind.EQ;
        }
      } else if (f.rel().equals(IIntRelation.GeqZero())) {
        return FunctionDeclarationKind.GTE_ZERO;
      } else {
        throw new AssertionError("Unhandled value for integer relation");
      }

    } else {
      // we cannot handle XOR, IMPLIES, DISTINCT, DIV, MODULO, LT, LTE GT in princess
      // they are either handled implicitly by the above mentioned parts or not at all
      return FunctionDeclarationKind.OTHER;
    }
  }

  private static boolean isBinaryFunction(IExpression t, Enumeration.Value val) {
    return (t instanceof IBinFormula) && val.equals(((IBinFormula) t).j()); // j is the operator
  }

  @Override
  public PrincessFunctionDeclaration declareUFImpl(
      String pName, Sort pReturnType, List<Sort> args) {
    return new PrincessIFunctionDeclaration(environment.declareFun(pName, pReturnType, args));
  }

  @Override
  public IExpression callFunctionImpl(
      PrincessFunctionDeclaration declaration, List<IExpression> args) {
    return declaration.makeApp(environment, args);
  }

  @Override
  protected PrincessFunctionDeclaration getBooleanVarDeclarationImpl(IExpression pIExpression) {
    return new PrincessByExampleDeclaration(pIExpression);
  }
}
