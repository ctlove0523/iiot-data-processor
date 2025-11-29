package com.huaweicloud.iiot;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfigureLoader {

    public static Map<String,String> loadDevice2ModelInfo() {
        Map<String,String> result = new HashMap<>();
        try (InputStream inputStream = ConfigureLoader.class.getClassLoader().getResourceAsStream("device2model.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] lineParts = line.split("\t");
                result.put(lineParts[0], lineParts[1]);
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static Map<String, Map<String, Map<String, PropertyDataType>>> loadModelInfo() {

        Map<String, Map<String, Map<String, PropertyDataType>>> result = new HashMap<>();
        try (InputStream inputStream = ConfigureLoader.class.getClassLoader().getResourceAsStream("properties.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] lineParts = line.split("\t");
                String modelId = lineParts[0];
                Map<String,Map<String, PropertyDataType>> modelMap = result.getOrDefault(modelId, new HashMap<>());
                result.put(modelId, modelMap);

                String serviceId = getServiceId(lineParts[1]);
                Map<String, PropertyDataType> serviceMap = modelMap.getOrDefault(serviceId, new HashMap<>());
                modelMap.put(serviceId, serviceMap);

                String propertyId = getPropertyId(lineParts[1]);

                String dataType = lineParts[2];
                PropertyDataType propertyDataType = new PropertyDataType();
                propertyDataType.setType(dataType);
                switch (dataType) {
                    case "string" -> {
                        int minLength = Integer.parseInt(lineParts[3]);
                        int maxLength = Integer.parseInt(lineParts[4]);
                        propertyDataType.setMaxLength(maxLength);
                        propertyDataType.setMinLength(minLength);
                    }
                    case "integer" -> {
                        int minIntValue = Integer.parseInt(lineParts[3]);
                        int maxIntValue = Integer.parseInt(lineParts[4]);
                        propertyDataType.setMinIntValue(minIntValue);
                        propertyDataType.setMaxIntValue(maxIntValue);
                    }
                    case "double" -> {
                        double minDoubleValue = Double.parseDouble(lineParts[3]);
                        double maxDoubleValue = Double.parseDouble(lineParts[4]);
                        propertyDataType.setMinDoubleValue(minDoubleValue);
                        propertyDataType.setMaxDoubleValue(maxDoubleValue);
                        serviceMap.put(propertyId, propertyDataType);
                    }
                    case "bool" -> {
                        System.out.println("no need to process bool type");
                    }
                    default -> {
                        System.out.println("数据类型错误");
                    }
                }

                serviceMap.put(propertyId, propertyDataType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String getServiceId(String path) {
        // 先不做异常数据处理
        String[] pathArray = path.split("/");
        if (pathArray.length == 2) {
            return "#";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < pathArray.length-1; i++) {
            sb.append("#");
            sb.append(pathArray[i]);
        }

        return sb.toString();
    }

    public static String getPropertyId(String path) {
        // 先不做异常数据处理
        String[] pathArray = path.split("/");
        return pathArray[pathArray.length-1];
    }
}
