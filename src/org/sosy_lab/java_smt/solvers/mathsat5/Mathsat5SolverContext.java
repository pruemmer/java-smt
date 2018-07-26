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

import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_create_config;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_create_env;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_create_shared_env;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_destroy_config;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_destroy_env;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_get_version;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_set_option_checked;
import static org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.msat_set_termination_test;

import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.NativeLibraries;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.FloatingPointRoundingMode;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.OptimizationProverEnvironment;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.basicimpl.AbstractNumeralFormulaManager.NonLinearArithmetic;
import org.sosy_lab.java_smt.basicimpl.AbstractSolverContext;
import org.sosy_lab.java_smt.solvers.mathsat5.Mathsat5NativeApi.TerminationTest;

public final class Mathsat5SolverContext extends AbstractSolverContext {

  @Options(prefix = "solver.mathsat5")
  private static class Mathsat5Settings {

    @Option(
        secure = true,
        description =
            "Further options that will be passed to Mathsat in addition to the default options. "
                + "Format is 'key1=value1,key2=value2'")
    private String furtherOptions = "";

    @Option(secure = true, description = "Load less stable optimizing version of mathsat5 solver.")
    boolean loadOptimathsat5 = false;

    private final @Nullable PathCounterTemplate logfile;

    private final ImmutableMap<String, String> furtherOptionsMap;

    private Mathsat5Settings(Configuration config, @Nullable PathCounterTemplate pLogfile)
        throws InvalidConfigurationException {
      config.inject(this);
      logfile = pLogfile;

      MapSplitter optionSplitter =
          Splitter.on(',')
              .trimResults()
              .omitEmptyStrings()
              .withKeyValueSeparator(Splitter.on('=').limit(2).trimResults());

      try {
        furtherOptionsMap = ImmutableMap.copyOf(optionSplitter.split(furtherOptions));
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(
            "Invalid Mathsat option in \"" + furtherOptions + "\": " + e.getMessage(), e);
      }
    }
  }

  private static final boolean USE_SHARED_ENV = true;
  private static final boolean USE_GHOST_FILTER = true;

  private final LogManager logger;
  private final long mathsatConfig;
  private final Mathsat5Settings settings;
  private final long randomSeed;

  private final ShutdownNotifier shutdownNotifier;
  private final TerminationTest terminationTest;
  private final Mathsat5FormulaCreator creator;

  private static boolean loaded = false;

  @SuppressWarnings("checkstyle:parameternumber")
  public Mathsat5SolverContext(
      LogManager logger,
      long mathsatConfig,
      Mathsat5Settings settings,
      long randomSeed,
      final ShutdownNotifier shutdownNotifier,
      Mathsat5FormulaManager manager,
      Mathsat5FormulaCreator creator) {
    super(manager);

    if (!loaded) { // Avoid logging twice.
      logger.log(
          Level.WARNING,
          "MathSAT5 is available for research and evaluation purposes only. It can not be used in"
              + " a commercial environment, particularly as part of a commercial product, without "
              + "written permission. MathSAT5 is provided as is, without any warranty. "
              + "Please write to mathsat@fbk.eu for additional questions regarding licensing "
              + "MathSAT5 or obtaining more up-to-date versions.");
    }
    this.logger = logger;
    this.mathsatConfig = mathsatConfig;
    this.settings = settings;
    this.randomSeed = randomSeed;
    this.shutdownNotifier = shutdownNotifier;
    this.creator = creator;

    terminationTest =
        () -> {
          shutdownNotifier.shutdownIfNecessary();
          return false;
        };
  }

