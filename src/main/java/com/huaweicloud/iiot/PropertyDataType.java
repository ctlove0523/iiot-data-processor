package com.huaweicloud.iiot;

import lombok.Getter;
import lombok.Setter;

import java.util.Random;

@Getter
@Setter
public class PropertyDataType {
    private static final Random random = new Random();
    private String type;
    private int minLength;
    private int maxLength;
    private int minIntValue;
    private int maxIntValue;
    private double minDoubleValue;
    private double maxDoubleValue;
}
