package com.huaweicloud.iiot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ApiDataSource {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();
    private static final String API_URL = "https://your host/v1/iotstage/auth/tokens";
    private static final String ProjectId = "your project id";
    private static final String ClientId = "user name";
    private static final String SecretId = "password";
    private static final Map<String, String> device2rules = new HashMap<>();
    private static final String API_DATA_SOURCE = "你的数据源链接";
    static {
        device2rules.put("MTO-C-001", "/device_code\tstring\t10\n" +
                "/Cyclone_Gas_Outlet_Zone/Vapor_Flow_Rate\tdouble\t1\t2\n");
    }


    public static void main(String[] args) throws Exception {
        String token = getToken(ClientId, SecretId, ProjectId, API_URL);
        OkHttpClient client = createUnsafeOkHttpClient();

        for (String deviceId : device2rules.keySet()) {
            String data = getReportData(device2rules.get(deviceId), deviceId);
            RequestBody body = RequestBody.create(data, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(API_DATA_SOURCE)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Auth-Token", token)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Send Message Success: " + response.code());
                } else {
                    System.err.println("Failed to send message: " + response.code() + " " + response.message());
                }
            }
        }

    }
    /**
     * 规则格式:属性路\t属性类型\t取值范围
     *
     * @param rules    点位的规则
     * @param deviceId 设备ID
     * @return 支持通过API数据源上报的数据信息
     */
    public static String getReportData(String rules, String deviceId) throws Exception {
        if (rules == null || deviceId == null || rules.isEmpty()) {
            return "";
        }
        String[] ruleParts = rules.split("\n");
        Map<String, Map<String, PropertyDataType>> propertyDataTypes = new HashMap<>();
        for (String rulePart : ruleParts) {
            String[] propertyParts = rulePart.split("\t");
            String serviceId = getServiceId(propertyParts[0]);
            Map<String, PropertyDataType> servicePropertyDataTypes = propertyDataTypes.getOrDefault(serviceId, new HashMap<>());
            String propertyId = getPropertyId(propertyParts[0]);
            PropertyDataType propertyDataType = createPropertyDataType(joinPropertyPartsFromSecondElement(propertyParts));
            servicePropertyDataTypes.put(propertyId, propertyDataType);
            propertyDataTypes.put(serviceId, servicePropertyDataTypes);
        }

        ArrayNode services = mapper.createArrayNode();
        for (Map.Entry<String, Map<String, PropertyDataType>> entry : propertyDataTypes.entrySet()) {
            String serviceId =entry.getKey();
            ObjectNode properties = mapper.createObjectNode();
            for (Map.Entry<String, PropertyDataType> propertyPropertyDataTypes : entry.getValue().entrySet()) {
                String propertyId = propertyPropertyDataTypes.getKey();
                PropertyDataType propertyDataType = propertyPropertyDataTypes.getValue();
                switch (propertyDataType.getType()) {
                    case "string":
                        String value = UUID.randomUUID().toString();
                        if (value.length() > propertyDataType.getMaxLength()) {
                            value = value.substring(0, propertyDataType.getMaxLength());
                        }
                        properties.put(propertyId, value);
                        break;
                    case "integer":
                        properties.put(propertyId, random.nextInt(propertyDataType.getMaxIntValue() - propertyDataType.getMinIntValue() + 1) + propertyDataType.getMinIntValue());
                        break;
                    case "double":
                        properties.put(propertyId, random.nextDouble() * (propertyDataType.getMaxDoubleValue() - propertyDataType.getMinDoubleValue()) + propertyDataType.getMinDoubleValue());
                        break;
                    case "bool":
                        properties.put(propertyId, random.nextBoolean());
                        break;
                }
            }
            ObjectNode service = mapper.createObjectNode();
            service.put("event_time", getEventTime());
            service.put("service_id", serviceId);
            service.set("properties", properties);
            services.add(service);

        }

        ObjectNode device = mapper.createObjectNode();
        device.put("device_id", deviceId);
        device.set("services", services);

        ArrayNode devices = mapper.createArrayNode();
        devices.add(device);

        ObjectNode result = mapper.createObjectNode();
        result.set("devices", devices);

        return mapper.writeValueAsString(result);
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

    public static String joinPropertyPartsFromSecondElement(String[] propertyParts) {
        if (propertyParts == null || propertyParts.length <= 1) {
            return ""; // 如果数组为空或只有一个元素，则返回空字符串
        }

        StringBuilder result = new StringBuilder();
        for (int i = 1; i < propertyParts.length; i++) {
            if (i > 1) {
                result.append("\t"); // 从第二个元素开始，每个元素前添加制表符
            }
            result.append(propertyParts[i]);
        }

        return result.toString();
    }

    public static PropertyDataType createPropertyDataType(String define) {
        if (define == null || define.isEmpty()) {
            return null;
        }
        String[] typeParts = define.split("\t");
        if (typeParts.length == 0) {
            return null;
        }
        String dataType = typeParts[0].trim();
        switch (dataType) {
            case "string" -> {
                int maxLength = Integer.parseInt(typeParts[1].trim());
                PropertyDataType propertyDataType = new PropertyDataType();
                propertyDataType.setType(dataType);
                propertyDataType.setMaxLength(maxLength);
                return propertyDataType;
            }
            case "integer" -> {
                int minIntValue = Integer.parseInt(typeParts[1].trim());
                int maxIntValue = Integer.parseInt(typeParts[2].trim());
                PropertyDataType propertyDataType = new PropertyDataType();
                propertyDataType.setType(dataType);
                propertyDataType.setMinIntValue(minIntValue);
                propertyDataType.setMaxIntValue(maxIntValue);
                return propertyDataType;
            }
            case "double" -> {
                double minDoubleValue = Double.parseDouble(typeParts[1].trim());
                double maxDoubleValue = Double.parseDouble(typeParts[2].trim());
                PropertyDataType propertyDataType = new PropertyDataType();
                propertyDataType.setType(dataType);
                propertyDataType.setMinDoubleValue(minDoubleValue);
                propertyDataType.setMaxDoubleValue(maxDoubleValue);
                return propertyDataType;
            }
            case "bool" -> {
                PropertyDataType propertyDataType = new PropertyDataType();
                propertyDataType.setType(dataType);
                return propertyDataType;
            }
            default -> {
                return null;
            }
        }
    }

    public static String getEventTime() {
        return dateTimeFormatter.format(Instant.now());
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * 获取访问令牌
     * @param clientId 平台用户名
     * @param secretId 平台用户密码
     * @param projectId 项目ID
     * @param tokenUrl Token获取URL
     * @return 响应结果
     * @throws Exception 网络请求异常
     */
    public static String getToken(String clientId, String secretId, String projectId, String tokenUrl) throws Exception {
        // 构建JSON请求体
        String json = String.format(
                "{\"client_id\":\"%s\",\"secret_id\":\"%s\",\"project\":{\"id\":\"%s\"}}",
                clientId, secretId, projectId
        );

        RequestBody body = RequestBody.create(json, JSON);

        // 构建请求
        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build();

        // 发送请求并获取响应
        try (Response response = createUnsafeOkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("获取token失败: " + response.code() + " " + response.message());
            }

            return response.header("X-Subject-Token");
        }
    }

    public static OkHttpClient createUnsafeOkHttpClient() {
        try {
            // 创建信任所有证书的TrustManager
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 初始化SSL上下文
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 创建SSLSocketFactory
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // 构建OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // 跳过主机名验证
                }
            });

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
