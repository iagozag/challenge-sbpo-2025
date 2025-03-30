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

    protected int[] sum;

    IloCplex cplex;

    protected IloIntVar[] x;
    protected IloIntVar[] y;
    
    IloObjective obj;
    IloLinearNumExpr restriction;
    IloConstraint constraint;

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

    protected void create_model(){
        try{
            cplex = new IloCplex();

            x = new IloIntVar[nOrders]; 
            y = new IloIntVar[nAisles]; 

            for (int i = 0; i < nOrders; i++) {
                x[i] = cplex.intVar(0, 1, "x_" + i);
            }
            for (int i = 0; i < nAisles; i++) {
                y[i] = cplex.intVar(0, 1, "y_" + i);
            }

            restriction = cplex.linearNumExpr();
            for (int i = 0; i < nOrders; i++) {
                restriction.addTerm(sum[i], x[i]);
            }

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
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    public ChallengeSolution brute(){
        try{

            double best = 0.0;
            Set<Integer> bestSelectedOrders = new HashSet<>();
            Set<Integer> bestSelectedAisles = new HashSet<>();

            create_model();

            obj = null;
            constraint = null;

            for(int K = 1; K <= nAisles; K++){
                if (obj != null) cplex.remove(obj);
                if (constraint != null) cplex.remove(constraint);

                obj = cplex.addMaximize(cplex.prod(restriction, 1.0/K));
                constraint = cplex.addEq(cplex.sum(y), K);

                if (cplex.solve() && cplex.getObjValue() > best) {
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
            }

            return new ChallengeSolution(bestSelectedOrders, bestSelectedAisles);
        } catch (IloException e) {
            e.printStackTrace();
        }

        return null;
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();

        sum = new int[nOrders];
        for (int i = 0; i < nOrders; i++) {
            sum[i] = orders.get(i).values().stream().mapToInt(Integer::intValue).sum();
        }

       // try{
       //     create_model();

       //     int l = 1, r = nAisles;
       //     while (r-l >= 3) {
       //         int m1 = l + (r - l) / 3;
       //         int m2 = r - (r - l) / 3;

       //         obj = cplex.addMaximize(cplex.prod(restriction, 1.0/m1));
       //         constraint = cplex.addEq(cplex.sum(y), m1);

       //         boolean b1 = cplex.solve();

       //         if(!b1){
       //             l = m1;
       //             cplex.remove(obj);
       //             cplex.remove(constraint);
       //             continue;
       //         }

       //         double objc1 = cplex.getObjValue();

       //         cplex.remove(obj);
       //         cplex.remove(constraint);


       //         obj = cplex.addMaximize(cplex.prod(restriction, 1.0/m2));
       //         constraint = cplex.addEq(cplex.sum(y), m2);

       //         cplex.solve();
       //         double objc2 = cplex.getObjValue();

       //         if (objc1 < objc2) l = m1;
       //         else r = m2;

       //         cplex.remove(obj);
       //         cplex.remove(constraint);
       //     }

       //     double best = 0.0;
       //     for(int K = l; K <= r; K++){
       //         obj = cplex.addMaximize(cplex.prod(restriction, 1.0/K));
       //         constraint = cplex.addEq(cplex.sum(y), K);

       //         if (cplex.solve() && cplex.getObjValue() > best) {
       //             best = cplex.getObjValue();

       //             Set<Integer> tmpSelectedOrders = new HashSet<>();
       //             Set<Integer> tmpSelectedAisles = new HashSet<>();

       //             for (int i = 0; i < nOrders; i++) {
       //                 if (cplex.getValue(x[i]) > 0.5)
       //                     tmpSelectedOrders.add(i);
       //             }

       //             for (int i = 0; i < nAisles; i++) {
       //                 if (cplex.getValue(y[i]) > 0.5)
       //                     tmpSelectedAisles.add(i);
       //             }

       //             selectedOrders = tmpSelectedOrders;
       //             selectedAisles = tmpSelectedAisles;
       //         }

       //         cplex.remove(obj);
       //         cplex.remove(constraint);
       //     }

       //     return new ChallengeSolution(selectedOrders, selectedAisles);
       // } catch (IloException e) {
       //     e.printStackTrace();
       // }

       // return null;

       return brute();
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
