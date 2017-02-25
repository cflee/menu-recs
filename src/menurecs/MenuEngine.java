package menurecs;

import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.io.IOException;
import java.util.*;

public class MenuEngine {
    public static void main(String[] args) {
        // load data
        String dataDir = "data";
        DataFileLoader loader = new DataFileLoader(dataDir);
        Map<String, Double> config;
        Map<String, MenuItem> menuItems;
        Map<String, Integer> currents;
        List<String> recIds;
        try {
            config = loader.readConfig();

            menuItems = loader.readMenu();
            System.out.println("Read menu items: " + menuItems.size());

            currents = loader.readCurrent();
            System.out.println("Read current: " + currents.size());

            recIds = loader.readRecommendation();
            System.out.println("Read recommendation: " + recIds.size());
        } catch (IOException e) {
            System.err.println("Error: could not read one or more files.");
            e.printStackTrace();
            return;
        }

        // process data: prices and categories
        double[] recPrices = new double[recIds.size()];
        int[] recCategories = new int[recIds.size()];
        List<String> categories = new ArrayList<>();
        for (int i = 0; i < recIds.size(); i++) {
            MenuItem item = menuItems.get(recIds.get(i));
            recPrices[i] = item.getPrice();

            int categoryIndex = categories.indexOf(item.getCategory());
            if (categoryIndex == -1) {
                categoryIndex = categories.size();
                categories.add(item.getCategory());
            }

            recCategories[i] = categoryIndex;
        }

        // init model
        try {
            // create active model.
            // IloCplex requires linking to the native CPLEX shared library, as defined in the JVM's java.library.path
            // system property, as it represents an instance of CPLEX itself. the property must be set to dir containing
            // libcplexXXXX.jnilib (XXXX version matching the cplex.jar file), etc.
            // e.g. -Djava.library.path=/Applications/IBM/ILOG/CPLEX_Studio1261/cplex/bin/x86-64_osx
            IloCplex cplex = new IloCplex();

            // create variables
            IloIntVar[] xs = cplex.boolVarArray(recIds.size());
            IloIntVar[] ys = cplex.boolVarArray(recCategories.length);
            IloNumVar z = cplex.numVar(1.0, 1000.0);

            // create objective function
            // available operators:
            // abs, constant, diff [subtract], max, min, negative, prod, scalProd, square, sum
            IloLinearNumExpr totalPrices = cplex.scalProd(recPrices, xs);
            IloNumExpr categoryPenalty = cplex.diff(1, cplex.prod(0.1, cplex.sum(ys)));
            IloNumExpr budgetPenalty = cplex.diff(2, z); // 1.05 -> deduct 5%. so max 2?
//            IloNumExpr obj = cplex.prod(totalPrices, cplex.prod(categoryPenalty, budgetPenalty));
            IloNumExpr obj = cplex.prod(totalPrices, categoryPenalty);
            cplex.addMaximize(obj);

            System.out.println("QO " + cplex.isQO());

            // create constraints
            // available operators:
            // addEq, addGe, addLe, and, not, or, eq, ge, le, ifThen
            cplex.addEq(config.get("outputLength"), cplex.sum(xs), "outputLength");
            cplex.addLe(totalPrices, cplex.prod(z, config.get("numOfPax") * config.get("spendPerPax")), "budget");
            IloLinearIntExpr[] categoryConstraints = new IloLinearIntExpr[categories.size()];
            for (int i = 0; i < categoryConstraints.length; i++) {
                categoryConstraints[i] = cplex.linearIntExpr();
            }
            for (int i = 0; i < recIds.size(); i++) {
                categoryConstraints[recCategories[i]].addTerm(1, xs[i]);
            }
            for (int i = 0; i < categoryConstraints.length; i++) {
                cplex.addLe(categoryConstraints[i], cplex.sum(1, cplex.prod(1000, ys[i])), "category"
                        + categories.get(i));
            }
            cplex.addGe(z, 1, "budgetMin");

            cplex.exportModel("test.lp");

            // solve
            if (cplex.solve()) {
                System.out.println("=== CPLEX STATUS");
                System.out.println(cplex.getStatus());
                System.out.println("=== OBJECTIVE VALUE");
                System.out.println(cplex.getObjValue());
                System.out.println("=== SOLUTION VALUES");
                for (int i = 0; i < recIds.size(); i++) {
                    if (cplex.getValue(xs[i]) == 1) {
                        MenuItem item = menuItems.get(recIds.get(i));
                        System.out.println(item.getId() + "," + item.getDescription() + "," + item.getCategory()
                                + "," + item.getPrice());
                    }
                }
            } else {
                System.out.println("Uh oh, no solution found.");
                System.out.println("=== CPLEX STATUS");
                System.out.println(cplex.getStatus());
            }

            // cleanup
            cplex.end();
        } catch (IloException e) {
            System.err.println("Error: encountered some problem with CPLEX.");
            e.printStackTrace();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Error: could not find CPLEX shared library.");
            e.printStackTrace();
        }
    }
}
