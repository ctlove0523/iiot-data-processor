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
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ApiDataSource {
	private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Random random = new Random();
	private static final String TOKEN_API_URL = "";
	private static final String ProjectId = "";
	private static final String ClientId = "";
	private static final String SecretId = "";
	private static final Map<String, String> device2Model = ConfigureLoader.loadDevice2ModelInfo();
	private static final Map<String, Map<String, Map<String, PropertyDataType>>> modelInfo = ConfigureLoader.loadModelInfo();
	private static final String API_DATA_SOURCE = "";
	private static final String token = getToken(ClientId, SecretId, ProjectId, TOKEN_API_URL);
	private static final OkHttpClient client = createUnsafeOkHttpClient();

	public static void main(String[] args) throws Exception {
		reportData(10, 5);
	}

	public static void reportData(int times, int interval) throws Exception {
		for (int i = 0; i < times; i++) {
            reportData();
            TimeUnit.SECONDS.sleep(interval);
		}
	}

	public static void reportData() throws Exception {
		for (String deviceId : device2Model.keySet()) {
			String data = createReportData(deviceId);
			RequestBody body = RequestBody.create(data, MediaType.parse("application/json"));
			Request request = new Request.Builder()
					.url(API_DATA_SOURCE)
					.addHeader("Content-Type", "application/json")
					.addHeader("X-Auth-Token", token)
					.post(body)
					.build();
			try (Response response = client.newCall(request).execute()) {
				if (response.isSuccessful()) {
					System.out.printf("Send Message Success: %s%n", deviceId);
				} else {
					System.out.printf("Failed to send message: %s %s%n", deviceId, response.message());
				}
			}
		}

	}

	public static String createReportData(String deviceId) throws Exception {
		Map<String, Map<String, PropertyDataType>> propertyDataTypes = modelInfo.get(device2Model.get(deviceId));
		ArrayNode services = mapper.createArrayNode();
		for (Map.Entry<String, Map<String, PropertyDataType>> entry : propertyDataTypes.entrySet()) {
			String serviceId = entry.getKey();
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

    public static String getEventTime() {
		return dateTimeFormatter.format(Instant.now());
	}

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	/**
	 * 获取访问令牌
	 *
	 * @param clientId  平台用户名
	 * @param secretId  平台用户密码
	 * @param projectId 项目ID
	 * @param tokenUrl  Token获取URL
	 * @return 响应结果
	 * @throws Exception 网络请求异常
	 */
	public static String getToken(String clientId, String secretId, String projectId, String tokenUrl) {
		// 构建JSON请求体
		String json = String.format(
				"{\"client_id\":\"%s\",\"secret_id\":\"%s\",\"project\":{\"id\":\"%s\"}}",
				clientId, secretId, projectId
		);

		RequestBody body = RequestBody.create(json, JSON);

		// 构建请求
		Request request = new Request.Builder()
				.url(tokenUrl)
                .addHeader("Content-Type", "application/json")
				.post(body)
				.build();

		// 发送请求并获取响应
		try (Response response = createUnsafeOkHttpClient().newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new RuntimeException("获取token失败: " + response.code() + " " + response.message());
			}

			return response.header("X-Subject-Token");
		} catch (Exception e) {
			System.out.println("获取token失败: " + e.getMessage());
		}
		return "";
	}

	public static OkHttpClient createUnsafeOkHttpClient() {
		try {
			// 创建信任所有证书的TrustManager
			final TrustManager[] trustAllCerts = new TrustManager[]{
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
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
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
