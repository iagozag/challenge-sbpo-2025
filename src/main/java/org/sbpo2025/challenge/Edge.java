package org.sbpo2025.challenge;

import java.util.*;

public class Edge {
    int to;
    int cap;
    int id;

    public Edge(int to, int capacity, int id) {
        this.to = to;
        this.cap = capacity;
        this.id = id;
    }
}