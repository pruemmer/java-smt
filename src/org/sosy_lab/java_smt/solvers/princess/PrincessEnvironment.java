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

import static com.google.common.collect.Iterables.getOnlyElement;
import static scala.collection.JavaConversions.asJavaIterable;
import static scala.collection.JavaConversions.asScalaBuffer;
import static scala.collection.JavaConversions.iterableAsScalaIterable;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static scala.collection.JavaConversions.seqAsJavaList;

import ap.SimpleAPI;
import ap.parser.IAtom;
import ap.parser.IConstant;
import ap.parser.IExpression;
import ap.parser.IFormula;
import ap.parser.IFunApp;
import ap.parser.IFunction;
import ap.parser.IIntFormula;
import ap.parser.ITerm;
import ap.parser.SMTLineariser;
import ap.parser.SMTParser2InputAbsy.SMTFunctionType;
import ap.parser.SMTParser2InputAbsy.SMTType;
import ap.terfor.ConstantTerm;
import ap.theories.SimpleArray;
import ap.types.MonoSortedIFunction;
import ap.types.Sort;
import ap.types.Sort$;
import ap.util.Debug;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.PathCounterTemplate;
import scala.Tuple2;
import scala.Tuple3;
import scala.collection.Seq;

/**
 * This is a Wrapper around Princess. This Wrapper allows to set a logfile for all Smt-Queries
 * (default "princess.###.smt2"). It also manages the "shared variables": each variable is declared
 * for all stacks.
 */
@Options(prefix = "solver.princess")
class PrincessEnvironment {

  @Option(
      secure = true,
      description =
          "The number of atoms a term has to have before"
              + " it gets abbreviated if there are more identical terms.")
  private int minAtomsForAbbreviation = 100;

  public static final Sort BOOL_SORT = Sort$.MODULE$.Bool();
  public static final Sort INTEGER_SORT = Sort.Integer$.MODULE$;

  @Option(secure = true, description = "log all queries as Princess-specific Scala code")
  private boolean logAllQueriesAsScala = false;

  @Option(secure = true, description = "file for Princess-specific dump of queries as Scala code")
  @FileOption(Type.OUTPUT_FILE)
  private PathCounterTemplate logAllQueriesAsScalaFile =
      PathCounterTemplate.ofFormatString("princess-query-%03d-");

  /**
   * cache for variables, because they do not implement equals() and hashCode(), so we need to have
   * the same objects.
   */
  private final Map<String, IFormula> boolVariablesCache = new HashMap<>();

  private final Map<String, ITerm> sortedVariablesCache = new HashMap<>();

  private final Map<String, IFunction> functionsCache = new HashMap<>();

  private final int randomSeed;
  private final @Nullable PathCounterTemplate basicLogfile;
  private final ShutdownNotifier shutdownNotifier;

  /**
   * The wrapped API is the first created API. It will never be used outside of this class and never
   * be closed. If a variable is declared, it is declared in the first api, then copied into all
   * registered APIs. Each API has its own stack for formulas.
   */
  private final SimpleAPI api;

  private final List<PrincessAbstractProver<?, ?>> registeredProvers = new ArrayList<>();

  PrincessEnvironment(
      Configuration config,
      @Nullable final PathCounterTemplate pBasicLogfile,
      ShutdownNotifier pShutdownNotifier,
      final int pRandomSeed)
      throws InvalidConfigurationException {
    config.inject(this);

    basicLogfile = pBasicLogfile;
    shutdownNotifier = pShutdownNotifier;
    randomSeed = pRandomSeed;

    // this api is only used local in this environment, no need for interpolation
    api = getNewApi(false);
  }

