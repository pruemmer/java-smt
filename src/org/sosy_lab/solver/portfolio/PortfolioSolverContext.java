package org.sosy_lab.solver.portfolio;

import com.google.common.collect.Lists;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.solver.SolverContextFactory;
import org.sosy_lab.solver.SolverContextFactory.Solvers;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.InterpolatingProverEnvironmentWithAssumptions;
import org.sosy_lab.solver.api.OptimizationProverEnvironment;
import org.sosy_lab.solver.api.ProverEnvironment;
import org.sosy_lab.solver.api.SolverContext;
import org.sosy_lab.solver.basicimpl.AbstractSolverContext;

import java.util.ArrayList;
import java.util.List;

public final class PortfolioSolverContext extends AbstractSolverContext {

  @Options(prefix = "solver.portfolio")
  static class PortfolioOptions {

    @Option(description = "sub-solvers for the portfolio-solver")
    public List<Solvers> solvers = Lists.newArrayList(Solvers.SMTINTERPOL);

    PortfolioOptions(Configuration config) throws InvalidConfigurationException {
      config.inject(this);
    }
  }

  private final ShutdownNotifier shutdownNotifier;
  private final PortfolioFormulaManager manager;
  private final List<SolverContext> contexts;
  private final PortfolioFormulaCreator creator;

  private PortfolioSolverContext(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      List<SolverContext> pContexts,
      PortfolioFormulaManager pManager,
      PortfolioFormulaCreator pCreator)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pManager);
    shutdownNotifier = pShutdownNotifier;
    manager = pManager;
    contexts = pContexts;
    creator = pCreator;
  }

  public static SolverContext create(
      Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    PortfolioOptions options = new PortfolioOptions(pConfig);

    // Create managers
    SolverContextFactory factory = new SolverContextFactory(pConfig, pLogger, pShutdownNotifier);
    List<SolverContext> contexts = new ArrayList<>();
    List<FormulaManager> mgrs = new ArrayList<>();
    for (Solvers solver : options.solvers) {
      @SuppressWarnings("resource")
      SolverContext context = factory.generateContext(solver);
      contexts.add(context);
      mgrs.add(context.getFormulaManager());
    }

    PortfolioFormulaCreator creator = new PortfolioFormulaCreator();
    PortfolioFormulaManager manager = new PortfolioFormulaManager(creator, mgrs);

    return new PortfolioSolverContext(
        pConfig, pLogger, pShutdownNotifier, contexts, manager, creator);
  }

  @Override
  public FormulaManager getFormulaManager() {
    return manager;
  }

  @Override
  public ProverEnvironment newProverEnvironment0(ProverOptions... options) {
    List<ProverEnvironment> provers = new ArrayList<>();
    for (SolverContext subContext : contexts) {
      provers.add(subContext.newProverEnvironment(options));
    }
    return new PortfolioTheoremProver(creator, shutdownNotifier, provers);
  }

  @Override
  public InterpolatingProverEnvironmentWithAssumptions<?> newProverEnvironmentWithInterpolation0() {
    List<InterpolatingProverEnvironmentWithAssumptions<?>> provers = new ArrayList<>();
    for (SolverContext subContext : contexts) {
      provers.add(subContext.newProverEnvironmentWithInterpolation());
    }
    return new PortfolioInterpolatingProverEnvironment(creator, shutdownNotifier, provers);
  }

  @Override
  public OptimizationProverEnvironment newOptimizationProverEnvironment() {
    List<OptimizationProverEnvironment> provers = new ArrayList<>();
    for (SolverContext subContext : contexts) {
      provers.add(subContext.newOptimizationProverEnvironment());
    }
    return new PortfolioOptimizationProverEnvironment(creator, shutdownNotifier, provers);
  }

  @Override
  public String getVersion() {
    StringBuilder str = new StringBuilder("[");
    for (SolverContext context : contexts) {
      str.append(context.getVersion()).append(", ");
    }
    str.append("]");
    return str.toString();
  }

  @Override
  public void close() {
    for (SolverContext context : contexts) {
      context.close();
    }
  }
}
