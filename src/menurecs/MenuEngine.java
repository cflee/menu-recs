package menurecs;

import static spark.Spark.*;

import com.google.gson.Gson;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.io.IOException;
import java.util.*;

public class MenuEngine {

    static Map<String, MenuItem> menuItems;
    static Map<String, List<String>> recommendations;

    public static void main(String[] args) {
        // load data
        String dataDir = "data";
        DataFileLoader loader = new DataFileLoader(dataDir);

        try {
            menuItems = loader.readMenu();
            System.out.println("Read menu items: " + menuItems.size());

            recommendations = loader.readRecommendation();
            System.out.println("Read recommendations: " + recommendations.size());
        } catch (IOException e) {
            System.err.println("Error: could not read one or more files.");
            e.printStackTrace();
            return;
        }

        // config
        port(8080);
        // provide routes and logic
        get("/menu", (request, response) -> {
            Map<String, Map<String, String>> menu = new HashMap<>();
            for (Map.Entry<String, MenuItem> entry : menuItems.entrySet()) {
                MenuItem item = entry.getValue();
                Map<String, String> menuDetails = new HashMap<>();

                menuDetails.put("itemid", item.getId());
                menuDetails.put("price", "" + item.getPrice());
                menuDetails.put("description", item.getDescription());
                menuDetails.put("category", item.getCategory());

                menu.put(item.getId(), menuDetails);
            }
            return new Gson().toJson(menu);
        });
        get("/recommend", (request, response) -> {
            // check for required params
            String[] requiredParams = {"customerid", "outputlength", "numpax", "targetspend", "itemids", "itemqty"};
            List<String> missingParams = new ArrayList<>();
            for (String param : requiredParams) {
                if (request.queryParams(param) == null) {
                    missingParams.add(param);
                }
            }
            if (!missingParams.isEmpty()) {
                response.status(400);
                return "Missing parameter(s): " + String.join(", ", missingParams);
            }

            // check for integer params
            String[] integerParams = {"customerid", "outputlength", "numpax"};
            List<String> notIntegerParams = new ArrayList<>();
            for (String param : integerParams) {
                try {
                    Integer.parseInt(request.queryParams(param));
                } catch (NumberFormatException e) {
                    notIntegerParams.add(param);
                }
            }
            if (!notIntegerParams.isEmpty()) {
                response.status(400);
                return "Non-integer parameter(s): " + String.join(", ", notIntegerParams);
            }

            // check for double params
            String[] doubleParams = {"customerid", "outputlength", "numpax"};
            List<String> notDoubleParams = new ArrayList<>();
            for (String param : doubleParams) {
                try {
                    Double.parseDouble(request.queryParams(param));
                } catch (NumberFormatException e) {
                    notDoubleParams.add(param);
                }
            }
            if (!notDoubleParams.isEmpty()) {
                response.status(400);
                return "Non-double parameter(s): " + String.join(", ", notDoubleParams);
            }

            // all parameters are validated somewhat
            try {
                String customerId = request.queryParams("customerid");
                int outputLength = Integer.parseInt(request.queryParams("outputlength"));
                int numPax = Integer.parseInt(request.queryParams("numpax"));
                double targetSpend = Double.parseDouble(request.queryParams("targetspend"));

                Map<String, Integer> currentOrder = new HashMap<>();
                String[] itemIdsStrings = request.queryParams("itemids").split(",");
                String[] itemQtyStrings = request.queryParams("itemqty").split(",");
                if (itemIdsStrings.length != itemQtyStrings.length) {
                    throw new Exception("Differing number of item ids and qty");
                }
                for (int i = 0; i < itemIdsStrings.length; i++) {
                    int itemQty = Integer.parseInt(itemQtyStrings[i]);
                    currentOrder.put(itemIdsStrings[i], itemQty);
                }

                List<String> results = computeRecommendation(customerId, outputLength, numPax, targetSpend, currentOrder);

                return new Gson().toJson(results);
            } catch (Exception e) {
                e.printStackTrace();
                response.status(400);
                return "Some exception occurred: " + e.getClass() + ": " + e.getMessage();
            }
        });
    }