  /**
   * This method returns a new prover, that is registered in this environment. All variables are
   * shared in all registered APIs.
   */
  PrincessAbstractProver<?, ?> getNewProver(
      boolean useForInterpolation,
      boolean unsatCores,
      PrincessFormulaManager mgr,
      PrincessFormulaCreator creator) {

    SimpleAPI newApi = getNewApi(useForInterpolation || unsatCores);

    // add all symbols, that are available until now
    boolVariablesCache.values().forEach(newApi::addBooleanVariable);
    sortedVariablesCache.values().forEach(newApi::addConstant);
    functionsCache.values().forEach(newApi::addFunction);

    PrincessAbstractProver<?, ?> prover;
    if (useForInterpolation) {
      prover = new PrincessInterpolatingProver(mgr, creator, newApi, shutdownNotifier);
    } else {
      prover = new PrincessTheoremProver(mgr, creator, newApi, shutdownNotifier, unsatCores);
    }
    registeredProvers.add(prover);
    return prover;
  }

  @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
  private SimpleAPI getNewApi(boolean constructProofs) {
    File directory = null;
    String smtDumpBasename = null;
    String scalaDumpBasename = null;

    if (basicLogfile != null) {
      Path logPath = basicLogfile.getFreshPath();
      directory = getAbsoluteParent(logPath);
      smtDumpBasename = logPath.getFileName().toString();
      if (Files.getFileExtension(smtDumpBasename).equals("smt2")) {
        // Princess adds .smt2 anyway
        smtDumpBasename = Files.getNameWithoutExtension(smtDumpBasename);
      }
      smtDumpBasename += "-";
    }

    if (logAllQueriesAsScala && logAllQueriesAsScalaFile != null) {
      Path logPath = logAllQueriesAsScalaFile.getFreshPath();
      if (directory == null) {
        directory = getAbsoluteParent(logPath);
      }
      scalaDumpBasename = logPath.getFileName().toString();
    }

    // We enable assertions because typically we use the "assertionless" JAR where they have no
    // effect anyway, but if we use the JAR with assertions we want them to be enabled.
    // The constructor parameter to SimpleAPI affects only part of the assertions.
    Debug.enableAllAssertions(true);

    final SimpleAPI newApi =
        SimpleAPI.apply(
            true, // enableAssert, see above
            false, // no sanitiseNames, because variable names may contain chars like "@" and ":".
            smtDumpBasename != null, // dumpSMT
            smtDumpBasename, // smtDumpBasename
            scalaDumpBasename != null, // dumpScala
            scalaDumpBasename, // scalaDumpBasename
            directory, // dumpDirectory
            SimpleAPI.apply$default$8(), // tightFunctionScopes
            SimpleAPI.apply$default$9(), // genTotalityAxioms
            new scala.Some<>(randomSeed) // randomSeed
            );

    if (constructProofs) { // needed for interpolation and unsat cores
      newApi.setConstructProofs(true);
    }

    return newApi;
  }

  private File getAbsoluteParent(Path path) {
    return Optional.ofNullable(path.getParent()).orElse(Paths.get(".")).toAbsolutePath().toFile();
  }

  int getMinAtomsForAbbreviation() {
    return minAtomsForAbbreviation;
  }

  void unregisterStack(PrincessAbstractProver<?, ?> stack) {
    assert registeredProvers.contains(stack) : "cannot unregister stack, it is not registered";
    registeredProvers.remove(stack);
  }

  public List<? extends IExpression> parseStringToTerms(String s, PrincessFormulaCreator creator) {

    Tuple3<
            Seq<IFormula>,
            scala.collection.immutable.Map<IFunction, SMTFunctionType>,
            scala.collection.immutable.Map<ConstantTerm, SMTType>>
        triple = api.extractSMTLIBAssertionsSymbols(new StringReader(s));

    List<? extends IExpression> formula = seqAsJavaList(triple._1());

    ImmutableSet.Builder<IExpression> declaredFunctions = ImmutableSet.builder();
    for (IExpression f : formula) {
      declaredFunctions.addAll(creator.extractVariablesAndUFs(f, true).values());
    }
    for (IExpression var : declaredFunctions.build()) {
      if (var instanceof IConstant) {
        sortedVariablesCache.put(var.toString(), (ITerm) var);
        addSymbol((IConstant) var);
      } else if (var instanceof IAtom) {
        boolVariablesCache.put(((IAtom) var).pred().name(), (IFormula) var);
        addSymbol((IAtom) var);
      } else if (var instanceof IFunApp) {
        IFunction fun = ((IFunApp) var).fun();
        functionsCache.put(fun.name(), fun);
        addFunction(fun);
      }
    }
    return formula;
  }

