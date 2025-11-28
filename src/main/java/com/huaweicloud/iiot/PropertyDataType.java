package com.huaweicloud.iiot;

import lombok.Getter;
import lombok.Setter;

import java.util.Random;
import java.util.UUID;

@Getter
@Setter
public class PropertyDataType {
    private static final Random random = new Random();
    private String type;
    private int maxLength;
    private int minIntValue;
    private int maxIntValue;
    private double minDoubleValue;
    private double maxDoubleValue;

    public Object getValue() {
        switch (type) {
            case "string" -> {
                String value = UUID.randomUUID().toString();
                if (value.length() > maxLength) {
                    value = value.substring(0, maxLength);
                }
                return value;
            }

            case "integer" -> {
                return random.nextInt(maxIntValue - minIntValue + 1) + minIntValue;
            }
            case "double" -> {
                return random.nextDouble() * (maxDoubleValue - minDoubleValue) + minDoubleValue;
            }
            case "bool" -> {
                return random.nextBoolean();
            }
            default -> {
                return null;
            }
        }
    }
}