  public static Mathsat5SolverContext create(
      LogManager logger,
      Configuration config,
      ShutdownNotifier pShutdownNotifier,
      @Nullable PathCounterTemplate solverLogFile,
      long randomSeed,
      FloatingPointRoundingMode pFloatingPointRoundingMode,
      NonLinearArithmetic pNonLinearArithmetic)
      throws InvalidConfigurationException {

    // Init Msat
    Mathsat5Settings settings = new Mathsat5Settings(config, solverLogFile);

    if (settings.loadOptimathsat5) {
      NativeLibraries.loadLibrary("optimathsat5j");
    } else {
      NativeLibraries.loadLibrary("mathsat5j");
    }

    long msatConf = msat_create_config();
    msat_set_option_checked(msatConf, "theory.la.split_rat_eq", "false");
    msat_set_option_checked(msatConf, "random_seed", Long.toString(randomSeed));

    for (Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      try {
        msat_set_option_checked(msatConf, option.getKey(), option.getValue());
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(e.getMessage(), e);
      }
    }

    final long msatEnv = msat_create_env(msatConf);

    // Create Mathsat5FormulaCreator
    Mathsat5FormulaCreator creator = new Mathsat5FormulaCreator(msatEnv);

    // Create managers
    Mathsat5UFManager functionTheory = new Mathsat5UFManager(creator);
    Mathsat5BooleanFormulaManager booleanTheory = new Mathsat5BooleanFormulaManager(creator);
    Mathsat5IntegerFormulaManager integerTheory =
        new Mathsat5IntegerFormulaManager(creator, pNonLinearArithmetic);
    Mathsat5RationalFormulaManager rationalTheory =
        new Mathsat5RationalFormulaManager(creator, pNonLinearArithmetic);
    Mathsat5BitvectorFormulaManager bitvectorTheory =
        Mathsat5BitvectorFormulaManager.create(creator);
    Mathsat5FloatingPointFormulaManager floatingPointTheory =
        new Mathsat5FloatingPointFormulaManager(creator, pFloatingPointRoundingMode);
    Mathsat5ArrayFormulaManager arrayTheory = new Mathsat5ArrayFormulaManager(creator);
    Mathsat5FormulaManager manager =
        new Mathsat5FormulaManager(
            creator,
            functionTheory,
            booleanTheory,
            integerTheory,
            rationalTheory,
            bitvectorTheory,
            floatingPointTheory,
            arrayTheory);
    return new Mathsat5SolverContext(
        logger, msatConf, settings, randomSeed, pShutdownNotifier, manager, creator);
  }

  long createEnvironment(long cfg) {
    if (USE_GHOST_FILTER) {
      msat_set_option_checked(cfg, "dpll.ghost_filtering", "true");
    }

    msat_set_option_checked(cfg, "theory.la.split_rat_eq", "false");
    msat_set_option_checked(cfg, "random_seed", Long.toString(randomSeed));

    for (Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      msat_set_option_checked(cfg, option.getKey(), option.getValue());
    }

    if (settings.logfile != null) {
      Path filename = settings.logfile.getFreshPath();
      try {
        MoreFiles.createParentDirectories(filename);
      } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Cannot create directory for MathSAT logfile");
      }

      msat_set_option_checked(cfg, "debug.api_call_trace", "1");
      msat_set_option_checked(
          cfg, "debug.api_call_trace_filename", filename.toAbsolutePath().toString());
    }

    final long env;
    if (USE_SHARED_ENV) {
      env = msat_create_shared_env(cfg, creator.getEnv());
    } else {
      env = msat_create_env(cfg);
    }

    return env;
  }

  @Override
  protected ProverEnvironment newProverEnvironment0(Set<ProverOptions> options) {
    if (options.contains(ProverOptions.GENERATE_UNSAT_CORE_OVER_ASSUMPTIONS)) {
      throw new UnsupportedOperationException(
          "Mathsat5 does not support generating UNSAT core over assumptions");
    }
    return new Mathsat5TheoremProver(this, shutdownNotifier, creator, options);
  }

  @Override
  protected InterpolatingProverEnvironment<?> newProverEnvironmentWithInterpolation0(
      Set<ProverOptions> options) {
    return new Mathsat5InterpolatingProver(this, shutdownNotifier, creator, options);
  }

  @Override
  public OptimizationProverEnvironment newOptimizationProverEnvironment0(
      Set<ProverOptions> options) {
    return new Mathsat5OptimizationProver(this, shutdownNotifier, creator, options);
  }

  @Override
  public String getVersion() {
    return msat_get_version();
  }

  @Override
  public Solvers getSolverName() {
    return Solvers.MATHSAT5;
  }

  @Override
  public void close() {
    logger.log(Level.FINER, "Freeing Mathsat environment");
    msat_destroy_env(creator.getEnv());
    msat_destroy_config(mathsatConfig);
  }

  long addTerminationTest(long env) {
    return msat_set_termination_test(env, terminationTest);
  }

  @Override
  protected boolean supportsAssumptionSolving() {
    return true;
  }
}