  public Appender dumpFormula(IFormula formula, final PrincessFormulaCreator creator) {
    // remove redundant expressions
    // TODO do we want to remove redundancy completely (as checked in the unit
    // tests (SolverFormulaIOTest class)) or do we want to remove redundancy up
    // to the point we do it for formulas that should be asserted
    Tuple2<IExpression, scala.collection.immutable.Map<IExpression, IExpression>> tuple =
        api.abbrevSharedExpressionsWithMap(formula, 1);
    final IExpression lettedFormula = tuple._1();
    final Map<IExpression, IExpression> abbrevMap = mapAsJavaMap(tuple._2());

    return new Appenders.AbstractAppender() {

      @Override
      public void appendTo(Appendable out) throws IOException {
        // allVars needs to be mutable, but declaredFunctions should have deterministic order
        Set<IExpression> allVars =
            ImmutableSet.copyOf(creator.extractVariablesAndUFs(lettedFormula, true).values());
        Deque<IExpression> declaredFunctions = new ArrayDeque<>(allVars);
        allVars = new HashSet<>(allVars);

        Set<String> doneFunctions = new HashSet<>();
        Set<String> todoAbbrevs = new HashSet<>();

        while (!declaredFunctions.isEmpty()) {
          IExpression var = declaredFunctions.poll();
          String name = getName(var);

          // we don't want to declare variables twice, so doublecheck
          // if we have already found the current variable
          if (doneFunctions.contains(name)) {
            continue;
          }
          doneFunctions.add(name);

          // we do only want to add declare-funs for things we really declared
          // the rest is done afterwards
          if (name.startsWith("abbrev_")) {
            todoAbbrevs.add(name);
            Set<IExpression> varsFromAbbrev =
                ImmutableSet.copyOf(
                    creator.extractVariablesAndUFs(abbrevMap.get(var), true).values());
            Sets.difference(varsFromAbbrev, allVars).forEach(declaredFunctions::push);
            allVars.addAll(varsFromAbbrev);
          } else {
            out.append("(declare-fun ").append(SMTLineariser.quoteIdentifier(name));

            // function parameters
            out.append(" (");
            if (var instanceof IFunApp) {
              IFunApp function = (IFunApp) var;
              Iterator<ITerm> args = asJavaIterable(function.args()).iterator();
              while (args.hasNext()) {
                args.next();
                // Princess does only support IntegerFormulas in UIFs we don't need
                // to check the type here separately
                if (args.hasNext()) {
                  out.append("Int ");
                } else {
                  out.append("Int");
                }
              }
            }

            out.append(") ");
            out.append(getType(var));
            out.append(")\n");
          }
        }

        // now as everything we know from the formula is declared we have to add
        // the abbreviations, too
        for (Entry<IExpression, IExpression> entry : abbrevMap.entrySet()) {
          IExpression abbrev = entry.getKey();
          IExpression fullFormula = entry.getValue();
          String name =
              getName(getOnlyElement(creator.extractVariablesAndUFs(abbrev, true).values()));

          // only add the necessary abbreviations
          if (!todoAbbrevs.contains(name)) {
            continue;
          }

          out.append("(define-fun ").append(SMTLineariser.quoteIdentifier(name));

          // the type of each abbreviation
          if (fullFormula instanceof IFormula) {
            out.append(" () Bool ");
          } else if (fullFormula instanceof ITerm) {
            out.append(" () Int ");
          }

          // the abbreviated formula
          out.append(SMTLineariser.asString(fullFormula)).append(" )\n");
        }

        // now add the final assert
        out.append("(assert ").append(SMTLineariser.asString(lettedFormula)).append(')');
      }
    };
  }

