package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import ilog.concert.*;
import ilog.cplex.*;

import java.io.FileWriter;
import java.io.IOException;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nOrders;
    protected int nItems;
    protected int nAisles;
    protected int waveSizeLB;
    protected int waveSizeUB;

    public ChallengeSolver(
        List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nOrders, int nItems, int nAisles, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nOrders = nOrders;
        this.nItems = nItems;
        this.nAisles = nAisles;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        try (FileWriter writer = new FileWriter("data/instance_0020.txt")){
            writer.write("K best\n");

            double best = 0.0;
            Set<Integer> bestSelectedOrders = new HashSet<>();
            Set<Integer> bestSelectedAisles = new HashSet<>();

            for(int K = 1; K <= nAisles; K++){
                int[] sum = new int[nOrders];
                for (int i = 0; i < nOrders; i++) {
                    sum[i] = orders.get(i).values().stream().mapToInt(Integer::intValue).sum();
                }

                IloCplex cplex = new IloCplex();

                IloIntVar[] x = new IloIntVar[nOrders]; 
                IloIntVar[] y = new IloIntVar[nAisles]; 

                for (int i = 0; i < nOrders; i++) {
                    x[i] = cplex.intVar(0, 1, "x_" + i);
                }
                for (int i = 0; i < nAisles; i++) {
                    y[i] = cplex.intVar(0, 1, "y_" + i);
                }

                cplex.addEq(cplex.sum(y), K);

                IloLinearNumExpr restriction = cplex.linearNumExpr();
                for (int i = 0; i < nOrders; i++) {
                    restriction.addTerm(sum[i], x[i]);
                }
                cplex.addMaximize(cplex.prod(restriction, 1.0/K));

                cplex.addGe(restriction, waveSizeLB);
                cplex.addLe(restriction, waveSizeUB);

                for (int i = 0; i < nItems; i++) {
                    IloLinearNumExpr itemOrdRestriction = cplex.linearNumExpr();
                    for (int j = 0; j < nOrders; j++) {
                        if (orders.get(j).containsKey(i)) {
                            itemOrdRestriction.addTerm(x[j], orders.get(j).get(i));
                        }
                    }

                    IloLinearNumExpr itemAisleRestriction = cplex.linearNumExpr();
                    for (int j = 0; j < nAisles; j++) {
                        if (aisles.get(j).containsKey(i)) {
                            itemAisleRestriction.addTerm(y[j], aisles.get(j).get(i));
                        }
                    }

                    cplex.addLe(itemOrdRestriction, itemAisleRestriction);
                }

                boolean ok = cplex.solve();
                if (ok && cplex.getObjValue() > best) {
                    best = cplex.getObjValue();
                    Set<Integer> selectedOrders = new HashSet<>();
                    Set<Integer> selectedAisles = new HashSet<>();

                    for (int i = 0; i < nOrders; i++) {
                        if (cplex.getValue(x[i]) > 0.5) {
                            selectedOrders.add(i);
                        }
                    }

                    for (int i = 0; i < nAisles; i++) {
                        if (cplex.getValue(y[i]) > 0.5) {
                            selectedAisles.add(i);
                        }
                    }

                    bestSelectedOrders = selectedOrders;
                    bestSelectedAisles = selectedAisles;
                }
                writer.write(K + " " + (ok ? cplex.getObjValue() : 0) + "\n");
            }

            return new ChallengeSolution(bestSelectedOrders, bestSelectedAisles);
        } catch (IloException | IOException e) {
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
}
