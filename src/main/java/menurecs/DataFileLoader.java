package menurecs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class DataFileLoader {
    String dataDir;

    public DataFileLoader(String dataDir) {
        this.dataDir = dataDir;
    }

    public Map<String, String> readConfig() throws IOException {
        Map<String, String> config = new HashMap<>();

        Scanner sc;
        try (Reader in = new FileReader(dataDir + File.separator + "config.txt")) {
            sc = new Scanner(in);
            config.put("outputLength", "" + sc.nextInt());
            config.put("numOfPax", "" + sc.nextInt());
            config.put("spendPerPax", "" + sc.nextDouble());
            config.put("customerId", sc.next());
            sc.close();
        }

        System.out.println("Read config: " + config.toString());

        return config;
    }

    public Map<String, MenuItem> readMenu() throws IOException {
        Map<String, MenuItem> itemMap = new HashMap<>();

        Reader in = new FileReader(dataDir + File.separator + "menu.csv");
        CSVFormat format = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord();
        for (CSVRecord record : format.parse(in)) {
            String itemId = record.get("Item");
            String category = record.get("Category");
            String description = record.get("Item Description");
            float price = Float.parseFloat(record.get("Price"));
            MenuItem item = new MenuItem(itemId, category, description, price);
            itemMap.put(itemId, item);
        }

        return itemMap;
    }

    public Map<String, Integer> readCurrent() throws IOException {
        Map<String, Integer> currentMap = new HashMap<>();

        Reader in = new FileReader(dataDir + File.separator + "current.csv");
        CSVFormat format = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord();
        for (CSVRecord record : format.parse(in)) {
            String itemId = record.get("Item");
            int quantity = Integer.parseInt(record.get("Quantity"));
            currentMap.put(itemId, quantity);
        }

        return currentMap;
    }

    public Map<String, List<String>> readRecommendation() throws IOException {
        Map<String, List<String>> recommendations = new HashMap<>();

        Reader in = new FileReader(dataDir + File.separator + "recommendation.csv");
        CSVFormat format = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord();
        for (CSVRecord record : format.parse(in)) {
            String user = record.get("user");
            List<String> recItems = new ArrayList<>();
            // iterate over items
            for (int i = 1; i <= 162; i++) {
                // needs to be a string instead of int, as it's a column name, not index
                String item = record.get("" + i);
                if (!item.equals("TAKEAWAY")) {
                    recItems.add(item);
                }
            }
            recommendations.put(user, recItems);
        }

        return recommendations;
    }
}