  private static String getName(IExpression var) {
    if (var instanceof IAtom) {
      return ((IAtom) var).pred().name();
    } else if (var instanceof IConstant) {
      return var.toString();
    } else if (var instanceof IFunApp) {
      String fullStr = ((IFunApp) var).fun().toString();
      return fullStr.substring(0, fullStr.indexOf('/'));
    } else if (var instanceof IIntFormula) {
      return getName(((IIntFormula) var).t());
    }

    throw new IllegalArgumentException("The given parameter is no variable or function");
  }

  private static String getType(IExpression var) {
    if (var instanceof IFormula) {
      return "Bool";

      // functions are included here, they cannot be handled separate for princess
    } else if (var instanceof ITerm) {
      return "Int";
    }

    throw new IllegalArgumentException("The given parameter is no variable or function");
  }

  public IExpression makeVariable(Sort type, String varname) {
    if (type == BOOL_SORT) {
      if (boolVariablesCache.containsKey(varname)) {
        return boolVariablesCache.get(varname);
      } else {
        IFormula var = api.createBooleanVariable(varname);
        addSymbol(var);
        boolVariablesCache.put(varname, var);
        return var;
      }
    } else {
      if (sortedVariablesCache.containsKey(varname)) {
        return sortedVariablesCache.get(varname);
      } else {
        ITerm var = api.createConstant(varname, type);
        addSymbol(var);
        sortedVariablesCache.put(varname, var);
        return var;
      }
    }
  }

  /** This function declares a new functionSymbol with the given argument types and result. */
  public IFunction declareFun(String name, Sort returnType, List<Sort> args) {
    if (functionsCache.containsKey(name)) {
      final IFunction res = functionsCache.get(name);
      assert (res instanceof MonoSortedIFunction)
          ? (((MonoSortedIFunction) res).resSort().equals(returnType)
              && seqAsJavaList(((MonoSortedIFunction) res).argSorts()).equals(args))
          : (returnType == INTEGER_SORT
              && res.arity() == args.size()
              && args.stream().allMatch(s -> s == INTEGER_SORT));
      return res;
    } else {
      IFunction funcDecl =
          api.createFunction(
              name,
              asScalaBuffer(args),
              returnType,
              false,
              SimpleAPI.FunctionalityMode$.MODULE$.Full());
      addFunction(funcDecl);
      functionsCache.put(name, funcDecl);
      return funcDecl;
    }
  }

  public ITerm makeSelect(ITerm array, ITerm index) {
    List<ITerm> args = ImmutableList.of(array, index);
    return api.select(iterableAsScalaIterable(args).toSeq());
  }

  public ITerm makeStore(ITerm array, ITerm index, ITerm value) {
    List<ITerm> args = ImmutableList.of(array, index, value);
    return api.store(iterableAsScalaIterable(args).toSeq());
  }

  public boolean hasArrayType(IExpression exp) {
    if (exp instanceof ITerm) {
      final ITerm t = (ITerm) exp;
      return Sort.sortOf(t) instanceof SimpleArray.ArraySort;
    } else {
      return false;
    }
  }

  public IFormula elimQuantifiers(IFormula formula) {
    return api.simplify(formula);
  }

  private void addSymbol(IFormula symbol) {
    for (PrincessAbstractProver<?, ?> prover : registeredProvers) {
      prover.addSymbol(symbol);
    }
  }

  private void addSymbol(ITerm symbol) {
    for (PrincessAbstractProver<?, ?> prover : registeredProvers) {
      prover.addSymbol(symbol);
    }
  }

  private void addFunction(IFunction funcDecl) {
    for (PrincessAbstractProver<?, ?> prover : registeredProvers) {
      prover.addSymbol(funcDecl);
    }
  }
}