    public static List<String> computeRecommendation(String customerId, int outputLength, int numPax, double spendPerPax,
                                             Map<String, Integer> currents) {
        // process data: currently ordered items
        double curTotalPrice = 0.0;
        for (Map.Entry<String, Integer> current : currents.entrySet()) {
            System.out.print(menuItems);
            System.out.print(menuItems.get(current.getKey()));
            curTotalPrice += menuItems.get(current.getKey()).getPrice();
        }

        // process data: only get the current customer
        List<String> customerRecommendations = recommendations.get(customerId);

        // process data: prices and categories
        double[] itemPrices = new double[customerRecommendations.size()];
        int[] itemScores = new int[customerRecommendations.size()];
        int[] itemCategories = new int[customerRecommendations.size()];
        List<String> categories = new ArrayList<>();
        for (int i = 0; i < customerRecommendations.size(); i++) {
            MenuItem item = menuItems.get(customerRecommendations.get(i));
            itemScores[i] = customerRecommendations.size() - i;
            itemPrices[i] = item.getPrice();
            System.out.println("Adding recommended item " + item.getDescription() + " with price " + item.getPrice() + " and score " + itemScores[i]);

            int categoryIndex = categories.indexOf(item.getCategory());
            if (categoryIndex == -1) {
                categoryIndex = categories.size();
                categories.add(item.getCategory());
            }

            itemCategories[i] = categoryIndex;
        }

        // result list
        List<String> results = new ArrayList<>();

        // init model
        try {
            // create active model.
            // IloCplex requires linking to the native CPLEX shared library, as defined in the JVM's java.library.path
            // system property, as it represents an instance of CPLEX itself. the property must be set to dir containing
            // libcplexXXXX.jnilib (XXXX version matching the cplex.jar file), etc.
            // e.g. -Djava.library.path=/Applications/IBM/ILOG/CPLEX_Studio1261/cplex/bin/x86-64_osx
            IloCplex cplex = new IloCplex();

            // static configuration
            double largeM = 1000.0;

            // DECISION VARIABLES
            // x and y dvars are boolean
            // z is int, 0 <= z <= 1000.0
            IloIntVar[] xs = cplex.boolVarArray(customerRecommendations.size());
            IloIntVar[] ys = cplex.boolVarArray(itemCategories.length);
            IloNumVar z = cplex.numVar(0.0, 1000.0);


            // OBJECTIVE FUNCTION
            // available operators:
            // abs, constant, diff [subtract], max, min, negative, prod, scalProd, square, sum

            // maximize sum(prices * x) * (1 - 0.1 * sum(y) - z)
            IloLinearIntExpr totalScores = cplex.scalProd(itemScores, xs);
            IloNumExpr categoryPenalty = cplex.diff(1, cplex.prod(0.1, cplex.sum(ys)));
            IloNumExpr obj = cplex.prod(totalScores, cplex.diff(categoryPenalty, z));
            cplex.addMaximize(obj);

            System.out.println("Quadratic objective? " + cplex.isQO());
            System.out.println("Quadratic constraint? " + cplex.isQC());


            // CONSTRAINTS
            // available operators:
            // addEq, addGe, addLe, and, not, or, eq, ge, le, ifThen

            // number of things to output
            // outputLength == sum(xs)
            cplex.addEq(outputLength, cplex.sum(xs), "outputLength");

            // totalPrices <= (1 + Z) * (NS - amount spent)
            IloLinearNumExpr totalPrices = cplex.scalProd(itemPrices, xs);
            double budget = numPax * spendPerPax;
            double remainingBudget = budget - curTotalPrice;
            System.out.println("Budget: " + budget);
            System.out.println("Remaining budget: " + remainingBudget);
            cplex.addLe(totalPrices, cplex.prod(cplex.sum(1, z), remainingBudget), "budget");

            // make the category's y 1 if exceeding 1 item per category
            // x <= 1 + My
            IloLinearIntExpr[] categoryConstraints = new IloLinearIntExpr[categories.size()];
            for (int i = 0; i < categoryConstraints.length; i++) {
                categoryConstraints[i] = cplex.linearIntExpr();
            }
            for (int i = 0; i < customerRecommendations.size(); i++) {
                categoryConstraints[itemCategories[i]].addTerm(1, xs[i]);
            }
            for (int i = 0; i < categoryConstraints.length; i++) {
                cplex.addLe(categoryConstraints[i], cplex.sum(1, cplex.prod(largeM, ys[i])), "category"
                        + categories.get(i));
            }

            // just write this lp file out for fun
            cplex.exportModel("test.lp");

            // provide visual separation from cplex engine's output
            System.out.println("=== STARTING CPLEX");

            // solve
            if (cplex.solve()) {
                System.out.println("=== CPLEX STATUS");
                System.out.println(cplex.getStatus());
                System.out.println("=== OBJECTIVE VALUE");
                System.out.println(cplex.getObjValue());
                System.out.println("=== SOLUTION VALUES");
                for (int i = 0; i < customerRecommendations.size(); i++) {
                    if (cplex.getValue(xs[i]) == 1) {
                        MenuItem item = menuItems.get(customerRecommendations.get(i));
                        System.out.println(item.getId() + "," + item.getDescription() + "," + item.getCategory()
                                + "," + item.getPrice());
                        results.add(item.getId());
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

        return results;
    }
}
