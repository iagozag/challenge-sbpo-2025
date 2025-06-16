package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import ilog.concert.*;
import ilog.cplex.*;

// import java.io.FileWriter;
// import java.io.IOException;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nOrders;
    protected int nItems;
    protected int nAisles;
    protected int waveSizeLB;
    protected int waveSizeUB;

    protected String outputFile;

    protected int[] sum_orders;
    protected int[] sum_aisles;

    Graph g;

    IloCplex cplex;

    protected IloIntVar[] x;
    protected IloIntVar[] y;
    protected IloNumVar[] f;

    IloObjective obj;
    IloLinearNumExpr expr;
    IloConstraint constraint;

    public ChallengeSolver(
        List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nOrders, int nItems, int nAisles, int waveSizeLB, int waveSizeUB, String outputFile) {
        this.orders = orders;
        this.aisles = aisles;
        this.nOrders = nOrders;
        this.nItems = nItems;
        this.nAisles = nAisles;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
        this.outputFile = outputFile;
        this.g = new Graph(2+nOrders+nItems+nAisles, nOrders, nItems, nAisles);

        this.sum_orders = new int[nOrders];
        this.sum_aisles = new int[nAisles];
        for (int i = 0; i < nOrders; i++) {
            sum_orders[i] = orders.get(i).values().stream().mapToInt(Integer::intValue).sum();
        }
        for (int i = 0; i < nAisles; i++) {
            sum_aisles[i] = aisles.get(i).values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    protected void build_graph(){
        // s -> o
        for(int i = 0; i < nOrders; i++){
            g.addEdge(0, g.getId(1, i), sum_orders[i]);
        }

        // o -> i
        for(int i = 0; i < nOrders; i++){
            for (Map.Entry<Integer, Integer> entry : orders.get(i).entrySet()) {
                Integer item = entry.getKey();
                Integer qnt = entry.getValue();
                g.addEdge(g.getId(1, i), g.getId(2, item), qnt);
            }
        }

        // i -> a
        for(int a = 0; a < nAisles; a++){
            for (Map.Entry<Integer, Integer> entry : aisles.get(a).entrySet()) {
                Integer item = entry.getKey();
                Integer qnt = entry.getValue();
                g.addEdge(g.getId(2, item), g.getId(3, a), qnt);
            }
        }

        // a -> t
        for(int i = 0; i < nAisles; i++){
            g.addEdge(g.getId(3, i), 1, sum_aisles[i]);
        }

        // g.printGraph();
    }

    protected void create_flow_model(){
        try{
            int N = g.getNumNodes();
            int M = g.getNumEdges();

            cplex = new IloCplex();

            // (16)
            x = cplex.intVarArray(nOrders, 0, 1); 

            // (17)
            y = cplex.intVarArray(nAisles, 0, 1); 

            // (18)
            f = cplex.numVarArray(M, 0, Double.MAX_VALUE);

            expr = cplex.linearNumExpr();
            for(Edge e: g.getEdges(0))
                expr.addTerm(1.0, f[e.id]);

            // ()
            cplex.addGe(expr, waveSizeLB);

            // (12)
            cplex.addLe(expr, waveSizeUB);

            // (9)
            for(int i = 2; i < N; i++){
                expr = cplex.linearNumExpr();

                for(Edge e: g.getEdges(i))
                    expr.addTerm(-1.0, f[e.id]);

                for(Edge e: g.getEdgesT(i))
                    expr.addTerm(1.0, f[e.id]);

                cplex.addEq(expr, 0);
            }

            // (10)
            for(int i = 0; i < N; i++){
                for(Edge e: g.getEdges(i)){
                    cplex.addLe(f[e.id], e.cap);
                }
            }

            // (11)
            int j = 0;
            for(Edge e: g.getEdges(0)){
                cplex.addEq(f[e.id], cplex.prod(x[j], e.cap));
                j++;
            }

            // (14) and (15)
            for(int i = 0; i < nAisles; i++){
                expr = cplex.linearNumExpr();

                for(Edge e: g.getEdgesT(g.getId(3, i))){
                    expr.addTerm(1.0, f[e.id]);
                }
                
                cplex.addLe(y[i], expr);

                int t_a = g.getEdges(g.getId(3, i)).get(0).id;
                cplex.addGe(y[i], cplex.prod(expr, 1.0/t_a));
            }
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    public void print_sol(){
        try{
            System.out.println("X: ");

            for (int i = 0; i < nOrders; i++) {
                System.out.print(cplex.getValue(x[i]) + " ");
            }

            System.out.println();
            System.out.println("Y: ");

            for (int i = 0; i < nAisles; i++) {
                System.out.print(cplex.getValue(y[i]) + " ");
            }

            System.out.println();
            System.out.println("F: ");

            for (int i = 0; i < g.getNumEdges(); i++) {
                System.out.print(cplex.getValue(f[i]) + " ");
            }

            System.out.println();
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    public ChallengeSolution get_solution(){
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();

        try{
            for (int i = 0; i < nOrders; i++) {
                if (cplex.getValue(x[i]) > 0.0) {
                    selectedOrders.add(i);
                }
            }

            for (int i = 0; i < nAisles; i++) {
                if (cplex.getValue(y[i]) > 0.0) {
                    selectedAisles.add(i);
                }
            }
        } catch (IloException e) {
            e.printStackTrace();
        }

        return new ChallengeSolution(selectedOrders, selectedAisles);
    }

	protected boolean setTimeLimit(StopWatch stopWatch){
		long cplexTimeLimit = getRemainingTime(stopWatch)-15;
		if(cplexTimeLimit < 0) return false;
		try{
			cplex.setParam(IloCplex.Param.TimeLimit, cplexTimeLimit);
		} catch(IloException e){
			e.printStackTrace();
		}

		return true;
	}

    public ChallengeSolution solve(StopWatch stopWatch) {
        try{
            if (!stopWatch.isStarted()) {
                stopWatch.start();
            }

            constraint = null;

            build_graph();

            create_flow_model();

            obj = cplex.addMinimize(cplex.sum(y));

            Challenge challenge = new Challenge();

            ChallengeSolution solution = null;

            int minimum_aisles = nAisles;
			try{
				setTimeLimit(stopWatch);
				if(cplex.solve()){
                    minimum_aisles = (int)cplex.getObjValue();
                    solution = get_solution();
                    challenge.writeOutput(solution, outputFile);
                }
			} catch (IloException e) {}

			cplex.remove(obj);

            // (8)
            expr = cplex.linearNumExpr();
            for(Edge e: g.getEdges(0)){
                expr.addTerm(1.0, f[e.id]);
            }

            obj = cplex.addMaximize(expr);

            double best = 0;
            for(int K = minimum_aisles; K <= nAisles; K++){
				if(!setTimeLimit(stopWatch))
					break;

                if(constraint != null) cplex.remove(constraint);
                constraint = cplex.addEq(cplex.sum(y), K);

                try{
                    if(!cplex.solve())
                        break;
                } catch(IloException e){
                    break;
                }

                if (cplex.getObjValue()/K > best) {
                    best = cplex.getObjValue()/K;
                    solution = get_solution();
                    challenge.writeOutput(solution, outputFile);
                }
            }

            return solution;
        } catch (IloException e) {
            e.printStackTrace();
        }

        return null;
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }

    protected void destroy() {
        try {
            x = null;
            y = null;
            f = null;

            obj = null;
            expr = null;
            constraint = null;

            if (cplex != null) {
                cplex.end();
                cplex = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }
}
