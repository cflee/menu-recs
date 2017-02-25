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

    public Map<String, Double> readConfig() throws IOException {
        Map<String, Double> config = new HashMap<>();

        Scanner sc;
        try (Reader in = new FileReader(dataDir + File.separator + "config.txt")) {
            sc = new Scanner(in);
            config.put("outputLength", (double) sc.nextInt());
            config.put("numOfPax", (double) sc.nextInt());
            config.put("spendPerPax", sc.nextDouble());
            sc.close();
        }

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

    public List<String> readRecommendation() throws IOException {
        List<String> recommendations = new ArrayList<>();

        Reader in = new FileReader(dataDir + File.separator + "recommendation.csv");
        CSVFormat format = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord();
        for (CSVRecord record : format.parse(in)) {
            String itemId = record.get("Item");
            recommendations.add(itemId);
        }

        return recommendations;
    }
}
