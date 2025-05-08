package org.sbpo2025.challenge;

import java.util.*;

public class Graph {
    private int numNodes;
    private int numEdges;
    private final int nOrders;
    private final int nItems;
    private final int nAisles;
    private final List<List<Edge>> adj;
    private final List<List<Edge>> adj_t;

    public Graph(int numNodes, int nOrders, int nItems, int nAisles) {
        this.numNodes = numNodes;
        this.numEdges = 0;
        this.nOrders = nOrders;
        this.nItems = nItems;
        this.nAisles = nAisles;
        this.adj = new ArrayList<>();
        this.adj_t = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            adj.add(new ArrayList<>());
            adj_t.add(new ArrayList<>());
        }
    }

    public int getNumNodes(){ return numNodes; }

    public int getNumEdges(){ return numEdges; }

    public void addEdge(int from, int to, int capacity) {
        adj.get(from).add(new Edge(to, capacity, numEdges));
        adj_t.get(to).add(new Edge(from, capacity, numEdges));
        numEdges++;
    }

    public List<Edge> getEdges(int node) {
        return adj.get(node);
    }

    public List<Edge> getEdgesT(int node) {
        return adj_t.get(node);
    }

    public int getId(int op, int i){
        // s, t -> 0 - s, 1 - t
        if(op <= 0) return i;

        // orders
        if(op == 1) return 2+i;

        // items
        if(op == 2) return 2+nOrders+i;

        // aisles
        return 2+nOrders+nItems+i;
    }

    public void printGraph() {
        System.out.println();
        for (int i = 0; i < numNodes; i++) {
            for (Edge e : adj.get(i)) {
                System.out.println(i + "-(" + e.cap + ")>" + e.to);
            }
        }
        System.out.println();
    }
}